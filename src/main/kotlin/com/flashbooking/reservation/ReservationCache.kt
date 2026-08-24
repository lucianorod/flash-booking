package com.flashbooking.reservation

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReservationCache(private val redisTemplate: StringRedisTemplate) {

	fun getReservation(reservationId: UUID): ReservationSnapshot? =
		try {
			val fields = redisTemplate.opsForHash<String, String>()
				.entries(ReservationLuaExecutor.reservationRedisKey(reservationId))

			if (fields.isEmpty()) {
				null
			} else {
				ReservationSnapshot(
					id = reservationId,
					eventId = UUID.fromString(fields.getValue("eventId")),
					userId = UUID.fromString(fields.getValue("userId")),
					quantity = fields.getValue("quantity").toInt(),
					status = ReservationStatus.valueOf(fields.getValue("status")),
					expiresAt = Instant.parse(fields.getValue("expiresAt"))
				)
			}
		} catch (_: DataAccessException) {
			null
		}

	fun repopulate(reservation: Reservation) {
		redisTemplate.opsForHash<String, String>().putAll(
			ReservationLuaExecutor.reservationRedisKey(reservation.id),
			mapOf(
				"eventId" to reservation.eventId.toString(),
				"userId" to reservation.userId.toString(),
				"quantity" to reservation.quantity.toString(),
				"status" to reservation.status.name,
				"expiresAt" to reservation.expiresAt.toString()
			)
		)
	}
}
