package com.flashbooking.reservation

import com.flashbooking.event.Event
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Import(TestRedisConfiguration::class)
@SpringBootTest
class ReservationStreamListenerTest {

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Autowired
	private lateinit var reservationStreamListener: ReservationStreamListener

	@BeforeEach
	fun setUp() {
		reservationRepository.deleteAll()
		eventRepository.deleteAll()
	}

	@Test
	fun `deve processar reentrega da mesma mensagem sem falhar e sem duplicar a reserva`() {
		val event = eventRepository.save(Event(name = "Show Reprocessado", totalCapacity = 20))
		val reservationId = UUID.randomUUID()
		val userId = UUID.randomUUID()
		val expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)

		val fields = mapOf(
			"reservationId" to reservationId.toString(),
			"eventId" to requireNotNull(event.id).toString(),
			"userId" to userId.toString(),
			"quantity" to "2",
			"status" to "PENDING",
			"expiresAt" to expiresAt.toString(),
			"idempotencyKey" to UUID.randomUUID().toString()
		)
		val record = MapRecord.create("stream:reservations", fields).withId(RecordId.of("0-1"))

		reservationStreamListener.onMessage(record)
		reservationStreamListener.onMessage(record)

		assertEquals(1, reservationRepository.count())
		val persisted = reservationRepository.findById(reservationId).orElseThrow()
		assertEquals(userId, persisted.userId)
		assertEquals(2, persisted.quantity)
	}
}
