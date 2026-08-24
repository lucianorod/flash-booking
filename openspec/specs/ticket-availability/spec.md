# ticket-availability Specification

## Purpose

Rastreia no Redis, com baixa latência, a quantidade de ingressos disponíveis por evento, servindo de base para as regras de reserva e prevenção de oversell que serão construídas em cima dessa contagem nas fases seguintes.

## Requirements

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
