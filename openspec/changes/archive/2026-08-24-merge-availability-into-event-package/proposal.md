## Why

Uma revisão do código mostra que o pacote `com.flashbooking.availability` não é independente de `com.flashbooking.event`: as duas direções de dependência já existem hoje. `EventService` (em `event`) depende de `EventAvailabilityCache` (em `availability`) para inicializar o saldo no Redis ao criar um evento, e `EventAvailabilityQueryService` (em `availability`) depende de `EventRepository`/`EventNotFoundException` (em `event`) para o fallback de leitura no cache miss. Essa dependência circular entre pacotes é sintoma de que a separação em dois pacotes Kotlin não reflete um limite de domínio real: as quatro classes de `availability` (mais o DTO) tratam do mesmo agregado `Event`, só que pela ótica do saldo em tempo real no Redis em vez do registro no Postgres. Consolidar tudo em `com.flashbooking.event` elimina o ciclo e reduz a indireção, sem alterar nenhum comportamento observável.

## What Changes

- Mover `EventAvailabilityCache`, `EventAvailabilityController`, `EventAvailabilityQueryService` e o DTO `EventAvailabilityResponse` do pacote `com.flashbooking.availability` (e `com.flashbooking.availability.dto`) para `com.flashbooking.event` (e `com.flashbooking.event.dto`), atualizando a declaração `package` de cada arquivo.
- Mover os quatro testes de integração hoje em `src/test/kotlin/com/flashbooking/availability/` para `src/test/kotlin/com/flashbooking/event/`, atualizando a declaração `package`.
- Atualizar todos os `import com.flashbooking.availability.*` remanescentes (em `ReservationLuaExecutor`, `EventService` e nos testes de reserva que usam `EventAvailabilityCache`) para `com.flashbooking.event.*`, removendo o import onde a classe passa a estar no mesmo pacote do arquivo que a usa.
- Remover os diretórios `com/flashbooking/availability` (main e test), agora vazios.
- Nenhuma classe é renomeada, combinada ou tem sua lógica alterada — é puramente uma reorganização de pacote.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

(nenhuma — reorganização de pacote pura, sem alteração de comportamento externamente observável; as capabilities OpenSpec `event-management` e `ticket-availability` continuam existindo como especificações separadas, já que specs descrevem comportamento e não precisam espelhar pacotes Kotlin 1:1; `.openspec.yaml` declara `skip_specs: true`)

## Impact

- Pacote `com.flashbooking.availability` deixa de existir (main e test); suas classes passam a viver em `com.flashbooking.event`.
- Nenhuma mudança de contrato HTTP, de schema de banco, de payload do stream ou de comportamento de cache — todos os testes de integração existentes continuam validando o mesmo comportamento, apenas movidos de pacote.
- Arquivos afetados fora do próprio pacote `availability`: `EventService.kt`, `ReservationLuaExecutor.kt`, e os testes de reserva que importam `EventAvailabilityCache` (`ReservationCreationIntegrationTest`, `ReservationQueryCancellationIntegrationTest`, `ReservationConcurrencyIntegrationTest`, `ReservationExpirationIntegrationTest`).
