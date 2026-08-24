## 1. Ajuste da entidade Reservation

- [x] 1.1 Tornar `Reservation.status` mutável (`var` com `protected set`) e adicionar um método de domínio `markCancelled()` que transiciona o status para `CANCELLED`.

## 2. Script Lua de cancelamento

- [x] 2.1 Criar o arquivo `cancel_reservation.lua` (em `src/main/resources`) implementando as responsabilidades descritas em `design.md`: buscar o Hash da reserva, abortar (`NOT_FOUND`/`ALREADY_CANCELLED`/`ALREADY_EXPIRED`) quando aplicável, ou atualizar o status para `CANCELLED`, devolver a quantidade à disponibilidade do evento e publicar `action: "CANCEL"` no stream, tudo como uma única operação atômica.
- [x] 2.2 Adicionar o carregamento e a invocação de `cancel_reservation.lua` em `ReservationLuaExecutor`, ao lado do script de criação já existente.
- [x] 2.3 Criar o tipo de resultado do cancelamento (`Cancelled`, `AlreadyCancelled`, `AlreadyExpired`, `NotFound`) mapeando a saída do script.

## 3. Cache de reserva para leitura

- [x] 3.1 Criar `ReservationCache` com `getReservation(id)` (lê o Hash `reservation:<id>`, retorna `null` em cache miss ou falha do Redis) e `repopulate(reservation)` (reescreve o Hash a partir dos dados do Postgres, melhor esforço).

## 4. Endpoint GET /reservations/:id

- [x] 4.1 Criar o DTO de resposta com `id`, `event_id`, `user_id`, `quantity`, `status` e `expires_at` (mapeados via `@JsonProperty` para o formato snake_case do contrato).
- [x] 4.2 Implementar o serviço de consulta de reserva: tenta `ReservationCache.getReservation`; em cache miss, consulta `ReservationRepository.findById`; se não existir, sinaliza não encontrada; se existir, tenta repopular o Redis (falha na repopulação não impede a resposta).
- [x] 4.3 Adicionar `GET /reservations/{id}` ao `ReservationController` (removendo o `@RequestMapping` de classe em favor de paths completos por método), retornando 200 com o DTO de reserva.

## 5. Endpoint DELETE /reservations/:id

- [x] 5.1 Implementar o serviço de cancelamento: invoca o script de cancelamento e mapeia `Cancelled`/`AlreadyCancelled` para sucesso, `AlreadyExpired` para conflito, `NotFound` para não encontrada.
- [x] 5.2 Adicionar `DELETE /reservations/{id}` ao `ReservationController`, retornando 204 em caso de sucesso (incluindo o caso idempotente de já estar cancelada).
- [x] 5.3 Adicionar tratamento de erro para reserva expirada, retornando 409 com o corpo de erro já padronizado no projeto.
- [x] 5.4 Adicionar tratamento de erro para reserva não encontrada no cancelamento, retornando 404 com o corpo de erro já padronizado no projeto.

## 6. Worker: distinguir criação e cancelamento no mesmo stream

- [x] 6.1 Atualizar `ReservationStreamListener` para ler o campo `action` de cada mensagem (tratando a ausência do campo como `"CREATE"`) e despachar entre o fluxo de criação já existente e o novo fluxo de cancelamento.
- [x] 6.2 Implementar o fluxo de `action = "CANCEL"`: buscar a reserva no Postgres, chamar `markCancelled()` e salvar; tratar reserva não encontrada como mensagem envenenada (não confirmar, logar com contexto).

## 7. Testes

- [x] 7.1 Escrever teste de integração validando `GET /reservations/:id` para uma reserva recém-criada, ainda só no Redis: 200, dados corretos.
- [x] 7.2 Escrever teste de integração validando cache miss no Redis com reserva já existente no Postgres: 200, dados corretos, e o Hash volta a existir no Redis após a consulta.
- [x] 7.3 Escrever teste de integração validando `GET /reservations/:id` para uma reserva inexistente: 404.
- [x] 7.4 Escrever teste de integração validando `DELETE /reservations/:id` numa reserva `PENDING`: 204, saldo do evento devolvido no Redis, reserva eventualmente `CANCELLED` no Postgres.
- [x] 7.5 Escrever teste de integração validando `DELETE` repetido numa reserva já `CANCELLED`: 204 novamente, saldo não devolvido uma segunda vez.
- [x] 7.6 Escrever teste de integração validando `DELETE` numa reserva `EXPIRED`: 409, saldo do evento inalterado.
- [x] 7.7 Escrever teste de integração validando `DELETE` de uma reserva inexistente: 404.

## 8. Build

- [x] 8.1 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.
