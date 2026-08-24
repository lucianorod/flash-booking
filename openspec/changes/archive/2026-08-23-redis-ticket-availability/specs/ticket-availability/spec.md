## Purpose

Rastreia no Redis, com baixa latência, a quantidade de ingressos disponíveis por evento, servindo de base para as regras de reserva e prevenção de oversell que serão construídas em cima dessa contagem nas fases seguintes.

## ADDED Requirements

### Requirement: Formato da chave de disponibilidade no Redis
O sistema SHALL representar a disponibilidade de ingressos de um evento no Redis através de uma chave no formato `event:<id>:available`, onde `<id>` é o identificador do evento, com o valor correspondendo à quantidade de ingressos disponíveis.

#### Scenario: Chave de disponibilidade de um evento existente
- **WHEN** um evento com identificador `<id>` possui disponibilidade rastreada no Redis
- **THEN** essa disponibilidade está armazenada na chave `event:<id>:available`

### Requirement: Inicialização da disponibilidade ao criar um evento
O sistema SHALL inicializar, no Redis, a disponibilidade de ingressos de um evento no momento em que ele é criado com sucesso via `POST /events`, com o valor igual à capacidade disponível do evento recém-criado.

#### Scenario: Evento criado com sucesso inicializa disponibilidade no Redis
- **WHEN** uma requisição `POST /events` cria um evento com sucesso e capacidade total de 1000 ingressos
- **THEN** a chave `event:<id>:available` é criada no Redis com o valor 1000, onde `<id>` é o identificador do evento criado

### Requirement: Falha atômica entre Postgres e Redis na criação do evento
O sistema SHALL falhar a criação do evento por completo, sem persistir o evento no Postgres, quando a inicialização da disponibilidade no Redis não puder ser concluída.

#### Scenario: Falha ao escrever a disponibilidade no Redis
- **WHEN** uma requisição `POST /events` é enviada com dados válidos e a escrita da disponibilidade no Redis falha
- **THEN** o sistema responde com erro, o evento não é persistido no Postgres, e nenhuma chave de disponibilidade é criada no Redis
