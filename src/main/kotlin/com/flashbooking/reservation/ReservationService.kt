package com.flashbooking.reservation

import com.flashbooking.event.EventAvailabilityQueryService
import com.flashbooking.reservation.config.ReservationProperties
import com.flashbooking.reservation.dto.CreateReservationRequest
import com.flashbooking.reservation.dto.ReservationResponse
import com.flashbooking.reservation.exception.InsufficientStockException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class ReservationService(
	private val reservationLuaExecutor: ReservationLuaExecutor,
	private val reservationProperties: ReservationProperties,
	private val eventAvailabilityQueryService: EventAvailabilityQueryService
) {

	private val log = LoggerFactory.getLogger(ReservationService::class.java)

	fun createReservation(
		eventId: UUID,
		idempotencyKey: String,
		request: CreateReservationRequest
	): ReservationCreationResult {
		eventAvailabilityQueryService.getAvailability(eventId)

		val reservationId = UUID.randomUUID()
		val expiresAt = Instant.now().plus(reservationProperties.holdMinutes, ChronoUnit.MINUTES)

		return when (
			val result = reservationLuaExecutor.reserve(
				reservationId = reservationId,
				eventId = eventId,
				userId = requireNotNull(request.userId),
				quantity = requireNotNull(request.quantity),
				expiresAt = expiresAt,
				idempotencyKey = idempotencyKey,
				idempotencyTtlSeconds = reservationProperties.idempotencyTtlSeconds
			)
		) {
			is ReservationScriptResult.Created -> {
				log.info(
					"Reserva aceita: reservationId={}, eventId={}, userId={}, quantity={}",
					result.reservationId, eventId, request.userId, request.quantity
				)
				ReservationCreationResult(
					response = ReservationResponse(result.reservationId, "PENDING"),
					alreadyProcessed = false
				)
			}

			is ReservationScriptResult.Idempotent -> {
				log.info(
					"Reserva já processada anteriormente para esta chave de idempotência: reservationId={}, eventId={}",
					result.reservationId, eventId
				)
				ReservationCreationResult(
					response = ReservationResponse(result.reservationId, "PENDING"),
					alreadyProcessed = true
				)
			}

			ReservationScriptResult.InsufficientStock -> {
				log.warn(
					"Reserva recusada por saldo insuficiente: eventId={}, quantity={}",
					eventId, request.quantity
				)
				throw InsufficientStockException(eventId)
			}
		}
	}
}
