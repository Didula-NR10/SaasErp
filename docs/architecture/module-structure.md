# Module Structure

Base package: com.saaserp.erp

## Platform Modules

- account
- auth
- tenant
- billing
- entitlement
- provisioning
- notification
- audit

## ERP Core Modules

- inventory
- sales
- customers
- suppliers
- purchasing
- staff
- reports

## Industry Modules

- salon
- bookshop
- mobile
- vehicle

## Feature Package Structure

Each feature uses the appropriate layers:

feature/
    api/
        controller/
        request/
        response/
    application/
        usecase/
        port/in/
        port/out/
    domain/
        model/
        service/
        exception/
        event/
    infrastructure/
        persistence/
            entity/
            repository/
            adapter/
            mapper/

Create packages only when required.

Modules communicate through explicit public contracts.
Do not directly access another module's persistence layer.
