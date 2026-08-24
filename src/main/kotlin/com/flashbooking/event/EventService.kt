package com.flashbooking.event

import com.flashbooking.event.dto.CreateEventRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
	private val eventRepository: EventRepository,
	private val eventAvailabilityCache: EventAvailabilityCache
) {

	private val log = LoggerFactory.getLogger(EventService::class.java)

	@Transactional
	fun createEvent(request: CreateEventRequest): Event {
		val event = eventRepository.save(
			Event(
				name = requireNotNull(request.name),
				totalCapacity = requireNotNull(request.totalCapacity)
			)
		)
		eventAvailabilityCache.initializeAvailability(requireNotNull(event.id), event.availableCapacity)
		log.info("Evento criado: id={}, name={}, totalCapacity={}", event.id, event.name, event.totalCapacity)
		return event
	}
}
