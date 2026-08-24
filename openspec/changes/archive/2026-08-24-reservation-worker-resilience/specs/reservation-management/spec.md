## ADDED Requirements

### Requirement: Persistência assíncrona consistente da expiração da reserva
O sistema SHALL, eventualmente, refletir no Postgres o status `EXPIRED` de toda reserva pendente cujo prazo de expiração tenha vencido e sido processado pela expiração automática.

#### Scenario: Expiração automática é eventualmente refletida no Postgres
- **WHEN** uma reserva pendente é expirada automaticamente
- **THEN** em algum momento após a expiração, a reserva correspondente no Postgres passa a ter status `EXPIRED`
