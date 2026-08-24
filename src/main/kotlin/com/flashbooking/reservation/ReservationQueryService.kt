package com.flashbooking.reservation

import com.flashbooking.reservation.dto.ReservationDetailResponse
import com.flashbooking.reservation.exception.ReservationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ReservationQueryService(
	private val reservationCache: ReservationCache,
	private val reservationRepository: ReservationRepository
) {

	private val log = LoggerFactory.getLogger(ReservationQueryService::class.java)

	fun getReservation(reservationId: UUID): ReservationDetailResponse {
		val cached = reservationCache.getReservation(reservationId)
		if (cached != null) {
			return toResponse(cached)
		}

		val reservation = reservationRepository.findById(reservationId)
			.orElseThrow { ReservationNotFoundException(reservationId) }

		try {
			reservationCache.repopulate(reservation)
		} catch (ex: DataAccessException) {
			log.warn("Não foi possível repopular a reserva {} no Redis", reservationId, ex)
		}

		return ReservationDetailResponse(
			id = reservation.id,
			eventId = reservation.eventId,
			userId = reservation.userId,
			quantity = reservation.quantity,
			status = reservation.status.name,
			expiresAt = reservation.expiresAt
		)
	}

	private fun toResponse(snapshot: ReservationSnapshot): ReservationDetailResponse =
		ReservationDetailResponse(
			id = snapshot.id,
			eventId = snapshot.eventId,
			userId = snapshot.userId,
			quantity = snapshot.quantity,
			status = snapshot.status.name,
			expiresAt = snapshot.expiresAt
		)
}
