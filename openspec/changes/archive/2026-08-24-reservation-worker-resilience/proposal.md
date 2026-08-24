## Why

O worker que consome `stream:reservations` tem três lacunas de robustez descobertas em revisão do código atual: (1) o container do listener registra um nome de consumidor fixo (`reservation-worker-1`) para o grupo de consumidores, o que impede rodar mais de uma instância da aplicação sem colisão de identidade de consumidor e sem qualquer mecanismo de retomada de mensagens pendentes de uma instância caída; (2) quando uma mensagem `action = "CANCEL"` chega ao worker antes da reserva correspondente existir no Postgres (cenário que passa a ser possível justamente com múltiplos consumidores processando mensagens em paralelo), o worker trata isso como mensagem envenenada definitiva, sem nunca tentar reprocessar; e (3) não existe, hoje, nenhum mecanismo de expiração de reservas — nem TTL no Hash do Redis, nem notificação de expiração, nem varredura agendada. O status `EXPIRED` existe no enum e é checado pelo script de cancelamento, mas nada jamais o atribui, então reservas `PENDING` cujo `expiresAt` já passou continuam ocupando saldo indefinidamente e nunca aparecem como `EXPIRED` no Postgres.

## What Changes

- Atribuir um nome de consumidor único por instância da aplicação (em vez do valor fixo `reservation-worker-1`), permitindo múltiplas instâncias consumindo o mesmo grupo de consumidores sem colisão.
- Adicionar uma rotina periódica de reivindicação (`XAUTOCLAIM`) de mensagens pendentes há mais tempo que um limite configurável, entregando-as a um consumidor ativo para reprocessamento — cobrindo o caso de uma instância cair com mensagens ainda não confirmadas.
- Alterar o tratamento de `action = "CANCEL"` no worker: quando a reserva não é encontrada no Postgres, em vez de travar como mensagem envenenada definitiva, republicar a mensagem de cancelamento no stream com uma contagem de tentativas, até um limite máximo; ao atingir o limite, tratar como mensagem envenenada (log detalhado, sem nova republicação).
- Implementar a expiração automática de reservas: registrar reservas pendentes em uma estrutura do Redis ordenada por `expiresAt`, executar uma varredura periódica que expira atomicamente (via script Lua) as reservas cujo prazo já passou — devolvendo a quantidade ao saldo do evento e publicando `action = "EXPIRE"` no stream — e fazer o worker aplicar o status `EXPIRED` correspondente no Postgres.
- Garantir que uma reserva expirada não possa mais ser cancelada com sucesso (o script de cancelamento já rejeita `EXPIRED`; passa a ser exercitado de ponta a ponta, com a expiração acontecendo de fato).

## Capabilities

### New Capabilities

(nenhuma)

### Modified Capabilities

- `ticket-availability`: adiciona os requisitos de consumo concorrente do stream por múltiplas instâncias, reprocessamento do cancelamento quando a reserva ainda não existe no Postgres, e expiração automática do saldo reservado (estrutura de rastreio por prazo + varredura atômica).
- `reservation-management`: adiciona o requisito de persistência assíncrona consistente do status `EXPIRED` no Postgres, espelhando o requisito já existente para a persistência da criação.

## Impact

- `ReservationStreamConfig`: nome de consumidor único por instância; nova rotina agendada de `XAUTOCLAIM`.
- `ReservationStreamListener`: novo tratamento de `action = "EXPIRE"`; alteração no tratamento de `action = "CANCEL"` para reprocessar via republicação em vez de falhar definitivamente na primeira tentativa.
- `ReservationLuaExecutor` e novo script `expire_reservations.lua`: nova operação de expiração atômica.
- `reserve_tickets.lua`: passa a registrar a reserva também na estrutura de rastreio por prazo de expiração.
- `Reservation` (entidade): novo método de domínio para transição a `EXPIRED`.
- Nenhuma mudança de contrato HTTP público; o impacto é inteiramente na camada assíncrona (Redis + worker).
