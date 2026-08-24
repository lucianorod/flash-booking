## ADDED Requirements

### Requirement: Criação de reserva
O sistema SHALL permitir a criação de uma reserva para um evento através de uma requisição `POST /events/<id>/reservations`, exigindo o header `Idempotency-Key` e um corpo com `user_id` e `quantity`, aceitando a reserva quando os dados forem válidos e houver saldo disponível suficiente.

#### Scenario: Reserva aceita com dados válidos e saldo suficiente
- **WHEN** uma requisição `POST /events/<id>/reservations` é enviada com um `Idempotency-Key` inédito, `user_id` e `quantity` válidos, e o evento possui saldo disponível suficiente
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

### Requirement: Validação dos dados de entrada da reserva
O sistema SHALL validar a presença do header `Idempotency-Key` e do `user_id`, e que `quantity` seja um inteiro positivo, retornando um erro explícito sem criar a reserva quando os dados forem inválidos.

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
