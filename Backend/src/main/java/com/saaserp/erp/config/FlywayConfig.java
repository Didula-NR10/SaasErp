package com.saaserp.erp.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
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
    Flyway platformFlyway(DataSource dataSource) {
        return createFlyway(dataSource, "platform", "classpath:db/migration/platform");
    }

    @Bean("billingFlyway")
    Flyway billingFlyway(DataSource dataSource) {
        return createFlyway(dataSource, "billing", "classpath:db/migration/billing");
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
            DataSource dataSource, String schema, String location) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .schemas(schema)
                .defaultSchema(schema)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .createSchemas(true)
                .load();
    }
}
