## 1. Leitura de disponibilidade no cache

- [x] 1.1 Adicionar um método de leitura em `EventAvailabilityCache` (ex.: `getAvailability(eventId): Int?`) que retorna o valor lido do Redis, ou `null` tanto para chave ausente quanto para falha de leitura no Redis (`DataAccessException` tratada como cache miss).

## 2. Endpoint GET /events/:id

- [x] 2.1 Criar o DTO de resposta com `event_id` e `available_capacity` (mapeados via `@JsonProperty` para o formato snake_case do contrato).
- [x] 2.2 Implementar o serviço de consulta de disponibilidade: tenta `EventAvailabilityCache.getAvailability`; em cache miss, consulta `EventRepository.findById`; se o evento não existir, sinaliza não encontrado; se existir, obtém `availableCapacity` e tenta repopular o Redis (falha na repopulação não impede a resposta).
- [x] 2.3 Implementar `EventAvailabilityController` com `GET /events/{id}`, retornando 200 com o DTO de disponibilidade.
- [x] 2.4 Adicionar tratamento de erro para evento não encontrado, retornando 404 com o corpo de erro já padronizado no projeto.

## 3. Testes

- [x] 3.1 Escrever teste de integração validando consulta com disponibilidade já presente no Redis: 200, valor correto.
- [x] 3.2 Escrever teste de integração validando cache miss com evento existente no Postgres: 200, valor correto, e a chave passa a existir no Redis após a consulta.
- [x] 3.3 Escrever teste de integração validando consulta a um evento inexistente: 404.
- [x] 3.4 Escrever teste validando que uma falha de leitura no Redis (indisponibilidade) degrada para o Postgres e responde normalmente (200/404), em vez de retornar erro.
- [x] 3.5 Escrever teste validando que uma falha ao repopular o cache não impede a resposta bem-sucedida da consulta.

## 4. Build

- [x] 4.1 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.

## 5. Timeout do cliente Redis (encontrado na verificação manual)

- [x] 5.1 Configurar `spring.data.redis.timeout` e `connect-timeout` explicitamente, para que uma indisponibilidade do Redis com conexão já estabelecida (ex.: `docker stop`) seja detectada em segundos e não trave a requisição — sem isso, a degradação para o Postgres não se sustenta na prática, como visto na verificação manual com Docker real.
