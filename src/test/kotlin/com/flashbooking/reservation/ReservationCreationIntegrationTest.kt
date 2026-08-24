package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityCache
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.awaitUntil
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

@Import(TestRedisConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationCreationIntegrationTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@BeforeEach
	fun setUp() {
		RestAssured.port = port
		reservationRepository.deleteAll()
		eventRepository.deleteAll()
	}

	private fun createEvent(totalCapacity: Int): String =
		RestAssured.given()
			.contentType(ContentType.JSON)
			.body("""{"name": "Show de Teste", "totalCapacity": $totalCapacity}""")
			.`when`()
			.post("/events")
			.then()
			.statusCode(201)
			.extract()
			.path("id")

	private fun availableCapacity(eventId: String): String? =
		redisTemplate.opsForValue().get(EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)))

	@Test
	fun `deve aceitar reserva com saldo suficiente e persistir eventualmente no Postgres`() {
		val eventId = createEvent(totalCapacity = 10)
		val userId = UUID.randomUUID()
		val idempotencyKey = UUID.randomUUID().toString()

		val reservationId = RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", idempotencyKey)
			.body("""{"userId": "$userId", "quantity": 3}""")
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(201)
			.body("reservationId", notNullValue())
			.body("status", equalTo("PENDING"))
			.extract()
			.path<String>("reservationId")

		assertEquals("7", availableCapacity(eventId))

		val reservationUuid = UUID.fromString(reservationId)
		awaitUntil { reservationRepository.findById(reservationUuid).isPresent }

		val persisted = reservationRepository.findById(reservationUuid).orElseThrow()
		assertEquals(UUID.fromString(eventId), persisted.eventId)
		assertEquals(userId, persisted.userId)
		assertEquals(3, persisted.quantity)
		assertEquals(ReservationStatus.PENDING, persisted.status)
		assertEquals(idempotencyKey, persisted.idempotencyKey)
	}

	@Test
	fun `deve retornar a mesma reserva ao reenviar a mesma Idempotency-Key`() {
		val eventId = createEvent(totalCapacity = 10)
		val requestBody = """{"userId": "${UUID.randomUUID()}", "quantity": 2}"""
		val idempotencyKey = UUID.randomUUID().toString()

		val firstReservationId = RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", idempotencyKey)
			.body(requestBody)
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(201)
			.extract()
			.path<String>("reservationId")

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", idempotencyKey)
			.body(requestBody)
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(200)
			.body("reservationId", equalTo(firstReservationId))

		assertEquals("8", availableCapacity(eventId))
		awaitUntil { reservationRepository.findById(UUID.fromString(firstReservationId)).isPresent }
	}

	@Test
	fun `deve recusar reserva quando estoque for insuficiente`() {
		val eventId = createEvent(totalCapacity = 2)

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.body("""{"userId": "${UUID.randomUUID()}", "quantity": 5}""")
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(409)

		assertEquals("2", availableCapacity(eventId))
		assertEquals(0, reservationRepository.count())
	}

	@Test
	fun `deve retornar 404 ao criar reserva para evento inexistente`() {
		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.body("""{"userId": "${UUID.randomUUID()}", "quantity": 1}""")
			.`when`()
			.post("/events/${UUID.randomUUID()}/reservations")
			.then()
			.statusCode(404)

		assertEquals(0, reservationRepository.count())
	}

	@Test
	fun `deve retornar 400 quando o header Idempotency-Key estiver ausente`() {
		val eventId = createEvent(totalCapacity = 10)

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body("""{"userId": "${UUID.randomUUID()}", "quantity": 1}""")
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(400)
	}

	@Test
	fun `deve retornar 400 quando a quantidade for invalida`() {
		val eventId = createEvent(totalCapacity = 10)

		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.body("""{"userId": "${UUID.randomUUID()}", "quantity": 0}""")
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(400)
	}
}
