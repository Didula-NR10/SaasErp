# Hich SaaS ERP Architecture

## Architecture Style

Modular monolith with pragmatic Clean Architecture.

The backend is deployed as one Spring Boot application.
Business features are organized into independent modules.

## Technology Stack

- Java 21
- Spring Boot
- Spring Security and JWT
- PostgreSQL
- Redis
- Flyway
- React and Vite
- Docker
- Nginx
- Cloudflare
- Maven
- GitHub Actions

## Business Module Groups

- platform: SaaS platform management
- erpcore: Shared ERP business capabilities
- verticals: Industry-specific ERP capabilities
- shared: Reusable technical and domain abstractions
- config: Application configuration and wiring

## Multi-Tenancy

Use a single PostgreSQL database with schema-per-tenant
isolation.

Platform and billing data use dedicated schemas.

Each customer company receives an independent schema.
Employees access company data through authorized memberships.

## Deployment Strategy

Use a single production application server initially.
Use a separate server for encrypted offsite backups.

Do not introduce microservices or Kubernetes without
a documented architectural decision.

## Architecture Enforcement

ArchUnit enforces Java layer and module dependency rules.

Database isolation, authentication, authorization and
subscription security require separate integration tests.
