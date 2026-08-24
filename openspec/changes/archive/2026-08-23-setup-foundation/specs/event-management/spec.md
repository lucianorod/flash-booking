## Purpose

Define o modelo de dados de Evento e o caso de uso de criação de evento, formando a base de domínio sobre a qual as regras futuras de reserva, prevenção de oversell e expiração de ingressos serão construídas.

## ADDED Requirements

### Requirement: Modelo de dados do Evento
O sistema SHALL representar um Evento com capacidade total de ingressos, capacidade disponível de ingressos, status (`PUBLISHED`, `SOLD_OUT` ou `CANCELLED`), e timestamps de criação e última atualização.

#### Scenario: Evento recém-criado é publicado com capacidade disponível igual à total
- **WHEN** um evento é criado com sucesso
- **THEN** o evento persistido possui status `PUBLISHED`, capacidade disponível igual à capacidade total informada, e os timestamps de criação e atualização preenchidos

### Requirement: Criação de evento
O sistema SHALL permitir a criação de um novo evento através de uma requisição `POST /events` contendo os dados do evento, persistindo-o quando os dados forem válidos.

#### Scenario: Criação de evento com dados válidos
- **WHEN** uma requisição `POST /events` é enviada com nome, dados do evento e capacidade total de ingressos válidos (capacidade maior que zero)
- **THEN** o sistema persiste o evento e responde com status 201 (Created) e os dados do evento criado, incluindo seu identificador

### Requirement: Validação e tratamento de erros na criação de evento
O sistema SHALL validar os dados recebidos em `POST /events` e retornar um erro explícito, sem persistir nenhum dado, quando os dados forem inválidos.

#### Scenario: Requisição sem campos obrigatórios
- **WHEN** uma requisição `POST /events` é enviada sem um campo obrigatório (por exemplo, sem nome do evento)
- **THEN** o sistema responde com status 400 (Bad Request) e um corpo de erro identificando o(s) campo(s) inválido(s), sem persistir o evento

#### Scenario: Requisição com capacidade total inválida
- **WHEN** uma requisição `POST /events` é enviada com capacidade total de ingressos igual ou menor que zero
- **THEN** o sistema responde com status 400 (Bad Request) e um corpo de erro indicando que a capacidade é inválida, sem persistir o evento
