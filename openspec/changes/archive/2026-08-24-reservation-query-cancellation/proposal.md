## Why

Uma reserva hoje só pode ser criada — não há como consultar seu estado nem desistir dela. Sob alta concorrência, a consulta precisa enxergar reservas recém-aceitas mesmo antes de o worker assíncrono terminar de persisti-las no Postgres (a mesma janela de "consistência eventual" já assumida desde `reservation-creation-flow`). O cancelamento, por sua vez, precisa devolver a capacidade ao evento de forma atômica e imediata — do contrário, ingressos cancelados ficariam presos, indisponíveis para outros compradores, exatamente o oposto do que a prevenção de oversell exige.

## What Changes

- Implementar `GET /reservations/:id`: consulta a reserva primeiro no Redis (`reservation:<id>`, onde reservas recém-criadas já estão disponíveis mesmo antes da sincronização assíncrona) e, em cache miss, cai para o Postgres — repopulando o Redis nesse caso, seguindo o mesmo padrão de resiliência já usado em `GET /events/:id`.
- Implementar `DELETE /reservations/:id`: cancela uma reserva `PENDING` ou `CONFIRMED`, devolvendo a quantidade reservada a `event:<id>:available` como parte de uma única operação atômica em um script Lua (`cancel_reservation.lua`) — o Kotlin nunca decide isso diretamente, assim como na criação.
- `204 No Content` quando o cancelamento é efetivado, ou quando a reserva já estava `CANCELLED` (idempotência da rota).
- `409 Conflict` quando a reserva já está `EXPIRED` — um estado terminal distinto de ter sido cancelada pelo usuário, não tratado como sucesso idempotente.
- `404 Not Found` quando a reserva não existe em nenhum dos dois armazenamentos.
- O cancelamento publica um evento `action: "CANCEL"` no mesmo stream `stream:reservations` já usado pela criação; o worker existente passa a distinguir `action` (tratando a ausência do campo como `"CREATE"`, por retrocompatibilidade com as mensagens já publicadas pelo script de criação) e, para `"CANCEL"`, atualiza o status da reserva para `CANCELLED` no Postgres.

## Capabilities

### New Capabilities
_Nenhuma — este change estende as duas capacidades já existentes que dividem a criação de reserva entre si._

### Modified Capabilities
- `reservation-management`: adiciona a consulta (`GET /reservations/:id`, com fallback Redis→Postgres) e o cancelamento (`DELETE /reservations/:id`, com as respostas 204/409/404) ao contrato HTTP de reservas.
- `ticket-availability`: adiciona a devolução atômica de saldo ao cancelar (script Lua), a atualização do status da reserva no Redis, e a extensão do worker para tratar `action: "CANCEL"` publicada no stream, além do `"CREATE"` que ele já trata.

## Impact

- **Código novo**: script Lua `cancel_reservation.lua`, componente de invocação do script, serviço e controller para `GET /reservations/:id` e `DELETE /reservations/:id`, DTO de resposta da reserva (`id`, `event_id`, `user_id`, `quantity`, `status`, `expires_at`).
- **Mudança em código existente**: `ReservationStreamListener` passa a distinguir `action` (`"CREATE"` vs `"CANCEL"`) em vez de assumir que toda mensagem do stream é uma criação.
- **Dependências**: nenhuma nova — reaproveita `StringRedisTemplate`, `ReservationRepository` e a infraestrutura de script Lua/stream já criada em `reservation-creation-flow`.
- **APIs**: novos endpoints `GET /reservations/:id` e `DELETE /reservations/:id`.
- **Sem impacto em `event-management` ou `project-foundation`**.
