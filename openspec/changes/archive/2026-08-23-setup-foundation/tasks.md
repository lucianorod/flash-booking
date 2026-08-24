## 1. Setup do projeto & Docker

- [x] 1.1 Inicializar o projeto Kotlin + Spring Boot com Gradle (Kotlin DSL), incluindo dependências base (Web, Validation, Data JPA, Data Redis, driver PostgreSQL, Flyway).
- [x] 1.2 Criar `docker-compose.yml` com serviços de PostgreSQL e Redis (portas, credenciais de desenvolvimento, volume nomeado para persistência do Postgres).
- [x] 1.3 Validar que a aplicação sobe localmente com os containers ativos e conecta a PostgreSQL e Redis sem erros.

## 2. Configuração de logs por profile

- [x] 2.1 Adicionar dependência do encoder JSON (ex.: `logstash-logback-encoder`).
- [x] 2.2 Criar `logback-spring.xml` com encoder de texto legível como padrão.
- [x] 2.3 Adicionar bloco `<springProfile name="qa">` com encoder JSON estruturado.
- [x] 2.4 Validar manualmente a saída de log no profile padrão (texto) e no profile `qa` (JSON).

## 3. Documentação da API (Swagger/OpenAPI)

- [x] 3.1 Adicionar dependência do springdoc-openapi e configuração básica (título, versão, descrição da API).
- [x] 3.2 Validar acesso à UI do Swagger e à especificação OpenAPI gerada.

## 4. Modelagem do banco de dados

- [x] 4.1 Configurar Flyway e definir `hibernate.ddl-auto=validate`.
- [x] 4.2 Criar migração Flyway inicial (`V1__create_events_table.sql`) para a tabela `events`, com colunas de capacidade e auditoria (criado em, atualizado em).
- [x] 4.3 Criar a entidade JPA `Event` mapeada para a tabela `events`, com auditoria automática (`@CreatedDate`/`@LastModifiedDate` via `AuditingEntityListener`).
- [x] 4.4 Criar o repositório JPA (`EventRepository`) para persistência do evento.

## 5. Endpoint POST /events

- [x] 5.1 Criar DTO de request com validações Bean Validation (nome obrigatório, capacidade total maior que zero, etc.).
- [x] 5.2 Criar DTO de resposta com os dados do evento criado (incluindo identificador e timestamps).
- [x] 5.3 Implementar o serviço de criação de evento (validação de regras adicionais e persistência via `EventRepository`).
- [x] 5.4 Implementar o controller `POST /events`, retornando 201 e o evento criado em caso de sucesso.
- [x] 5.5 Implementar `@RestControllerAdvice` centralizado traduzindo erros de validação e de regra de negócio em respostas 400 com corpo de erro padronizado.

## 6. Testes de integração (API First)

- [x] 6.1 Configurar Testcontainers (PostgreSQL) e RestAssured no módulo de testes.
- [x] 6.2 Escrever teste de integração `@SpringBootTest` para `POST /events` com dados válidos, validando status 201 e corpo da resposta com Hamcrest.
- [x] 6.3 Escrever teste de integração para requisição sem campo obrigatório, validando status 400 e corpo de erro.
- [x] 6.4 Escrever teste de integração para capacidade total inválida (<= 0), validando status 400 e corpo de erro.
- [x] 6.5 Escrever teste de integração validando o estado inicial do evento criado (capacidade e timestamps) imediatamente após a criação.

## 7. Qualidade e cobertura

- [x] 7.1 Configurar o plugin Jacoco no Gradle com regra de verificação de cobertura mínima de 80%.
- [x] 7.2 Configurar exclusões de cobertura para classes sem lógica de negócio (classe principal, configurações, DTOs simples).
- [x] 7.3 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura passam.

## 8. Modelagem completa do Evento e da Reserva

- [x] 8.1 Garantir que `V1__create_events_table.sql` crie, numa única migração, a tabela `events` (com `available_capacity` e `status`, `VARCHAR` com `CHECK` constraint listando `PUBLISHED`, `SOLD_OUT`, `CANCELLED`) e a tabela `reservations` (`event_id` FK para `events` com `ON DELETE RESTRICT`, `user_id`, `quantity`, `status`, `expires_at`, `idempotency_key` `UNIQUE`, `created_at`, `updated_at`).
- [x] 8.2 Apagar e recriar o banco de dados local (ex.: `docker compose down -v`) antes de subir a aplicação, garantindo que o Flyway aplique a `V1` num banco limpo.
- [x] 8.3 Criar enum `EventStatus` (`PUBLISHED`, `SOLD_OUT`, `CANCELLED`) e garantir que a entidade `Event` exponha `availableCapacity` e `status`, inicializando `availableCapacity = totalCapacity` e `status = PUBLISHED` na criação.
- [x] 8.4 Garantir que `EventResponse` e `EventService` exponham `availableCapacity` e `status` do evento criado.
- [x] 8.5 Criar enum `ReservationStatus` (`PENDING`, `CONFIRMED`, `EXPIRED`, `CANCELLED`), a entidade JPA `Reservation` e o repositório `ReservationRepository` — sem controller/service de reserva nesta fase.
- [x] 8.6 Escrever teste de integração de `POST /events` validando que o evento criado nasce com `status = PUBLISHED` e capacidade disponível igual à capacidade total.
- [x] 8.7 Escrever teste cobrindo a persistência de uma `Reservation` com todos os campos obrigatórios.
- [x] 8.8 Escrever teste validando que a chave de idempotência da reserva é única (persistência de uma segunda reserva com a mesma chave deve falhar).
- [x] 8.9 Rodar o build completo (`./gradlew build`) e confirmar que testes e verificação de cobertura continuam passando.
