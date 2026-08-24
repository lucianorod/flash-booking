package com.flashbooking.reservation.exception

import java.util.UUID

class ReservationNotFoundException(reservationId: UUID) : RuntimeException("Reserva não encontrada: $reservationId")
