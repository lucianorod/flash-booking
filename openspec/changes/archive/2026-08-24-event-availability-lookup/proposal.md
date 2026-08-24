## Why

O Flash Booking já rastreia a disponibilidade de ingressos no Redis (`event:<id>:available`), mas nenhum endpoint permite consultá-la. Sob alta concorrência (Flash Sale), essa consulta é o caminho de leitura mais frequente do sistema — se cada leitura batesse direto no Postgres, o banco relacional se tornaria o gargalo e o ponto de falha sob pico de tráfego. Este change implementa a consulta de disponibilidade seguindo estritamente o padrão Cache-Aside: o Redis responde a leitura na imensa maioria dos casos, e o Postgres só é consultado (e o cache repopulado) quando a chave ainda não existe no Redis.

## What Changes

- Implementar o endpoint `GET /events/:id`, retornando a disponibilidade de ingressos do evento.
- `200 OK` com `{ "event_id": "...", "available_capacity": N }` quando a disponibilidade for encontrada — no Redis (caminho comum) ou, em cache miss, no Postgres (o cache é repopulado nesse caso).
- `404 Not Found` quando o evento não existir no Postgres (só verificado em cache miss).
- Se a leitura no Redis falhar por indisponibilidade (não por a chave simplesmente não existir), o sistema degrada para o Postgres como se fosse um cache miss, mantendo a leitura disponível às custas de proteção total do banco relacional durante essa janela de falha.
- Nenhuma mudança nas escritas existentes (criação de evento, criação de reserva) — este change é somente leitura.

## Capabilities

### New Capabilities
_Nenhuma — este change estende a capacidade `ticket-availability`, que já previa a leitura de disponibilidade como trabalho de uma fase futura._

### Modified Capabilities
- `ticket-availability`: adiciona a consulta de disponibilidade via `GET /events/:id`, com leitura Cache-Aside (Redis primeiro, Postgres em cache miss com repopulação do cache) e degradação para o Postgres quando o Redis está indisponível.

## Impact

- **Código novo**: endpoint `GET /events/:id` (controller/serviço), DTO de resposta de disponibilidade, lógica de leitura Cache-Aside (tentativa no Redis, fallback ao `EventRepository`, repopulação via `EventAvailabilityCache` ou equivalente).
- **Dependências**: nenhuma nova — usa `StringRedisTemplate` e `EventRepository` já existentes.
- **APIs**: novo endpoint `GET /events/:id`.
- **Sem impacto em `event-management`, `reservation-management` ou `project-foundation`** — o endpoint expõe apenas disponibilidade (não o recurso Evento completo) e não altera nenhum fluxo de escrita existente.
