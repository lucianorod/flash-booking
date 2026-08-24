## Context

Projeto greenfield: não existe ainda código, build nem infraestrutura. Ver `proposal.md` - Why para a motivação. Stack já definida pelo time: Kotlin + Spring Boot, Gradle (Kotlin DSL), JPA/Hibernate sobre PostgreSQL, Redis, Docker Compose, Swagger/OpenAPI, e testes com JUnit + RestAssured + Hamcrest + Jacoco (Mockito só quando estritamente necessário). Fases futuras (fora deste change) tratarão de reserva de ingressos com prevenção de oversell, expiração automática, idempotência e consistência eventual — as decisões aqui devem deixar essas fases mais fáceis, sem implementá-las.

## Goals / Non-Goals

**Goals:**
- Estabelecer uma estrutura de projeto e infraestrutura local que as fases futuras reutilizem sem retrabalho (build, banco, cache, logging, docs, qualidade).
- Entregar o primeiro caso de uso de domínio (criação de evento) como referência de padrão de código, validação e testes para os próximos endpoints.
- Garantir que a evolução do schema do banco seja controlada desde o início, já que fases futuras adicionarão tabelas de reserva com regras de concorrência sensíveis.
- Mapear no banco de dados o modelo de Evento (com status e capacidade disponível) e o modelo de Reserva (vinculado a evento e usuário), deixando o schema pronto para o fluxo de reservas das fases seguintes.

**Non-Goals:**
- Implementar o fluxo de negócio de reserva (criação via API, confirmação, expiração automática, prevenção de oversell, idempotência aplicada em runtime) ou lock distribuído com Redis — apenas mapear as entidades e o schema de banco que essas regras vão usar (dependência e conectividade do Redis já configuradas, sem uso funcional).
- Autenticação/autorização da API.
- Deploy em ambiente de produção ou pipeline de CI/CD.

## Decisions

### Estrutura em camadas simples (controller / service / repository)
Adotar separação clássica por camadas (`controller` → `service` → `repository`/JPA) dentro de um único módulo Gradle. Alternativa considerada: arquitetura hexagonal/ports-and-adapters completa desde já — rejeitada por ser prematura para um único caso de uso; a estrutura em camadas simples não impede migrar para hexagonal quando a complexidade de domínio (reservas, locks) justificar.

### Migração de schema via Flyway, não `ddl-auto`
Usar Flyway com migrações versionadas (`V1__create_events_table.sql`, etc.) e configurar `hibernate.ddl-auto=validate`. Alternativa considerada: `ddl-auto=update` — rejeitada porque fases futuras vão introduzir tabelas de reserva com constraints e índices sensíveis a condições de corrida; controlar o schema via migração versionada desde o primeiro commit evita reescrever a estratégia de banco mais tarde.

### Testes de integração com Testcontainers declarativo (PostgreSQL real), não H2
Para os testes `@SpringBootTest` de `POST /events`, subir um container PostgreSQL real via Testcontainers, configurado de forma puramente declarativa no `src/test/resources/application.yml` através da URL JDBC especial `jdbc:tc:postgresql:16-alpine:///flash_booking`. Alternativa considerada (1): H2 em memória — rejeitada porque diferenças de dialeto/comportamento de constraints entre H2 e PostgreSQL poderiam mascarar bugs reais, especialmente relevante para as regras de concorrência e constraints de capacidade que serão adicionadas nas próximas fases sobre a mesma tabela `events`. Alternativa considerada (2): declarar instâncias programáticas com `@Container` e `@ServiceConnection` dentro de `companion object` nas classes de teste — rejeitada para evitar acoplamento e boilerplate de infraestrutura no código de teste quando a declaração em YAML atende plenamente. RestAssured roda contra o servidor embarcado (`webEnvironment = RANDOM_PORT`); Hamcrest valida status code e corpo da resposta. Mockito fica reservado para eventual isolamento de um componente complexo, não para o fluxo principal.

### Logging: Logback com encoder condicional por profile
Usar Logback (padrão do Spring Boot) com duas configurações: `logback-spring.xml` com um encoder de texto padrão (profile ausente/`default`) e um encoder JSON estruturado (ex.: `logstash-logback-encoder`) ativado exclusivamente sob o profile `qa`, usando `<springProfile name="qa">`. Alternativa considerada: sempre logar em JSON e deixar o desenvolvedor formatar localmente — rejeitada por piorar a experiência de leitura em desenvolvimento local, contrariando o pedido explícito de texto legível como padrão.

### Redis configurado, porém sem uso funcional nesta fase
Adicionar a dependência `spring-boot-starter-data-redis` e o container no `docker-compose.yml`, validando a conectividade na subida da aplicação (ex.: via Actuator health indicator do Redis), mas sem implementar cache ou lock distribuído agora. Isso evita retrabalho de configuração de conexão/credenciais quando o lock distribuído for implementado nas próximas fases, mantendo o escopo deste change restrito à fundação.

### Tratamento de erros centralizado
Usar Bean Validation (`jakarta.validation`, anotações no DTO de request) combinado com um `@RestControllerAdvice` central que traduz `MethodArgumentNotValidException` e violações de regra de negócio simples (ex.: capacidade inválida) em um corpo de erro HTTP padronizado (status, mensagem, campos inválidos). Alternativa considerada: validar manualmente dentro do controller/service — rejeitada por gerar tratamento de erro inconsistente à medida que mais endpoints forem adicionados.

### Gate de cobertura Jacoco com exclusões de boilerplate
Configurar o plugin Jacoco no Gradle com verificação mínima de 80% de cobertura de linha, excluindo classes sem lógica de negócio (classe principal `@SpringBootApplication`, classes de configuração puras, DTOs simples). Alternativa considerada: aplicar 80% sem exclusões — rejeitada por penalizar boilerplate que não agrega valor ao ser testado, incentivando testes artificiais só para atingir número.

### Status de Evento e de Reserva como VARCHAR com CHECK constraint, não ENUM nativo do Postgres
Mapear `Event.status` e `Reservation.status` via `@Enumerated(EnumType.STRING)` em colunas `VARCHAR`, com `CHECK` constraint no banco listando os valores válidos. Alternativa considerada: tipo `ENUM` nativo do PostgreSQL — rejeitada porque adicionar um novo valor a um enum nativo exige `ALTER TYPE ... ADD VALUE`, que não pode rodar dentro da mesma transação que outros comandos DDL/DML em versões mais antigas do Postgres e complica migrações; fases futuras provavelmente vão adicionar novos status (ex.: processamento em lote de reservas `EXPIRED`), e VARCHAR+CHECK evolui como qualquer outra migração comum.

### Capacidade disponível como coluna persistida, não calculada
`available_capacity` é uma coluna própria na tabela `events`, inicializada com o valor de `total_capacity` na criação do evento, em vez de ser calculada a partir de reservas ativas em tempo de leitura. Alternativa considerada: calcular disponibilidade via agregação sobre reservas a cada leitura — rejeitada nesta fase por adicionar complexidade de consulta sem necessidade; a lógica de decremento/reposição desse contador ao confirmar, cancelar ou expirar reservas é regra de negócio das fases futuras (fora do escopo deste change), que por ora só inicializa a coluna.

### Uma única migração Flyway (`V1`) para o schema completo desta fase
`V1__create_events_table.sql` cria, numa única migração, a tabela `events` (com `available_capacity` e `status`) e a tabela `reservations`, já que as duas fazem parte do mesmo marco de fundação do banco. Alternativa considerada: dividir a criação de `events` e `reservations` em migrações incrementais separadas — rejeitada porque, nesta fase inicial, não há schema anterior nem dado real em produção a preservar; fragmentar sem necessidade só adiciona migrações para acompanhar. A divisão em migrações incrementais volta a fazer sentido a partir da primeira mudança de schema depois que o banco tiver dados reais em algum ambiente compartilhado — daí em diante, a prática padrão de nunca editar uma migração já aplicada passa a valer.

### Reserva com FK para evento, sem FK para usuário
`reservation.event_id` terá uma foreign key para `events(id)` com `ON DELETE RESTRICT`, impedindo apagar um evento que já tenha reservas. `reservation.user_id` é apenas uma coluna `UUID`, sem foreign key, pois não existe tabela de usuários nesta fase (autenticação é um não-objetivo). Alternativa considerada: criar agora uma tabela mínima de usuários só para suportar a FK — rejeitada por estar fora do escopo deste change.

## Risks / Trade-offs

- [Testcontainers exige Docker disponível no ambiente de execução dos testes] → Mitigação: já é uma premissa do projeto (Docker Compose para Postgres/Redis em dev); documentar o requisito no README para qualquer ambiente de CI futuro.
- [Flyway adiciona um passo a mais no fluxo de desenvolvimento (criar migração a cada mudança de schema)] → Mitigação: aceitável dado o ganho de controle sobre um schema que terá evolução sensível a concorrência nas próximas fases.
- [Redis configurado sem uso funcional pode ficar "esquecido" e divergir quando for realmente usado] → Mitigação: validar a conectividade na inicialização (health check) para que qualquer quebra de configuração seja detectada cedo, antes da fase de lock distribuído.
- [Exclusões no gate de cobertura podem ser usadas indevidamente para esconder código não testado] → Mitigação: lista de exclusão explícita e restrita a boilerplate (main class, config, DTOs simples), revisada em cada PR que a alterar.
- [Os valores de `ReservationStatus` (`PENDING`, `CONFIRMED`, `EXPIRED`, `CANCELLED`) são um palpite razoável, definido sem o fluxo de negócio de reserva real ainda desenhado] → Mitigação: como nenhuma regra de transição de status é implementada nesta fase, ajustar os valores do enum em uma migração futura tem custo baixo; a fase que implementar o fluxo de reserva deve revisitar essa lista antes de construir a máquina de estados.
