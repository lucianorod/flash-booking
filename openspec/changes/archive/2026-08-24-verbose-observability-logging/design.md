## Context

Ver `proposal.md` - Why para a motivação e o inventário das lacunas atuais. O projeto já usa SLF4J via `LoggerFactory.getLogger(...)` como padrão (presente em `ReservationQueryService`, `EventAvailabilityQueryService`, `ReservationStreamListener`, `ReservationStreamRecoveryTask`, `ReservationExpirationSweepTask`), sempre como `private val log = LoggerFactory.getLogger(<Classe>::class.java)` e mensagens com placeholders `{}` (nunca concatenação de string), em português. `logback-spring.xml` já existe e não precisa mudar. `ReservationStreamListener` já loga, desde a fase anterior, o corpo completo (`fields`, o `Map<String,String>` da mensagem) em nível ERROR tanto para mensagens envenenadas por esgotamento de tentativas quanto para qualquer exceção não tratada durante o processamento — esse mecanismo já satisfaz o exemplo citado na proposta e não precisa ser alterado, apenas estendido para os caminhos de sucesso.

## Goals / Non-Goals

**Goals:**
- Estabelecer uma convenção única de nível de log (o que vai em DEBUG/INFO/WARN/ERROR) e aplicá-la de forma consistente em todos os controllers, no `GlobalExceptionHandler`, nos serviços de negócio e no executor de scripts Lua.
- Garantir que nenhuma exceção tratada pelo `GlobalExceptionHandler` passe em silêncio — em especial o catch-all de erro 500, hoje completamente sem rastro.
- Preservar exatamente o comportamento observável hoje existente (contratos HTTP, corpos de resposta, contrato do stream) — esta é uma mudança de observabilidade pura.

**Non-Goals:**
- Logging estruturado (JSON) ou correlação de requisições via trace/span ID — fora de escopo; mantém o formato de log em texto já usado pelo `logback-spring.xml` atual.
- Métricas (Micrometer/Prometheus) ou tracing distribuído — não fazem parte desta mudança.
- Auditoria persistente (logs de negócio gravados em uma tabela) — os logs continuam sendo só logs de aplicação.

## Decisions

### Convenção de níveis de log

- **DEBUG**: detalhe de alta frequência ou baixo valor fora de depuração ativa — resultado bruto de cada script Lua (`ReservationLuaExecutor`), e a varredura de expiração quando não encontra nada vencido (`ReservationExpirationSweepTask`). Como o nível padrão em produção é INFO (comportamento já assumido pelo `logback-spring.xml` atual, que não é alterado por esta mudança), esses logs ficam disponíveis sob demanda sem poluir o log padrão.
- **INFO**: eventos de negócio esperados e o ciclo de vida normal de uma requisição ou de uma mensagem do stream — requisição recebida/concluída em cada controller, evento criado, reserva aceita, cancelamento aplicado, persistência de `CREATE`/`CANCEL`/`EXPIRE` bem-sucedida no worker.
- **WARN**: falhas esperadas do ponto de vista do sistema, mas que representam uma rejeição de negócio ou um caminho de fallback — respostas 400/404/409 no `GlobalExceptionHandler`, recusa por saldo insuficiente, reencaminhamento de mensagem `CANCEL`/`EXPIRE` no worker (já existe, mantido).
- **ERROR**: falha inesperada ou definitivamente não recuperável automaticamente — o catch-all de erro 500 no `GlobalExceptionHandler` (com stack trace completo), e o tratamento de mensagem envenenada no worker (já existe, mantido, incluindo o corpo completo da mensagem).

Alternativa considerada: logar toda requisição HTTP em WARN "para garantir visibilidade" — rejeitada por inverter a convenção usual (WARN deveria significar "algo digno de atenção", não "toda operação normal"), o que tornaria os logs de produção ruidosos e diluiria o sinal real de problemas.

### O que incluir nos logs, e o que não incluir

Cada log de negócio inclui os identificadores relevantes já usados hoje em toda a base (`eventId`, `reservationId`, `userId` — todos UUIDs) e valores numéricos (quantidade, saldo). Nenhum desses campos é sensível no domínio atual (não há senha, token ou dado pessoal identificável além do UUID de usuário, que já circula livremente pela API e pelo Redis). Por isso, esta mudança não introduz nenhuma redação/mascaramento de campos — decisão explícita, revisitável se o domínio um dia incluir dados sensíveis (nome, e-mail, pagamento). O corpo bruto de uma requisição nunca é logado por inteiro nos controllers; loga-se sempre por campo nomeado (os mesmos já extraídos pelo `@RequestBody`/`@PathVariable`), para manter o log legível e evitar logar acidentalmente um campo sensível adicionado no futuro sem revisão.

### Log nos controllers, não (só) nos serviços

A proposta pede explicitamente logs "para todos os controllers". Em vez de logar só nos serviços de negócio (que já recebem seus próprios logs de evento crítico) e deixar os controllers mudos, cada controller loga a entrada (parâmetros do path/query, não o corpo inteiro) e o resultado (status HTTP efetivo, ex.: 201 vs. 200 no caso idempotente de criação de reserva). Isso dá visibilidade da camada HTTP (o que foi de fato requisitado e respondido) independente da camada de negócio, e ajuda a diagnosticar problemas de roteamento/serialização que nunca chegam ao serviço.

### `GlobalExceptionHandler` loga antes de montar a resposta, sem alterar o corpo retornado

Cada `@ExceptionHandler` ganha uma chamada de log como primeira linha do método, usando o nível definido na convenção acima, incluindo a exceção (mensagem, e stack trace completo apenas no handler de 500) e o identificador disponível (ex.: `eventId` para `EventNotFoundException`). O corpo (`ErrorResponse`) e o status HTTP retornados não mudam. Alternativa considerada: logar via um `HandlerExceptionResolver`/filtro genérico central, olhando o tipo da exceção uma única vez — rejeitada porque perderia o contexto específico de cada exceção (ex.: qual `reservationId` gerou o 404) sem reintroduzir, na prática, o mesmo `when`/`if` por tipo que os `@ExceptionHandler`s já fazem.

## Risks / Trade-offs

- [Logar em cada controller e em cada serviço pode parecer duplicado para o mesmo evento (ex.: "reserva aceita" no serviço e "requisição concluída" no controller)] → Mitigação: as mensagens são deliberadamente diferentes em foco (camada HTTP vs. regra de negócio), o que ajuda a diagnosticar em qual camada um problema está, e o custo de duas linhas de log em INFO é desprezível frente ao ganho de rastreabilidade.
- [Aumento do volume de log em produção pode elevar custo de armazenamento/ingestão em um ambiente de observabilidade externo] → Mitigação: os logs de alta frequência (resultado de script Lua, varredura de expiração vazia) ficam em DEBUG, não INFO; o nível padrão de produção não muda nesta fase.
- [Logar identificadores (`reservationId`, `userId`) facilita correlação, mas nenhum mecanismo de correlação (ex.: MDC com um request ID) existe ainda para juntar as linhas de uma mesma requisição] → Mitigação: fora de escopo desta mudança (ver Non-Goals); os UUIDs de domínio já servem como chave de correlação manual suficiente para o volume atual do sistema.

## Migration Plan

Mudança aditiva e sem estado: não há dado a migrar, não há flag de rollout, e o rollback é reverter os arquivos alterados (todos puramente logging, sem qualquer efeito colateral em Redis/Postgres/contrato HTTP).
