package com.saaserp.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ModuleArchitectureTest {

    private static final String BASE = "com.saaserp.erp";

    private static final JavaClasses CLASSES =
            new ClassFileImporter()
                    .withImportOption(
                            ImportOption.Predefined.DO_NOT_INCLUDE_TESTS
                    )
                    .importPackages(BASE);

    @Test
    void platformModulesMustBeFreeOfCycles() {
        slices()
                .matching(BASE + ".platform.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void erpCoreModulesMustBeFreeOfCycles() {
        slices()
                .matching(BASE + ".erpcore.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void verticalModulesMustBeFreeOfCycles() {
        slices()
                .matching(BASE + ".verticals.(*)..")
                .should().beFreeOfCycles()
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void platformMustNotDependOnErpModules() {
        noClasses()
                .that().resideInAPackage(BASE + ".platform..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE + ".erpcore..",
                        BASE + ".verticals.."
                )
                .allowEmptyShould(true)
                .because("Platform must remain independent of ERP implementations")
                .check(CLASSES);
    }

    @Test
    void erpCoreMustNotDependOnVerticals() {
        noClasses()
                .that().resideInAPackage(BASE + ".erpcore..")
                .should().dependOnClassesThat()
                .resideInAPackage(BASE + ".verticals..")
                .allowEmptyShould(true)
                .because("Shared ERP core must remain industry independent")
                .check(CLASSES);
    }

    @Test
    void verticalModulesMustBeIndependent() {
        slices()
                .matching(BASE + ".verticals.(*)..")
                .should().notDependOnEachOther()
                .allowEmptyShould(true)
                .because("Industry modules must remain independently maintainable")
                .check(CLASSES);
    }

    @Test
    void sharedMustNotDependOnBusinessModules() {
        noClasses()
                .that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        BASE + ".platform..",
                        BASE + ".erpcore..",
                        BASE + ".verticals..",
                        BASE + ".config.."
                )
                .allowEmptyShould(true)
                .because("Shared code must not depend on business implementations")
                .check(CLASSES);
    }
}
