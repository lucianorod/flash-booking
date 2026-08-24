package com.flashbooking.reservation.dto

import java.util.UUID

data class ReservationResponse(
	val reservationId: UUID,
	val status: String
)
