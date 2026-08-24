package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityCache
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.awaitUntil
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
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
class ReservationQueryCancellationIntegrationTest {

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
			.path<String>("id")

	private fun createReservation(eventId: String, quantity: Int): String =
		RestAssured.given()
			.contentType(ContentType.JSON)
			.header("Idempotency-Key", UUID.randomUUID().toString())
			.body("""{"userId": "${UUID.randomUUID()}", "quantity": $quantity}""")
			.`when`()
			.post("/events/$eventId/reservations")
			.then()
			.statusCode(201)
			.extract()
			.path<String>("reservationId")

	private fun availableCapacity(eventId: String): String? =
		redisTemplate.opsForValue().get(EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)))

	@Test
	fun `deve retornar reserva recem-criada lendo apenas do Redis`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)

		RestAssured.given()
			.`when`()
			.get("/reservations/$reservationId")
			.then()
			.statusCode(200)
			.body("id", equalTo(reservationId))
			.body("eventId", equalTo(eventId))
			.body("quantity", equalTo(3))
			.body("status", equalTo("PENDING"))
	}

	@Test
	fun `deve consultar o Postgres e repopular o Redis quando o Hash nao existe`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 2)
		val reservationUuid = UUID.fromString(reservationId)

		awaitUntil { reservationRepository.findById(reservationUuid).isPresent }
		redisTemplate.delete(ReservationLuaExecutor.reservationRedisKey(reservationUuid))

		RestAssured.given()
			.`when`()
			.get("/reservations/$reservationId")
			.then()
			.statusCode(200)
			.body("id", equalTo(reservationId))
			.body("eventId", equalTo(eventId))
			.body("quantity", equalTo(2))
			.body("status", equalTo("PENDING"))

		val repopulated = redisTemplate.opsForHash<String, String>()
			.entries(ReservationLuaExecutor.reservationRedisKey(reservationUuid))
		assertEquals("PENDING", repopulated["status"])
	}

	@Test
	fun `deve retornar 404 ao consultar reserva inexistente`() {
		RestAssured.given()
			.`when`()
			.get("/reservations/${UUID.randomUUID()}")
			.then()
			.statusCode(404)
	}

	@Test
	fun `deve cancelar reserva pendente devolvendo o saldo e refletindo no Postgres`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)
		val reservationUuid = UUID.fromString(reservationId)
		assertEquals("7", availableCapacity(eventId))

		RestAssured.given()
			.`when`()
			.delete("/reservations/$reservationId")
			.then()
			.statusCode(204)

		assertEquals("10", availableCapacity(eventId))
		awaitUntil {
			reservationRepository.findById(reservationUuid).map { it.status == ReservationStatus.CANCELLED }.orElse(false)
		}
	}

	@Test
	fun `deve manter idempotencia ao cancelar reserva ja cancelada sem devolver saldo duas vezes`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)

		RestAssured.given().`when`().delete("/reservations/$reservationId").then().statusCode(204)
		assertEquals("10", availableCapacity(eventId))

		RestAssured.given()
			.`when`()
			.delete("/reservations/$reservationId")
			.then()
			.statusCode(204)

		assertEquals("10", availableCapacity(eventId))
	}

	@Test
	fun `deve retornar 409 ao cancelar reserva expirada sem devolver saldo`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)
		val reservationUuid = UUID.fromString(reservationId)
		redisTemplate.opsForHash<String, String>()
			.put(ReservationLuaExecutor.reservationRedisKey(reservationUuid), "status", "EXPIRED")
		assertEquals("7", availableCapacity(eventId))

		RestAssured.given()
			.`when`()
			.delete("/reservations/$reservationId")
			.then()
			.statusCode(409)

		assertEquals("7", availableCapacity(eventId))
	}

	@Test
	fun `deve retornar 404 ao cancelar reserva inexistente`() {
		RestAssured.given()
			.`when`()
			.delete("/reservations/${UUID.randomUUID()}")
			.then()
			.statusCode(404)
	}
}
