## Why

O sistema de "Flash Booking" ainda não possui base técnica: não há projeto Kotlin/Spring Boot inicializado, infraestrutura local (Postgres/Redis) via Docker, nem o primeiro caso de uso funcional. Antes de endereçar concorrência, prevenção de oversell, expiração de reservas e idempotência (fases futuras), é preciso estabelecer a fundação do projeto e o primeiro caso de uso de domínio (criação de evento), com qualidade de testes e observabilidade já configuradas desde o início.

## What Changes

- Inicializar o projeto Kotlin + Spring Boot com Gradle (Kotlin DSL).
- Criar `docker-compose.yml` com containers de PostgreSQL e Redis, prontos para a aplicação se conectar localmente.
- Configurar logging via Spring Boot profiles: formato texto legível como padrão (desenvolvimento local) e formato JSON estruturado no profile `qa`.
- Configurar Swagger/OpenAPI para documentação interativa da API.
- Modelar a entidade JPA `Event`, com capacidade total, capacidade disponível e status (`PUBLISHED`, `SOLD_OUT`, `CANCELLED`), além de auditoria (criado em, atualizado em), preparando o terreno para as regras de oversell das fases seguintes.
- Modelar a entidade JPA `Reservation`, vinculada a um evento e a um usuário, com quantidade, status, prazo de expiração (`expires_at`) e chave de idempotência, mapeando o schema de banco que o fluxo de reserva das fases seguintes vai usar — sem implementar criação, confirmação ou expiração de reservas nesta fase.
- Implementar o endpoint `POST /events` (criar evento), incluindo validação de dados de entrada e tratamento explícito de erros para dados inválidos.
- Configurar testes de integração ponta a ponta (`@SpringBootTest` + RestAssured + Hamcrest) para `POST /events`, cobrindo contrato HTTP, status codes e corpo da resposta, com banco real/em memória (Mockito somente quando estritamente necessário).
- Configurar Jacoco no Gradle exigindo cobertura mínima de 80%.

## Capabilities

### New Capabilities
- `project-foundation`: requisitos técnicos de fundação do projeto — infraestrutura local via Docker (Postgres e Redis), formato de logs por profile (texto local / JSON em `qa`), documentação interativa via Swagger e gate de qualidade de cobertura de testes (Jacoco, mínimo 80%).
- `event-management`: modelagem do evento (capacidade total, capacidade disponível, status, auditoria) e caso de uso de criação de evento via `POST /events`, incluindo validação e tratamento de erros.
- `reservation-management`: modelagem da entidade Reserva (evento, usuário, quantidade, status, expiração, chave de idempotência), mapeando o schema de banco que o fluxo de reservas das fases seguintes vai usar — sem endpoints ou regras de negócio de reserva nesta fase.

### Modified Capabilities
_Nenhuma — este é o primeiro change do projeto, não há specs arquivadas para modificar._

## Impact

- **Código novo**: estrutura do projeto Gradle/Kotlin, `docker-compose.yml`, configurações de `application.yml` (profiles padrão e `qa`), entidades `Event` e `Reservation`, enums `EventStatus` e `ReservationStatus`, repositórios JPA (`EventRepository`, `ReservationRepository`), controller/serviço para `POST /events`, DTOs de request/response, tratamento de erros (`@ControllerAdvice` ou equivalente), configuração do Swagger/OpenAPI, configuração do Jacoco no Gradle.
- **Dependências**: Spring Boot Web, Spring Data JPA, driver PostgreSQL, Spring Data Redis (cliente configurado, ainda sem uso funcional de lock distribuído — isso é fundação para fases futuras), springdoc-openapi (Swagger), Jacoco Gradle plugin, JUnit, RestAssured, Hamcrest.
- **Infraestrutura**: novo `docker-compose.yml` local com serviços de PostgreSQL e Redis.
- **APIs**: novo endpoint `POST /events`, retornando o evento criado com capacidade disponível e status.
- **Sem impacto em specs existentes**, pois este é o change fundacional do projeto.
