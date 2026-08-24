# project-foundation Specification

## Purpose

Estabelece os requisitos técnicos de fundação do sistema Flash Booking: infraestrutura local via containers, formato de logs por ambiente, documentação interativa da API e um gate mínimo de qualidade de testes, servindo de base para todas as capacidades de domínio futuras.

## Requirements

### Requirement: Infraestrutura local via Docker Compose
O sistema SHALL disponibilizar um arquivo de orquestração de containers que suba PostgreSQL e Redis localmente, permitindo que a aplicação se conecte a ambos os serviços ao ser iniciada.

#### Scenario: Subir dependências locais
- **WHEN** o desenvolvedor executa o comando de subida dos containers de infraestrutura
- **THEN** os containers de PostgreSQL e Redis ficam disponíveis e acessíveis nas portas configuradas

#### Scenario: Aplicação conecta às dependências
- **WHEN** a aplicação é iniciada com os containers de PostgreSQL e Redis em execução
- **THEN** a aplicação estabelece conexão com o banco de dados e com o Redis sem erros de inicialização

### Requirement: Logs em texto legível por padrão
O sistema SHALL emitir logs em formato de texto legível por humanos quando executado sem um profile específico (ambiente de desenvolvimento local).

#### Scenario: Execução em desenvolvimento local
- **WHEN** a aplicação é iniciada sem o profile `qa` ativo
- **THEN** as entradas de log são emitidas em formato de texto simples, legível por humanos

### Requirement: Logs estruturados em JSON no profile qa
O sistema SHALL emitir logs estritamente em formato JSON estruturado quando o profile `qa` estiver ativo.

#### Scenario: Execução com profile qa
- **WHEN** a aplicação é iniciada com o profile `qa` ativo
- **THEN** cada entrada de log é emitida como um objeto JSON estruturado, sem misturar com linhas de texto livre

### Requirement: Documentação interativa da API
O sistema SHALL expor uma interface de documentação interativa (Swagger/OpenAPI) descrevendo os endpoints disponíveis, seus parâmetros e respostas.

#### Scenario: Acesso à documentação
- **WHEN** um cliente acessa o endpoint da documentação interativa da API
- **THEN** o sistema retorna a interface Swagger com a especificação OpenAPI dos endpoints existentes, incluindo `POST /events`

### Requirement: Gate mínimo de cobertura de testes
O sistema SHALL impor, via build do Gradle, uma cobertura mínima de testes de 80%, falhando o build quando a cobertura ficar abaixo desse limite.

#### Scenario: Build com cobertura suficiente
- **WHEN** a suíte de testes é executada e a cobertura agregada é igual ou superior a 80%
- **THEN** a etapa de verificação de cobertura do build é bem-sucedida

#### Scenario: Build com cobertura insuficiente
- **WHEN** a suíte de testes é executada e a cobertura agregada é inferior a 80%
- **THEN** a etapa de verificação de cobertura do build falha, impedindo a conclusão do build

### Requirement: Configuração declarativa de banco em testes de integração
O sistema SHALL configurar o banco de dados de testes (PostgreSQL via Testcontainers) de maneira puramente declarativa no arquivo de propriedades/YAML de teste, sem instanciar containers programaticamente nas classes de teste.

#### Scenario: Execução de teste de integração
- **WHEN** uma suíte de teste de integração anotada com `@SpringBootTest` é disparada
- **THEN** o Spring Boot conecta ao PostgreSQL provisionado sob demanda pelo driver JDBC do Testcontainers sem necessidade de classes ou anotações de infraestrutura de container no código de teste
