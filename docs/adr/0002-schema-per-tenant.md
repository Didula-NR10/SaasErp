# ADR-0002: Schema-per-Tenant

Status: Accepted

## Context

Multiple customer companies require isolated ERP data.

## Decision

Use one PostgreSQL database with a separate schema for
each customer company.

Maintain dedicated platform and billing schemas.

## Alternatives Considered

- Shared tables with tenant identifiers
- Separate database for every tenant

## Consequences

Benefits:

- Logical separation of customer data
- Lower infrastructure complexity than database-per-tenant
- Support for industry-specific schema migrations

Risks:

- Incorrect schema routing can expose customer data.
- Schema migrations become more complex as tenants grow.
- Connection pool state must be managed carefully.
- Schema separation alone does not guarantee security.

Mitigate these risks through authorization, safe schema
routing, least privilege and tenant isolation tests.
