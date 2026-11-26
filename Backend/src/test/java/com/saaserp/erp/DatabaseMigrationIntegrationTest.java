package com.saaserp.erp;

import java.util.Arrays;
import java.util.List;

import jakarta.persistence.EntityManagerFactory;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIntegrationTest {

    @Autowired
    @Qualifier("platformFlyway")
    private Flyway platformFlyway;

    @Autowired
    @Qualifier("billingFlyway")
    private Flyway billingFlyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void initializesPlatformAndBillingWithIndependentHistoryTables() {
        assertThat(schemaAndHistoryExist("platform")).isTrue();
        assertThat(schemaAndHistoryExist("billing")).isTrue();
        assertExpectedMigrationApplied(platformFlyway, "1");
        assertExpectedMigrationApplied(billingFlyway, "1");
        assertThat(successfulPlatformMigrationExists("1")).isTrue();
        assertThat(successfulBillingMigrationExists("1")).isTrue();
        assertThat(platformFlyway.getConfiguration().getSchemas())
                .containsExactly("platform");
        assertThat(billingFlyway.getConfiguration().getSchemas())
                .containsExactly("billing");
        assertThat(platformFlyway.getConfiguration().getDefaultSchema())
                .isEqualTo("platform");
        assertThat(billingFlyway.getConfiguration().getDefaultSchema())
                .isEqualTo("billing");
    }

    @Test
    void configuresOnlyExplicitCentralLocations() {
        assertThat(locations(platformFlyway))
                .containsExactly("classpath:db/migration/platform");
        assertThat(locations(billingFlyway))
                .containsExactly("classpath:db/migration/billing");
        assertThat(applicationContext.getBeansOfType(Flyway.class))
                .containsOnlyKeys("platformFlyway", "billingFlyway");
        assertThat(applicationContext.getBeansOfType(FlywayMigrationInitializer.class))
                .containsOnlyKeys(
                        "platformFlywayMigrationInitializer",
                        "billingFlywayMigrationInitializer");
        assertThat(platformFlyway.getConfiguration().isValidateOnMigrate()).isTrue();
        assertThat(billingFlyway.getConfiguration().isValidateOnMigrate()).isTrue();
        assertThat(platformFlyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(billingFlyway.getConfiguration().isBaselineOnMigrate()).isFalse();
        assertThat(platformFlyway.getConfiguration().isCleanDisabled()).isTrue();
        assertThat(billingFlyway.getConfiguration().isCleanDisabled()).isTrue();
    }

    @Test
    void migrationsFinishBeforeHibernateInitializes() {
        var entityManagerFactoryDefinition = applicationContext.getBeanFactory()
                .getBeanDefinition("entityManagerFactory");
        var billingInitializerDefinition = applicationContext.getBeanFactory()
                .getBeanDefinition("billingFlywayMigrationInitializer");

        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(schemaAndHistoryExist("platform")).isTrue();
        assertThat(schemaAndHistoryExist("billing")).isTrue();
        assertThat(billingInitializerDefinition.getDependsOn())
                .contains("platformFlywayMigrationInitializer");
        assertThat(entityManagerFactoryDefinition.getDependsOn())
                .contains("platformFlywayMigrationInitializer",
                        "billingFlywayMigrationInitializer");
    }

    @Test
    void repeatedMigrationDoesNotReapplyCompletedMigrations() {
        var appliedPlatformVersions = appliedMigrationVersions(platformFlyway);
        var appliedBillingVersions = appliedMigrationVersions(billingFlyway);

        assertThat(platformFlyway.migrate().migrationsExecuted).isZero();
        assertThat(billingFlyway.migrate().migrationsExecuted).isZero();
        assertThat(appliedMigrationVersions(platformFlyway))
                .containsExactlyElementsOf(appliedPlatformVersions);
        assertThat(appliedMigrationVersions(billingFlyway))
                .containsExactlyElementsOf(appliedBillingVersions);
        assertNoPendingMigrations(platformFlyway);
        assertNoPendingMigrations(billingFlyway);
    }

    @Test
    void centralStartupDoesNotExecuteTenantMigrationLocations() {
        assertThat(locations(platformFlyway))
                .noneMatch(location -> location.contains("/tenant/"));
        assertThat(locations(billingFlyway))
                .noneMatch(location -> location.contains("/tenant/"));
        assertThat(applicationContext.getBeansOfType(Flyway.class)).hasSize(2);
    }

    private boolean schemaAndHistoryExist(String schema) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL",
                Boolean.class,
                schema + ".flyway_schema_history"));
    }

    private static void assertExpectedMigrationApplied(
            Flyway flyway, String expectedVersion) {
        var applied = Arrays.stream(flyway.info().applied())
                .filter(MigrationInfo::isVersioned)
                .toList();

        assertThat(applied)
                .extracting(migration -> migration.getVersion().getVersion())
                .contains(expectedVersion);
        assertThat(applied)
                .allSatisfy(migration ->
                        assertThat(migration.getState().isApplied()).isTrue());
        assertNoPendingMigrations(flyway);
    }

    private boolean successfulPlatformMigrationExists(String version) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM platform.flyway_schema_history "
                        + "WHERE version = ? AND success)",
                Boolean.class,
                version));
    }

    private boolean successfulBillingMigrationExists(String version) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM billing.flyway_schema_history "
                        + "WHERE version = ? AND success)",
                Boolean.class,
                version));
    }

    private static List<String> appliedMigrationVersions(Flyway flyway) {
        return Arrays.stream(flyway.info().applied())
                .filter(MigrationInfo::isVersioned)
                .map(migration -> migration.getVersion().getVersion())
                .toList();
    }

    private static void assertNoPendingMigrations(Flyway flyway) {
        assertThat(flyway.info().pending()).isEmpty();
    }

    private static List<String> locations(Flyway flyway) {
        return Arrays.stream(flyway.getConfiguration().getLocations())
                .map(Location::getDescriptor)
                .toList();
    }
}
