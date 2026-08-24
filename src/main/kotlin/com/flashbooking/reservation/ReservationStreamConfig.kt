package com.flashbooking.reservation

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.net.InetAddress
import java.time.Duration
import java.util.UUID

data class ReservationConsumerIdentity(val name: String)

@Configuration
class ReservationStreamConfig {

	private val log = LoggerFactory.getLogger(ReservationStreamConfig::class.java)

	@Bean
	fun reservationConsumerIdentity(): ReservationConsumerIdentity {
		val hostName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("unknown-host")
		return ReservationConsumerIdentity("reservation-worker-$hostName-${UUID.randomUUID().toString().take(8)}")
	}

	// Desligável apenas em testes que precisam controlar manualmente a entrega de mensagens
	// pendentes (recuperação e reprocessamento), sem o container consumindo o stream em paralelo.
	@Bean
	@ConditionalOnProperty(
		prefix = "flash-booking.reservation.stream-consumer",
		name = ["enabled"],
		havingValue = "true",
		matchIfMissing = true
	)
	fun reservationStreamContainer(
		connectionFactory: RedisConnectionFactory,
		redisTemplate: StringRedisTemplate,
		listener: ReservationStreamListener,
		consumerIdentity: ReservationConsumerIdentity
	): StreamMessageListenerContainer<String, MapRecord<String, String, String>> {
		ensureConsumerGroupExists(redisTemplate)

		val container = StreamMessageListenerContainer.create(
			connectionFactory,
			StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
				.pollTimeout(Duration.ofSeconds(1))
				.build()
		)

		container.receive(
			Consumer.from(ReservationStreamListener.CONSUMER_GROUP, consumerIdentity.name),
			StreamOffset.create(ReservationLuaExecutor.STREAM_KEY, ReadOffset.lastConsumed()),
			listener
		)

		// DefaultStreamMessageListenerContainer.isAutoStartup() is always false,
		// so Spring never starts it automatically during context refresh.
		container.start()

		return container
	}

	private fun ensureConsumerGroupExists(redisTemplate: StringRedisTemplate) {
		try {
			redisTemplate.opsForStream<String, String>().createGroup(
				ReservationLuaExecutor.STREAM_KEY,
				ReadOffset.from("$"),
				ReservationStreamListener.CONSUMER_GROUP
			)
		} catch (ex: Exception) {
			val alreadyExists = generateSequence(ex as Throwable) { it.cause }
				.any { it.message?.contains("BUSYGROUP") == true }
			if (!alreadyExists) {
				log.warn(
					"Não foi possível garantir o grupo de consumidores do stream de reservas na inicialização; " +
						"o worker não vai receber reservas até que isso seja resolvido",
					ex
				)
			}
		}
	}
}
