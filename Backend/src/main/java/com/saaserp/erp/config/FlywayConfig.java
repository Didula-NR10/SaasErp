package com.saaserp.erp.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(DatabaseInitializationDependencyConfigurer.class)
public class FlywayConfig {

    @Bean("platformFlyway")
    Flyway platformFlyway(
            @Value("${spring.datasource.username}") String runtimeUsername,
            @Value("${spring.datasource.password}") String runtimePassword,
            @Value("${app.datasource.migration.url}") String url,
            @Value("${app.datasource.migration.username}") String username,
            @Value("${app.datasource.migration.password}") String password) {
        return createFlyway(
                runtimeUsername, runtimePassword, url, username, password,
                "platform", "classpath:db/migration/platform");
    }

    @Bean("billingFlyway")
    Flyway billingFlyway(
            @Value("${spring.datasource.username}") String runtimeUsername,
            @Value("${spring.datasource.password}") String runtimePassword,
            @Value("${app.datasource.migration.url}") String url,
            @Value("${app.datasource.migration.username}") String username,
            @Value("${app.datasource.migration.password}") String password) {
        return createFlyway(
                runtimeUsername, runtimePassword, url, username, password,
                "billing", "classpath:db/migration/billing");
    }

    @Bean("platformFlywayMigrationInitializer")
    FlywayMigrationInitializer platformFlywayMigrationInitializer(
            @Qualifier("platformFlyway") Flyway platformFlyway) {
        return new FlywayMigrationInitializer(platformFlyway);
    }

    @Bean("billingFlywayMigrationInitializer")
    @DependsOn("platformFlywayMigrationInitializer")
    FlywayMigrationInitializer billingFlywayMigrationInitializer(
            @Qualifier("billingFlyway") Flyway billingFlyway) {
        return new FlywayMigrationInitializer(billingFlyway);
    }

    private static Flyway createFlyway(
            String runtimeUsername,
            String runtimePassword,
            String url,
            String username,
            String password,
            String schema,
            String location) {
        requireNonBlankCredential("runtime username", runtimeUsername);
        requireNonBlankCredential("runtime password", runtimePassword);
        requireNonBlankCredential("migration username", username);
        requireNonBlankCredential("migration password", password);

        return Flyway.configure()
                .dataSource(url, username, password)
                .locations(location)
                .schemas(schema)
                .defaultSchema(schema)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .createSchemas(false)
                .load();
    }

    private static void requireNonBlankCredential(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Database " + name + " must be configured");
        }
    }
}
