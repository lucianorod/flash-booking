package com.flashbooking.reservation.dto

import java.time.Instant
import java.util.UUID

data class ReservationDetailResponse(
	val id: UUID,
	val eventId: UUID,
	val userId: UUID,
	val quantity: Int,
	val status: String,
	val expiresAt: Instant
)
