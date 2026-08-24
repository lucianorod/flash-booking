package com.flashbooking.reservation

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ReservationStreamConfigTest {

	@Test
	fun `deve gerar nomes de consumidor distintos a cada chamada`() {
		val config = ReservationStreamConfig()

		val first = config.reservationConsumerIdentity()
		val second = config.reservationConsumerIdentity()

		assertNotEquals(first.name, second.name)
	}
}
