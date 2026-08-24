## Why

`EventController` e `EventAvailabilityController` já vivem no mesmo pacote (`com.flashbooking.event`, desde o change `merge-availability-into-event-package`) e já compartilham o mesmo `@RequestMapping("/events")`, cada um expondo apenas um endpoint (`POST /events` e `GET /events/{id}`, respectivamente). Manter dois `@RestController` para o mesmo recurso HTTP (`/events`) é indireção sem benefício agora que a separação de pacote que antes os distinguia deixou de existir.

## What Changes

- Unificar os dois endpoints em um único `EventController`, com `POST /events` (criação) e `GET /events/{id}` (consulta de disponibilidade), injetando tanto `EventService` quanto `EventAvailabilityQueryService`.
- Remover `EventAvailabilityController.kt`.
- Nenhuma mudança de rota, verbo HTTP, corpo de requisição/resposta ou código de status — contrato HTTP idêntico ao atual.
- `EventService` e `EventAvailabilityQueryService` permanecem como serviços separados, sem alteração — o pedido é sobre reduzir o número de controllers, não de serviços.

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

(nenhuma — fusão de controllers pura, sem alteração de comportamento externamente observável; `.openspec.yaml` declara `skip_specs: true`)

## Impact

- `EventController.kt`: ganha o método `getAvailability`, a dependência de `EventAvailabilityQueryService` e seus logs de requisição/resposta.
- `EventAvailabilityController.kt`: removido.
- Nenhum teste referencia essas classes pelo nome (todos exercitam via HTTP com RestAssured), então nenhum teste precisa ser reescrito — apenas continuar passando.
