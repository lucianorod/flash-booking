package com.flashbooking.event.dto

import java.util.UUID

data class EventAvailabilityResponse(
	val eventId: UUID,
	val availableCapacity: Int
)
