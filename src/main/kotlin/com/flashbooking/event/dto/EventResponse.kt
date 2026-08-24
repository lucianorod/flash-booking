package com.flashbooking.event.dto

import com.flashbooking.event.Event
import com.flashbooking.event.EventStatus
import java.time.Instant
import java.util.UUID

data class EventResponse(
	val id: UUID,
	val name: String,
	val totalCapacity: Int,
	val availableCapacity: Int,
	val status: EventStatus,
	val createdAt: Instant,
	val updatedAt: Instant
) {
	companion object {
		fun from(event: Event): EventResponse = EventResponse(
			id = requireNotNull(event.id),
			name = event.name,
			totalCapacity = event.totalCapacity,
			availableCapacity = event.availableCapacity,
			status = event.status,
			createdAt = requireNotNull(event.createdAt),
			updatedAt = requireNotNull(event.updatedAt)
		)
	}
}
