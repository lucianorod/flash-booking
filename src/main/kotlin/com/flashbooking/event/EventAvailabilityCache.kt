package com.flashbooking.event

import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

data class EventAvailabilitySnapshot(
	val name: String,
	val availableCapacity: Int
)

@Component
class EventAvailabilityCache(private val redisTemplate: StringRedisTemplate) {

	fun initializeAvailability(eventId: UUID, name: String, availableCapacity: Int) {
		redisTemplate.opsForValue().set(availabilityKey(eventId), availableCapacity.toString())
		redisTemplate.opsForValue().set(nameKey(eventId), name)
	}

	fun getAvailability(eventId: UUID): EventAvailabilitySnapshot? =
		try {
			val keys = listOf(nameKey(eventId), availabilityKey(eventId))
			val values = redisTemplate.opsForValue().multiGet(keys)
			val name = values?.getOrNull(0)
			val available = values?.getOrNull(1)?.toIntOrNull()
			if (name != null && available != null) {
				EventAvailabilitySnapshot(name = name, availableCapacity = available)
			} else {
				null
			}
		} catch (ex: DataAccessException) {
			null
		}

	companion object {
		fun availabilityKey(eventId: UUID): String = "event:$eventId:available"
		fun nameKey(eventId: UUID): String = "event:$eventId:name"
	}
}
