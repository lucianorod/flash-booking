# reservation-management Specification

## Purpose

Define o modelo de dados da Reserva, vinculando um evento a um usuário e mapeando o schema de banco sobre o qual o fluxo de reserva, a expiração automática e a idempotência serão construídos nas fases seguintes.

## Requirements

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

### Requirement: Criação de reserva
O sistema SHALL permitir a criação de uma reserva para um evento através de uma requisição `POST /events/<id>/reservations`, exigindo o header `Idempotency-Key` e um corpo com `userId` e `quantity`, aceitando a reserva quando os dados forem válidos e houver saldo disponível suficiente.

#### Scenario: Reserva aceita com dados válidos e saldo suficiente
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada com um `Idempotency-Key` inédito, `userId` e `quantity` válidos, e o evento possui saldo disponível suficiente
- **THEN** o sistema responde com status 201 (Created) e o identificador da reserva aceita

### Requirement: Resposta idempotente para chave de idempotência repetida
O sistema SHALL responder com status 200 (OK) e o identificador da reserva processada originalmente quando a mesma `Idempotency-Key` for reutilizada, sem criar uma nova reserva.

#### Scenario: Reenvio com a mesma chave de idempotência retorna a reserva original
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada com um `Idempotency-Key` já usado em uma requisição aceita anteriormente
- **THEN** o sistema responde com status 200 (OK) e o identificador da reserva criada na requisição original, sem criar uma nova reserva

### Requirement: Recusa por estoque insuficiente
O sistema SHALL responder com status 409 (Conflict), sem aceitar a reserva, quando não houver saldo disponível suficiente para a quantidade solicitada.

#### Scenario: Requisição com quantidade maior que o saldo disponível
- **WHEN** uma requisição `POST /events/<id>/reservations` solicita uma quantidade maior do que o saldo disponível do evento
- **THEN** o sistema responde com status 409 (Conflict) e nenhuma reserva é criada

### Requirement: Recusa de reserva para evento inexistente
O sistema SHALL responder com status 404 (Not Found), sem aceitar a reserva, quando `POST /events/<id>/reservations` for chamado para um identificador de evento que não corresponde a nenhum evento conhecido.

#### Scenario: Requisição de reserva para evento inexistente
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada para um identificador de evento que não corresponde a nenhum evento conhecido no Redis nem no Postgres
- **THEN** o sistema responde com status 404 (Not Found) e nenhuma reserva é criada

### Requirement: Validação dos dados de entrada da reserva
O sistema SHALL validar a presença do header `Idempotency-Key` e do `userId`, e que `quantity` seja um inteiro positivo, retornando um erro explícito sem criar a reserva quando os dados forem inválidos.

#### Scenario: Requisição sem o header Idempotency-Key
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada sem o header `Idempotency-Key`
- **THEN** o sistema responde com status 400 (Bad Request) e um corpo de erro indicando o header ausente, sem criar a reserva

#### Scenario: Requisição com quantidade inválida
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada com `quantity` igual ou menor que zero
- **THEN** o sistema responde com status 400 (Bad Request) e um corpo de erro indicando que a quantidade é inválida, sem criar a reserva

### Requirement: Persistência assíncrona consistente da reserva aceita
O sistema SHALL, eventualmente, persistir no Postgres toda reserva aceita, com todos os campos obrigatórios preenchidos e corretamente tipados, refletindo os mesmos dados aceitos no momento da criação.

#### Scenario: Reserva aceita é eventualmente persistida no Postgres
- **WHEN** uma reserva é aceita
- **THEN** em algum momento após a aceitação, uma reserva com o mesmo identificador passa a existir no Postgres, com evento, usuário, quantidade, status, prazo de expiração e chave de idempotência preenchidos e corretamente tipados

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

### Requirement: Persistência assíncrona consistente da expiração da reserva
O sistema SHALL, eventualmente, refletir no Postgres o status `EXPIRED` de toda reserva pendente cujo prazo de expiração tenha vencido e sido processado pela expiração automática.

#### Scenario: Expiração automática é eventualmente refletida no Postgres
- **WHEN** uma reserva pendente é expirada automaticamente
- **THEN** em algum momento após a expiração, a reserva correspondente no Postgres passa a ter status `EXPIRED`
