package com.flashbooking.reservation.dto

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.util.UUID

data class CreateReservationRequest(
	@field:NotNull(message = "O identificador do usuário é obrigatório")
	@field:JsonProperty("user_id")
    var userId: UUID?,

	@field:NotNull(message = "A quantidade é obrigatória")
	@field:Positive(message = "A quantidade deve ser maior que zero")
    var quantity: Int?
)
