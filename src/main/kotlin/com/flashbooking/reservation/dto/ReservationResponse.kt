package com.flashbooking.reservation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class ReservationResponse(
	@field:JsonProperty("reservation_id")
	val reservationId: UUID,

	val status: String
)
