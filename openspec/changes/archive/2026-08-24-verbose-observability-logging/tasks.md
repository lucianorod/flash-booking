## 1. Controllers

- [x] 1.1 Adicionar logger e logs de nível INFO em `EventController.createEvent`: requisição recebida (nome, capacidade total) e evento criado (id).
- [x] 1.2 Adicionar logger e logs de nível INFO em `ReservationController`: para cada um dos três endpoints (`createReservation`, `getReservation`, `cancelReservation`), logar a requisição recebida (identificadores relevantes) e o resultado (status HTTP efetivo, incluindo o caso idempotente 200 vs. 201 na criação).
- [x] 1.3 Adicionar logger e log de nível INFO em `EventAvailabilityController.getAvailability`: requisição recebida e disponibilidade retornada.

## 2. Tratamento de exceções

- [x] 2.1 Adicionar logger em `GlobalExceptionHandler`.
- [x] 2.2 Adicionar log de nível WARN em `handleValidationError`, `handleMalformedRequest` e `handleMissingHeader`, incluindo os detalhes relevantes (campos inválidos, header ausente).
- [x] 2.3 Adicionar log de nível WARN em `handleInsufficientStock`, `handleEventNotFound`, `handleReservationNotFound` e `handleReservationExpired`, incluindo o identificador (`eventId`/`reservationId`) disponível na exceção.
- [x] 2.4 Adicionar log de nível ERROR com stack trace completo em `handleUnexpectedError`, cobrindo a lacuna atual em que um erro 500 não deixa nenhum rastro em log.

## 3. Serviços de negócio

- [x] 3.1 Adicionar logger e log de nível INFO em `EventService.createEvent`: evento criado (id, nome, capacidade).
- [x] 3.2 Adicionar logger e logs de nível INFO/WARN em `ReservationService.createReservation`: reserva aceita (INFO, incluindo o caso idempotente) e recusa por saldo insuficiente (WARN, com `eventId` e quantidade solicitada).
- [x] 3.3 Adicionar logger e log de nível INFO em `ReservationCancellationService.cancelReservation`: resultado do cancelamento (`Cancelled`, `AlreadyCancelled`, `AlreadyExpired`, `NotFound`), antes de repassar o resultado ao chamador.

## 4. Camada Redis/Lua e worker assíncrono

- [x] 4.1 Adicionar logger e logs de nível DEBUG em `ReservationLuaExecutor.reserve`, `.cancel` e `.expirePendingReservations`, expondo o resultado bruto retornado por cada script.
- [x] 4.2 Adicionar logs de nível INFO em `ReservationStreamListener` para os caminhos de sucesso hoje silenciosos: reserva persistida via `CREATE`, e status aplicado com sucesso via `CANCEL`/`EXPIRE` (mantendo inalterados os logs de reencaminhamento e de mensagem envenenada já existentes, que já atendem ao caso citado na proposta de mostrar o corpo completo em ERROR).
- [x] 4.3 Adicionar log de nível DEBUG em `ReservationExpirationSweepTask` para o caso em que a varredura não encontra nenhuma reserva vencida (`expiredCount == 0`).

## 5. Build

- [x] 5.1 Rodar o build completo (`./gradlew build`) e confirmar que todos os testes existentes continuam passando (mudança de observabilidade pura, sem novo teste exigido) e que a verificação de cobertura passa.
