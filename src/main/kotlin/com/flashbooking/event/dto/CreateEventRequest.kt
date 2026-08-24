package com.flashbooking.event.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateEventRequest(
	@field:NotBlank(message = "O nome do evento é obrigatório")
	val name: String?,

	@field:NotNull(message = "A capacidade total é obrigatória")
	@field:Positive(message = "A capacidade total deve ser maior que zero")
	val totalCapacity: Int?
)
