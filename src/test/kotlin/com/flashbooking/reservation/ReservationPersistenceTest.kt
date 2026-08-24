package com.flashbooking.reservation

import com.flashbooking.event.Event
import com.flashbooking.event.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
class ReservationPersistenceTest {

	@Autowired
	private lateinit var eventRepository: EventRepository

	@Autowired
	private lateinit var reservationRepository: ReservationRepository

	@BeforeEach
	fun setUp() {
		reservationRepository.deleteAll()
		eventRepository.deleteAll()
	}

	@Test
	fun `deve persistir reserva com todos os campos obrigatorios`() {
		val event = eventRepository.save(Event(name = "Show de Rock", totalCapacity = 100))
		val userId = UUID.randomUUID()
		val expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES)

		val reservation = reservationRepository.save(
			Reservation(
				id = UUID.randomUUID(),
				eventId = requireNotNull(event.id),
				userId = userId,
				quantity = 2,
				expiresAt = expiresAt,
				initialStatus = ReservationStatus.PENDING,
				idempotencyKey = UUID.randomUUID().toString()
			)
		)

		val saved = reservationRepository.findById(reservation.id).orElseThrow()

		assertEquals(event.id, saved.eventId)
		assertEquals(userId, saved.userId)
		assertEquals(2, saved.quantity)
		assertEquals(ReservationStatus.PENDING, saved.status)
		assertNotNull(saved.expiresAt)
		assertNotNull(saved.idempotencyKey)
		assertNotNull(saved.createdAt)
		assertNotNull(saved.updatedAt)
	}

	@Test
	fun `nao deve permitir duas reservas com a mesma chave de idempotencia`() {
		val event = eventRepository.save(Event(name = "Peca de Teatro", totalCapacity = 50))
		val idempotencyKey = UUID.randomUUID().toString()

		reservationRepository.saveAndFlush(
			Reservation(
				id = UUID.randomUUID(),
				eventId = requireNotNull(event.id),
				userId = UUID.randomUUID(),
				quantity = 1,
				expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
				idempotencyKey = idempotencyKey,
				initialStatus = ReservationStatus.PENDING
			)
		)

		val duplicate = Reservation(
			id = UUID.randomUUID(),
			eventId = requireNotNull(event.id),
			userId = UUID.randomUUID(),
			quantity = 1,
			expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES),
			idempotencyKey = idempotencyKey,
			initialStatus = ReservationStatus.PENDING
		)

		assertThrows(DataIntegrityViolationException::class.java) {
			reservationRepository.saveAndFlush(duplicate)
		}

		assertEquals(1, reservationRepository.count())
	}
}
