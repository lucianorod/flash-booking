package com.flashbooking.reservation

import java.util.UUID

sealed interface ReservationScriptResult {
	data class Created(val reservationId: UUID) : ReservationScriptResult
	data class Idempotent(val reservationId: UUID) : ReservationScriptResult
	data object InsufficientStock : ReservationScriptResult

	companion object {
		fun from(raw: List<*>): ReservationScriptResult {
            return when (val status = raw[0] as String) {
				"CREATED" -> Created(UUID.fromString(raw[1] as String))
				"IDEMPOTENT" -> Idempotent(UUID.fromString(raw[1] as String))
				"INSUFFICIENT_STOCK" -> InsufficientStock
				else -> error("Resultado inesperado do script de reserva: $status")
			}
		}
	}
}
