package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityCache
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReservationLuaExecutor(private val redisTemplate: StringRedisTemplate) {

	private val log = LoggerFactory.getLogger(ReservationLuaExecutor::class.java)

	@Suppress("UNCHECKED_CAST")
	private val reserveScript: DefaultRedisScript<List<*>> = DefaultRedisScript<List<*>>().apply {
		setLocation(ClassPathResource("reserve_tickets.lua"))
		resultType = List::class.java
	}

	@Suppress("UNCHECKED_CAST")
	private val cancelScript: DefaultRedisScript<List<*>> = DefaultRedisScript<List<*>>().apply {
		setLocation(ClassPathResource("cancel_reservation.lua"))
		resultType = List::class.java
	}

	@Suppress("UNCHECKED_CAST")
	private val expireScript: DefaultRedisScript<List<*>> = DefaultRedisScript<List<*>>().apply {
		setLocation(ClassPathResource("expire_reservations.lua"))
		resultType = List::class.java
	}

	fun reserve(
		reservationId: UUID,
		eventId: UUID,
		userId: UUID,
		quantity: Int,
		expiresAt: Instant,
		idempotencyKey: String,
		idempotencyTtlSeconds: Long
	): ReservationScriptResult {
		val keys = listOf(
			EventAvailabilityCache.availabilityKey(eventId),
			idempotencyRedisKey(idempotencyKey),
			reservationRedisKey(reservationId),
			STREAM_KEY,
			PENDING_EXPIRATION_KEY
		)
		val raw: List<*> = redisTemplate.execute(
			reserveScript,
			keys,
			reservationId.toString(),
			eventId.toString(),
			userId.toString(),
			quantity.toString(),
			expiresAt.toString(),
			idempotencyTtlSeconds.toString(),
			idempotencyKey,
			expiresAt.toEpochMilli().toString()
		)
		log.debug("Resultado bruto do script de reserva para reservationId={}: {}", reservationId, raw)

		return ReservationScriptResult.from(raw)
	}

	fun cancel(reservationId: UUID): ReservationCancellationResult {
		val keys = listOf(
			reservationRedisKey(reservationId),
			STREAM_KEY,
			PENDING_EXPIRATION_KEY
		)
		val raw: List<*> = redisTemplate.execute(
			cancelScript,
			keys,
			reservationId.toString()
		)
		log.debug("Resultado bruto do script de cancelamento para reservationId={}: {}", reservationId, raw)

		return ReservationCancellationResult.from(raw)
	}

	fun expirePendingReservations(batchSize: Int): Int {
		val keys = listOf(PENDING_EXPIRATION_KEY, STREAM_KEY)
		val raw: List<*> = redisTemplate.execute(
			expireScript,
			keys,
			Instant.now().toEpochMilli().toString(),
			batchSize.toString()
		)
		log.debug("Resultado bruto do script de expiração: {}", raw)
		return (raw[1] as String).toInt()
	}

	companion object {
		const val STREAM_KEY = "stream:reservations"
		const val PENDING_EXPIRATION_KEY = "reservations:pending-expiration"

		fun reservationRedisKey(reservationId: UUID): String = "reservation:$reservationId"
		fun idempotencyRedisKey(idempotencyKey: String): String = "idempotency:$idempotencyKey"
	}
}
