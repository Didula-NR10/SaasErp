package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import com.saaserp.erp.shared.tenant.application.TenantContextHolder;
import com.saaserp.erp.shared.tenant.domain.TenantId;
import com.saaserp.erp.shared.tenant.domain.TenantSchemaIdentifier;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;

/**
 * Routing infrastructure for a future, separate tenant persistence unit.
 * No Spring registration is intentional. Supply only the existing runtime Hikari pool.
 * Connections belong to Hibernate and must be returned through this provider, not closed directly.
 */
public final class TenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private static final String BASELINE_PATH = "pg_catalog, pg_temp";
    private static final String RUNTIME_CHECK = """
            SELECT NOT r.rolsuper AND NOT r.rolcreaterole AND NOT r.rolcreatedb
                AND NOT r.rolreplication AND NOT r.rolbypassrls
                AND current_user = session_user AND session_user = ?
                AND pg_catalog.pg_has_role(current_user, 'hich_runtime', 'MEMBER')
                AND NOT pg_catalog.has_database_privilege(current_user, pg_catalog.current_database(), 'CREATE')
                AND NOT pg_catalog.has_database_privilege(current_user, pg_catalog.current_database(), 'TEMP')
            FROM pg_catalog.pg_roles r WHERE r.rolname = current_user
            """;
    private static final String SCHEMA_CHECK = """
            SELECT pg_catalog.has_schema_privilege(current_user, n.oid, 'USAGE')
                AND NOT pg_catalog.has_schema_privilege(current_user, n.oid, 'CREATE')
            FROM pg_catalog.pg_namespace n WHERE n.nspname = ?
            """;
    private static final String SET_PATH = "SELECT pg_catalog.set_config('search_path', ?, false)";
    private static final String PATH_CHECK = """
            SELECT pg_catalog.current_setting('search_path') = ?
                AND pg_catalog.current_schema() = ?
                AND pg_catalog.current_schemas(true)::text[] = pg_catalog.string_to_array(?, ',')
                AND pg_catalog.pg_my_temp_schema() = 0
            """;

    private final HikariDataSource runtimePool;
    private final String expectedRuntimeUser;
    // Identity keys prevent a foreign proxy's equals/hashCode from impersonating an owned lease.
    private final Map<Connection, Lease> leases = Collections.synchronizedMap(new IdentityHashMap<>());

    public TenantConnectionProvider(HikariDataSource runtimePool) {
        this.runtimePool = Objects.requireNonNull(runtimePool, "Runtime pool is required");
        this.expectedRuntimeUser = runtimePool.getUsername();
        if (expectedRuntimeUser == null || expectedRuntimeUser.isBlank()) {
            throw new IllegalArgumentException("An explicit runtime database identity is required");
        }
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        TenantId tenantId = TenantId.parse(tenantIdentifier);
        if (!TenantContextHolder.requireCurrent().tenantId().equals(tenantId)) {
            throw new IllegalStateException("Tenant connection identity does not match the active context");
        }
        return acquire(tenantId);
    }

    /** Metadata access has no customer identity and an explicitly catalog-only baseline. */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return acquire(null);
    }

    private Connection acquire(TenantId tenantId) throws SQLException {
        Connection connection;
        try {
            connection = runtimePool.getConnection();
        } catch (SQLException failure) {
            throw new SQLException("Unable to acquire a routing connection", failure);
        }
        try {
            PostgresSessionState.requireIdle(connection);
            verifyRuntime(connection);
            PostgresSessionState.reset(connection, runtimePool);
            setAndVerifyPath(connection, null);
            verifyRuntime(connection);
            String schema = tenantId == null ? null : TenantSchemaIdentifier.from(tenantId).value();
            if (schema != null) {
                try (PreparedStatement statement = connection.prepareStatement(SCHEMA_CHECK)) {
                    statement.setQueryTimeout(5);
                    statement.setString(1, schema);
                    requireTrue(statement, "Tenant schema is unavailable to the runtime identity");
                }
                setAndVerifyPath(connection, schema);
            }
            PostgresSessionState.requireIdle(connection);
            leases.put(connection, new Lease(tenantId == null ? null : tenantId.toString()));
            return connection;
        } catch (SQLException | RuntimeException failure) {
            SQLException reported = new SQLException("Unable to initialize a routing connection", failure);
            invalidate(connection, reported);
            throw reported;
        } catch (Error failure) {
            invalidate(connection, failure);
            throw failure;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        release(tenantIdentifier, connection, false);
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        release(null, connection, true);
    }

    private void release(String tenantIdentifier, Connection connection, boolean bootstrap) throws SQLException {
        Objects.requireNonNull(connection, "Routing connection is required");
        Lease lease = leases.remove(connection);
        if (lease == null) {
            throw new SQLException("Connection is not an active lease of this provider");
        }
        // A directly closed Hikari proxy can refer to a pool entry already borrowed
        // by somebody else. Never evict that stale proxy's newer physical lease.
        boolean closed;
        try {
            closed = connection.isClosed();
        } catch (SQLException | RuntimeException failure) {
            SQLException reported = new SQLException("Unable to establish connection lease ownership", failure);
            stopPool(reported);
            throw reported;
        } catch (Error failure) {
            stopPool(failure);
            throw failure;
        }
        if (closed) {
            throw new SQLException("Routing connection is already closed");
        }
        if (bootstrap != (lease.tenantIdentifier() == null)
                || !Objects.equals(tenantIdentifier, lease.tenantIdentifier())) {
            SQLException failure = new SQLException("Connection release does not match its lease");
            invalidate(connection, failure);
            throw failure;
        }
        boolean closeAttempted = false;
        try {
            // A session SET inside an unfinished transaction can be undone by rollback.
            // Never toggle autocommit, commit, or roll back somebody else's transaction.
            PostgresSessionState.requireIdle(connection);
            verifyRuntime(connection);
            PostgresSessionState.reset(connection, runtimePool);
            setAndVerifyPath(connection, null);
            verifyRuntime(connection);
            PostgresSessionState.requireIdle(connection);
            closeAttempted = true;
            connection.close();
        } catch (SQLException | RuntimeException failure) {
            SQLException reported = new SQLException("Unable to safely release a routing connection", failure);
            invalidate(connection, reported, closeAttempted);
            throw reported;
        } catch (Error failure) {
            invalidate(connection, failure, closeAttempted);
            throw failure;
        }
    }

    private void verifyRuntime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RUNTIME_CHECK)) {
            statement.setQueryTimeout(5);
            statement.setString(1, expectedRuntimeUser);
            requireTrue(statement, "Connection does not satisfy restricted runtime requirements");
        }
    }

    private static void setAndVerifyPath(Connection connection, String schema) throws SQLException {
        String path = schema == null ? BASELINE_PATH : schema + ", pg_temp";
        try (PreparedStatement statement = connection.prepareStatement(SET_PATH)) {
            statement.setQueryTimeout(5);
            statement.setString(1, path);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !path.equals(result.getString(1))) {
                    throw new SQLException("Unable to establish the required connection path");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(PATH_CHECK)) {
            statement.setQueryTimeout(5);
            statement.setString(1, path);
            statement.setString(2, schema == null ? "pg_catalog" : schema);
            statement.setString(3, schema == null ? "pg_catalog" : "pg_catalog," + schema);
            requireTrue(statement, "Effective connection path is unsafe");
        }
    }

    private static void requireTrue(PreparedStatement statement, String message) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            if (!result.next() || !result.getBoolean(1)) {
                throw new SQLException(message);
            }
        }
    }

    private void invalidate(Connection connection, Throwable original) {
        invalidate(connection, original, false);
    }

    private void invalidate(Connection connection, Throwable original, boolean closeAttempted) {
        try {
            if (closeAttempted && connection.isClosed()) {
                // Hikari can recycle in a finally block before close throws. Do not
                // evict that old proxy's possibly newer physical lease.
                throw new SQLException("Connection ownership is uncertain after failed close");
            }
            // Eviction removes the pool entry and schedules physical closure. Do NOT
            // close the proxy afterward: Hikari.close() can issue rollback on a dirty
            // transaction and can recycle in its finally block. Physical destruction
            // aborts unfinished work without invoking JDBC transaction control here.
            runtimePool.evictConnection(connection);
        } catch (SQLException | RuntimeException | Error evictionFailure) {
            original.addSuppressed(evictionFailure);
            stopPool(original);
        }
        // If invalidation and shutdown both fail, leave the unsafe handle checked out.
        // Never ordinary-close it to recover availability. Operational recovery is required.
    }

    private void stopPool(Throwable original) {
        try {
            runtimePool.close();
        } catch (RuntimeException | Error shutdownFailure) {
            original.addSuppressed(shutdownFailure);
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean handlesConnectionSchema() {
        return true;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType == TenantConnectionProvider.class || unwrapType == MultiTenantConnectionProvider.class;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        Objects.requireNonNull(unwrapType, "Unwrap type is required");
        if (!isUnwrappableAs(unwrapType)) {
            throw new UnknownUnwrapTypeException(unwrapType);
        }
        return unwrapType.cast(this);
    }

    private record Lease(String tenantIdentifier) {
    }
}
