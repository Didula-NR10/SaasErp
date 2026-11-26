# Hich SaaS ERP - Engineering Instructions

## Project Architecture

- Use a modular monolith.
- Follow pragmatic Clean Architecture.
- Backend: Java 21 and Spring Boot.
- Frontend: React and Vite.
- Database: PostgreSQL.
- Cache: Redis.
- Database migrations: Flyway.
- Base Java package: com.saaserp.erp.
- REST API prefix: /api/v1.

## Module Organization

Organize backend features under:

- platform
- erpcore
- verticals
- shared
- config

Each business feature should use appropriate layers:

- api
- application
- domain
- infrastructure

Create packages and classes only when needed.

## Dependency Rules

- Domain logic must remain framework independent.
- Domain must not depend on outer layers.
- Application must not depend on infrastructure.
- Application must not depend on API controllers.
- API must not directly access infrastructure.
- Define repository ports in the application layer.
- Implement repository ports in infrastructure.
- Keep JPA entities separate from domain models.
- Avoid circular module dependencies.
- Keep shared code minimal.
- Communicate across modules through explicit contracts.

## Multi-Tenancy

- Use one PostgreSQL database with schema-per-tenant isolation.
- Each company owns its own tenant schema.
- Employees belong to authorized company memberships.
- Keep platform and billing data in separate schemas.
- Never trust a client-supplied tenant identifier alone.
- Validate authenticated tenant membership.
- Never fall back to the public schema for tenant operations.
- Validate schema identifiers before database operations.
- Reset tenant-specific connection state before returning
  connections to the pool.
- Include tenant identifiers in cache keys.
- Test cross-tenant access prevention.

## Billing and Subscription

- Verify payment webhook signatures.
- Process webhook events idempotently.
- Never trust frontend payment confirmation.
- Provision tenant resources only after verified payment
  and entitlement checks.
- Enforce subscription access on the server.
- Preserve customer data when subscriptions expire.

## Security

- Never hardcode passwords, tokens or API keys.
- Never expose secrets in logs or API responses.
- Use environment variables for sensitive configuration.
- Validate all external input.
- Enforce authentication and authorization server-side.
- Do not disable security controls to make tests pass.
- Follow least privilege principles.

## Database

- Use Flyway for schema changes.
- Never use Hibernate automatic schema updates in production.
- Never modify previously applied migrations.
- Separate platform, billing and tenant migrations.
- Do not execute tenant migrations through an unrestricted
  recursive Flyway migration location.
- Use transactions for consistent business operations.

## API Standards

- Use versioned REST endpoints.
- Use request and response DTOs.
- Never expose persistence entities directly.
- Use appropriate HTTP status codes.
- Use consistent error responses.
- Use pagination for potentially large collections.

## Testing

- Write unit tests for business rules.
- Write integration tests for persistence and APIs.
- Write tenant isolation tests.
- Maintain ArchUnit architecture rules.
- Do not remove tests to conceal failures.
- Verify changes using Maven.
- Prefer offline Maven when dependencies are cached.

## Git and CI

- Use feature branches.
- Do not commit directly to main.
- Do not commit environment secrets.
- Do not commit generated build artifacts.
- Do not bypass required CI checks.
- Preserve existing frontend functionality.
- Keep changes focused on the requested task.

## Development Environment

- Use the existing Docker Dev Container.
- Do not rebuild Docker images unnecessarily.
- Do not remove containers or database volumes without approval.
- Do not install unnecessary dependencies.
- Reuse downloaded Maven and Node.js dependencies.

## Agent Behavior

- Inspect existing code before making changes.
- Explain significant architectural decisions.
- Do not introduce microservices or Kubernetes.
- Do not create speculative abstractions.
- Do not modify unrelated files.
- Report changed files and test results.
- Clearly report incomplete work and unresolved errors.
