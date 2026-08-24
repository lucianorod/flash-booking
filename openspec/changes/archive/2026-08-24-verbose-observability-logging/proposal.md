## Why

Uma revisão do código atual mostra lacunas relevantes de observabilidade: nenhum dos três controllers REST tem qualquer log; o `GlobalExceptionHandler` converte toda exceção (incluindo o catch-all de erro 500) em resposta HTTP sem registrar nada, apagando o rastro de falhas inesperadas; os serviços de negócio (`EventService`, `ReservationService`, `ReservationCancellationService`) não logam nenhum evento crítico (evento criado, reserva aceita/recusada, cancelamento aplicado); e o `ReservationLuaExecutor` não expõe o resultado dos scripts atômicos em log algum. O único ponto já bem coberto é o worker do stream (`ReservationStreamListener`), que desde a fase anterior (`reservation-worker-resilience`) já loga em nível ERROR o corpo completo de uma mensagem tratada como envenenada após esgotar as tentativas. Esta mudança estende esse mesmo padrão de rigor para o restante da aplicação, sem alterar nenhum comportamento observável externamente (contratos HTTP, códigos de status e corpos de resposta permanecem os mesmos).

## What Changes

- Adicionar logs de entrada e conclusão em `EventController`, `ReservationController` e `EventAvailabilityController` para cada endpoint (requisição recebida com os parâmetros relevantes; resultado da operação).
- Adicionar logging em `GlobalExceptionHandler` para cada `@ExceptionHandler`: nível WARN para respostas 400/404/409 (erros esperados de validação ou de regra de negócio, com o identificador relevante), e nível ERROR com stack trace completo para o catch-all de erro 500 — hoje esse caso não deixa nenhum rastro em log.
- Adicionar logs de nível INFO nos serviços de negócio (`EventService`, `ReservationService`, `ReservationCancellationService`) para os eventos críticos: evento criado, reserva aceita, reserva recusada por saldo insuficiente, cancelamento aplicado/rejeitado.
- Adicionar logs de nível DEBUG em `ReservationLuaExecutor` expondo o resultado bruto de cada script Lua (criação, cancelamento, expiração), para depuração de condições de corrida sem poluir o log em nível INFO por padrão.
- Adicionar logs de nível INFO em `ReservationStreamListener` para os casos de sucesso hoje silenciosos (reserva persistida via `CREATE`, status aplicado via `CANCEL`/`EXPIRE`), complementando os logs de reencaminhamento e mensagem envenenada já existentes.
- Adicionar log de nível DEBUG em `ReservationExpirationSweepTask` para o caso de a varredura não encontrar nenhuma reserva vencida (hoje só loga quando `expiredCount > 0`).

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

(nenhuma — mudança de observabilidade pura, sem alteração de comportamento externamente observável; `.openspec.yaml` declara `skip_specs: true`)

## Impact

- `EventController`, `ReservationController`, `EventAvailabilityController`: novos loggers e chamadas de log, sem mudança de assinatura ou de contrato HTTP.
- `GlobalExceptionHandler`: novo logger; cada `@ExceptionHandler` passa a logar antes de montar a resposta, sem alterar o corpo ou o status já retornados.
- `EventService`, `ReservationService`, `ReservationCancellationService`: novos loggers e chamadas de log nos pontos de decisão de negócio.
- `ReservationLuaExecutor`: novo logger, log em nível DEBUG do resultado bruto de cada script.
- `ReservationStreamListener`, `ReservationExpirationSweepTask`: logs adicionais de nível INFO/DEBUG nos caminhos de sucesso hoje silenciosos.
- Nenhuma dependência nova; nenhuma mudança de schema de banco, contrato HTTP ou payload do stream.
