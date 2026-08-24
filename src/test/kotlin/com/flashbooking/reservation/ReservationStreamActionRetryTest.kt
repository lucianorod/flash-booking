package com.flashbooking.reservation

import com.flashbooking.event.Event
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.resetReservationStreamGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Import(TestRedisConfiguration::class)
@SpringBootTest(
	properties = [
		"flash-booking.reservation.stream-consumer.enabled=false",
		"flash-booking.reservation.action-message-max-retries=2"
	]
)
class ReservationStreamActionRetryTest {

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@Autowired
	private lateinit var reservationStreamListener: ReservationStreamListener

	@BeforeEach
	fun setUp() {
		reservationRepository.deleteAll()
		eventRepository.deleteAll()
		resetReservationStreamGroup(redisTemplate)
	}

	@Test
	fun `nao deve incrementar o saldo do evento duas vezes ao reprocessar o mesmo CANCEL`() {
		val event = eventRepository.save(Event(name = "Show com Reentrega", totalCapacity = 20))
		val reservation = reservationRepository.save(
			Reservation(
				id = UUID.randomUUID(),
				eventId = requireNotNull(event.id),
				userId = UUID.randomUUID(),
				quantity = 3,
				expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
				idempotencyKey = UUID.randomUUID().toString(),
				initialStatus = ReservationStatus.PENDING
			)
		)
		val record = fabricateRecord(mapOf("reservationId" to reservation.id.toString(), "action" to "CANCEL"))

		reservationStreamListener.onMessage(record)
		reservationStreamListener.onMessage(record)

		assertEquals(ReservationStatus.CANCELLED, reservationRepository.findById(reservation.id).orElseThrow().status)
		// A reserva foi inserida diretamente (sem passar pelo CREATE), então o saldo nunca foi decrementado;
		// o cancelamento processado duas vezes deve incrementá-lo uma única vez: 20 + 3, nunca 20 + 3 + 3.
		assertEquals(23, eventRepository.findById(requireNotNull(event.id)).orElseThrow().availableCapacity)
	}

	@Test
	fun `deve reencaminhar CANCEL quando a reserva ainda nao existe no Postgres, incrementando retryCount`() {
		val reservationId = UUID.randomUUID()
		val record = fabricateRecord(mapOf("reservationId" to reservationId.toString(), "action" to "CANCEL"))

		reservationStreamListener.onMessage(record)

		val republished = streamRecords()
		assertEquals(1, republished.size)
		assertEquals(reservationId.toString(), republished[0].value["reservationId"])
		assertEquals("1", republished[0].value["retryCount"])
	}

	@Test
	fun `deve tratar como mensagem envenenada apos esgotar as tentativas de CANCEL, sem novo reencaminhamento`() {
		val reservationId = UUID.randomUUID()

		reservationStreamListener.onMessage(
			fabricateRecord(mapOf("reservationId" to reservationId.toString(), "action" to "CANCEL"))
		)
		var republished = streamRecords()
		assertEquals(1, republished.size)
		assertEquals("1", republished[0].value["retryCount"])

		reservationStreamListener.onMessage(republished[0])
		republished = streamRecords()
		assertEquals(2, republished.size)
		assertEquals("2", republished[1].value["retryCount"])

		// max-retries = 2: a tentativa com retryCount = 2 esgota o limite e não deve gerar uma terceira mensagem.
		reservationStreamListener.onMessage(republished[1])
		republished = streamRecords()
		assertEquals(2, republished.size)
	}

	private fun streamRecords(): List<MapRecord<String, String, String>> =
		requireNotNull(redisTemplate.opsForStream<String, String>().range(ReservationLuaExecutor.STREAM_KEY, Range.unbounded()))

	private fun fabricateRecord(fields: Map<String, String>): MapRecord<String, String, String> =
		MapRecord.create(ReservationLuaExecutor.STREAM_KEY, fields).withId(RecordId.of("0-1"))
}
