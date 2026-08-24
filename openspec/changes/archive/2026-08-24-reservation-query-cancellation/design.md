## Context

Ver `proposal.md` - Why para a motivação. Hoje existem: `Reservation` no Postgres com `id` (atribuído pela aplicação, ver `reservation-creation-flow`), `eventId`, `userId`, `quantity`, `status` (todos `val`, imutáveis), `expiresAt`, `idempotencyKey`; o Hash `reservation:<id>` no Redis (campos `eventId`, `userId`, `quantity`, `status`, `expiresAt`, sem `idempotencyKey`), escrito atomicamente por `reserve_tickets.lua`; o stream `stream:reservations`, hoje só recebendo mensagens de criação; e `ReservationStreamListener`, que hoje assume que toda mensagem do stream é uma criação. `ReservationLuaExecutor` carrega e invoca `reserve_tickets.lua`. `event:<id>:available` (Redis) e `ReservationController` (`POST /events/<id>/reservations`) já existem.

## Goals / Non-Goals

**Goals:**
- `GET /reservations/<id>` lendo o Redis primeiro (reservas recém-criadas, ainda não sincronizadas), caindo para o Postgres e repopulando o Redis em cache miss — mesmo padrão de `event-availability-lookup`.
- `DELETE /reservations/<id>` devolvendo a quantidade ao saldo do evento como parte de uma única operação atômica no Redis, com o status do Postgres sincronizado depois, de forma assíncrona, pelo mesmo worker que já processa criações.

**Non-Goals:**
- Confirmar reservas (`PENDING` → `CONFIRMED`) ou processar expiração automática — o cancelamento aceita `CONFIRMED` como estado cancelável, mas nada nesta fase transiciona uma reserva para `CONFIRMED` nem para `EXPIRED`; ambos continuam inalcançáveis na prática até fases futuras.
- Popular `event:<id>:pending` (um ZSET de reservas pendentes) na criação — ver Decisão abaixo; esta fase implementa a remoção desse ZSET no cancelamento como preparação, não a escrita dele na criação.
- Reconciliar um cancelamento quando o Hash `reservation:<id>` foi perdido no Redis (ex.: reinício sem persistência) mas a reserva ainda existe no Postgres — ver Riscos.

## Decisions

### `event:<id>:pending` não é criado nesta fase; a remoção no cancelamento é uma preparação, não uma limpeza real
O pedido original descreve o script de cancelamento removendo a reserva de um ZSET `event:<id>:pending` (`ZREM`). Hoje nada popula esse ZSET — `reserve_tickets.lua` (de `reservation-creation-flow`) não grava nele. Retrofitar a criação para populá-lo agora expandiria este change para modificar um script já implementado e arquivado, sem que exista ainda um consumidor real desse índice (ele só faz sentido para a futura fase de expiração automática, que vai precisar localizar reservas pendentes por prazo). Alternativa considerada: adicionar `ZADD event:<id>:pending <reservationId> <expiresAt>` em `reserve_tickets.lua` agora — rejeitada por expandir o escopo deste change para uma capacidade (expiração) que ainda não tem nenhum outro pedaço implementado; o `ZREM` no script de cancelamento **não é incluído** nesta versão do script (ver pseudo-código abaixo) — quando a fase de expiração introduzir o ZSET, ela deve adicionar o `ZREM` correspondente ao script de cancelamento junto com o `ZADD` na criação, mantendo os dois em sincronia desde o início.

### Cancelamento verifica existência só no Redis, não com fallback ao Postgres
Ao contrário do `GET`, que cai para o Postgres em cache miss, o `DELETE` verifica a existência da reserva **apenas** no Hash `reservation:<id>`. Se o Hash não existir, a resposta é 404, mesmo que a reserva ainda exista no Postgres. Alternativa considerada: replicar o mesmo fallback do `GET` (buscar no Postgres e reconstruir o Hash antes de cancelar) — rejeitada porque cancelar é uma escrita cuja atomicidade depende do saldo já estar correto no Redis; reconstruir um Hash a partir do Postgres só para então cancelá-lo introduz uma janela não-atômica (ler do Postgres, escrever o Hash, só então rodar o script) que contradiz o próprio objetivo de atomicidade do cancelamento. Ver Riscos para o cenário em que isso importa.

### Script `cancel_reservation.lua` deriva a chave de disponibilidade internamente, sem receber `eventId` do chamador
O endpoint só recebe o `id` da reserva (não o `eventId`), então o script lê `eventId` do próprio Hash (`HGETALL reservation:<id>`) e monta a chave `event:<eventId>:available` internamente via concatenação, em vez de exigir uma leitura prévia do Kotlin antes de invocar o script. Isso é seguro porque o Redis deste projeto roda em nó único (`docker-compose.yml`, sem cluster) — em um cluster Redis, `KEYS` teria que ser conhecido antecipadamente para roteamento por slot, o que exigiria a alternativa abaixo. Alternativa considerada: o serviço Kotlin ler `eventId` do Hash primeiro e passar `event:<eventId>:available` como `KEYS[]` já resolvido — rejeitada por adicionar uma chamada Redis extra sem necessidade neste ambiente de nó único.

### Pseudo-código do script Lua de cancelamento
```
KEYS[1] = reservation:{reservationId}
KEYS[2] = stream:reservations

ARGV[1] = reservationId

1. hash = HGETALL KEYS[1]
2. SE hash está vazio:
     RETORNAR {'NOT_FOUND'}
3. status = hash['status']
4. SE status == 'CANCELLED':
     RETORNAR {'ALREADY_CANCELLED'}
5. SE status == 'EXPIRED':
     RETORNAR {'ALREADY_EXPIRED'}
6. eventId = hash['eventId']; quantity = hash['quantity']
7. HSET KEYS[1] status 'CANCELLED'
8. INCRBY event:{eventId}:available quantity
9. XADD KEYS[2] * reservationId ARGV[1] action 'CANCEL'
10. RETORNAR {'CANCELLED'}
```
Os passos 3–9 rodam como uma única operação atômica; nenhuma outra chamada pode observar um estado intermediário (saldo já incrementado mas status ainda não atualizado, ou vice-versa).

### `ReservationLuaExecutor` ganha um segundo script, não uma classe nova
`cancel_reservation.lua` é carregado e invocado pelo mesmo `ReservationLuaExecutor` que já carrega `reserve_tickets.lua`, mantendo toda invocação de script Lua relacionado a reserva centralizada numa única classe. Alternativa considerada: um `ReservationCancellationLuaExecutor` separado — rejeitada por fragmentar sem necessidade uma responsabilidade que já pertence à mesma classe.

### Novo tipo de resultado dedicado ao cancelamento, não reaproveitando `ReservationScriptResult`
O cancelamento tem seu próprio tipo selado (`CANCELLED`, `ALREADY_CANCELLED`, `ALREADY_EXPIRED`, `NOT_FOUND`) em vez de estender `ReservationScriptResult` (que já modela os resultados específicos da criação: `CREATED`, `IDEMPOTENT`, `INSUFFICIENT_STOCK`). Alternativa considerada: um único tipo compartilhado com todos os sete casos — rejeitada por misturar dois scripts com semânticas diferentes num único tipo, tornando o `when` de cada consumidor mais confuso.

### `Reservation.status` passa a ser mutável através de um método de domínio, não um setter público
Para o worker persistir o cancelamento, `status` deixa de ser `val` e passa a ser `var` com `protected set`, alterado apenas através de um método `markCancelled()` na própria entidade — seguindo o mesmo padrão de encapsulamento já usado para `id`/`createdAt`/`updatedAt` (nunca um setter público solto). Alternativa considerada: expor um setter público genérico para `status` — rejeitada por permitir qualquer transição de status arbitrária a partir de qualquer lugar do código, quando hoje só a transição para `CANCELLED` é implementada.

### Worker distingue `action` no stream, com `"CREATE"` como padrão retrocompatível
`ReservationStreamListener` passa a ler um campo opcional `action` de cada mensagem do stream. Mensagens sem esse campo (todas as já publicadas por `reserve_tickets.lua`, que nunca foi alterado para incluí-lo) são tratadas como `"CREATE"`, preservando o comportamento atual sem exigir alterar o script de criação. Mensagens com `action = "CANCEL"` atualizam o status da reserva correspondente no Postgres para `CANCELLED` via `markCancelled()`. Uma reserva de cancelamento sem correspondente encontrado no Postgres é tratada como mensagem envenenada (mesmo tratamento já usado para falha de conversão de tipos: não confirma, loga com contexto) — na arquitetura atual (um único consumidor, stream sem particionamento), isso só aconteceria por uma inconsistência real, já que mensagens do mesmo stream são entregues em ordem.

### `GET /reservations/<id>` e `DELETE /reservations/<id>` entram no `ReservationController` já existente
Em vez de um controller novo, `ReservationController` perde seu `@RequestMapping` de classe e passa a declarar o path completo em cada método (`@PostMapping("/events/{eventId}/reservations")`, `@GetMapping("/reservations/{id}")`, `@DeleteMapping("/reservations/{id}")`), já que as três operações pertencem ao mesmo recurso Reserva e à mesma capacidade (`reservation-management`) — ao contrário da separação entre `EventController`/`ReservationController`/`EventAvailabilityController`, que existe porque essas classes pertencem a capacidades diferentes. Alternativa considerada: um `ReservationQueryController` separado — rejeitada por não haver fronteira de capacidade entre criar, consultar e cancelar uma reserva.

### `ReservationCache`, um componente novo espelhando `EventAvailabilityCache`
Um novo componente `ReservationCache` expõe `getReservation(id): ReservationSnapshot?` (lê o Hash, retorna `null` em cache miss ou falha do Redis — mesmo tratamento de `DataAccessException` já usado em `EventAvailabilityCache.getAvailability`) e `repopulate(reservation)` (reescreve o Hash a partir dos dados do Postgres, melhor esforço). `ReservationSnapshot` é um tipo interno simples (não o DTO HTTP), mantendo a leitura do Redis desacoplada do formato de resposta.

## Risks / Trade-offs

- [Se o Hash `reservation:<id>` for perdido no Redis antes do cancelamento (reinício sem persistência, evição), o `DELETE` responde 404 mesmo que a reserva ainda exista no Postgres] → Mitigação: cenário já seria uma perda de dados do Redis pós-criação, risco residual aceito desde `redis-ticket-availability`; reconciliar esse caso fica para uma fase futura, se necessário.
- [`event:<id>:pending` não é populado nesta fase, então o `ZREM` descrito no pedido original não está no script implementado] → Mitigação: documentado acima como decisão deliberada; a fase de expiração deve introduzir `ZADD`/`ZREM` juntos.
- [Concorrência entre um `DELETE` e a leitura de `GET` no exato momento da transição pode, em teoria, retornar um status levemente desatualizado do Redis para o Postgres durante a janela de sincronização assíncrona] → Mitigação: já é a mesma consistência eventual aceita desde `reservation-creation-flow`; o Redis (fonte mais atual) é sempre consultado primeiro pelo `GET`.

## Open Questions

Nenhuma — os pontos que poderiam mudar a spec ou a abordagem já foram resolvidos acima ou com o usuário (status de `DELETE` para reserva `EXPIRED`).
