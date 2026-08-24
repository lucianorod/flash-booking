## ADDED Requirements

### Requirement: Consulta de disponibilidade de um evento
O sistema SHALL permitir consultar a disponibilidade de ingressos de um evento através de uma requisição `GET /events/<id>`, respondendo com o identificador do evento e a capacidade disponível quando o evento existir.

#### Scenario: Consulta bem-sucedida
- **WHEN** uma requisição `GET /events/<id>` é enviada para um evento cuja disponibilidade pode ser determinada (no Redis ou no Postgres)
- **THEN** o sistema responde com status 200 (OK) e um corpo contendo o identificador do evento e a capacidade disponível

#### Scenario: Evento inexistente
- **WHEN** uma requisição `GET /events/<id>` é enviada para um identificador que não corresponde a nenhum evento persistido
- **THEN** o sistema responde com status 404 (Not Found)

### Requirement: Leitura Cache-Aside com repopulação em cache miss
O sistema SHALL priorizar o Redis como fonte da disponibilidade consultada em `GET /events/<id>`, consultando o Postgres somente quando a disponibilidade não estiver presente no Redis, e SHALL repopular o Redis com o valor obtido do Postgres nesse caso, de modo que consultas subsequentes para o mesmo evento não precisem mais acessar o Postgres.

#### Scenario: Disponibilidade encontrada no Redis
- **WHEN** uma requisição `GET /events/<id>` é enviada e a disponibilidade do evento já está registrada no Redis
- **THEN** o sistema responde com base nesse valor sem consultar o Postgres

#### Scenario: Disponibilidade ausente no Redis e evento existente no Postgres
- **WHEN** uma requisição `GET /events/<id>` é enviada, a disponibilidade não está registrada no Redis, e o evento existe no Postgres
- **THEN** o sistema responde com a capacidade disponível obtida do Postgres, e a disponibilidade do evento passa a estar registrada no Redis para consultas futuras

### Requirement: Degradação para o Postgres quando o Redis está indisponível
O sistema SHALL tratar uma falha de leitura no Redis (indisponibilidade, e não a simples ausência da chave) da mesma forma que um cache miss em `GET /events/<id>`, consultando o Postgres para responder à requisição em vez de falhar a requisição.

#### Scenario: Redis indisponível durante a consulta
- **WHEN** uma requisição `GET /events/<id>` é enviada e a leitura no Redis falha por indisponibilidade
- **THEN** o sistema consulta o Postgres e responde normalmente (200 com a capacidade disponível, ou 404 se o evento não existir) em vez de retornar um erro relacionado ao Redis
