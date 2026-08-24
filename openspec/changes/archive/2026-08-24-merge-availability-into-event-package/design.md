## Context

Ver `proposal.md` - Why para a motivação e o inventário da dependência circular atual. Hoje `com.flashbooking.availability` tem 4 classes principais (`EventAvailabilityCache`, `EventAvailabilityController`, `EventAvailabilityQueryService`, `EventAvailabilityResponse`) e 4 testes de integração; `com.flashbooking.event` já é o pacote de referência do agregado `Event` (`Event`, `EventController`, `EventService`, `EventRepository`, `EventStatus`, `EventNotFoundException`, DTOs). Todas as classes de `availability` já têm nomes prefixados com `Event` (convenção já usada pelas classes de `event`), então a fusão não exige renomear nada.

## Goals / Non-Goals

**Goals:**
- Eliminar a dependência circular entre pacotes, movendo `availability` para dentro de `event`.
- Preservar exatamente o comportamento observável hoje existente (endpoints, formato do cache no Redis, resultado dos testes).

**Non-Goals:**
- Combinar classes (ex.: unir `EventController` e `EventAvailabilityController` num único controller, ou `EventService` e `EventAvailabilityQueryService`) — fora de escopo; o pedido é sobre organização de pacote, não sobre reduzir o número de classes.
- Mudar a separação das capabilities OpenSpec (`event-management` e `ticket-availability` continuam sendo especificações distintas) — specs descrevem comportamento, não estrutura de pacote Kotlin.
- Resolver a questão, já registrada como decisão em aberto de uma fase anterior (`redis-ticket-availability`), de qual fonte (Postgres ou Redis) é a autoridade de disponibilidade no longo prazo — não relacionada a esta mudança.

## Decisions

### Destino: pacote `com.flashbooking.event` (plano), não um subpacote `event.availability`

As classes movidas vão direto para `com.flashbooking.event` (e `com.flashbooking.event.dto` para o DTO), no mesmo nível de `EventController`/`EventService`, em vez de um subpacote `com.flashbooking.event.availability`. Alternativa considerada: manter um subpacote `event.availability` para preservar alguma segmentação visual — rejeitada porque os nomes das classes já carregam "Availability" de forma explícita (`EventAvailabilityCache`, etc.), tornando um subpacote redundante para um total de só 4 classes + 1 DTO; um subpacote também recriaria, em miniatura, a mesma fronteira artificial que está sendo removida.

### Direção do import remanescente: remover onde já fica no mesmo pacote, atualizar onde não

Depois da fusão, `EventService` (que já está em `com.flashbooking.event`) deixa de precisar de qualquer `import` para usar `EventAvailabilityCache` — o import é removido, não só atualizado. Já `ReservationLuaExecutor` (pacote `com.flashbooking.reservation`) continua precisando de um import, só que agora apontando para `com.flashbooking.event.EventAvailabilityCache`. O mesmo vale para os testes de reserva que hoje importam `com.flashbooking.availability.EventAvailabilityCache`.

### Testes movidos junto, sem reescrever asserts

Os quatro testes de integração hoje em `src/test/kotlin/com/flashbooking/availability/` são movidos para `src/test/kotlin/com/flashbooking/event/` com a `package` atualizada, mas sem nenhuma mudança de conteúdo/asserção — eles já testam comportamento (contratos HTTP, estado no Redis) que não muda com a reorganização de pacote.

## Risks / Trade-offs

- [Mover classes de pacote é uma mudança mecânica em vários arquivos; um import esquecido quebra a compilação] → Mitigação: `./gradlew build` ao final da mudança pega qualquer import não atualizado imediatamente, antes de considerar a mudança concluída.
- [Ferramentas de IDE que dependem de caminho de pacote (ex.: breakpoints salvos, bookmarks) ficam desatualizadas para quem já tinha essas classes abertas] → Mitigação: é um custo único e local ao ambiente de cada desenvolvedor, sem impacto em runtime ou nos testes.

## Migration Plan

Mudança puramente estrutural, sem estado a migrar (não toca em dados de Postgres/Redis nem em contrato de API). Rollback é reverter os arquivos movidos/editados; não há flag de rollout.
