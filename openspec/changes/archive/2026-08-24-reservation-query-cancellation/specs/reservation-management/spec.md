## ADDED Requirements

### Requirement: Consulta de reserva
O sistema SHALL permitir consultar uma reserva através de uma requisição `GET /reservations/<id>`, respondendo com o identificador da reserva, o identificador do evento, o identificador do usuário, a quantidade, o status e o prazo de expiração quando a reserva existir.

#### Scenario: Consulta de reserva recém-criada, ainda não sincronizada no Postgres
- **WHEN** uma requisição `GET /reservations/<id>` é enviada para uma reserva que acabou de ser aceita e ainda não foi persistida no Postgres pelo processo assíncrono
- **THEN** o sistema responde com status 200 (OK) e os dados da reserva, obtidos a partir do Redis

#### Scenario: Consulta de reserva já sincronizada no Postgres
- **WHEN** uma requisição `GET /reservations/<id>` é enviada para uma reserva que já foi persistida no Postgres
- **THEN** o sistema responde com status 200 (OK) e os dados da reserva

#### Scenario: Reserva inexistente
- **WHEN** uma requisição `GET /reservations/<id>` é enviada para um identificador que não corresponde a nenhuma reserva conhecida no Redis nem no Postgres
- **THEN** o sistema responde com status 404 (Not Found)

### Requirement: Cancelamento de reserva
O sistema SHALL permitir cancelar uma reserva com status `PENDING` ou `CONFIRMED` através de uma requisição `DELETE /reservations/<id>`, devolvendo imediatamente a quantidade reservada à disponibilidade do evento.

#### Scenario: Cancelamento de reserva pendente
- **WHEN** uma requisição `DELETE /reservations/<id>` é enviada para uma reserva com status `PENDING`
- **THEN** o sistema responde com status 204 (No Content), a reserva passa a ter status `CANCELLED`, e a quantidade reservada volta a fazer parte da disponibilidade do evento

#### Scenario: Cancelamento de reserva confirmada
- **WHEN** uma requisição `DELETE /reservations/<id>` é enviada para uma reserva com status `CONFIRMED`
- **THEN** o sistema responde com status 204 (No Content), a reserva passa a ter status `CANCELLED`, e a quantidade reservada volta a fazer parte da disponibilidade do evento

### Requirement: Idempotência do cancelamento
O sistema SHALL responder com status 204 (No Content), sem alterar nada, quando `DELETE /reservations/<id>` for chamado para uma reserva que já está `CANCELLED`.

#### Scenario: Cancelamento repetido de uma reserva já cancelada
- **WHEN** uma requisição `DELETE /reservations/<id>` é enviada para uma reserva cujo status já é `CANCELLED`
- **THEN** o sistema responde com status 204 (No Content), sem devolver a quantidade novamente à disponibilidade do evento

### Requirement: Recusa de cancelamento de reserva expirada
O sistema SHALL responder com status 409 (Conflict), sem alterar a reserva, quando `DELETE /reservations/<id>` for chamado para uma reserva com status `EXPIRED`.

#### Scenario: Tentativa de cancelar reserva expirada
- **WHEN** uma requisição `DELETE /reservations/<id>` é enviada para uma reserva com status `EXPIRED`
- **THEN** o sistema responde com status 409 (Conflict), e a reserva permanece com status `EXPIRED`

### Requirement: Cancelamento de reserva inexistente
O sistema SHALL responder com status 404 (Not Found) quando `DELETE /reservations/<id>` for chamado para um identificador que não corresponde a nenhuma reserva conhecida.

#### Scenario: Cancelamento de reserva inexistente
- **WHEN** uma requisição `DELETE /reservations/<id>` é enviada para um identificador que não corresponde a nenhuma reserva conhecida no Redis nem no Postgres
- **THEN** o sistema responde com status 404 (Not Found)

### Requirement: Persistência assíncrona consistente do cancelamento
O sistema SHALL, eventualmente, refletir no Postgres o status `CANCELLED` de toda reserva cancelada com sucesso.

#### Scenario: Cancelamento aceito é eventualmente refletido no Postgres
- **WHEN** uma reserva é cancelada com sucesso
- **THEN** em algum momento após o cancelamento, a reserva correspondente no Postgres passa a ter status `CANCELLED`
