package com.saaserp.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(
                            ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
                    )
                    .importPackages("com.saaserp.erp");

    @Test
    void domainMustNotDependOnFrameworks() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "org.hibernate.."
                )
                .allowEmptyShould(true)
                .because("Domain logic must be framework independent")
                .check(CLASSES);
    }

    @Test
    void domainMustNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..application..",
                        "..api..",
                        "..infrastructure.."
                )
                .allowEmptyShould(true)
                .because("Domain must not depend on outer layers")
                .check(CLASSES);
    }

    @Test
    void applicationMustNotDependOnInfrastructureOrApi() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..infrastructure..",
                        "..api.."
                )
                .allowEmptyShould(true)
                .because("Application must depend on abstractions")
                .check(CLASSES);
    }

    @Test
    void apiMustNotDirectlyDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..api..")
                .should().dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .because("API must communicate through application use cases")
                .check(CLASSES);
    }
}
