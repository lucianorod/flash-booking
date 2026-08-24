## Why

O Flash Booking já modela a Reserva no Postgres e a disponibilidade de ingressos no Redis, mas nenhum fluxo ainda cria reservas de fato. Sob alta concorrência, decidir "há ingresso disponível?" e "decrementar o estoque" precisam ser uma única operação atômica — se isso for feito em duas etapas (ler no Redis, depois escrever no Postgres), duas requisições concorrentes podem ler o mesmo saldo disponível e ambas decidirem que há estoque, causando oversell. Este change implementa o endpoint de criação de reserva com a checagem e o decremento de estoque atômicos via Redis (Lua script), idempotência obrigatória por chave de cliente, e persistência assíncrona no Postgres via um worker consumidor de um Redis Stream — mantendo o caminho de escrita rápido (Redis) desacoplado da consistência forte de longo prazo (Postgres).

## What Changes

- Implementar o endpoint `POST /events/:id/reservations`, exigindo o header `Idempotency-Key` e um corpo com `user_id` (UUID) e `quantity` (Int).
- Toda a decisão de negócio da reserva (checar idempotência, checar saldo em `event:<id>:available`, decrementar o saldo, gravar os dados da reserva, publicar no stream, registrar a chave de idempotência) roda em um único script Lua executado atomicamente pelo Redis — nenhuma dessas decisões é tomada em código Kotlin.
- Resposta `201 Created` quando a reserva é aceita e enfileirada para persistência; `200 OK` quando a `Idempotency-Key` já foi usada antes (retorna o identificador da reserva processada na primeira vez); `409 Conflict` quando não há saldo suficiente para a quantidade pedida.
- Implementar um worker Spring (`@Component`) consumidor do Redis Stream `stream:reservations`, responsável por converter os dados (string do Redis para `UUID`, `Int` e `Instant`) e persistir a reserva no Postgres via JPA, sem nunca tentar persistir um valor nulo.
- Resolve a pergunta em aberto deixada pelo change `redis-ticket-availability`: a partir deste change, o Redis passa a ser a fonte da verdade para a disponibilidade em tempo real durante a janela de reserva; o Postgres não é atualizado em tempo real (fica com a capacidade nominal do evento) — reconciliação entre os dois fica para uma fase futura.

## Capabilities

### New Capabilities
_Nenhuma — este change estende duas capacidades já existentes com o fluxo de criação de reserva que elas já previam para "fases seguintes"._

### Modified Capabilities
- `ticket-availability`: adiciona as responsabilidades atômicas do script Lua de reserva (idempotência, checagem e decremento de saldo, gravação da reserva em Redis, publicação no stream, registro da chave de idempotência com TTL).
- `reservation-management`: adiciona o endpoint `POST /events/:id/reservations` (contrato HTTP, idempotência, respostas de sucesso e de estoque insuficiente) e o worker assíncrono que persiste a reserva no Postgres a partir do stream.

## Impact

- **Código novo**: script Lua `reserve_tickets.lua`, controller/serviço para `POST /events/:id/reservations`, DTOs de request/response, componente de invocação do script Lua, worker `@Component` consumidor do Redis Stream, tratamento de erro para estoque insuficiente (409) e para idempotência (200).
- **Mudança em código existente**: a entidade `Reservation` precisa aceitar um `id` atribuído externamente (gerado antes da chamada ao Redis) em vez de gerado pelo Hibernate, para que o mesmo identificador exista no Hash do Redis e na linha do Postgres.
- **Dependências**: usa `spring-boot-starter-data-redis` já configurado; não adiciona nova dependência de biblioteca (execução de script Lua e Redis Streams já fazem parte da API do Spring Data Redis).
- **APIs**: novo endpoint `POST /events/:id/reservations`.
- **Sem impacto em `event-management` ou `project-foundation`** — este change não altera o modelo ou o endpoint de Evento.
