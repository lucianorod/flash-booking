package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityCache
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.awaitUntil
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.util.UUID

@Import(TestRedisConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationExpirationIntegrationTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@Autowired
	private lateinit var expirationSweepTask: ReservationExpirationSweepTask

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

	private fun forceExpired(reservationId: String) {
		redisTemplate.opsForZSet().add(
			ReservationLuaExecutor.PENDING_EXPIRATION_KEY,
			reservationId,
			Instant.now().minusSeconds(60).toEpochMilli().toDouble()
		)
	}

	@Test
	fun `deve expirar automaticamente reserva pendente com prazo vencido`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)
		val reservationUuid = UUID.fromString(reservationId)
		assertEquals("7", availableCapacity(eventId))

		forceExpired(reservationId)
		expirationSweepTask.expirePendingReservations()

		val hash = redisTemplate.opsForHash<String, String>()
			.entries(ReservationLuaExecutor.reservationRedisKey(reservationUuid))
		assertEquals("EXPIRED", hash["status"])
		assertEquals("10", availableCapacity(eventId))
		assertNull(redisTemplate.opsForZSet().score(ReservationLuaExecutor.PENDING_EXPIRATION_KEY, reservationId))

		awaitUntil {
			reservationRepository.findById(reservationUuid).map { it.status == ReservationStatus.EXPIRED }.orElse(false)
		}
	}

	@Test
	fun `nao deve expirar reserva ja cancelada antes do vencimento`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)
		val reservationUuid = UUID.fromString(reservationId)

		RestAssured.given().`when`().delete("/reservations/$reservationId").then().statusCode(204)
		assertEquals("10", availableCapacity(eventId))

		forceExpired(reservationId)
		expirationSweepTask.expirePendingReservations()

		assertEquals("10", availableCapacity(eventId))
		val hash = redisTemplate.opsForHash<String, String>()
			.entries(ReservationLuaExecutor.reservationRedisKey(reservationUuid))
		assertEquals("CANCELLED", hash["status"])
	}

	@Test
	fun `deve retornar 409 ao cancelar reserva apos expiracao automatica, sem devolver saldo novamente`() {
		val eventId = createEvent(totalCapacity = 10)
		val reservationId = createReservation(eventId, quantity = 3)

		forceExpired(reservationId)
		expirationSweepTask.expirePendingReservations()
		assertEquals("10", availableCapacity(eventId))

		RestAssured.given()
			.`when`()
			.delete("/reservations/$reservationId")
			.then()
			.statusCode(409)

		assertEquals("10", availableCapacity(eventId))
	}
}
