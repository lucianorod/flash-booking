package com.flashbooking.common.error

import java.time.Instant

data class ErrorResponse(
	val timestamp: Instant,
	val status: Int,
	val error: String,
	val message: String,
	val fieldErrors: List<FieldErrorDetail> = emptyList()
)

data class FieldErrorDetail(
	val field: String,
	val message: String
)
