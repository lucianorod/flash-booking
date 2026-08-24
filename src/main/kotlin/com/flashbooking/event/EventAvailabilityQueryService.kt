package com.flashbooking.event

import com.flashbooking.event.dto.EventAvailabilityResponse
import com.flashbooking.event.exception.EventNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class EventAvailabilityQueryService(
	private val eventAvailabilityCache: EventAvailabilityCache,
	private val eventRepository: EventRepository
) {

	private val log = LoggerFactory.getLogger(EventAvailabilityQueryService::class.java)

	fun getAvailability(eventId: UUID): EventAvailabilityResponse {
		val cached = eventAvailabilityCache.getAvailability(eventId)
		if (cached != null) {
			return EventAvailabilityResponse(
				eventId = eventId,
				name = cached.name,
				availableCapacity = cached.availableCapacity
			)
		}

		val event = eventRepository.findById(eventId).orElseThrow { EventNotFoundException(eventId) }

		try {
			eventAvailabilityCache.initializeAvailability(eventId, event.name, event.availableCapacity)
		} catch (ex: DataAccessException) {
			log.warn("Não foi possível repopular a disponibilidade no Redis para o evento {}", eventId, ex)
		}

		return EventAvailabilityResponse(
			eventId = eventId,
			name = event.name,
			availableCapacity = event.availableCapacity
		)
	}
}
