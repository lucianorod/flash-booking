## Why

O núcleo do Flash Booking precisa controlar a disponibilidade de ingressos com baixa latência e alta concorrência, o que o Postgres sozinho não atende bem sob picos de tráfego. O Redis já está configurado como dependência do projeto, mas ainda sem uso funcional. Este change dá o primeiro passo: ao criar um evento, sua disponibilidade de ingressos também passa a existir no Redis, servindo de base para as regras de reserva e prevenção de oversell que serão construídas em cima dessa contagem nas fases seguintes.

## What Changes

- Ao criar um evento com sucesso via `POST /events`, o sistema também inicializa, no Redis, a disponibilidade de ingressos desse evento na chave `event:<id>:available`, com o valor da capacidade disponível do evento recém-criado.
- Se a escrita no Redis falhar, a criação do evento falha por completo: nada é persistido no Postgres nem no Redis (tudo ou nada), evitando que um evento exista sem disponibilidade rastreada.
- Esta é a spec inicial de uma capacidade que será incrementada em fases futuras (decremento ao reservar, reposição ao expirar/cancelar, leitura de disponibilidade, reconciliação com o Postgres). Este change cobre apenas a inicialização da chave na criação do evento.

## Capabilities

### New Capabilities
- `ticket-availability`: disponibilidade de ingressos por evento, rastreada no Redis na chave `event:<id>:available`. Nesta primeira versão, cobre apenas a inicialização da chave ao criar um evento; decremento/reposição/leitura ficam para fases futuras.

### Modified Capabilities
_Nenhuma — a criação de evento em si (contrato HTTP de `POST /events`) não muda; apenas passa a ter um efeito colateral adicional coberto pela nova capacidade `ticket-availability`._

## Impact

- **Código novo**: componente de acesso ao Redis para disponibilidade de ingressos (ex.: `EventAvailabilityCache` ou equivalente), integração desse componente ao fluxo de criação de evento (`EventService`), tratamento de erro para falha de escrita no Redis.
- **Dependências**: usa a dependência `spring-boot-starter-data-redis` já configurada (sem uso funcional até este change).
- **Infraestrutura**: nenhuma mudança — Redis já está no `docker-compose.yml` desde a fundação do projeto.
- **APIs**: nenhuma mudança de contrato em `POST /events`; passa a ter um efeito colateral (escrita no Redis) que, se falhar, faz a requisição inteira falhar.
- **Sem impacto nas specs `event-management`, `reservation-management` ou `project-foundation`** — capacidade nova e independente.
