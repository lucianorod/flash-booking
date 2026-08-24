package com.flashbooking.reservation.exception

import java.util.UUID

class ReservationExpiredException(reservationId: UUID) : RuntimeException("Reserva expirada: $reservationId")
