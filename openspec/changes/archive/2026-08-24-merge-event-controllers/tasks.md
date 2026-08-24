## 1. Unificar os controllers

- [x] 1.1 Adicionar a dependência `EventAvailabilityQueryService` e o método `getAvailability` (`GET /events/{id}`) a `EventController`, incluindo os logs de requisição/resposta hoje presentes em `EventAvailabilityController`.
- [x] 1.2 Remover `EventAvailabilityController.kt`.

## 2. Verificação

- [x] 2.1 Rodar o build completo (`./gradlew build`) e confirmar que compila e que todos os testes existentes continuam passando, sem nenhum novo teste exigido (mudança estrutural pura, contrato HTTP inalterado).
