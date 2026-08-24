## ADDED Requirements

### Requirement: Consumo concorrente do stream de reservas por múltiplas instâncias
O sistema SHALL permitir que múltiplas instâncias da aplicação consumam simultaneamente o grupo de consumidores do stream de reservas, cada uma identificada por um nome de consumidor próprio, de modo que cada mensagem seja entregue a exatamente uma instância por vez.

#### Scenario: Duas instâncias consumindo o mesmo grupo de consumidores
- **WHEN** duas ou mais instâncias da aplicação estão ativas e consumindo o grupo de consumidores do stream de reservas
- **THEN** cada instância se registra com um nome de consumidor distinto, e cada mensagem publicada no stream é processada por exatamente uma das instâncias

### Requirement: Retomada de mensagens pendentes de uma instância inativa
O sistema SHALL, periodicamente, reivindicar mensagens do stream de reservas que permaneceram pendentes (entregues, mas não confirmadas) por mais tempo que um limite configurável, entregando-as a uma instância ativa para reprocessamento.

#### Scenario: Instância cai antes de confirmar uma mensagem recebida
- **WHEN** uma instância recebe uma mensagem do stream de reservas e se torna inativa antes de confirmá-la, e o tempo decorrido desde a entrega ultrapassa o limite configurado de inatividade
- **THEN** a mensagem é reivindicada por uma instância ativa e reprocessada, sem exigir intervenção manual

### Requirement: Reprocessamento de mensagem de cancelamento ou expiração pendente de sincronização
O sistema SHALL, ao consumir uma mensagem de cancelamento ou de expiração cuja reserva ainda não exista no Postgres, republicar essa mensagem no stream de reservas para nova tentativa, até um número máximo configurável de tentativas, em vez de tratá-la como falha definitiva na primeira tentativa.

#### Scenario: Cancelamento processado antes da criação ser persistida
- **WHEN** o worker consome uma mensagem de cancelamento de uma reserva que ainda não foi persistida no Postgres pelo processamento assíncrono da criação
- **THEN** o worker republica a mensagem de cancelamento no stream de reservas para nova tentativa, sem registrar isso como mensagem envenenada

#### Scenario: Limite de tentativas de reprocessamento esgotado
- **WHEN** uma mensagem de cancelamento ou de expiração é reprocessada até atingir o número máximo configurável de tentativas, e a reserva correspondente continua inexistente no Postgres
- **THEN** o worker deixa de republicar essa mensagem e registra o ocorrido como mensagem envenenada, com contexto completo para inspeção manual

### Requirement: Rastreio de reservas pendentes por prazo de expiração
O sistema SHALL, como parte da mesma operação atômica que registra uma reserva aceita, registrar essa reserva em uma estrutura do Redis ordenada pelo seu prazo de expiração, habilitando a identificação periódica de reservas pendentes vencidas.

#### Scenario: Reserva aceita é registrada para rastreio de expiração
- **WHEN** uma reserva é aceita
- **THEN** o identificador dessa reserva passa a constar na estrutura de rastreio por prazo de expiração, ordenado pelo seu `expiresAt`

### Requirement: Expiração atômica de reservas pendentes vencidas
O sistema SHALL, periodicamente, identificar reservas com status `PENDING` cujo prazo de expiração já tenha passado e, para cada uma, em uma única operação atômica e indivisível: atualizar seu status para `EXPIRED` no Redis, somar a quantidade reservada de volta à disponibilidade do evento, remover a reserva da estrutura de rastreio por prazo de expiração, e publicar um evento identificando a expiração no stream de reservas.

#### Scenario: Reserva pendente com prazo vencido é expirada automaticamente
- **WHEN** uma reserva com quantidade N e status `PENDING` tem seu `expiresAt` ultrapassado
- **THEN** em algum momento após o vencimento, o status dessa reserva no Redis passa a ser `EXPIRED`, a disponibilidade do evento no Redis aumenta em N, a reserva é removida da estrutura de rastreio por prazo de expiração, e um evento identificando essa expiração é publicado no stream de reservas

#### Scenario: Reserva com prazo vencido, mas não mais pendente, não é expirada
- **WHEN** uma reserva cujo `expiresAt` já passou não está mais com status `PENDING` (por já ter sido cancelada ou confirmada)
- **THEN** a varredura de expiração remove essa reserva da estrutura de rastreio sem alterar seu status nem a disponibilidade do evento
