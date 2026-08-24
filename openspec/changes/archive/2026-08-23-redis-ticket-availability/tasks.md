## 1. Acesso à disponibilidade no Redis

- [x] 1.1 Criar o componente de acesso à disponibilidade de ingressos no Redis (ex.: `EventAvailabilityCache`), usando `StringRedisTemplate`, com um método para inicializar a chave `event:<id>:available` com um valor inteiro.
- [x] 1.2 Garantir que uma falha na escrita do Redis (ex.: exceção de conexão) se propague como uma exceção, sem ser silenciada.

## 2. Integração com a criação de evento

- [x] 2.1 Anotar o método de criação de evento em `EventService` com `@Transactional`.
- [x] 2.2 Após persistir o evento no Postgres (e já com o `id` gerado disponível), chamar o componente de disponibilidade para inicializar `event:<id>:available` com o valor de `availableCapacity` do evento criado.
- [x] 2.3 Confirmar que, se a chamada ao Redis lançar exceção, a transação do Postgres é revertida (nenhum evento persistido) e a exceção se propaga até o controller.
- [x] 2.4 Garantir que o controller `POST /events` traduza essa falha em uma resposta de erro (5xx) sem vazar detalhes internos no corpo da resposta.

## 3. Infraestrutura de testes para Redis

- [x] 3.1 Criar uma classe `@TestConfiguration` reutilizável com um `GenericContainer("redis:7-alpine")` anotado `@ServiceConnection`, para ser importada pelos testes que precisam de Redis.

## 4. Testes

- [x] 4.1 Escrever teste de integração validando que, ao criar um evento com sucesso via `POST /events`, a chave `event:<id>:available` é criada no Redis com o valor igual à capacidade total/disponível informada.
- [x] 4.2 Escrever teste validando que, quando a escrita no Redis falha, o evento não é persistido no Postgres e nenhuma chave é criada no Redis (comportamento tudo ou nada).
- [x] 4.3 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.
