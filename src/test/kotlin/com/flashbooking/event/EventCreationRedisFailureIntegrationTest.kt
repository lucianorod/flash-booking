package com.flashbooking.event

import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventCreationRedisFailureIntegrationTest {

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
	fun `nao deve persistir evento quando escrita da disponibilidade no Redis falhar`() {
		val requestBody = """
			{
				"name": "Show Cancelado por Falha no Redis",
				"totalCapacity": 10
			}
		""".trimIndent()

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(500)

		assertEquals(0, eventRepository.count())
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
