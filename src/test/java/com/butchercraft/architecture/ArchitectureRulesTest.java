package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureComponent;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureReference;
import com.butchercraft.architecture.validation.ArchitectureRules;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.DependencyConstraint;
import com.butchercraft.architecture.validation.DependencyDescriptor;
import com.butchercraft.architecture.validation.OrderingPolicy;
import com.butchercraft.architecture.validation.OwnershipAssignment;
import com.butchercraft.architecture.validation.PersistenceDataKind;
import com.butchercraft.architecture.validation.PersistenceDescriptor;
import com.butchercraft.architecture.validation.RegistryDescriptor;
import com.butchercraft.architecture.validation.RegistryEntryDescriptor;
import com.butchercraft.architecture.validation.SchedulerDescriptor;
import com.butchercraft.architecture.validation.SchedulerStageDescriptor;
import com.butchercraft.architecture.validation.SimulationInvariantDescriptor;
import com.butchercraft.architecture.validation.SimulationInvariantType;
import com.butchercraft.architecture.validation.ValidationCategory;
import com.butchercraft.architecture.validation.ValidationContext;
import com.butchercraft.architecture.validation.ValidationReport;
import com.butchercraft.architecture.validation.ValidationResult;
import com.butchercraft.architecture.validation.ValidationRule;
import com.butchercraft.architecture.validation.ValidationRuleRegistry;
import com.butchercraft.architecture.validation.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureRulesTest {
    @Test
    void currentButcherCraftArchitecturePassesEveryStandardRule() {
        ValidationReport report = ButcherCraftArchitectureValidation.validateCurrentArchitecture();

        assertTrue(report.successful(), () -> "Architecture failures: " + report.failedRules());
        assertEquals(21, report.summary().ruleCount());
        assertEquals(21, report.summary().passedRules());
        assertEquals(0, report.summary().failedRules());
        assertTrue(report.findByCategory(ValidationCategory.ALLOCATION).stream()
                .allMatch(result -> result.status() == ValidationStatus.PASSED));
    }

    @Test
    void equivalentInputsProduceEqualReportsAndStableRuleOrder() {
        ArchitectureValidator validator = new ArchitectureValidator(ArchitectureRules.standardRegistry());

        ValidationReport first = validator.validate(ButcherCraftArchitectureManifest.current());
        ValidationReport second = validator.validate(ButcherCraftArchitectureManifest.current());

        assertEquals(first, second);
        assertEquals(
                validator.rules().rules().stream().map(ValidationRule::id).toList(),
                first.results().stream().map(ValidationResult::ruleId).toList()
        );
    }

    @Test
    void componentRuleDetectsDuplicateIdsAndPackageRoots() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<ArchitectureComponent> components = new ArrayList<>(base.components());
        ArchitectureComponent first = components.getFirst();
        components.add(new ArchitectureComponent(first.id(), "Duplicate", first.packageRoot()));

        ValidationResult result = validate(
                ArchitectureRules.componentIntegrity(),
                ArchitectureValidationTestFixtures.withComponents(base, components)
        );

        assertFailedWith(result, "Duplicate component id", "Duplicate component package root");
    }

    @Test
    void ownershipRulesDetectMultipleUnknownMissingAndMismatchedOwners() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<OwnershipAssignment> assignments = new ArrayList<>(base.ownershipAssignments());
        OwnershipAssignment first = assignments.getFirst();
        assignments.add(new OwnershipAssignment(
                first.responsibilityId(),
                ArchitectureId.of("butchercraft:planning")
        ));
        assignments.add(new OwnershipAssignment(
                ArchitectureId.of("butchercraft:responsibility/unknown"),
                ArchitectureId.of("butchercraft:missing_owner")
        ));
        assignments.removeIf(assignment -> assignment.responsibilityId().value()
                .equals("butchercraft:responsibility/economic_mutations"));
        ValidationContext context = ArchitectureValidationTestFixtures.withOwnership(
                base,
                assignments,
                base.ownershipContracts()
        );

        ValidationResult singular = validate(ArchitectureRules.singularOwnership(), context);
        ValidationResult transactions = validate(
                ArchitectureRules.ownershipContracts(ValidationCategory.TRANSACTIONS),
                context
        );

        assertFailedWith(singular, "Multiple owners", "Unknown owner");
        assertFailedWith(transactions, "Missing owner");
    }

    @Test
    void ownershipContractDetectsWrongOwner() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<OwnershipAssignment> assignments = base.ownershipAssignments().stream()
                .map(assignment -> assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/planning_decisions")
                        ? new OwnershipAssignment(
                                assignment.responsibilityId(),
                                ArchitectureId.of("butchercraft:production")
                        )
                        : assignment)
                .toList();

        ValidationResult result = validate(
                ArchitectureRules.ownershipContracts(ValidationCategory.PLANNING),
                ArchitectureValidationTestFixtures.withOwnership(base, assignments, base.ownershipContracts())
        );

        assertFailedWith(result, "Ownership mismatch");
    }

    @Test
    void dependencyRulesDetectUnknownDuplicateForbiddenAndCyclicEdges() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<DependencyDescriptor> dependencies = new ArrayList<>(base.dependencies());
        dependencies.add(base.dependencies().getFirst());
        dependencies.add(new DependencyDescriptor(
                ArchitectureId.of("butchercraft:planning"),
                ArchitectureId.of("butchercraft:missing")
        ));
        dependencies.add(new DependencyDescriptor(
                ArchitectureId.of("butchercraft:inventory"),
                ArchitectureId.of("butchercraft:production")
        ));
        dependencies.add(new DependencyDescriptor(
                ArchitectureId.of("butchercraft:production"),
                ArchitectureId.of("butchercraft:planning")
        ));
        ValidationContext context = ArchitectureValidationTestFixtures.withDependencies(
                base,
                dependencies,
                base.dependencyConstraints()
        );

        assertFailedWith(validate(ArchitectureRules.dependencyIntegrity(), context),
                "Duplicate dependency", "Unknown dependency provider");
        assertFailedWith(validate(ArchitectureRules.forbiddenDependencies(), context),
                "Forbidden dependency");
        assertFailedWith(validate(ArchitectureRules.dependencyCycles(), context),
                "Dependency loop includes");
    }

    @Test
    void registryRulesDetectDuplicateMalformedUnorderedAndUnknownReferences() {
        RegistryDescriptor target = new RegistryDescriptor(
                "butchercraft:target",
                OrderingPolicy.CANONICAL_ID,
                List.of(RegistryEntryDescriptor.of("butchercraft:known"))
        );
        RegistryDescriptor malformed = new RegistryDescriptor(
                "Bad Registry",
                OrderingPolicy.CANONICAL_ID,
                List.of(
                        new RegistryEntryDescriptor(
                                "butchercraft:zeta",
                                0,
                                List.of(new ArchitectureReference("butchercraft:target", "butchercraft:missing"))
                        ),
                        RegistryEntryDescriptor.of("Bad Entry"),
                        RegistryEntryDescriptor.of("Bad Entry")
                )
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withRegistries(
                ArchitectureValidationTestFixtures.validContext(),
                List.of(target, malformed, target)
        );

        assertFailedWith(validate(ArchitectureRules.registryIdentity(), context),
                "Duplicate registry id", "Duplicate entry", "Non-canonical registry id", "Non-canonical entry id");
        assertFailedWith(validate(ArchitectureRules.registryOrdering(), context),
                "Registry is not in canonical id order");
        assertFailedWith(validate(ArchitectureRules.registryReferences(), context),
                "Unknown target entry");
    }

    @Test
    void explicitRegistryOrderingDetectsDuplicateNegativeAndOutOfOrderValues() {
        RegistryDescriptor registry = new RegistryDescriptor(
                "butchercraft:ordered",
                OrderingPolicy.EXPLICIT_ORDER,
                List.of(
                        new RegistryEntryDescriptor("butchercraft:b", 2, List.of()),
                        new RegistryEntryDescriptor("butchercraft:a", -1, List.of()),
                        new RegistryEntryDescriptor("butchercraft:c", 2, List.of())
                )
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withRegistries(
                ArchitectureValidationTestFixtures.validContext(),
                List.of(registry)
        );

        assertFailedWith(validate(ArchitectureRules.registryOrdering(), context),
                "Duplicate explicit order", "Negative explicit order", "Registry is not in explicit order");
    }

    @Test
    void persistenceRulesDetectIdentitySchemaAuthorityOrderingAndReferenceViolations() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        PersistenceDescriptor invalid = new PersistenceDescriptor(
                "Bad Persistence",
                "butchercraft/duplicate.json",
                ArchitectureId.of("butchercraft:missing_owner"),
                0,
                PersistenceDataKind.MIXED_AUTHORITY,
                OrderingPolicy.UNSPECIFIED,
                List.of(new ArchitectureReference("butchercraft:missing_registry", "butchercraft:missing"))
        );
        PersistenceDescriptor duplicate = new PersistenceDescriptor(
                "Bad Persistence",
                "butchercraft/duplicate.json",
                ArchitectureId.of("butchercraft:planning"),
                1,
                PersistenceDataKind.MUTABLE_RUNTIME,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withPersistence(
                base,
                List.of(invalid, duplicate)
        );

        assertFailedWith(validate(ArchitectureRules.persistenceIdentity(), context),
                "Duplicate persistence id", "Duplicate persistence path", "Non-canonical persistence id",
                "Unsupported schema version", "Unknown persistence owner");
        assertFailedWith(validate(ArchitectureRules.persistenceSeparation(), context),
                "Mixed immutable and mutable authority", "Unspecified persistence ordering");
        assertFailedWith(validate(ArchitectureRules.persistenceReferences(), context),
                "Unknown persisted reference");
    }

    @Test
    void schedulerRulesDetectDuplicatesGapsUnknownDependenciesAndLoops() {
        SchedulerDescriptor scheduler = new SchedulerDescriptor(
                "butchercraft:test_scheduler",
                100,
                List.of(
                        new SchedulerStageDescriptor("butchercraft:first", 100, List.of("butchercraft:third")),
                        new SchedulerStageDescriptor("butchercraft:second", 100, List.of("butchercraft:missing")),
                        new SchedulerStageDescriptor("butchercraft:third", 350, List.of("butchercraft:first")),
                        new SchedulerStageDescriptor("Bad Stage", -1, List.of())
                )
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withSchedulers(
                ArchitectureValidationTestFixtures.validContext(),
                List.of(scheduler, scheduler)
        );

        assertFailedWith(validate(ArchitectureRules.schedulerIdentity(), context),
                "Duplicate scheduler id", "Duplicate stage order", "Non-canonical stage id",
                "Non-positive stage order");
        assertFailedWith(validate(ArchitectureRules.schedulerOrdering(), context),
                "Stages are not stored in execution order", "Ordering gap");
        assertFailedWith(validate(ArchitectureRules.schedulerDependencies(), context),
                "Unknown stage dependency", "does not precede", "dependency loop");
    }

    @Test
    void simulationRuleDetectsMissingDuplicateAndUnsatisfiedDeclarations() {
        SimulationInvariantDescriptor failed = new SimulationInvariantDescriptor(
                ArchitectureId.of("butchercraft:invariant/replay"),
                SimulationInvariantType.REPLAY_COMPATIBILITY,
                false,
                "Replay failed"
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withSimulation(
                ArchitectureValidationTestFixtures.validContext(),
                List.of(failed, failed)
        );

        ValidationResult result = validate(ArchitectureRules.simulationInvariants(), context);

        assertFailedWith(result,
                "Duplicate simulation invariant", "Missing required simulation declaration",
                "Unsatisfied simulation invariant");
    }

    @Test
    void insertionOrderingIsAcceptedAsAnExplicitDeterministicPolicy() {
        RegistryDescriptor registry = new RegistryDescriptor(
                "butchercraft:insertion",
                OrderingPolicy.INSERTION,
                List.of(
                        RegistryEntryDescriptor.of("butchercraft:z"),
                        RegistryEntryDescriptor.of("butchercraft:a")
                )
        );

        ValidationResult result = validate(
                ArchitectureRules.registryOrdering(),
                ArchitectureValidationTestFixtures.withRegistries(
                        ArchitectureValidationTestFixtures.validContext(),
                        List.of(registry)
                )
        );

        assertEquals(ValidationStatus.PASSED, result.status());
    }

    @Test
    void dependencyConstraintsRemainDataDrivenAndAdditive() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        DependencyDescriptor observed = new DependencyDescriptor(
                ArchitectureId.of("butchercraft:planning"),
                ArchitectureId.of("butchercraft:goods")
        );
        DependencyConstraint newConstraint = new DependencyConstraint(
                observed.consumerId(),
                observed.providerId(),
                "Test extension"
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withDependencies(
                base,
                List.of(observed),
                List.of(newConstraint)
        );

        assertFalse(validate(ArchitectureRules.forbiddenDependencies(), context).isSuccessful());
    }

    private static ValidationResult validate(ValidationRule rule, ValidationContext context) {
        return new ArchitectureValidator(ValidationRuleRegistry.of(List.of(rule)))
                .validate(context)
                .results()
                .getFirst();
    }

    private static void assertFailedWith(ValidationResult result, String... fragments) {
        assertEquals(ValidationStatus.FAILED, result.status(), () -> "Expected failure: " + result);
        String joined = String.join("\n", result.details());
        for (String fragment : fragments) {
            assertTrue(joined.contains(fragment), () -> "Missing '" + fragment + "' in " + joined);
        }
    }
}
