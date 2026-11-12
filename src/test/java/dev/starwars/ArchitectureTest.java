package dev.starwars;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/**
 * Layering rules from ADR-001. Rules allow empty sets so they apply from the first commit, before the packages exist.
 */
@AnalyzeClasses(packages = "dev.starwars", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule apiDoesNotAccessSwapiClient = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..swapi..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule apiDoesNotAccessRepositories = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceDoesNotDependOnApi = noClasses()
            .that()
            .resideInAPackage("..service..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..api..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule swapiDoesNotDependOnFeatures = noClasses()
            .that()
            .resideInAPackage("dev.starwars.swapi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("dev.starwars.catalog..", "dev.starwars.auth..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noCyclesBetweenTopLevelPackages =
            slices().matching("dev.starwars.(*)..").should().beFreeOfCycles().allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersFollowConvention = classes()
            .that()
            .areAnnotatedWith(RestController.class)
            .should()
            .haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);
}
