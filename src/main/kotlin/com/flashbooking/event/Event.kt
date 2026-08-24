package com.flashbooking.event

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "events")
@EntityListeners(AuditingEntityListener::class)
class Event(
	@Column(nullable = false)
	val name: String,

	@Column(name = "total_capacity", nullable = false)
	val totalCapacity: Int,

	@Column(name = "available_capacity", nullable = false)
	val availableCapacity: Int = totalCapacity,

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	val status: EventStatus = EventStatus.PUBLISHED
) {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@Column(nullable = false, updatable = false)
	var id: UUID? = null
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
