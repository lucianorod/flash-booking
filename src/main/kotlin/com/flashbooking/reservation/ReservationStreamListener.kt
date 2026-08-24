package com.flashbooking.reservation

import com.flashbooking.reservation.config.ReservationProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class ReservationStreamListener(
	private val reservationRepository: ReservationRepository,
	private val reservationPersistenceService: ReservationPersistenceService,
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
		val persisted = reservationPersistenceService.persistCreated(toReservation(fields))
		if (persisted) {
			log.info(
				"Reserva {} persistida no Postgres via CREATE, saldo do evento decrementado",
				fields["reservationId"]
			)
		} else {
			log.info("Reserva {} já persistida anteriormente, ignorando reentrega", fields["reservationId"])
		}
	}

	private fun handleCancel(fields: Map<String, String>) {
		applyStatusChangeOrRequeue(fields, action = "CANCEL") { reservation ->
			reservationPersistenceService.applyCancellation(reservation.id, reservation.eventId, reservation.quantity)
		}
	}

	private fun handleExpire(fields: Map<String, String>) {
		applyStatusChangeOrRequeue(fields, action = "EXPIRE") { reservation ->
			reservationPersistenceService.applyExpiration(reservation.id, reservation.eventId, reservation.quantity)
		}
	}

	private fun applyStatusChangeOrRequeue(
		fields: Map<String, String>,
		action: String,
		transition: (Reservation) -> Boolean
	) {
		val reservationId = UUID.fromString(requireNotNull(fields["reservationId"]))
		val reservation = reservationRepository.findById(reservationId).orElse(null)
		if (reservation == null) {
			requeueOrTreatAsPoison(fields, action, reservationId)
			return
		}
		if (transition(reservation)) {
			log.info("Reserva {} atualizada no Postgres via {}, saldo do evento ajustado", reservationId, action)
		} else {
			log.info(
				"Reserva {} já estava em status final ao processar {}; nenhuma alteração aplicada",
				reservationId,
				action
			)
		}
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
