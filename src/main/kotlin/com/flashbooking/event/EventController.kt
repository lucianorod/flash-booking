package com.flashbooking.event

import com.flashbooking.event.dto.CreateEventRequest
import com.flashbooking.event.dto.EventAvailabilityResponse
import com.flashbooking.event.dto.EventResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/events")
class EventController(
	private val eventService: EventService,
	private val eventAvailabilityQueryService: EventAvailabilityQueryService
) {

	private val log = LoggerFactory.getLogger(EventController::class.java)

	@PostMapping
	fun createEvent(@Valid @RequestBody request: CreateEventRequest): ResponseEntity<EventResponse> {
		log.info("Requisição recebida: POST /events, name={}, totalCapacity={}", request.name, request.totalCapacity)
		val event = eventService.createEvent(request)
		log.info("Requisição concluída: POST /events, status=201, eventId={}", event.id)
		return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event))
	}

	@GetMapping("/{id}")
	fun getAvailability(@PathVariable id: UUID): EventAvailabilityResponse {
		log.info("Requisição recebida: GET /events/{}", id)
		val response = eventAvailabilityQueryService.getAvailability(id)
		log.info("Requisição concluída: GET /events/{}, status=200, availableCapacity={}", id, response.availableCapacity)
		return response
	}
}
