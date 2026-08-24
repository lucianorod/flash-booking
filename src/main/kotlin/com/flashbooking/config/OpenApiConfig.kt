package com.flashbooking.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

	@Bean
	fun flashBookingOpenApi(): OpenAPI =
		OpenAPI().info(
			Info()
				.title("Flash Booking API")
				.version("v1")
				.description("API do núcleo de reserva de ingressos do Flash Booking.")
		)
}
