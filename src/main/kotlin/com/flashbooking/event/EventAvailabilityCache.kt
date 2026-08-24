package com.flashbooking.event

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class EventAvailabilityCache(private val redisTemplate: StringRedisTemplate) {

	fun initializeAvailability(eventId: UUID, availableCapacity: Int) {
		redisTemplate.opsForValue().set(availabilityKey(eventId), availableCapacity.toString())
	}

	fun getAvailability(eventId: UUID): Int? =
		try {
			redisTemplate.opsForValue().get(availabilityKey(eventId))?.toInt()
		} catch (ex: DataAccessException) {
			null
		}

	companion object {
		fun availabilityKey(eventId: UUID): String = "event:$eventId:available"
	}
}
