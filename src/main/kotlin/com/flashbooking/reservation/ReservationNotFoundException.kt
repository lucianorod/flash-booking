package com.flashbooking.reservation

import java.util.UUID

class ReservationNotFoundException(reservationId: UUID) : RuntimeException("Reserva não encontrada: $reservationId")
