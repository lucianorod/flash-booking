package com.flashbooking.reservation

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReservationStreamListener(
	private val reservationRepository: ReservationRepository,
	private val redisTemplate: StringRedisTemplate,
	private val reservationProperties: ReservationProperties
) : StreamListener<String, MapRecord<String, String, String>> {

	private val log = LoggerFactory.getLogger(ReservationStreamListener::class.java)

	override fun onMessage(record: MapRecord<String, String, String>) {
		val fields = record.value
		try {
			when (fields["action"] ?: "CREATE") {
				"CREATE" -> handleCreate(fields)
				"CANCEL" -> handleCancel(fields)
				"EXPIRE" -> handleExpire(fields)
				else -> log.warn("Ação desconhecida em mensagem do stream de reservas: {}", fields)
			}
		} catch (ex: Exception) {
			log.error("Falha ao processar a mensagem {} do stream de reservas: {}", record.id, fields, ex)
			return
		}
		redisTemplate.opsForStream<String, String>().acknowledge(CONSUMER_GROUP, record)
	}

	private fun handleCreate(fields: Map<String, String>) {
		try {
			reservationRepository.save(toReservation(fields))
			log.info("Reserva {} persistida no Postgres via CREATE", fields["reservationId"])
		} catch (_: DataIntegrityViolationException) {
			log.info("Reserva {} já persistida anteriormente, ignorando reentrega", fields["reservationId"])
		}
	}

	private fun handleCancel(fields: Map<String, String>) {
		applyStatusChangeOrRequeue(fields, action = "CANCEL") { it.markCancelled() }
	}

	private fun handleExpire(fields: Map<String, String>) {
		applyStatusChangeOrRequeue(fields, action = "EXPIRE") { it.markExpired() }
	}

	private fun applyStatusChangeOrRequeue(
		fields: Map<String, String>,
		action: String,
		transition: (Reservation) -> Unit
	) {
		val reservationId = UUID.fromString(requireNotNull(fields["reservationId"]))
		val reservation = reservationRepository.findById(reservationId).orElse(null)
		if (reservation == null) {
			requeueOrTreatAsPoison(fields, action, reservationId)
			return
		}
		transition(reservation)
		reservationRepository.save(reservation)
		log.info("Reserva {} atualizada no Postgres via {}: novo status={}", reservationId, action, reservation.status)
	}

	private fun requeueOrTreatAsPoison(fields: Map<String, String>, action: String, reservationId: UUID) {
		val retryCount = fields["retryCount"]?.toIntOrNull() ?: 0
		if (retryCount < reservationProperties.actionMessageMaxRetries) {
			val nextFields = fields + ("retryCount" to (retryCount + 1).toString())
			redisTemplate.opsForStream<String, String>().add(ReservationLuaExecutor.STREAM_KEY, nextFields)
			log.info(
				"Reserva {} ainda não encontrada no Postgres para aplicar {}; reencaminhada para nova tentativa ({}/{})",
				reservationId,
				action,
				retryCount + 1,
				reservationProperties.actionMessageMaxRetries
			)
		} else {
			log.error(
				"Reserva {} não encontrada no Postgres após {} tentativa(s) de aplicar {}; tratando como mensagem envenenada: {}",
				reservationId,
				retryCount,
				action,
				fields
			)
		}
	}

	private fun toReservation(fields: Map<String, String>): Reservation =
		Reservation(
			id = UUID.fromString(requireNotNull(fields["reservationId"])),
			eventId = UUID.fromString(requireNotNull(fields["eventId"])),
			userId = UUID.fromString(requireNotNull(fields["userId"])),
			quantity = requireNotNull(fields["quantity"]).toInt(),
			expiresAt = Instant.parse(requireNotNull(fields["expiresAt"])),
			idempotencyKey = requireNotNull(fields["idempotencyKey"]),
			initialStatus = ReservationStatus.valueOf(requireNotNull(fields["status"]))
		)

	companion object {
		const val CONSUMER_GROUP = "reservation-worker"
	}
}
