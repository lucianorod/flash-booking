## Context

Ver `proposal.md` - Why para a motivação. Hoje já existem: `Event` no Postgres com `available_capacity` (ver `event-management`); `event:<id>:available` no Redis, inicializado na criação do evento (ver `ticket-availability`); e a entidade `Reservation` no Postgres com `id` (UUID gerado pelo Hibernate via `@GeneratedValue(strategy = GenerationType.UUID)`), `eventId`, `userId`, `quantity`, `status`, `expiresAt`, `idempotencyKey` (com `UNIQUE` constraint), `createdAt`, `updatedAt` (ver `reservation-management`). Nenhum fluxo cria reservas ainda. O Redis do projeto (`redis:7-alpine`) suporta scripts Lua (`EVAL`/`EVALSHA`) e Streams nativamente.

## Goals / Non-Goals

**Goals:**
- Checagem de saldo, decremento, registro da reserva, publicação para persistência e registro de idempotência acontecendo como uma única operação atômica no Redis.
- Persistência eventual e não-duplicada da reserva aceita no Postgres via worker consumidor de stream.
- Semântica idempotente do endpoint HTTP via `Idempotency-Key` fornecida pelo cliente.

**Non-Goals:**
- Confirmação, expiração automática ou cancelamento de reservas — fases futuras.
- Reconciliar a capacidade nominal do evento no Postgres com o saldo em tempo real do Redis — decisão deliberada desta fase (ver proposal.md), fica para depois.
- Lock distribuído (ex.: Redlock) — a atomicidade do script Lua (Redis executa comandos e scripts de forma single-threaded) já garante exclusão mútua suficiente para esta operação, sem necessidade de um lock separado.
- Política de retentativa/backoff refinada para mensagens não confirmadas do stream — ver Riscos e Open Questions.

## Decisions

### Toda a lógica transacional roda em um único script Lua (`reserve_tickets.lua`)
O Redis executa scripts Lua de forma atômica e single-threaded, então codificar "checar idempotência → checar saldo → decrementar → gravar a reserva → publicar no stream → registrar a chave de idempotência" como uma única chamada `EVAL` garante que nenhuma outra operação intercala no meio do caminho. Alternativa considerada: transação Redis via `MULTI`/`EXEC` com `WATCH` (bloqueio otimista) — rejeitada porque `MULTI`/`EXEC` não permite lógica condicional (ler um valor e decidir o que fazer dentro da própria transação) como o Lua permite; reproduzir esse comportamento com `WATCH` exigiria um loop de retentativa no lado da aplicação sob concorrência, adicionando latência e complexidade sem a mesma garantia de atomicidade em todos os cenários de falha.

### Responsabilidades do script Lua (pseudo-código)
```
KEYS[1] = event:{eventId}:available
KEYS[2] = idempotency:{idempotencyKey}
KEYS[3] = reservation:{reservationId}
KEYS[4] = stream:reservations

ARGV[1] = reservationId (UUID gerado pela aplicação Kotlin)
ARGV[2] = eventId
ARGV[3] = userId
ARGV[4] = quantity
ARGV[5] = expiresAt (calculado pela aplicação: agora + janela de retenção da reserva)
ARGV[6] = idempotencyTtlSeconds (ex.: 86400 = 24h)

1. existingId = GET KEYS[2]
2. SE existingId existe:
     RETORNAR {status = "IDEMPOTENT", reservationId = existingId}   -- aborta sem tocar no saldo
3. available = GET KEYS[1]
4. SE available < ARGV[4]:
     RETORNAR {status = "INSUFFICIENT_STOCK"}                       -- aborta sem decrementar
5. DECRBY KEYS[1] ARGV[4]
6. HSET KEYS[3] eventId ARGV[2] userId ARGV[3] quantity ARGV[4] status "PENDING" expiresAt ARGV[5]
7. XADD KEYS[4] * reservationId ARGV[1] eventId ARGV[2] userId ARGV[3] quantity ARGV[4] status "PENDING" expiresAt ARGV[5] idempotencyKey <chave original>
8. SET KEYS[2] ARGV[1] EX ARGV[6]
9. RETORNAR {status = "CREATED", reservationId = ARGV[1]}
```
Os passos 3–8 só executam se os passos 1–2 e 3–4 não abortarem antes; nenhum passo entre a leitura do saldo (passo 3) e o registro da chave de idempotência (passo 8) é observável por outra chamada concorrente, pois todo o script roda como uma única operação atômica do Redis.

### ID da reserva gerado pela aplicação Kotlin antes de chamar o script, não pelo Lua
Como o mesmo identificador precisa existir no Hash do Redis (`reservation:{reservationId}`), no stream, e depois na linha do Postgres, e o Lua não tem gerador de UUID nativo, o serviço Kotlin gera um `UUID.randomUUID()` antes de invocar o script e o passa como argumento (`ARGV[1]`). Alternativa considerada: gerar o ID dentro do próprio script Lua (ex.: combinando `TIME` com algum valor pseudoaleatório) — rejeitada por ser frágil e não padronizada, quando gerar o UUID em Kotlin é trivial e não depende de nenhum estado interno do script.

### Entidade `Reservation` passa a ter `id` atribuído pela aplicação, não gerado pelo Hibernate
Como o `id` agora se origina fora do Postgres (no fluxo de reserva, antes da chamada ao Redis), o campo `@Id` de `Reservation` deixa de usar `@GeneratedValue(strategy = GenerationType.UUID)` — o Hibernate não aceita um valor pré-atribuído para essa estratégia de geração. O `id` passa a ser um identificador atribuído: recebido no construtor como os demais campos obrigatórios, sem `@GeneratedValue`. Alternativa considerada: manter `@GeneratedValue` e usar um campo de correlação separado para ligar o ID do Postgres ao ID do Redis — rejeitada porque fragmenta "a identidade da reserva" em dois IDs diferentes dependendo de onde se está olhando, exatamente o tipo de complexidade que buscas idempotentes por ID existem para evitar; um único ID compartilhado entre Redis e Postgres é mais simples e é o mesmo ID que o cliente já recebe nas respostas 201/200.

### Chave de idempotência rastreada globalmente no Redis (`idempotency:<key>` → `reservationId`), TTL de 24h
Espelha a constraint `UNIQUE` já existente em `reservations.idempotency_key` no Postgres (de `setup-foundation`) — ambos os armazenamentos tratam a chave como única em todo o sistema, não por evento. O TTL de 24h (valor do exemplo do pedido original) limita por quanto tempo o Redis precisa lembrar de uma chave processada; uma retentativa que chegue depois do TTL é tratada como uma nova tentativa (ver Riscos — a constraint `UNIQUE` do Postgres continua sendo a segunda linha de defesa). Alternativa considerada: sem TTL (retenção permanente) — rejeitada por crescer indefinidamente a memória do Redis para um dado cujo único propósito é proteção de retentativa de curto prazo.

### Stream `stream:reservations` com grupo de consumidores, worker confirma via `XACK`
O worker (`@Component`) lê `stream:reservations` usando um grupo de consumidores (`XGROUP CREATE ... $ MKSTREAM` na inicialização, ignorando o erro `BUSYGROUP` se o grupo já existir), consome via `XREADGROUP`, e confirma (`XACK`) somente após persistir com sucesso no Postgres. Isso garante entrega "pelo menos uma vez": se o worker cair depois de ler mas antes de confirmar, a mensagem continua pendente e pode ser reprocessada. Alternativa considerada: `XREAD` sem grupo de consumidores (sem confirmação, sem reprocessamento) — rejeitada porque pode perder reservas silenciosamente se o worker reiniciar, contrariando o requisito de consistência eventual (eventual, mas não "talvez nunca").

### Persistência do worker é idempotente via violação de constraint única, não deduplicação própria
Como a entrega do stream é "pelo menos uma vez", o worker pode processar a mesma entrada mais de uma vez (ex.: após um reinício com reentrega). Como o `id` atribuído é o mesmo em toda reentrega do mesmo evento, e `idempotency_key` também tem sua própria constraint `UNIQUE`, uma segunda tentativa de persistência falha com violação de constraint. O worker captura especificamente essa violação, trata como "já persistida" (não faz nada) e ainda assim confirma (`XACK`) a mensagem — tornando a escrita no Postgres idempotente independentemente de quantas vezes o stream reentregar a mesma mensagem. Alternativa considerada: deduplicar usando um conjunto próprio de "IDs já processados" no lado do consumidor — rejeitada por ser redundante; a própria constraint do banco já garante isso sem custo adicional.

### Conversão de tipos estrita na borda do worker, antes de chamar o repositório
O worker lê os campos do stream como strings e precisa convertê-los para os tipos exatos que `Reservation` exige — `UUID.fromString(...)` para os identificadores, `.toInt()` para a quantidade, `Instant.parse(...)` (ISO-8601) para o prazo de expiração — antes de construir a entidade. Uma falha de conversão é tratada como mensagem envenenada: não é retentada indefinidamente nem descartada silenciosamente; ela não é confirmada (sem `XACK`), permanecendo visível na lista de pendências do grupo de consumidores para inspeção manual, e o erro é logado com contexto completo. Uma fila de mensagens mortas dedicada fica como possível evolução futura (ver Riscos), não como requisito desta fase.

### Prazo de expiração calculado pela aplicação, não fixo no script Lua
`expiresAt` é calculado como "agora + janela de retenção da reserva", onde a janela é um valor de configuração da aplicação Kotlin (não fornecido pelo cliente), passado ao script como argumento — mantendo essa política fora do script Lua. Assume-se um valor padrão razoável de 15 minutos; como este change não implementa o processamento de expiração em si (ver Non-Goals), esse valor apenas alimenta o campo para a fase futura que vai efetivamente expirar reservas.

## Risks / Trade-offs

- [Lógica de negócio em Lua é mais difícil de testar isoladamente do que código Kotlin] → Mitigação: cobrir com testes de integração contra Redis real (Testcontainers), em vez de tentar testar o Lua isoladamente; manter o script restrito exatamente às responsabilidades listadas na spec.
- [Se o worker nunca conseguir persistir uma entrada (mensagem envenenada), a reserva fica visível só no Redis, nunca no Postgres] → Mitigação: mensagens não confirmadas permanecem na lista de pendências do grupo de consumidores, disponíveis para reprocessamento manual ou para uma fila de mensagens mortas numa fase futura.
- [TTL de 24h na chave de idempotência no Redis é menor que "para sempre" — uma retentativa fora dessa janela pode, em tese, tentar criar uma segunda reserva] → Mitigação: a constraint `UNIQUE` de `idempotency_key` no Postgres barra a duplicata na persistência; o pior caso é uma segunda tentativa decrementar o saldo do Redis uma segunda vez até a falha de persistência ser detectada — risco residual aceito nesta fase, não resolvido aqui.
- [Divergência entre a capacidade nominal do evento no Postgres (nunca atualizada) e o saldo em tempo real no Redis] → Mitigação: decisão deliberada desta fase (ver proposal.md); reconciliação fica para fase futura.

## Migration Plan

- Sem migração de schema no Postgres: a tabela `reservations` já tem todas as colunas necessárias desde `setup-foundation`; apenas a forma de gerar o `id` muda no código (de gerado pelo Hibernate para atribuído pela aplicação), o que não exige alteração de schema.
- O script Lua é carregado de um arquivo de recurso (`reserve_tickets.lua`) e registrado no Redis via `SCRIPT LOAD` na inicialização da aplicação, sendo então invocado por `EVALSHA` (com fallback para `EVAL` caso o hash não esteja em cache) — abordagem padrão para não reenviar o script inteiro a cada chamada.
- O grupo de consumidores do stream é criado de forma idempotente na inicialização do worker (`XGROUP CREATE stream:reservations ... $ MKSTREAM`, ignorando o erro `BUSYGROUP` se já existir).

## Open Questions

- Política de retentativa/backoff para mensagens não confirmadas na lista de pendências (reclamar automaticamente via `XAUTOCLAIM`, ou só inspeção manual)? Não afeta o contrato desta fase; pode ser definida quando houver volume real de mensagens presas para observar.
