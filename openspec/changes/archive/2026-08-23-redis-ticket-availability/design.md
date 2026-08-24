## Context

Ver `proposal.md` - Why para a motivação. O Redis já está configurado no projeto desde a fundação (`spring-boot-starter-data-redis`, container no `docker-compose.yml`, conectividade validada na subida da aplicação), mas sem uso funcional até este change. O `Event` já existe no Postgres com `id` (UUID), `totalCapacity`, `availableCapacity` e `status` (ver capacidade `event-management`).

## Goals / Non-Goals

**Goals:**
- Inicializar, no Redis, a disponibilidade de ingressos de um evento no momento em que ele é criado, na chave `event:<id>:available`.
- Garantir que a criação do evento seja tudo-ou-nada entre Postgres e Redis: se a escrita no Redis falhar, nada fica persistido em nenhum dos dois.

**Non-Goals:**
- Definir qual armazenamento (Postgres ou Redis) é a fonte da verdade para disponibilidade no longo prazo — decisão das fases futuras, quando decremento por reserva, expiração e reconciliação forem implementados.
- Implementar leitura de disponibilidade, decremento por reserva, reposição por cancelamento/expiração, ou qualquer lock distribuído — ficam para as próximas fases desta mesma capacidade (`ticket-availability`).
- Garantir atomicidade distribuída (2PC) entre Postgres e Redis — ver Riscos/Trade-offs.

## Decisions

### Valor simples no Redis (`SET`), não hash
A disponibilidade é armazenada como uma string simples via `StringRedisTemplate`/`opsForValue().set(...)` na chave `event:<id>:available`, e não como um Redis Hash com múltiplos campos. Alternativa considerada: um Hash `event:<id>` com o campo `available` (e outros futuramente) — rejeitada por ora porque o formato pedido (`event:123:available → 1000`) já é uma chave-valor simples, e introduzir uma estrutura de Hash antes de haver um segundo campo é complexidade prematura; migrar para Hash mais tarde, se necessário, é uma mudança localizada nesta mesma capacidade.

### Ordem das escritas: Postgres primeiro, Redis depois, com rollback via `@Transactional`
O evento é salvo no Postgres primeiro (para obter o `id` gerado, necessário para montar a chave do Redis) e, em seguida, a disponibilidade é escrita no Redis dentro do mesmo método `@Transactional` do serviço de criação de evento. Se a escrita no Redis lançar uma exceção, ela se propaga e o Spring reverte a transação do Postgres antes de confirmá-la (o commit só acontece ao final do método, quando ele retorna normalmente) — nenhum evento fica persistido. Alternativa considerada: coordenar Postgres e Redis via transação distribuída (2PC/JTA) — rejeitada por ser complexidade de infraestrutura desproporcional a este incremento; a ordenação com rollback no lado do Postgres cobre o caso real de falha (Redis indisponível ou erro de escrita) sem exigir um coordenador de transações distribuídas.

### Redis de teste via `@TestConfiguration` reutilizável, não Testcontainers declarativo
O Postgres de teste usa a URL JDBC declarativa `jdbc:tc:postgresql:...` (sem `@Container`/`@ServiceConnection` no código de teste), mas o Redis não tem um driver JDBC — essa técnica não se aplica a ele. Em vez de instanciar um `GenericContainer` do Redis dentro de cada classe de teste (o boilerplate que a URL declarativa do Postgres evita), a suíte usa uma única classe `@TestConfiguration` reutilizável com um `GenericContainer("redis:7-alpine")` anotado `@ServiceConnection`, importada pelos testes que precisam de Redis. Alternativa considerada: subir o Redis via `spring-boot-docker-compose` reaproveitando o `docker-compose.yml` do projeto — rejeitada porque misturaria dois mecanismos de teste diferentes (Testcontainers efêmero para o Postgres, Docker Compose de longa duração para o Redis) na mesma suíte, tornando o ciclo de vida dos testes inconsistente.

### Chave de disponibilidade sem TTL
A chave `event:<id>:available` não expira — representa o estado corrente de inventário do evento, não um cache com validade. Alternativa considerada: TTL curto com repopulação sob demanda a partir do Postgres — rejeitada porque, sem uma rotina de repopulação implementada nesta fase, um TTL faria a chave sumir e deixaria a disponibilidade sem fonte, contrariando o próprio objetivo do change.

## Risks / Trade-offs

- [A ordenação Postgres→Redis com rollback via `@Transactional` não é uma transação distribuída de verdade: se o processo falhar exatamente entre o commit do Postgres e a escrita no Redis, um evento pode ficar sem chave de disponibilidade] → Mitigação: essa janela é a mesma assumida por qualquer integração sem 2PC; fases futuras que implementarem reconciliação Postgres↔Redis podem detectar e corrigir esse caso. Não é resolvido neste change.
- [Redis indisponível bloqueia toda a criação de eventos, já que a escrita é obrigatória (tudo ou nada)] → Mitigação: é a troca deliberada desta decisão (ver proposal.md e a pergunta respondida pelo usuário) — prioriza consistência entre Postgres e Redis em vez de disponibilidade da criação de evento.

## Open Questions

- Qual armazenamento será a fonte da verdade para disponibilidade quando o decremento por reserva for implementado (Redis com sincronização assíncrona para o Postgres, ou o inverso)? Não afeta este change; fica para a fase que implementar reservas.
