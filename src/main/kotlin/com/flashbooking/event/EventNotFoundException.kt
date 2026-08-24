package com.flashbooking.event

import java.util.UUID

class EventNotFoundException(eventId: UUID) : RuntimeException("Evento não encontrado: $eventId")
