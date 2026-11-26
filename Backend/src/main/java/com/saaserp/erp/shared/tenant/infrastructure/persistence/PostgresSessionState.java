package com.saaserp.erp.shared.tenant.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.TransactionState;

/** PostgreSQL-specific reset policy. Never runs transaction control on a caller's behalf. */
final class PostgresSessionState {

    private static final String SETTINGS = """
            SELECT pg_catalog.set_config('TimeZone', 'UTC', false) = 'UTC'
                AND pg_catalog.set_config('application_name', 'hich-erp', false) = 'hich-erp'
                AND pg_catalog.set_config('statement_timeout', '30s', false) IS NOT NULL
                AND pg_catalog.set_config('lock_timeout', '5s', false) IS NOT NULL
                AND pg_catalog.set_config('idle_in_transaction_session_timeout', '30s', false) IS NOT NULL
                AND pg_catalog.set_config('default_transaction_read_only', ?, false) IS NOT NULL
            """;
    private static final String VERIFY = """
            SELECT pg_catalog.current_setting('TimeZone') = 'UTC'
                AND pg_catalog.current_setting('application_name') = 'hich-erp'
                AND pg_catalog.current_setting('statement_timeout')::interval = interval '30 seconds'
                AND pg_catalog.current_setting('lock_timeout')::interval = interval '5 seconds'
                AND pg_catalog.current_setting('idle_in_transaction_session_timeout')::interval = interval '30 seconds'
                AND pg_catalog.current_setting('default_transaction_isolation') = ?
                AND pg_catalog.current_setting('transaction_isolation') = ?
                AND pg_catalog.current_setting('default_transaction_read_only') = ?
                AND pg_catalog.current_setting('transaction_read_only') = ?
                AND pg_catalog.pg_my_temp_schema() = 0
                AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_listening_channels())
                AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_locks
                    WHERE pid = pg_catalog.pg_backend_pid() AND locktype = 'advisory')
                AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_prepared_statements WHERE from_sql)
                AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_cursors WHERE is_holdable)
            """;

    private PostgresSessionState() {
    }

    static BaseConnection requireIdle(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Routing handoff requires an autocommit connection");
        }
        BaseConnection physical = connection.unwrap(BaseConnection.class);
        if (physical == null || physical.isClosed() || physical.getTransactionState() != TransactionState.IDLE) {
            throw new SQLException("Routing handoff requires an idle PostgreSQL session");
        }
        return physical;
    }

    static void reset(Connection connection, HikariDataSource pool) throws SQLException {
        BaseConnection physical = requireIdle(connection);
        // pgjdbc 42.7.14 recognizes the DISCARD ALL completion tag and invalidates
        // cached server statement names. Keep that behavior enabled explicitly.
        physical.setFlushCacheOnDeallocate(true);
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            statement.execute("DISCARD ALL");
        }
        requireIdle(connection);
        // DISCARD removes server subscriptions, but the driver can retain notifications
        // already received. Drain that queue without exposing its prior payloads.
        physical.getNotifications();

        String configured = pool.getTransactionIsolation();
        int isolation = configured == null ? physical.getTransactionIsolation() : configuredIsolation(configured);
        boolean readOnly = pool.isReadOnly();
        // Synchronize the driver and Hikari's tracked JDBC state after SQL-level reset.
        // Autocommit is intentionally never changed here.
        connection.setReadOnly(readOnly);
        connection.setTransactionIsolation(isolation);
        connection.setSchema(pool.getSchema());
        String readOnlySetting = readOnly ? "on" : "off";
        try (PreparedStatement statement = connection.prepareStatement(SETTINGS)) {
            statement.setQueryTimeout(5);
            statement.setString(1, readOnlySetting);
            requireTrue(statement, "Unable to establish the required session settings");
        }
        try (PreparedStatement statement = connection.prepareStatement(VERIFY)) {
            statement.setQueryTimeout(5);
            statement.setString(1, isolationSetting(isolation));
            statement.setString(2, isolationSetting(isolation));
            statement.setString(3, readOnlySetting);
            statement.setString(4, readOnlySetting);
            requireTrue(statement, "PostgreSQL session reset verification failed");
        }
        requireIdle(connection);
    }

    private static int configuredIsolation(String configured) throws SQLException {
        return switch (configured) {
            case "TRANSACTION_READ_UNCOMMITTED" -> Connection.TRANSACTION_READ_UNCOMMITTED;
            case "TRANSACTION_READ_COMMITTED" -> Connection.TRANSACTION_READ_COMMITTED;
            case "TRANSACTION_REPEATABLE_READ" -> Connection.TRANSACTION_REPEATABLE_READ;
            case "TRANSACTION_SERIALIZABLE" -> Connection.TRANSACTION_SERIALIZABLE;
            default -> throw new SQLException("Unsupported runtime transaction isolation configuration");
        };
    }

    private static String isolationSetting(int isolation) throws SQLException {
        return switch (isolation) {
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "read uncommitted";
            case Connection.TRANSACTION_READ_COMMITTED -> "read committed";
            case Connection.TRANSACTION_REPEATABLE_READ -> "repeatable read";
            case Connection.TRANSACTION_SERIALIZABLE -> "serializable";
            default -> throw new SQLException("Unsupported runtime transaction isolation level");
        };
    }

    private static void requireTrue(PreparedStatement statement, String message) throws SQLException {
        try (ResultSet rows = statement.executeQuery()) {
            if (!rows.next() || !rows.getBoolean(1)) {
                throw new SQLException(message);
            }
        }
    }
}
