package com.flashbooking.reservation.exception

import java.util.UUID

class InsufficientStockException(eventId: UUID) : RuntimeException("Saldo insuficiente para o evento $eventId")
