package com.flashbooking.event

import io.restassured.RestAssured
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventAvailabilityRedisDegradationIntegrationTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var eventRepository: EventRepository

	@BeforeEach
	fun setUp() {
		RestAssured.port = port
		eventRepository.deleteAll()
	}

	@Test
	fun `deve consultar o Postgres e responder normalmente quando o Redis estiver indisponivel`() {
		val event = eventRepository.save(Event(name = "Show Sem Redis", totalCapacity = 15))

		RestAssured.given()
			.`when`()
			.get("/events/${event.id}")
			.then()
			.statusCode(200)
			.body("eventId", equalTo(event.id.toString()))
			.body("availableCapacity", equalTo(15))
	}

	@Test
	fun `deve retornar 404 quando o evento nao existir mesmo com o Redis indisponivel`() {
		RestAssured.given()
			.`when`()
			.get("/events/${UUID.randomUUID()}")
			.then()
			.statusCode(404)
	}

	companion object {
		@JvmStatic
		@DynamicPropertySource
		fun redisProperties(registry: DynamicPropertyRegistry) {
			registry.add("spring.data.redis.host") { "localhost" }
			registry.add("spring.data.redis.port") { 1 }
		}
	}
}
