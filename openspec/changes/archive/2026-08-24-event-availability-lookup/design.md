## Context

Ver `proposal.md` - Why para a motivação. Hoje `event:<id>:available` já existe no Redis desde a criação do evento (`EventAvailabilityCache.initializeAvailability`, ver `ticket-availability`) e é decrementado atomicamente pelo script Lua de reserva. `EventRepository` já expõe `findById` sobre a entidade `Event`, que tem `availableCapacity` (ver `event-management`). Nenhum endpoint de leitura existe ainda.

## Goals / Non-Goals

**Goals:**
- Responder `GET /events/<id>` majoritariamente a partir do Redis, sem tocar o Postgres.
- Em cache miss (chave ausente) ou falha de leitura no Redis (indisponibilidade), consultar o Postgres e repopular o Redis, sem falhar a requisição por causa do Redis.

**Non-Goals:**
- Expor o recurso Evento completo (nome, status, timestamps) — a resposta é só disponibilidade, como pedido.
- Implementar qualquer lock, invalidação proativa de cache, ou sincronização entre o contador do Redis e `events.available_capacity` no Postgres — esse último já é uma divergência aceita desde `redis-ticket-availability`/`reservation-creation-flow`; este change só lê o que já existe em cada armazenamento, sem reconciliar os dois.
- Métricas ou alertas de cache hit/miss — pode ser adicionado depois sem mudar o contrato.

## Decisions

### Cache miss e falha de leitura no Redis tratados pelo mesmo caminho de código
Tanto "chave ausente" (`GET` retorna `null` com o Redis saudável) quanto "Redis indisponível" (a chamada lança uma exceção) levam ao mesmo fallback: consultar o Postgres. A leitura ao Redis é envolvida em um `try/catch` que trata `DataAccessException` (hierarquia de exceção do Spring Data já usada em `ReservationStreamConfig`) como cache miss, unificando os dois cenários em vez de tratá-los como dois fluxos separados. Alternativa considerada: propagar a exceção de indisponibilidade do Redis como erro 5xx — rejeitada pela decisão já tomada (ver proposal.md): a leitura deve degradar para o Postgres em vez de falhar.

### Novo endpoint em um controller próprio, não no `EventController` existente
`GET /events/<id>` é implementado em um novo `EventAvailabilityController` (pacote `com.flashbooking.availability`), não como um novo método em `EventController`. Alternativa considerada: adicionar o método `GET` diretamente ao `EventController` existente, já que o path `/events` é o mesmo — rejeitada para manter a fronteira de capacidades já estabelecida em `reservation-creation-flow` (o `ReservationController` também vive fora de `EventController` apesar de compartilhar o prefixo `/events`): este endpoint pertence à capacidade `ticket-availability`, não a `event-management`, e sua resposta é inteiramente sobre disponibilidade, não sobre o recurso Evento.

### Repopulação do cache é melhor esforço, não bloqueia a resposta
Se a escrita de repopulação no Redis falhar após um cache miss resolvido via Postgres, a requisição atual ainda responde normalmente (200 ou 404) com os dados já obtidos do Postgres; a falha de repopulação é apenas logada. Alternativa considerada: falhar a requisição se a repopulação falhar — rejeitada porque o dado para responder ao cliente já foi obtido com sucesso do Postgres; fazer a resposta depender de uma escrita de cache que é puramente uma otimização para requisições futuras contradiz o próprio objetivo de proteger a disponibilidade da leitura.

### Reaproveitar `EventAvailabilityCache` para leitura, não um componente novo
`EventAvailabilityCache` (hoje só com `initializeAvailability`) ganha um método de leitura (`getAvailability`) e é reaproveitado tanto para escrever quanto para repopular a chave `event:<id>:available`, mantendo o formato da chave centralizado num único lugar. Alternativa considerada: criar um componente de leitura separado — rejeitada por duplicar a lógica do formato da chave sem necessidade.

### Timeout explícito no cliente Redis (`spring.data.redis.timeout`/`connect-timeout`)
Descoberto durante verificação manual: sem um timeout configurado, quando o Redis cai depois que uma conexão já foi estabelecida (ex.: `docker stop` no container), o cliente Lettuce pode ficar bloqueado por minutos numa chamada em vez de falhar rapidamente — muito diferente de uma porta fechada (que falha imediatamente), cenário que os testes automatizados usavam para simular indisponibilidade. Sem essa configuração, a "degradação para o Postgres" desta capacidade não se sustenta na prática: a requisição trava antes de sequer chegar ao `catch`. Um timeout de 2s (`spring.data.redis.timeout` e `connect-timeout`) foi adicionado em `application.yml`, garantindo que qualquer operação Redis sem resposta em 2s lance uma exceção (subclasse de `DataAccessException`) que os fluxos de leitura e escrita já tratam. Verificado manualmente: `GET /events/<id>` com Redis parado no meio de uma conexão estabelecida responde em menos de 200ms via Postgres, em vez de travar; `POST /events` também passa a falhar rápido (500) em vez de travar, mantendo o comportamento "tudo ou nada" já decidido em `redis-ticket-availability`, só que agora de forma responsiva.

## Risks / Trade-offs

- [Degradar para o Postgres durante indisponibilidade do Redis, sob tráfego de Flash Sale, pode gerar uma carga repentina de leituras no Postgres exatamente quando o Redis (a proteção) está fora] → Mitigação: é a troca deliberada desta decisão (ver proposal.md); a alternativa (falhar a leitura) foi explicitamente descartada em favor de disponibilidade da consulta.
- [Repopulação do cache após cada cache miss individual não usa nenhuma proteção contra "thundering herd" (muitas requisições simultâneas causando cache miss para o mesmo evento e todas consultando o Postgres ao mesmo tempo)] → Mitigação: fora do escopo deste change; um cache miss só deve ser comum logo após a criação de um evento com Redis recém-reiniciado, não durante operação normal, já que a chave normalmente já existe desde a criação do evento.

## Open Questions

- Vale a pena adicionar um lock/single-flight para colapsar leituras concorrentes de Postgres no mesmo cache miss? Não afeta o contrato desta fase; pode ser avaliado se o risco de "thundering herd" acima se mostrar real em produção.
