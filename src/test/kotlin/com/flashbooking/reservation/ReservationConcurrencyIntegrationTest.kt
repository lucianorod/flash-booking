package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityCache
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.awaitUntil
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(TestRedisConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationConcurrencyIntegrationTest {

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

	@Test
	fun `duas requisicoes concorrentes disputando o ultimo ingresso resultam em exatamente uma aceita`() {
		val eventId = RestAssured.given()
			.contentType(ContentType.JSON)
			.body("""{"name": "Ultimo Ingresso", "totalCapacity": 1}""")
			.`when`()
			.post("/events")
			.then()
			.statusCode(201)
			.extract()
			.path<String>("id")

		val executor = Executors.newFixedThreadPool(2)
		val readyLatch = CountDownLatch(2)
		val goLatch = CountDownLatch(1)

		val requests = (1..2).map { index ->
			executor.submit<Int> {
				readyLatch.countDown()
				goLatch.await()
				RestAssured.given()
					.contentType(ContentType.JSON)
					.header("Idempotency-Key", "concurrency-test-$index-${UUID.randomUUID()}")
					.body("""{"userId": "${UUID.randomUUID()}", "quantity": 1}""")
					.`when`()
					.post("/events/$eventId/reservations")
					.then()
					.extract()
					.statusCode()
			}
		}

		readyLatch.await(5, TimeUnit.SECONDS)
		goLatch.countDown()

		val statusCodes = requests.map { it.get(10, TimeUnit.SECONDS) }
		executor.shutdown()

		assertEquals(1, statusCodes.count { it == 201 }, "esperado exatamente uma requisição aceita (201)")
		assertEquals(1, statusCodes.count { it == 409 }, "esperado exatamente uma requisição recusada por estoque insuficiente (409)")

		val available = redisTemplate.opsForValue()
			.get(EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)))
		assertEquals("0", available)

		awaitUntil { reservationRepository.count() == 1L }
	}
}
