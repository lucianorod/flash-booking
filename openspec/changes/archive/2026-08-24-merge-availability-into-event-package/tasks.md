## 1. Mover classes principais

- [x] 1.1 Mover `EventAvailabilityCache.kt` de `src/main/kotlin/com/flashbooking/availability/` para `src/main/kotlin/com/flashbooking/event/`, atualizando a declaração `package` para `com.flashbooking.event`.
- [x] 1.2 Mover `EventAvailabilityController.kt` para `src/main/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.
- [x] 1.3 Mover `EventAvailabilityQueryService.kt` para `src/main/kotlin/com/flashbooking/event/`, atualizando a declaração `package` e removendo os imports de `com.flashbooking.event.*` que passam a ser desnecessários (mesmo pacote).
- [x] 1.4 Mover `dto/EventAvailabilityResponse.kt` para `src/main/kotlin/com/flashbooking/event/dto/`, atualizando a declaração `package` para `com.flashbooking.event.dto`.

## 2. Atualizar dependentes no código principal

- [x] 2.1 Atualizar `EventService.kt`: remover o import de `EventAvailabilityCache` (mesmo pacote após a fusão).
- [x] 2.2 Atualizar `ReservationLuaExecutor.kt`: trocar o import de `com.flashbooking.availability.EventAvailabilityCache` para `com.flashbooking.event.EventAvailabilityCache`.

## 3. Mover testes de integração

- [x] 3.1 Mover `EventAvailabilityCreationIntegrationTest.kt` de `src/test/kotlin/com/flashbooking/availability/` para `src/test/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.
- [x] 3.2 Mover `EventAvailabilityQueryIntegrationTest.kt` para `src/test/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.
- [x] 3.3 Mover `EventAvailabilityRedisDegradationIntegrationTest.kt` para `src/test/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.
- [x] 3.4 Mover `EventCreationRedisFailureIntegrationTest.kt` para `src/test/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.

## 4. Atualizar imports nos testes de reserva

- [x] 4.1 Atualizar `ReservationCreationIntegrationTest.kt`, `ReservationQueryCancellationIntegrationTest.kt`, `ReservationConcurrencyIntegrationTest.kt` e `ReservationExpirationIntegrationTest.kt`: trocar o import de `com.flashbooking.availability.EventAvailabilityCache` para `com.flashbooking.event.EventAvailabilityCache`.

## 5. Limpeza e verificação

- [x] 5.1 Remover os diretórios `com/flashbooking/availability` vazios em `src/main/kotlin` e `src/test/kotlin` (incluindo `main/.../availability/dto`).
- [x] 5.2 Rodar o build completo (`./gradlew build`) e confirmar que compila e que todos os testes existentes continuam passando, sem nenhum novo teste exigido (mudança estrutural pura).
