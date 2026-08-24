package com.flashbooking.reservation

import com.flashbooking.reservation.exception.ReservationExpiredException
import com.flashbooking.reservation.exception.ReservationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ReservationCancellationService(private val reservationLuaExecutor: ReservationLuaExecutor) {

	private val log = LoggerFactory.getLogger(ReservationCancellationService::class.java)

	fun cancelReservation(reservationId: UUID) {
		val result = reservationLuaExecutor.cancel(reservationId)
		log.info("Cancelamento de reserva {}: resultado={}", reservationId, result)
		when (result) {
			ReservationCancellationResult.Cancelled,
			ReservationCancellationResult.AlreadyCancelled -> Unit

			ReservationCancellationResult.AlreadyExpired -> throw ReservationExpiredException(reservationId)
			ReservationCancellationResult.NotFound -> throw ReservationNotFoundException(reservationId)
		}
	}
}
