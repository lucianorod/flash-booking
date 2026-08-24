package com.flashbooking.event.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class EventAvailabilityResponse(
	@field:JsonProperty("event_id")
	val eventId: UUID,

	@field:JsonProperty("available_capacity")
	val availableCapacity: Int
)
