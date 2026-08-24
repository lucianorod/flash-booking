package com.flashbooking.testsupport

import com.flashbooking.reservation.ReservationLuaExecutor
import com.flashbooking.reservation.ReservationStreamListener
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Recria do zero o stream de reservas e o grupo de consumidores, para testes que desligam
 * o container do stream (`flash-booking.reservation.stream-consumer.enabled=false`) e por
 * isso precisam garantir o grupo manualmente, sem interferência de entregas de outros testes.
 */
fun resetReservationStreamGroup(redisTemplate: StringRedisTemplate) {
	redisTemplate.delete(ReservationLuaExecutor.STREAM_KEY)
	try {
		redisTemplate.opsForStream<String, String>().createGroup(
			ReservationLuaExecutor.STREAM_KEY,
			ReadOffset.from("$"),
			ReservationStreamListener.CONSUMER_GROUP
		)
	} catch (ex: Exception) {
		val alreadyExists = generateSequence(ex as Throwable) { it.cause }
			.any { it.message?.contains("BUSYGROUP") == true }
		if (!alreadyExists) throw ex
	}
}
