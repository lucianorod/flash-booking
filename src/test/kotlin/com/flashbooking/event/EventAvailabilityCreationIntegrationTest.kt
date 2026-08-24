package com.flashbooking.event

import com.flashbooking.testsupport.TestRedisConfiguration
import io.restassured.RestAssured
import io.restassured.http.ContentType
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
class EventAvailabilityCreationIntegrationTest {

	@LocalServerPort
	private var port: Int = 0

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@BeforeEach
	fun setUp() {
		RestAssured.port = port
		eventRepository.deleteAll()
	}

	@Test
	fun `deve inicializar disponibilidade no Redis ao criar evento com sucesso`() {
		val requestBody = """
			{
				"name": "Show de Rock",
				"totalCapacity": 1000
			}
		""".trimIndent()

		val eventId = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(201)
			.body("id", notNullValue())
			.extract()
			.path<String>("id")

		val availability = redisTemplate.opsForValue()
			.get(EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)))
		val name = redisTemplate.opsForValue()
			.get(EventAvailabilityCache.nameKey(UUID.fromString(eventId)))

		assertEquals("1000", availability)
		assertEquals("Show de Rock", name)
	}
}
