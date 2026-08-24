package com.flashbooking.common.error

import com.flashbooking.event.EventNotFoundException
import com.flashbooking.reservation.InsufficientStockException
import com.flashbooking.reservation.ReservationExpiredException
import com.flashbooking.reservation.ReservationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

	private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

	@ExceptionHandler(MethodArgumentNotValidException::class)
	fun handleValidationError(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
		val fieldErrors = ex.bindingResult.fieldErrors.map {
			FieldErrorDetail(field = it.field, message = it.defaultMessage ?: "Valor inválido")
		}
		log.warn("Dados inválidos na requisição: {}", fieldErrors)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.BAD_REQUEST.value(),
			error = HttpStatus.BAD_REQUEST.reasonPhrase,
			message = "Dados inválidos para a criação do evento",
			fieldErrors = fieldErrors
		)
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleMalformedRequest(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
		log.warn("Corpo da requisição ausente ou malformado: {}", ex.message)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.BAD_REQUEST.value(),
			error = HttpStatus.BAD_REQUEST.reasonPhrase,
			message = "Corpo da requisição ausente ou malformado"
		)
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
	}

	@ExceptionHandler(MissingRequestHeaderException::class)
	fun handleMissingHeader(ex: MissingRequestHeaderException): ResponseEntity<ErrorResponse> {
		log.warn("Header obrigatório ausente: {}", ex.headerName)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.BAD_REQUEST.value(),
			error = HttpStatus.BAD_REQUEST.reasonPhrase,
			message = "Header obrigatório ausente: ${ex.headerName}"
		)
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body)
	}

	@ExceptionHandler(InsufficientStockException::class)
	fun handleInsufficientStock(ex: InsufficientStockException): ResponseEntity<ErrorResponse> {
		log.warn("Saldo insuficiente para reserva: {}", ex.message)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.CONFLICT.value(),
			error = HttpStatus.CONFLICT.reasonPhrase,
			message = "Saldo insuficiente para a quantidade solicitada"
		)
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
	}

	@ExceptionHandler(EventNotFoundException::class)
	fun handleEventNotFound(ex: EventNotFoundException): ResponseEntity<ErrorResponse> {
		log.warn("Evento não encontrado: {}", ex.message)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.NOT_FOUND.value(),
			error = HttpStatus.NOT_FOUND.reasonPhrase,
			message = "Evento não encontrado"
		)
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
	}

	@ExceptionHandler(ReservationNotFoundException::class)
	fun handleReservationNotFound(ex: ReservationNotFoundException): ResponseEntity<ErrorResponse> {
		log.warn("Reserva não encontrada: {}", ex.message)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.NOT_FOUND.value(),
			error = HttpStatus.NOT_FOUND.reasonPhrase,
			message = "Reserva não encontrada"
		)
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body)
	}

	@ExceptionHandler(ReservationExpiredException::class)
	fun handleReservationExpired(ex: ReservationExpiredException): ResponseEntity<ErrorResponse> {
		log.warn("Tentativa de cancelar reserva já expirada: {}", ex.message)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.CONFLICT.value(),
			error = HttpStatus.CONFLICT.reasonPhrase,
			message = "Reserva já expirou e não pode ser cancelada"
		)
		return ResponseEntity.status(HttpStatus.CONFLICT).body(body)
	}

	@ExceptionHandler(Exception::class)
	fun handleUnexpectedError(ex: Exception): ResponseEntity<ErrorResponse> {
		log.error("Erro inesperado ao processar a requisição", ex)
		val body = ErrorResponse(
			timestamp = Instant.now(),
			status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
			error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
			message = "Erro interno ao processar a requisição"
		)
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body)
	}
}
