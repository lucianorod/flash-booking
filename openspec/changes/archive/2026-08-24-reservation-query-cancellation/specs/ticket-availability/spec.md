## ADDED Requirements

### Requirement: Devolução atômica de saldo ao cancelar
O sistema SHALL, em uma única operação atômica e indivisível, atualizar o status da reserva para `CANCELLED` no Redis e somar a quantidade da reserva de volta à disponibilidade do evento (`event:<id>:available`), quando a reserva cancelada estiver com status `PENDING` ou `CONFIRMED`.

#### Scenario: Cancelamento devolve a quantidade ao saldo disponível
- **WHEN** uma reserva com quantidade N e status `PENDING` ou `CONFIRMED` é cancelada
- **THEN** a disponibilidade do evento no Redis aumenta em N, e o status da reserva no Redis passa a ser `CANCELLED`, como parte da mesma operação atômica

### Requirement: Cancelamento não altera reservas já finalizadas
O sistema SHALL abortar a operação de cancelamento, sem alterar o saldo disponível nem o status da reserva, quando a reserva já estiver `CANCELLED` ou `EXPIRED`.

#### Scenario: Tentativa de cancelar reserva já cancelada não devolve saldo novamente
- **WHEN** uma reserva já com status `CANCELLED` é submetida novamente ao cancelamento
- **THEN** o saldo disponível do evento não é alterado, e o status da reserva permanece `CANCELLED`

#### Scenario: Tentativa de cancelar reserva expirada não devolve saldo
- **WHEN** uma reserva com status `EXPIRED` é submetida ao cancelamento
- **THEN** o saldo disponível do evento não é alterado, e o status da reserva permanece `EXPIRED`

### Requirement: Publicação do cancelamento para sincronização assíncrona
O sistema SHALL publicar, como parte da mesma operação atômica que devolve o saldo, um evento identificando a reserva cancelada no stream de reservas, habilitando a atualização assíncrona do status no Postgres.

#### Scenario: Cancelamento efetivado é publicado para sincronização
- **WHEN** uma reserva é cancelada com sucesso
- **THEN** um evento identificando essa reserva como cancelada é publicado no stream de reservas, disponível para consumo pelo processo de sincronização assíncrona

### Requirement: Worker distingue criação e cancelamento no mesmo stream
O sistema SHALL, ao consumir o stream de reservas, distinguir eventos de criação e de cancelamento, aplicando a cada reserva a persistência ou a atualização de status correspondente no Postgres.

#### Scenario: Worker aplica um cancelamento publicado no stream
- **WHEN** o worker consome um evento de cancelamento de uma reserva já existente
- **THEN** o status dessa reserva no Postgres é atualizado para `CANCELLED`
