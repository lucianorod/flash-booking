package com.flashbooking.reservation

import java.util.UUID

class ReservationExpiredException(reservationId: UUID) : RuntimeException("Reserva expirada: $reservationId")
