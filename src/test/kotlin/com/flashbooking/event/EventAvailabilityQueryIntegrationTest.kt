package com.flashbooking.event

import com.flashbooking.testsupport.TestRedisConfiguration
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
class EventAvailabilityQueryIntegrationTest {

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

	@Test
	fun `deve retornar disponibilidade a partir do Redis quando a chave existe`() {
		val eventId = createEvent(totalCapacity = 25)

		RestAssured.given()
			.`when`()
			.get("/events/$eventId")
			.then()
			.statusCode(200)
			.body("eventId", equalTo(eventId))
			.body("name", equalTo("Show de Teste"))
			.body("availableCapacity", equalTo(25))
	}

	@Test
	fun `deve consultar o Postgres e repopular o Redis quando a chave nao existe`() {
		val eventId = createEvent(totalCapacity = 40)
		redisTemplate.delete(listOf(
			EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)),
			EventAvailabilityCache.nameKey(UUID.fromString(eventId))
		))

		RestAssured.given()
			.`when`()
			.get("/events/$eventId")
			.then()
			.statusCode(200)
			.body("eventId", equalTo(eventId))
			.body("name", equalTo("Show de Teste"))
			.body("availableCapacity", equalTo(40))

		val repopulated = redisTemplate.opsForValue()
			.get(EventAvailabilityCache.availabilityKey(UUID.fromString(eventId)))
		assertEquals("40", repopulated)
		val repopulatedName = redisTemplate.opsForValue()
			.get(EventAvailabilityCache.nameKey(UUID.fromString(eventId)))
		assertEquals("Show de Teste", repopulatedName)
	}

	@Test
	fun `deve retornar 404 quando o evento nao existir`() {
		RestAssured.given()
			.`when`()
			.get("/events/${UUID.randomUUID()}")
			.then()
			.statusCode(404)
	}
}
