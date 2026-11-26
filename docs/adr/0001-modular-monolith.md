# ADR-0001: Modular Monolith

Status: Accepted

## Context

Hich ERP must support multiple business industries while
maintaining manageable infrastructure costs and complexity.

## Decision

Use one Spring Boot application organized into independent
business modules.

## Alternatives Considered

- Microservices
- Traditional layered monolith

## Consequences

Benefits:

- Simpler deployment
- Lower infrastructure costs
- Easier transaction management
- Reduced operational complexity

Trade-offs:

- Modules share one application process.
- Module boundaries require explicit enforcement.
- Independent module scaling is limited.
