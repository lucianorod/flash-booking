package com.flashbooking.reservation

import java.time.Instant
import java.util.UUID

data class ReservationSnapshot(
	val id: UUID,
	val eventId: UUID,
	val userId: UUID,
	val quantity: Int,
	val status: ReservationStatus,
	val expiresAt: Instant
)
