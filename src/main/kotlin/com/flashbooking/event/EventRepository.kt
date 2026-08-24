package com.flashbooking.event

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface EventRepository : JpaRepository<Event, UUID> {

	@Modifying
	@Query("UPDATE Event e SET e.availableCapacity = e.availableCapacity - :quantity WHERE e.id = :eventId")
	fun decrementAvailableCapacity(@Param("eventId") eventId: UUID, @Param("quantity") quantity: Int): Int

	@Modifying
	@Query("UPDATE Event e SET e.availableCapacity = e.availableCapacity + :quantity WHERE e.id = :eventId")
	fun incrementAvailableCapacity(@Param("eventId") eventId: UUID, @Param("quantity") quantity: Int): Int
}
