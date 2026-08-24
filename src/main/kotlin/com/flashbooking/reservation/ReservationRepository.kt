package com.flashbooking.reservation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ReservationRepository : JpaRepository<Reservation, UUID> {

	@Modifying
	@Query("UPDATE Reservation r SET r.status = :newStatus WHERE r.id = :id AND r.status IN :fromStatuses")
	fun transitionStatus(
		@Param("id") id: UUID,
		@Param("fromStatuses") fromStatuses: Collection<ReservationStatus>,
		@Param("newStatus") newStatus: ReservationStatus
	): Int
}
