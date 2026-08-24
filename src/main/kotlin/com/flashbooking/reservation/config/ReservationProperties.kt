package com.flashbooking.reservation.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "flash-booking.reservation")
data class ReservationProperties(
	val holdMinutes: Long,
	val idempotencyTtlSeconds: Long,
	val streamRecoveryMinIdleTimeMs: Long,
	val streamRecoveryIntervalMs: Long,
	val actionMessageMaxRetries: Int,
	val expirationSweepIntervalMs: Long,
	val expirationSweepBatchSize: Int
)
