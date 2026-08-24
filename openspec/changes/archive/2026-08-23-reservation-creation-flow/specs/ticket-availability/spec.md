## ADDED Requirements

### Requirement: Verificação e decremento atômico de saldo ao reservar
O sistema SHALL checar, em uma única operação atômica e indivisível, se `event:<id>:available` possui saldo suficiente para a quantidade solicitada e, em caso positivo, decrementar esse saldo pela quantidade solicitada — de forma que duas requisições concorrentes nunca possam ambas decrementar o saldo além do que ele suporta.

#### Scenario: Duas requisições concorrentes disputando o último ingresso
- **WHEN** duas requisições de reserva concorrentes solicitam ingressos de um evento cujo saldo disponível só é suficiente para uma delas
- **THEN** exatamente uma das requisições tem seu saldo decrementado com sucesso, e a outra é recusada por saldo insuficiente sem decrementar o saldo

#### Scenario: Saldo insuficiente impede a reserva
- **WHEN** uma requisição de reserva solicita uma quantidade maior do que o saldo disponível em `event:<id>:available`
- **THEN** o saldo não é decrementado e nenhuma reserva é registrada

### Requirement: Registro atômico dos dados da reserva no Redis
O sistema SHALL, como parte da mesma operação atômica que decrementa o saldo, registrar os dados da reserva no Redis (evento, usuário, quantidade, status `PENDING` e prazo de expiração) antes de confirmar sucesso ao cliente.

#### Scenario: Reserva aceita tem seus dados registrados no Redis
- **WHEN** uma reserva é aceita porque havia saldo suficiente
- **THEN** os dados da reserva (evento, usuário, quantidade, status `PENDING` e prazo de expiração) ficam registrados no Redis com o mesmo identificador retornado ao cliente

### Requirement: Publicação da reserva para persistência assíncrona
O sistema SHALL publicar, como parte da mesma operação atômica, os dados completos da reserva aceita em um stream do Redis, habilitando a persistência assíncrona no Postgres.

#### Scenario: Reserva aceita é publicada para persistência
- **WHEN** uma reserva é aceita
- **THEN** os dados completos dessa reserva são publicados no stream de reservas, disponíveis para consumo por um processo de persistência assíncrona

### Requirement: Idempotência da reserva por chave fornecida pelo cliente
O sistema SHALL tratar uma requisição de reserva que reutiliza uma chave de idempotência já usada anteriormente como já processada, retornando o resultado da requisição original sem reavaliar o saldo nem criar uma nova reserva, durante uma janela de retenção limitada dessa chave.

#### Scenario: Reenvio com a mesma chave de idempotência não decrementa o saldo novamente
- **WHEN** uma requisição de reserva é enviada com uma chave de idempotência já usada em uma requisição aceita anteriormente
- **THEN** o saldo disponível não é decrementado novamente, e o identificador da reserva original é retornado

#### Scenario: Chave de idempotência expira após a janela de retenção
- **WHEN** uma requisição de reserva é enviada com uma chave de idempotência que já foi usada, mas cuja janela de retenção já expirou
- **THEN** a requisição é tratada como uma nova reserva, sujeita à checagem de saldo normalmente
