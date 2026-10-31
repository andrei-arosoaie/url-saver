package com.know;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("com.know");

  @Test
  void applicationDoesNotDependOnAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..presentation..", "..infrastructure..")
        .check(classes);
  }

  @Test
  void presentationDoesNotDependOnInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..presentation..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..infrastructure..")
        .allowEmptyShould(true)
        .check(classes);
  }

  @Test
  void infrastructureDoesNotDependOnPresentation() {
    noClasses()
        .that()
        .resideInAPackage("..infrastructure..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..presentation..")
        .check(classes);
  }

  @Test
  void featuresHaveNoCycles() {
    slices().matching("com.know.(*)..").should().beFreeOfCycles().check(classes);
  }

  @Test
  void applicationDoesNotExposePersistenceOrAiProviderTypes() {
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "jakarta.persistence..",
            "org.springframework.data..",
            "org.springframework.ai..",
            "com.openai..")
        .check(classes);
  }
}
