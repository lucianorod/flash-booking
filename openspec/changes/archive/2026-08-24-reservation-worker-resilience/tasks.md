## 1. Configuração

- [x] 1.1 Adicionar `@EnableScheduling` na classe principal da aplicação.
- [x] 1.2 Estender `ReservationProperties` (ou criar uma classe de propriedades aninhada dedicada) com: `streamRecoveryMinIdleTimeMs`, `streamRecoveryIntervalMs`, `actionMessageMaxRetries`, `expirationSweepIntervalMs`, `expirationSweepBatchSize`.
- [x] 1.3 Adicionar os valores padrão dessas propriedades em `application.yml` (ex.: idle mínimo 60000ms, intervalo de recuperação 30000ms, máximo de 5 tentativas, intervalo de varredura de expiração 5000ms, lote de 200).

## 2. Consumidor único por instância

- [x] 2.1 Alterar `ReservationStreamConfig` para gerar, na inicialização do bean, um nome de consumidor único combinando hostname e um sufixo aleatório, em vez do valor fixo `reservation-worker-1`.
- [x] 2.2 Escrever teste validando que duas instâncias do gerador de nome de consumidor (ou dois carregamentos do bean) produzem nomes distintos.

## 3. Recuperação de mensagens pendentes

- [x] 3.1 Criar um componente agendado (`@Scheduled`, intervalo = `streamRecoveryIntervalMs`) que lista, via `StreamOperations.pending(...)`, as entradas pendentes do grupo de consumidores do stream de reservas com tempo de inatividade maior que `streamRecoveryMinIdleTimeMs`.
- [x] 3.2 Para cada entrada pendente encontrada, reivindicá-la para a instância atual via `StreamOperations.claim(...)` e invocar `ReservationStreamListener.onMessage` diretamente, reaproveitando a confirmação (`XACK`) já existente no listener.
- [x] 3.3 Escrever teste de integração: publicar uma mensagem, simular uma instância que nunca confirma (chamar o listener sem invocar `acknowledge`, ou usar um listener que descarta a confirmação), configurar um tempo de inatividade mínimo baixo, disparar a tarefa de recuperação manualmente e validar que a mensagem é reivindicada e efetivamente processada (reserva persistida no Postgres).

## 4. Reprocessamento de cancelamento/expiração pendente de sincronização

- [x] 4.1 Adicionar um método de domínio `markExpired()` em `Reservation`, análogo a `markCancelled()`, transicionando o status para `EXPIRED`.
- [x] 4.2 Extrair, em `ReservationStreamListener`, uma função auxiliar comum para tratar "reserva não encontrada" ao processar `CANCEL` ou `EXPIRE`: ler `retryCount` da mensagem (ausente = 0); se menor que `actionMessageMaxRetries`, publicar (`XADD`) uma nova mensagem idêntica com `retryCount` incrementado e confirmar a mensagem original; caso contrário, logar como mensagem envenenada com contexto completo e confirmar a mensagem original.
- [x] 4.3 Atualizar `handleCancel` para usar essa função auxiliar quando a reserva não for encontrada no Postgres, em vez de lançar exceção que propaga para o `catch` genérico de `onMessage`.
- [x] 4.4 Escrever teste de integração validando que uma mensagem `CANCEL` para uma reserva ainda inexistente no Postgres é republicada com `retryCount` incrementado, e não tratada como mensagem envenenada antes de esgotar as tentativas.
- [x] 4.5 Escrever teste de integração validando que, ao esgotar `actionMessageMaxRetries` tentativas de `CANCEL` sem a reserva existir no Postgres, o worker confirma a última mensagem sem republicá-la novamente e registra o ocorrido em log.

## 5. Rastreio e expiração automática de reservas no Redis

- [x] 5.1 Atualizar `reserve_tickets.lua` para registrar a reserva aceita no ZSET global `reservations:pending-expiration` (`ZADD`, score = `expiresAt` em epoch milissegundos), como parte da mesma operação atômica.
- [x] 5.2 Atualizar `cancel_reservation.lua` para remover a reserva cancelada desse ZSET (`ZREM`), como parte da mesma operação atômica de cancelamento.
- [x] 5.3 Criar o script `expire_reservations.lua`: buscar, via `ZRANGEBYSCORE` limitado a `expirationSweepBatchSize`, as reservas do ZSET com `expiresAt` já vencido; para cada uma, se o status no Hash ainda for `PENDING`, atualizar para `EXPIRED`, devolver a quantidade à disponibilidade do evento e publicar `action = "EXPIRE"` no stream; em qualquer caso (vencida e ainda `PENDING`, ou já não mais `PENDING`), remover a reserva do ZSET; retornar a quantidade de reservas efetivamente expiradas.
- [x] 5.4 Adicionar o carregamento e a invocação de `expire_reservations.lua` em `ReservationLuaExecutor`.
- [x] 5.5 Criar um componente agendado (`@Scheduled`, intervalo = `expirationSweepIntervalMs`) que invoca a expiração no `ReservationLuaExecutor`.
- [x] 5.6 Implementar o fluxo de `action = "EXPIRE"` em `ReservationStreamListener`: buscar a reserva no Postgres e chamar `markExpired()`; ao não encontrar a reserva, aplicar o mesmo tratamento de reprocessamento com contagem de tentativas da seção 4.

## 6. Testes de expiração de ponta a ponta

- [x] 6.1 Escrever teste de integração validando que uma reserva `PENDING` com `expiresAt` vencido é expirada pela varredura: status `EXPIRED` no Redis, saldo do evento devolvido no Redis, reserva removida do ZSET de rastreio, e reserva eventualmente `EXPIRED` no Postgres.
- [x] 6.2 Escrever teste de integração validando que uma reserva já cancelada antes do vencimento não é afetada pela varredura de expiração (permanece `CANCELLED`, saldo não é alterado uma segunda vez).
- [x] 6.3 Escrever teste de integração validando que, após a expiração automática, uma tentativa de `DELETE /reservations/:id` para essa reserva responde 409 (reaproveitando a regra já existente para reserva `EXPIRED`), sem devolver saldo novamente.

## 7. Build

- [x] 7.1 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.
