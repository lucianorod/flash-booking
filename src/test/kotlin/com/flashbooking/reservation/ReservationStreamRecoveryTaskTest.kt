package com.flashbooking.reservation

import com.flashbooking.event.Event
import com.flashbooking.event.EventRepository
import com.flashbooking.testsupport.TestRedisConfiguration
import com.flashbooking.testsupport.awaitUntil
import com.flashbooking.testsupport.resetReservationStreamGroup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Import(TestRedisConfiguration::class)
@SpringBootTest(
	properties = [
		"flash-booking.reservation.stream-consumer.enabled=false",
		"flash-booking.reservation.stream-recovery-min-idle-time-ms=0"
	]
)
class ReservationStreamRecoveryTaskTest {

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@Autowired
	private lateinit var redisTemplate: StringRedisTemplate

	@Autowired
	private lateinit var recoveryTask: ReservationStreamRecoveryTask

	@BeforeEach
	fun setUp() {
		reservationRepository.deleteAll()
		eventRepository.deleteAll()
		resetReservationStreamGroup(redisTemplate)
	}

	@Test
	fun `deve reivindicar e reprocessar mensagem pendente de um consumidor que nunca confirmou`() {
		val event = eventRepository.save(Event(name = "Show Recuperado", totalCapacity = 10))
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
		redisTemplate.opsForStream<String, String>().add(ReservationLuaExecutor.STREAM_KEY, fields)

		// Simula uma instância que recebeu a mensagem e caiu antes de confirmá-la (sem XACK).
		redisTemplate.opsForStream<String, String>().read(
			Consumer.from(ReservationStreamListener.CONSUMER_GROUP, "reservation-worker-instancia-caida"),
			StreamReadOptions.empty().count(1),
			StreamOffset.create(ReservationLuaExecutor.STREAM_KEY, ReadOffset.lastConsumed())
		)

		recoveryTask.recoverStalePendingMessages()

		awaitUntil { reservationRepository.findById(reservationId).isPresent }
	}
}
