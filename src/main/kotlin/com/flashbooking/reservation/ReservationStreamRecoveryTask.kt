package com.flashbooking.reservation

import com.flashbooking.reservation.config.ReservationConsumerIdentity
import com.flashbooking.reservation.config.ReservationProperties
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Range
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ReservationStreamRecoveryTask(
	private val redisTemplate: StringRedisTemplate,
	private val listener: ReservationStreamListener,
	private val consumerIdentity: ReservationConsumerIdentity,
	private val reservationProperties: ReservationProperties
) {

	private val log = LoggerFactory.getLogger(ReservationStreamRecoveryTask::class.java)

	@Scheduled(fixedDelayString = "\${flash-booking.reservation.stream-recovery-interval-ms}")
	fun recoverStalePendingMessages() {
		val minIdleTime = Duration.ofMillis(reservationProperties.streamRecoveryMinIdleTimeMs)

		val pending = redisTemplate.opsForStream<String, String>().pending(
			ReservationLuaExecutor.STREAM_KEY,
			ReservationStreamListener.CONSUMER_GROUP,
			Range.unbounded<String>(),
			PENDING_SCAN_BATCH_SIZE
		)

		val staleIds = pending.toList()
			.filter { it.elapsedTimeSinceLastDelivery >= minIdleTime }
			.map { it.id }

		if (staleIds.isEmpty()) {
			return
		}

		val claimed = redisTemplate.opsForStream<String, String>().claim(
			ReservationLuaExecutor.STREAM_KEY,
			ReservationStreamListener.CONSUMER_GROUP,
			consumerIdentity.name,
			minIdleTime,
			*staleIds.toTypedArray()
		)

		claimed.forEach { record ->
			log.warn(
				"Reivindicando mensagem pendente {} do stream de reservas, sem confirmação há mais de {}, para reprocessamento",
				record.id,
				minIdleTime
			)
			listener.onMessage(record)
		}
	}

	companion object {
		private const val PENDING_SCAN_BATCH_SIZE = 100L
	}
}
