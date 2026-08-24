package com.flashbooking.event.exception

import java.util.UUID

class EventNotFoundException(eventId: UUID) : RuntimeException("Evento não encontrado: $eventId")
