package com.flashbooking.reservation

import com.flashbooking.reservation.dto.ReservationResponse

data class ReservationCreationResult(
	val response: ReservationResponse,
	val alreadyProcessed: Boolean
)
