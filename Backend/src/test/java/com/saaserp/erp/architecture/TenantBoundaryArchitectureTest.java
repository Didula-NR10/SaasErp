package com.saaserp.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Protects inner layers; infrastructure adapters and configuration wiring remain permitted. */
class TenantBoundaryArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.saaserp.erp");

    @Test
    void applicationAndDomainMustNotUseRawRedisClients() {
        noClasses().that().resideInAnyPackage("..application..", "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.data.redis..", "io.lettuce.core..", "redis.clients.jedis..")
                .allowEmptyShould(true)
                .because("Tenant cache callers must use the scoped application contract")
                .check(CLASSES);
    }

    @Test
    void applicationAndDomainMustNotBypassRoutingThroughRawDatabaseClients() {
        noClasses().that().resideInAnyPackage("..application..", "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.sql..", "javax.sql..", "org.postgresql..", "com.zaxxer.hikari..",
                        "org.hibernate.engine.jdbc..")
                .allowEmptyShould(true)
                .because("Database acquisition and schema routing belong to infrastructure")
                .check(CLASSES);
    }

    @Test
    void tenantCacheApplicationContractMustRemainFrameworkIndependent() {
        noClasses().that().resideInAPackage("com.saaserp.erp.shared.cache.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.hibernate..", "jakarta.persistence..",
                        "..infrastructure..", "com.saaserp.erp.config..")
                .allowEmptyShould(true)
                .because("The tenant cache port and key policy must remain portable inner-layer contracts")
                .check(CLASSES);
    }
}
