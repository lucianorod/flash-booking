package com.flashbooking.event

import com.flashbooking.testsupport.TestRedisConfiguration
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import

@Import(TestRedisConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventCreationIntegrationTest {

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
	fun `deve criar evento com dados validos e retornar 201`() {
		val requestBody = """
			{
				"name": "Show de Rock",
				"totalCapacity": 100
			}
		""".trimIndent()

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(201)
			.body("id", notNullValue())
			.body("name", equalTo("Show de Rock"))
			.body("totalCapacity", equalTo(100))
			.body("availableCapacity", equalTo(100))
			.body("status", equalTo("PUBLISHED"))
			.body("createdAt", notNullValue())
			.body("updatedAt", notNullValue())
	}

	@Test
	fun `deve retornar 400 quando nome do evento estiver ausente`() {
		val requestBody = """
			{
				"totalCapacity": 100
			}
		""".trimIndent()

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(400)
			.body("status", equalTo(400))
			.body("fieldErrors.field", hasItem("name"))

		org.junit.jupiter.api.Assertions.assertEquals(0, eventRepository.count())
	}

	@Test
	fun `deve retornar 400 quando corpo da requisicao estiver malformado`() {
		val malformedBody = """{ "name": "Show de Rock", "totalCapacity": }"""

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body(malformedBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(400)
			.body("status", equalTo(400))
			.body("message", notNullValue())

		org.junit.jupiter.api.Assertions.assertEquals(0, eventRepository.count())
	}

	@Test
	fun `deve retornar 400 quando capacidade total for invalida`() {
		val requestBody = """
			{
				"name": "Show de Rock",
				"totalCapacity": 0
			}
		""".trimIndent()

		RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(400)
			.body("status", equalTo(400))
			.body("fieldErrors.field", hasItem("totalCapacity"))

		org.junit.jupiter.api.Assertions.assertEquals(0, eventRepository.count())
	}

	@Test
	fun `evento criado deve nascer publicado com capacidade disponivel igual a total e timestamps preenchidos`() {
		val requestBody = """
			{
				"name": "Peca de Teatro",
				"totalCapacity": 50
			}
		""".trimIndent()

		val eventId = RestAssured.given()
			.contentType(ContentType.JSON)
			.body(requestBody)
			.`when`()
			.post("/events")
			.then()
			.statusCode(201)
			.extract()
			.path<String>("id")

		val savedEvent = eventRepository.findById(java.util.UUID.fromString(eventId)).orElseThrow()

		org.junit.jupiter.api.Assertions.assertEquals(EventStatus.PUBLISHED, savedEvent.status)
		org.junit.jupiter.api.Assertions.assertEquals(savedEvent.totalCapacity, savedEvent.availableCapacity)
		org.junit.jupiter.api.Assertions.assertNotNull(savedEvent.createdAt)
		org.junit.jupiter.api.Assertions.assertNotNull(savedEvent.updatedAt)
	}
}
