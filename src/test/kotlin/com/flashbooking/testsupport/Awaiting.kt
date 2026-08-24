package com.flashbooking.testsupport

import java.time.Duration
import java.time.Instant

fun awaitUntil(
	timeout: Duration = Duration.ofSeconds(5),
	poll: Duration = Duration.ofMillis(100),
	condition: () -> Boolean
) {
	val deadline = Instant.now().plus(timeout)
	while (Instant.now().isBefore(deadline)) {
		if (condition()) return
		Thread.sleep(poll.toMillis())
	}
	if (!condition()) {
		throw AssertionError("Condição não satisfeita dentro do tempo limite de $timeout")
	}
}
