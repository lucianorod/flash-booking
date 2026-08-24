package com.flashbooking.reservation

import com.flashbooking.event.EventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class ReservationPersistenceService(
	private val reservationRepository: ReservationRepository,
	private val eventRepository: EventRepository
) {

	@Transactional
	fun persistCreated(reservation: Reservation): Boolean {
		if (reservationRepository.existsById(reservation.id)) {
			return false
		}
		reservationRepository.save(reservation)
		eventRepository.decrementAvailableCapacity(reservation.eventId, reservation.quantity)
		return true
	}

	@Transactional
	fun applyCancellation(reservationId: UUID, eventId: UUID, quantity: Int): Boolean {
		val updated = reservationRepository.transitionStatus(
			id = reservationId,
			fromStatuses = listOf(ReservationStatus.PENDING, ReservationStatus.CONFIRMED),
			newStatus = ReservationStatus.CANCELLED
		)
		if (updated == 0) return false
		eventRepository.incrementAvailableCapacity(eventId, quantity)
		return true
	}

	@Transactional
	fun applyExpiration(reservationId: UUID, eventId: UUID, quantity: Int): Boolean {
		val updated = reservationRepository.transitionStatus(
			id = reservationId,
			fromStatuses = listOf(ReservationStatus.PENDING),
			newStatus = ReservationStatus.EXPIRED
		)
		if (updated == 0) return false
		eventRepository.incrementAvailableCapacity(eventId, quantity)
		return true
	}
}
