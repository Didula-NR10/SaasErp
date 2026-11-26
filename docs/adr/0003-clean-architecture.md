# ADR-0003: Clean Architecture

Status: Accepted

## Context

The ERP backend must remain maintainable as business
features and development teams grow.

## Decision

Use pragmatic Clean Architecture inside business modules.

Separate API, application, domain and infrastructure
responsibilities.

Define repository ports in the application layer and
implement them in infrastructure.

## Consequences

Benefits:

- Testable business logic
- Reduced framework coupling
- Clear dependency direction
- Maintainable business modules

Trade-offs:

- Additional interfaces and mapping may be required.
- Developers must understand architectural boundaries.

Use ArchUnit to enforce architectural rules.
Avoid unnecessary abstractions and premature complexity.
