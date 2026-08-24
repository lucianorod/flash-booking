package com.flashbooking.reservation

import com.flashbooking.reservation.dto.CreateReservationRequest
import com.flashbooking.reservation.dto.ReservationDetailResponse
import com.flashbooking.reservation.dto.ReservationResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class ReservationController(
	private val reservationService: ReservationService,
	private val reservationQueryService: ReservationQueryService,
	private val reservationCancellationService: ReservationCancellationService
) {

	private val log = LoggerFactory.getLogger(ReservationController::class.java)

	@PostMapping("/events/{eventId}/reservations")
	fun createReservation(
		@PathVariable eventId: UUID,
		@RequestHeader("Idempotency-Key") idempotencyKey: String,
		@Valid @RequestBody request: CreateReservationRequest
	): ResponseEntity<ReservationResponse> {
		log.info(
			"Requisição recebida: POST /events/{}/reservations, userId={}, quantity={}, idempotencyKey={}",
			eventId, request.userId, request.quantity, idempotencyKey
		)
		val result = reservationService.createReservation(eventId, idempotencyKey, request)
		val status = if (result.alreadyProcessed) HttpStatus.OK else HttpStatus.CREATED
		log.info(
			"Requisição concluída: POST /events/{}/reservations, status={}, reservationId={}",
			eventId, status.value(), result.response.reservationId
		)
		return ResponseEntity.status(status).body(result.response)
	}

	@GetMapping("/reservations/{id}")
	fun getReservation(@PathVariable id: UUID): ReservationDetailResponse {
		log.info("Requisição recebida: GET /reservations/{}", id)
		val response = reservationQueryService.getReservation(id)
		log.info("Requisição concluída: GET /reservations/{}, status=200", id)
		return response
	}

	@DeleteMapping("/reservations/{id}")
	fun cancelReservation(@PathVariable id: UUID): ResponseEntity<Void> {
		log.info("Requisição recebida: DELETE /reservations/{}", id)
		reservationCancellationService.cancelReservation(id)
		log.info("Requisição concluída: DELETE /reservations/{}, status=204", id)
		return ResponseEntity.noContent().build()
	}
}
