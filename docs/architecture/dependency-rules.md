# Architecture Dependency Rules

## Layer Rules

1. Domain must not depend on Spring, JPA or Hibernate.
2. Domain must not depend on outer layers.
3. Application must not depend on API or infrastructure.
4. API must not directly depend on infrastructure.

## Module Rules

1. Platform modules must be free of circular dependencies.
2. ERP core modules must be free of circular dependencies.
3. Industry modules must be free of circular dependencies.
4. Platform must not depend on ERP implementations.
5. ERP core must not depend on industry modules.
6. Industry modules must not depend on each other.
7. Shared code must not depend on business modules or config.

## Enforcement

ArchitectureTest.java enforces layer rules.
ModuleArchitectureTest.java enforces module rules.

Execute architecture tests with:

mvn -Dtest=ArchitectureTest,ModuleArchitectureTest test

GitHub Actions executes these tests during Maven verification.

## Current Limitations

Empty packages are temporarily permitted.

Public contract enforcement and additional module boundary
checks must be strengthened as business modules are created.

ArchUnit does not guarantee runtime tenant isolation.
Separate integration tests are required.
