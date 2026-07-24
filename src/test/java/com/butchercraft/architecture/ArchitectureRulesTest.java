package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureComponent;
import com.butchercraft.architecture.validation.ArchitectureDocumentDescriptor;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureReference;
import com.butchercraft.architecture.validation.ArchitectureRules;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.ArchitectureValidationDisposition;
import com.butchercraft.architecture.validation.DependencyConstraint;
import com.butchercraft.architecture.validation.DependencyDescriptor;
import com.butchercraft.architecture.validation.OrderingPolicy;
import com.butchercraft.architecture.validation.OwnershipAssignment;
import com.butchercraft.architecture.validation.PersistenceDataKind;
import com.butchercraft.architecture.validation.PersistenceDescriptor;
import com.butchercraft.architecture.validation.PlatformContractDescriptor;
import com.butchercraft.architecture.validation.PlatformIdentityDescriptor;
import com.butchercraft.architecture.validation.PlatformIdentityKind;
import com.butchercraft.architecture.validation.RegistryDescriptor;
import com.butchercraft.architecture.validation.RegistryEntryDescriptor;
import com.butchercraft.architecture.validation.RuntimeAuthorityDescriptor;
import com.butchercraft.architecture.validation.SchedulerDescriptor;
import com.butchercraft.architecture.validation.SchedulerEffectDeclaration;
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
        assertEquals(26, report.summary().ruleCount());
        assertEquals(26, report.summary().passedRules());
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
    void platformDocumentRuleDetectsMissingDuplicateAndEnvironmentSpecificDescriptors() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        ArchitectureDocumentDescriptor first = base.architectureDocuments().getFirst();
        List<ArchitectureDocumentDescriptor> documents = new ArrayList<>(base.architectureDocuments());
        documents.add(new ArchitectureDocumentDescriptor(
                first.id(),
                "C:/absolute/doc.md",
                first.status(),
                first.revision(),
                first.disposition()
        ));

        ValidationResult duplicate = validate(
                ArchitectureRules.architectureDocuments(),
                ArchitectureValidationTestFixtures.withArchitectureDocuments(base, documents)
        );
        ValidationResult missing = validate(
                ArchitectureRules.architectureDocuments(),
                ArchitectureValidationTestFixtures.withArchitectureDocuments(base, List.of())
        );

        assertFailedWith(duplicate, "Duplicate architecture document id",
                "Architecture document path must be repository-relative");
        assertFailedWith(missing, "No architecture document descriptors");
    }

    @Test
    void currentManifestRegistersRatifiedPlatformDocumentsAndRfc0023Draft2() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/platform_canonicalization_addendum")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/evidence_lifecycle_adr")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/checkpoint_recovery_adr")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/transaction_validation_authority_adr")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/planning_cadence_adr")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value()
                        .equals("butchercraft:document/scheduler_effects_authority_adr")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value().equals("butchercraft:document/rfc_0022")));
        assertTrue(context.architectureDocuments().stream()
                .anyMatch(document -> document.id().value().equals("butchercraft:document/rfc_0023")
                        && document.revision().equals("Draft 2")));
    }

    @Test
    void platformIdentityRuleRequiresEveryCanonicalIdentityKindExactlyOnce() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<PlatformIdentityDescriptor> identities = new ArrayList<>(base.platformIdentities());
        PlatformIdentityDescriptor entity = identities.stream()
                .filter(identity -> identity.kind() == PlatformIdentityKind.ENTITY)
                .findFirst()
                .orElseThrow();
        identities.add(new PlatformIdentityDescriptor(
                ArchitectureId.of("butchercraft:identity/entity_duplicate"),
                entity.kind(),
                entity.disposition(),
                entity.source(),
                entity.description()
        ));
        identities.removeIf(identity -> identity.kind() == PlatformIdentityKind.CONFIGURATION);

        ValidationResult result = validate(
                ArchitectureRules.platformIdentityDeclarations(),
                ArchitectureValidationTestFixtures.withPlatformIdentities(base, identities)
        );

        assertFailedWith(result, "Duplicate platform identity kind: ENTITY",
                "Missing platform identity kind: CONFIGURATION");
    }

    @Test
    void platformContractRuleDetectsDuplicateDeclarationsAndUnknownOwners() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<PlatformContractDescriptor> contracts = new ArrayList<>(base.platformContracts());
        PlatformContractDescriptor first = contracts.getFirst();
        contracts.add(first);
        contracts.add(new PlatformContractDescriptor(
                ArchitectureId.of("butchercraft:platform_contract/missing_owner"),
                ValidationCategory.PLATFORM,
                ArchitectureId.of("butchercraft:missing_owner"),
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "test",
                "test contract"
        ));

        ValidationResult result = validate(
                ArchitectureRules.platformContractDeclarations(),
                ArchitectureValidationTestFixtures.withPlatformContracts(base, contracts)
        );

        assertFailedWith(result, "Duplicate platform contract id", "Unknown platform contract owner");
    }

    @Test
    void runtimeAuthorityRuleDetectsDuplicateScopesAndUnknownOwners() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<RuntimeAuthorityDescriptor> authorities = new ArrayList<>(base.runtimeAuthorities());
        RuntimeAuthorityDescriptor first = authorities.getFirst();
        authorities.add(new RuntimeAuthorityDescriptor(
                ArchitectureId.of("butchercraft:runtime_authority/duplicate_scope"),
                first.ownerId(),
                first.scopeId(),
                first.disposition(),
                first.source(),
                first.description()
        ));
        authorities.add(new RuntimeAuthorityDescriptor(
                ArchitectureId.of("butchercraft:runtime_authority/missing_owner"),
                ArchitectureId.of("butchercraft:missing_owner"),
                first.scopeId(),
                first.disposition(),
                "test",
                "missing owner"
        ));

        ValidationResult result = validate(
                ArchitectureRules.runtimeAuthorityDeclarations(),
                ArchitectureValidationTestFixtures.withRuntimeAuthorities(base, authorities)
        );

        assertFailedWith(result, "Duplicate runtime authority scope", "Unknown runtime authority owner");
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
    void executionToAllocationDependencyIsForbiddenByRfc0023Draft2() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<DependencyDescriptor> dependencies = new ArrayList<>(base.dependencies());
        dependencies.add(new DependencyDescriptor(
                ArchitectureId.of("butchercraft:execution"),
                ArchitectureId.of("butchercraft:allocation")
        ));

        ValidationResult result = validate(
                ArchitectureRules.forbiddenDependencies(),
                ArchitectureValidationTestFixtures.withDependencies(base, dependencies, base.dependencyConstraints())
        );

        assertFailedWith(result, "butchercraft:execution->butchercraft:allocation");
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
    void schedulerEffectRuleDetectsUnknownDuplicateMissingAndWrongOwnerDeclarations() {
        ValidationContext base = ArchitectureValidationTestFixtures.validContext();
        List<SchedulerEffectDeclaration> effects = new ArrayList<>(base.schedulerEffects());
        SchedulerEffectDeclaration first = effects.getFirst();
        effects.removeIf(effect -> effect.effectKind().equals("NON_REPEATABLE"));
        effects.add(new SchedulerEffectDeclaration(
                ArchitectureId.of("butchercraft:scheduler_effect/duplicate_kind"),
                first.effectKind(),
                ArchitectureId.of("butchercraft:planning"),
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "test",
                "wrong owner"
        ));
        effects.add(new SchedulerEffectDeclaration(
                ArchitectureId.of("butchercraft:scheduler_effect/unknown"),
                "unknown_effect",
                ArchitectureId.of("butchercraft:simulation_scheduler"),
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "test",
                "unknown effect"
        ));

        ValidationResult result = validate(
                ArchitectureRules.schedulerEffectDeclarations(),
                ArchitectureValidationTestFixtures.withSchedulerEffects(base, effects)
        );

        assertFailedWith(result, "Duplicate Scheduler effect kind", "Unknown Scheduler effect kind",
                "Scheduler effect owned by non-Scheduler component",
                "Missing Scheduler effect declaration: NON_REPEATABLE");
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

    @Test
    void evidenceAndCheckpointContractsDoNotTransferSubsystemFactOwnership() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        assertTrue(context.platformContracts().stream().anyMatch(contract -> contract.id().value()
                .equals("butchercraft:platform_contract/evidence_not_fact_owner")
                && contract.ownerId().value().equals("butchercraft:evidence_lifecycle")
                && contract.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.platformContracts().stream().anyMatch(contract -> contract.id().value()
                .equals("butchercraft:platform_contract/checkpoint_owner_snapshots")
                && contract.ownerId().value().equals("butchercraft:checkpoint_recovery")
                && contract.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.ownershipAssignments().stream().noneMatch(assignment ->
                assignment.ownerId().value().equals("butchercraft:evidence_lifecycle")
                        && assignment.responsibilityId().value().equals(
                                "butchercraft:responsibility/economic_mutations"
                        )));
        assertTrue(context.ownershipAssignments().stream().noneMatch(assignment ->
                assignment.ownerId().value().equals("butchercraft:checkpoint_recovery")
                        && assignment.responsibilityId().value().equals(
                                "butchercraft:responsibility/inventory_quantities"
                        )));
    }

    @Test
    void implementationGatedPlatformDeclarationsAreNotReportedAsImplementedGuarantees() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        assertTrue(context.schedulerEffects().stream().allMatch(effect ->
                effect.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.platformIdentities().stream().allMatch(identity ->
                identity.disposition() == ArchitectureValidationDisposition.DOCUMENTATION_ONLY));
        assertTrue(context.platformContracts().stream()
                .filter(contract -> !contract.id().value()
                        .equals("butchercraft:platform_contract/execution_independent_of_allocation"))
                .allMatch(contract ->
                        contract.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.runtimeAuthorities().stream()
                .filter(authority -> authority.ownerId().value().equals("butchercraft:execution"))
                .allMatch(authority ->
                        authority.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
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
