package com.flashbooking.reservation

sealed interface ReservationCancellationResult {
	data object Cancelled : ReservationCancellationResult
	data object AlreadyCancelled : ReservationCancellationResult
	data object AlreadyExpired : ReservationCancellationResult
	data object NotFound : ReservationCancellationResult

	companion object {
		fun from(raw: List<*>): ReservationCancellationResult =
			when (val status = raw[0] as String) {
				"CANCELLED" -> Cancelled
				"ALREADY_CANCELLED" -> AlreadyCancelled
				"ALREADY_EXPIRED" -> AlreadyExpired
				"NOT_FOUND" -> NotFound
				else -> error("Resultado inesperado do script de cancelamento: $status")
			}
	}
}
