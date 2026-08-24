package com.flashbooking.reservation

import com.flashbooking.reservation.config.ReservationProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ReservationExpirationSweepTask(
	private val reservationLuaExecutor: ReservationLuaExecutor,
	private val reservationProperties: ReservationProperties
) {

	private val log = LoggerFactory.getLogger(ReservationExpirationSweepTask::class.java)

	@Scheduled(fixedDelayString = "\${flash-booking.reservation.expiration-sweep-interval-ms}")
	fun expirePendingReservations() {
		val expiredCount = reservationLuaExecutor.expirePendingReservations(reservationProperties.expirationSweepBatchSize)
		if (expiredCount > 0) {
			log.info("Varredura de expiração processou {} reserva(s) vencida(s)", expiredCount)
		} else {
			log.debug("Varredura de expiração executada, nenhuma reserva vencida encontrada")
		}
	}
}
