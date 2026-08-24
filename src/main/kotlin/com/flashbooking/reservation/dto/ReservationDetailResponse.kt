package com.flashbooking.reservation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

data class ReservationDetailResponse(
	val id: UUID,

	@field:JsonProperty("event_id")
	val eventId: UUID,

	@field:JsonProperty("user_id")
	val userId: UUID,

	val quantity: Int,
	val status: String,

	@field:JsonProperty("expires_at")
	val expiresAt: Instant
)
