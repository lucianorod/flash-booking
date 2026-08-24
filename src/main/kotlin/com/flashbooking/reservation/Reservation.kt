package com.flashbooking.reservation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener::class)
class Reservation(
	@Id
	@Column(nullable = false, updatable = false)
	val id: UUID,

	@Column(name = "event_id", nullable = false, updatable = false)
	val eventId: UUID,

	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: UUID,

	@Column(nullable = false)
	val quantity: Int,

	@Column(name = "expires_at", nullable = false)
	val expiresAt: Instant,

	@Column(name = "idempotency_key", nullable = false, unique = true)
	val idempotencyKey: String,

	initialStatus: ReservationStatus
) {
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	var status: ReservationStatus = initialStatus
		protected set

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	var createdAt: Instant? = null
		protected set

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	var updatedAt: Instant? = null
		protected set
}
