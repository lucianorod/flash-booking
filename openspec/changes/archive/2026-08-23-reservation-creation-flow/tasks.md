## 1. Ajuste da entidade Reservation para ID atribuído

- [x] 1.1 Remover `@GeneratedValue` de `Reservation.id`, tornando-o um identificador atribuído pela aplicação e recebido no construtor.
- [x] 1.2 Atualizar os testes existentes que já constroem `Reservation` diretamente (`ReservationPersistenceTest`) para atribuir um `id` explícito ao construir a entidade.

## 2. Script Lua de reserva

- [x] 2.1 Criar o arquivo `reserve_tickets.lua` (em `src/main/resources`) implementando as responsabilidades atômicas descritas em `design.md`: checar idempotência, checar e decrementar saldo, gravar o hash da reserva, publicar no stream, registrar a chave de idempotência com TTL.
- [x] 2.2 Criar o componente Kotlin que carrega o script na inicialização (`SCRIPT LOAD`) e o invoca (`EVALSHA`, com fallback para `EVAL`), passando `KEYS`/`ARGV` conforme o design.
- [x] 2.3 Mapear os três resultados possíveis do script (`CREATED`, `IDEMPOTENT`, `INSUFFICIENT_STOCK`) para um tipo Kotlin que o serviço de reserva possa tratar.

## 3. Endpoint POST /events/:id/reservations

- [x] 3.1 Criar o DTO de request (`userId`, `quantity`) com validação Bean Validation (`userId` obrigatório, `quantity` inteiro positivo).
- [x] 3.2 Criar o DTO de resposta (`reservationId`, `status`).
- [x] 3.3 Implementar o serviço de criação de reserva: gerar o UUID da reserva, calcular `expiresAt` (agora + janela de retenção configurável), invocar o script Lua.
- [x] 3.4 Implementar o controller `POST /events/:id/reservations`, exigindo o header `Idempotency-Key`, mapeando os resultados do script para 201 (`CREATED`), 200 (`IDEMPOTENT`) e 409 (`INSUFFICIENT_STOCK`).
- [x] 3.5 Adicionar tratamento de erro para o header `Idempotency-Key` ausente, retornando 400 com o corpo de erro já padronizado no projeto.

## 4. Worker consumidor do Stream

- [x] 4.1 Criar o grupo de consumidores do stream de forma idempotente na inicialização do worker (`XGROUP CREATE ... MKSTREAM`, ignorando o erro `BUSYGROUP`).
- [x] 4.2 Implementar o worker `@Component` que consome `stream:reservations` via `XREADGROUP`, faz o parsing e a conversão estrita de tipos (`UUID`, `Int`, `Instant`), monta a entidade `Reservation` com o `id` atribuído, e persiste via `ReservationRepository`.
- [x] 4.3 Confirmar (`XACK`) a mensagem após persistência bem-sucedida.
- [x] 4.4 Tratar a violação da constraint única de `idempotency_key` como "já persistida": não propagar erro, confirmar (`XACK`) mesmo assim.
- [x] 4.5 Tratar falha de conversão de tipos como mensagem envenenada: logar com contexto completo e não confirmar (`XACK`), sem derrubar o worker.

## 5. Testes

- [x] 5.1 Escrever teste de integração validando reserva aceita com sucesso: 201, saldo decrementado no Redis, reserva eventualmente persistida no Postgres com todos os campos corretos.
- [x] 5.2 Escrever teste de integração validando reenvio com a mesma `Idempotency-Key`: 200, saldo não decrementado novamente, mesmo `reservationId` retornado.
- [x] 5.3 Escrever teste de integração validando estoque insuficiente: 409, saldo inalterado, nenhuma reserva criada.
- [x] 5.4 Escrever teste de integração validando requisição sem `Idempotency-Key`: 400.
- [x] 5.5 Escrever teste de integração validando `quantity` inválida (≤ 0): 400.
- [x] 5.6 Escrever teste validando concorrência: duas requisições simultâneas disputando o último ingresso resultam em exatamente uma aceita e uma recusada por estoque insuficiente.
- [x] 5.7 Escrever teste validando que o worker trata o reprocessamento da mesma mensagem do stream sem falhar e sem duplicar a reserva no Postgres.

## 6. Build

- [x] 6.1 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.
