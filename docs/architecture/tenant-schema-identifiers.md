# Tenant Schema Identifiers

## Stable naming contract

Each internal company UUID maps to exactly one canonical tenant schema identifier:

```text
tenant_<32 lowercase hexadecimal UUID characters>

550e8400-e29b-41d4-a716-446655440000
    -> tenant_550e8400e29b41d4a716446655440000
```

This convention is a stable compatibility contract for future tenant provisioning.
Do not casually change it once schemas are provisioned: a naming change requires
an explicit architecture decision and a migration/compatibility plan. No existing
accepted ADR specifies a competing convention. This foundation supplements
ADR-0002's schema-per-tenant decision; it does not create any schemas.

The mapping removes only the four hyphens from the UUID's canonical JDK string
representation and adds `tenant_`. It preserves all 128 bits, including leading
zeros. It uses no hashing, randomness, truncation or locale-sensitive formatting.
Identical UUIDs produce equal identifiers; distinct UUIDs produce distinct
identifiers. The existing `TenantId` permits both the zero and maximum UUID bit
patterns, so they map to 32 zero and 32 `f` characters respectively.

Internal UUIDs give stable company identities independent of employee identities,
business names, emails, usernames and subdomains. Company renaming does not change
the schema identifier.

## Value object and validation boundary

`com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier` is a final immutable
value object beside the existing `TenantId`. It depends only on the JDK and that
shared domain identity. It introduces no application, infrastructure, Spring,
Hibernate, JPA or JDBC dependencies and no cross-module dependencies.

The public API is deliberately small:

- `TenantSchemaIdentifier.from(TenantId)` generates and validates an identifier.
- `value()` returns the canonical identifier string.
- `toString()` returns the same string.
- `equals(Object)` and `hashCode()` provide value-based behavior.

The constructor is private. There is no raw-string constructor, parser, setter or
schema override. Current and planned routing/provisioning can derive the name from
the internal identity, so accepting arbitrary raw schema input is unnecessary.
This also prevents callers from constructing values for central or system schemas.
If raw parsing becomes necessary later, add it only with a reviewed trust boundary,
the same invariants and comprehensive malformed-input tests.

Every construction path goes through the private constructor. Null tenant identity
throws `NullPointerException` consistently with `TenantId` and `TenantContext`.
Before a value is stored, the generated string must have exactly 39 characters,
match the full-string pattern below, and remain within the explicit PostgreSQL
63-byte identifier limit:

```text
\Atenant_[0-9a-f]{32}\z
```

In Java source this is `"\\Atenant_[0-9a-f]{32}\\z"`. `\z` means the absolute
end of input, including rejection of a trailing newline. The character ranges
are ASCII-only. Unicode hexadecimal lookalikes, uppercase characters, whitespace,
CR/LF, NUL, quotes, punctuation, comments, qualified names, additional prefixes
and suffixes cannot match. Values are never sanitized or normalized from untrusted
raw schema strings. Invalid generated values throw `IllegalArgumentException`
without echoing the input; there is no replacement or fallback name.

PostgreSQL identifiers must fit its 63-byte limit, not merely a character count.
This convention yields exactly 39 ASCII characters and therefore exactly 39 UTF-8
bytes, leaving 24 bytes below that limit. The constructor explicitly checks UTF-8
byte length as well as the pattern and character length. Identifiers begin with a
letter and contain only lowercase letters, digits and an underscore. No truncation
or quoting utility is implemented.

## Usage and trust boundary

```java
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;

TenantId tenantId = TenantId.parse("550e8400-e29b-41d4-a716-446655440000");
TenantSchemaIdentifier schema = TenantSchemaIdentifier.from(tenantId);
String identifier = schema.value();
// identifier is tenant_550e8400e29b41d4a716446655440000
```

The UUID above is only an example. Production boundaries must obtain a trusted
internal tenant identity after independently verifying authentication, authorized
company membership and applicable server-side entitlement checks. A client-supplied
UUID is not trusted merely because it parses or maps to a canonical schema name.

Syntactic schema validation proves neither tenant existence nor authorization.
Identifier generation alone does not enforce database isolation. It does not
consult or modify the tenant context, so authorization and context lifecycle remain
separate responsibilities; see [Tenant Context Management](tenant-context.md).

`public`, `platform`, `billing`, `information_schema`, `pg_catalog`, `pg_temp`, other
PostgreSQL system/temporary schemas and arbitrary administrator schemas cannot be
represented by this type. They do not satisfy the naming contract and cannot be
supplied through its only construction API. Missing identities fail rather than
using a central schema, previous tenant or administrator fallback.

## Future database integration

Future Hibernate routing and Flyway tenant provisioning should accept this validated
value type, derive it from an independently authorized `TenantId`, and extract its
string only at the database integration boundary. They must never accept an
untrusted schema override or silently fall back to a central/system schema.

SQL integrations must use appropriate identifier handling for the chosen driver or
library. SQL identifiers are not ordinary bindable data parameters; syntactic
validation does not justify concatenating untrusted strings into SQL. Consume the
validated identifier and keep unrelated SQL values parameterized. No SQL execution,
identifier quoting utility, Hibernate routing, connection provider, `search_path`
switching, tenant creation or Flyway provisioning is implemented by this value
object. The subsequent [Hibernate Tenant Routing Foundation](hibernate-tenant-routing.md)
implements and tests resolver/provider infrastructure; activation of a separate
tenant persistence unit remains deferred.

Existing migration streams, restricted runtime/migration identities, database
permissions and central schemas remain unchanged. Future routing must also reset
tenant-specific connection state before pooled connections are returned. Redis
isolation and asynchronous context propagation are outside this task.

## Tests and verification handoff

`TenantSchemaIdentifierTest` contains 17 framework-independent JUnit test methods.
They cover the known mapping, determinism, distinct identities, leading zeros,
zero/max UUID values, ASCII and UTF-8 length, strict pattern, equality/hash codes,
string rendering, null rejection, existing `TenantId` compatibility and independence
of generated values. A per-bit round trip across all 128 UUID bits checks that no
UUID information is lost. An API boundary test checks that constructors remain
private and the only public static factory accepts `TenantId`, not raw strings.

There is no raw schema parsing API, so malformed schema strings have no construction
entry point; tests do not bypass the private constructor or introduce a parser just
to exercise invalid strings. The exact pattern, absolute end anchor, byte limit
and absence of a raw-string API are also part of the static correctness review.

At implementation time the checkout contains 66 existing JUnit test methods, giving
an expected total of 83 with these 17 additions. This is a static source count,
not a test result. No Maven compilation, automated tests or CI verification was
executed for this task; final acceptance remains with the human developer.

From the existing Dev Container:

```bash
cd /workspace/Backend
mvn -o -B -ntp -Dtest=TenantSchemaIdentifierTest test
mvn -o -B -ntp -Dtest=TenantIdTest,TenantContextTest,TenantContextHolderTest,ArchitectureTest,ModuleArchitectureTest test
mvn -o -B -ntp clean verify
```

Full verification requires the restricted development/test database configuration
already described in [Database Migrations](database-migrations.md). The schema
identifier unit tests themselves require neither Spring Boot startup nor a database.
