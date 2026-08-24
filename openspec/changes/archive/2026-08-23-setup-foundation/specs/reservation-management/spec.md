## Purpose

Define o modelo de dados da Reserva, vinculando um evento a um usuário e mapeando o schema de banco sobre o qual o fluxo de reserva, a expiração automática e a idempotência serão construídos nas fases seguintes.

## ADDED Requirements

### Requirement: Modelo de dados da Reserva
O sistema SHALL representar uma Reserva vinculada a um Evento e a um usuário, com quantidade de ingressos, status, prazo de expiração, chave de idempotência e timestamps de criação e atualização.

#### Scenario: Reserva persistida com todos os campos obrigatórios
- **WHEN** uma reserva é persistida com evento, usuário, quantidade, status, prazo de expiração e chave de idempotência válidos
- **THEN** o registro persistido contém o identificador do evento, o identificador do usuário, a quantidade, o status, o prazo de expiração, a chave de idempotência e os timestamps de criação e atualização preenchidos

### Requirement: Unicidade da chave de idempotência da reserva
O sistema SHALL impedir a existência de duas reservas com a mesma chave de idempotência.

#### Scenario: Tentativa de persistir chave de idempotência duplicada
- **WHEN** uma segunda reserva é persistida utilizando uma chave de idempotência já usada por outra reserva
- **THEN** a persistência da segunda reserva falha, e a reserva original permanece inalterada
