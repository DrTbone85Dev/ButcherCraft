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
    void currentManifestRegistersEvidenceLifecycleFoundationAsPartialImplementation() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/evidence_classification_foundation")
                        && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/evidence_retention_policy_foundation")
                        && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/evidence_retention_decision_foundation")
                        && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/evidence_policy_ownership")
                        && contract.disposition()
                        == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/checkpoint_publication")
                        && contract.disposition()
                        == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
    }

    @Test
    void currentManifestRegistersCheckpointRecoveryFoundationAsPartialImplementation() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedFoundationContracts = List.of(
                "butchercraft:platform_contract/checkpoint_generation_identity_foundation",
                "butchercraft:platform_contract/checkpoint_owner_snapshot_metadata_foundation",
                "butchercraft:platform_contract/checkpoint_generation_manifest_foundation",
                "butchercraft:platform_contract/checkpoint_integrity_validation_foundation",
                "butchercraft:platform_contract/checkpoint_recovery_selection_foundation",
                "butchercraft:platform_contract/checkpoint_rollback_selection_foundation",
                "butchercraft:platform_contract/checkpoint_filesystem_store_foundation",
                "butchercraft:platform_contract/checkpoint_staged_generation_publication_foundation",
                "butchercraft:platform_contract/checkpoint_immutable_generation_publication_foundation",
                "butchercraft:platform_contract/checkpoint_head_publication_foundation",
                "butchercraft:platform_contract/checkpoint_filesystem_digest_validation_foundation",
                "butchercraft:platform_contract/checkpoint_filesystem_recovery_selection_foundation",
                "butchercraft:platform_contract/checkpoint_storage_artifact_classification_foundation",
                "butchercraft:platform_contract/checkpoint_live_clock_owner_snapshot_provider_foundation",
                "butchercraft:platform_contract/checkpoint_live_scheduler_owner_snapshot_provider_foundation",
                "butchercraft:platform_contract/checkpoint_owner_controlled_restoration_candidate_foundation",
                "butchercraft:platform_contract/checkpoint_clock_scheduler_cross_owner_validation_foundation",
                "butchercraft:platform_contract/checkpoint_coordinated_restoration_boundary_foundation",
                "butchercraft:platform_contract/world_identity_external_root_validation_foundation",
                "butchercraft:platform_contract/checkpoint_development_capture_invocation_foundation",
                "butchercraft:platform_contract/checkpoint_development_generation_inspection_foundation",
                "butchercraft:platform_contract/checkpoint_development_integrity_validation_foundation",
                "butchercraft:platform_contract/checkpoint_development_root_world_scoping_foundation",
                "butchercraft:platform_contract/checkpoint_development_controlled_restoration_harness_foundation",
                "butchercraft:platform_contract/checkpoint_development_live_restore_safety_gate"
        );

        for (String contractId : implementedFoundationContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/checkpoint_publication")
                        && contract.disposition()
                        == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/checkpoint_owner_snapshots")
                        && contract.disposition()
                        == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.platformContracts().stream()
                .anyMatch(contract -> contract.id().value()
                        .equals("butchercraft:platform_contract/platform_determinism_manifest")
                        && contract.disposition()
                        == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
    }

    @Test
    void currentManifestRegistersTransactionValidationBindingFoundationAsPartialImplementation() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedFoundationContracts = List.of(
                "butchercraft:platform_contract/transaction_proposal_identity_foundation",
                "butchercraft:platform_contract/inventory_freshness_identity_foundation",
                "butchercraft:platform_contract/transaction_validation_plan_identity_foundation",
                "butchercraft:platform_contract/transaction_validation_binding_foundation",
                "butchercraft:platform_contract/validation_consumption_authority_foundation",
                "butchercraft:platform_contract/transaction_result_evidence_foundation",
                "butchercraft:platform_contract/transaction_duplicate_conflict_foundation",
                "butchercraft:platform_contract/transaction_binding_validation_checks_foundation"
        );

        for (String contractId : implementedFoundationContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        List<String> implementedLiveContracts = List.of(
                "butchercraft:platform_contract/transaction_validation_binding",
                "butchercraft:platform_contract/transaction_consumption_authority",
                "butchercraft:platform_contract/serialized_transaction_owner_boundary",
                "butchercraft:platform_contract/transaction_live_duplicate_conflict_behavior",
                "butchercraft:platform_contract/transaction_live_result_evidence"
        );

        for (String contractId : implementedLiveContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
    }

    @Test
    void currentManifestRegistersGenericExecutionRuntimeFoundationAsPartialImplementation() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedExecutionContracts = List.of(
                "butchercraft:platform_contract/execution_authorization_evidence",
                "butchercraft:platform_contract/execution_private_authorization_consumption",
                "butchercraft:platform_contract/execution_lifecycle_runtime",
                "butchercraft:platform_contract/execution_handler_boundary",
                "butchercraft:platform_contract/execution_scheduler_handler_boundary",
                "butchercraft:platform_contract/execution_owner_result_evidence",
                "butchercraft:platform_contract/execution_duplicate_conflict_behavior",
                "butchercraft:platform_contract/execution_unknown_outcome_runtime",
                "butchercraft:platform_contract/execution_minimal_persistence",
                "butchercraft:platform_contract/execution_independent_of_allocation"
        );

        for (String contractId : implementedExecutionContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/execution_world")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.dependencies().stream().anyMatch(dependency ->
                dependency.consumerId().value().equals("butchercraft:execution")
                        && dependency.providerId().value().equals("butchercraft:simulation_scheduler")));
        assertTrue(context.persistenceDescriptors().stream().anyMatch(descriptor ->
                descriptor.id().equals("butchercraft:execution_operations")
                        && descriptor.ownerId().value().equals("butchercraft:execution")));
    }

    @Test
    void currentManifestRegistersGrinderGameplayPromotionAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedPromotionContracts = List.of(
                "butchercraft:platform_contract/grinder_promoted_gameplay_content",
                "butchercraft:platform_contract/grinder_six_promoted_processes",
                "butchercraft:platform_contract/grinder_deterministic_multi_process_resolution",
                "butchercraft:platform_contract/grinder_process_specific_execution_identity",
                "butchercraft:platform_contract/grinder_process_specific_owner_results",
                "butchercraft:platform_contract/grinder_recipe_catalog_gametest_coverage",
                "butchercraft:platform_contract/grinder_survival_obtainability",
                "butchercraft:platform_contract/grinder_player_visible_status_sync",
                "butchercraft:platform_contract/grinder_active_break_preservation"
        );

        for (String contractId : implementedPromotionContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
    }

    @Test
    void currentManifestRegistersProductionGrinderIntegrationAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedIntegrationContracts = List.of(
                "butchercraft:platform_contract/production_grinder_assignment_binding",
                "butchercraft:platform_contract/production_grinder_completion_evidence",
                "butchercraft:platform_contract/production_grinder_authority_boundary",
                "butchercraft:platform_contract/production_grinder_duplicate_safety",
                "butchercraft:platform_contract/production_grinder_persistence_references"
        );

        for (String contractId : implementedIntegrationContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
    }

    @Test
    void currentManifestRegistersPattyFormerAndProductionChainAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedPattyFormerContracts = List.of(
                "butchercraft:platform_contract/patty_former_gameplay_workstation",
                "butchercraft:platform_contract/patty_former_ground_beef_process",
                "butchercraft:platform_contract/patty_former_execution_handler",
                "butchercraft:platform_contract/patty_former_owner_result_publication",
                "butchercraft:platform_contract/patty_former_duplicate_safety",
                "butchercraft:platform_contract/production_two_step_workstation_chain",
                "butchercraft:platform_contract/production_manual_transfer_boundary",
                "butchercraft:platform_contract/production_chain_product_flow_validation",
                "butchercraft:platform_contract/production_chain_persistence_references",
                "butchercraft:platform_contract/patty_former_chain_gametest_coverage"
        );

        for (String contractId : implementedPattyFormerContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.ownershipAssignments().stream()
                .anyMatch(assignment -> assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/production_workstation_chain")
                        && assignment.ownerId().value().equals("butchercraft:production")));
        assertTrue(context.ownershipAssignments().stream()
                .anyMatch(assignment -> assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/production_product_flow_identity_validation")
                        && assignment.ownerId().value().equals("butchercraft:production")));
    }

    @Test
    void currentManifestRegistersWorldTimeAndBusinessCalendarFoundationAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> implementedWorldTimeContracts = List.of(
                "butchercraft:platform_contract/configurable_minecraft_day_length",
                "butchercraft:platform_contract/deterministic_scaled_day_time_accumulator",
                "butchercraft:platform_contract/business_calendar_day_time_derivation",
                "butchercraft:platform_contract/world_time_no_catch_up_rule",
                "butchercraft:platform_contract/world_time_dimension_policy",
                "butchercraft:platform_contract/world_time_client_display_synchronization",
                "butchercraft:platform_contract/world_time_diagnostics"
        );

        for (String contractId : implementedWorldTimeContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.ownerId().value().equals("butchercraft:simulation")
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/world_time")
                        && authority.ownerId().value().equals("butchercraft:simulation")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.persistenceDescriptors().stream().anyMatch(descriptor ->
                descriptor.id().equals("butchercraft:world_time_state")
                        && descriptor.ownerId().value().equals("butchercraft:simulation")
                        && descriptor.path().equals("butchercraft/world_time.json")));
        assertTrue(context.ownershipAssignments().stream()
                .anyMatch(assignment -> assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/scaled_day_time_advancement")
                        && assignment.ownerId().value().equals("butchercraft:simulation")));
        assertTrue(context.ownershipAssignments().stream()
                .anyMatch(assignment -> assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/business_calendar_derivation")
                        && assignment.ownerId().value().equals("butchercraft:simulation")));
    }

    @Test
    void currentManifestRegistersBusinessHoursShiftsAndDeadlinesAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> businessRuntimeContracts = List.of(
                "butchercraft:platform_contract/business_runtime_configurable_operating_hours",
                "butchercraft:platform_contract/business_runtime_open_closed_observation",
                "butchercraft:platform_contract/business_runtime_configurable_shift_definitions",
                "butchercraft:platform_contract/business_runtime_active_next_shift_observation",
                "butchercraft:platform_contract/business_runtime_identity_foundation",
                "butchercraft:platform_contract/business_runtime_time_jump_observation",
                "butchercraft:platform_contract/business_hours_shift_deadline_persistence",
                "butchercraft:platform_contract/business_runtime_diagnostics"
        );
        List<String> productionContracts = List.of(
                "butchercraft:platform_contract/production_deadline_identity",
                "butchercraft:platform_contract/production_deadline_status",
                "butchercraft:platform_contract/production_deadline_completion_timing",
                "butchercraft:platform_contract/production_order_deadline_display"
        );

        for (String contractId : businessRuntimeContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.ownerId().value().equals("butchercraft:business_runtime")
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        for (String contractId : productionContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.ownerId().value().equals("butchercraft:production")
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value()
                        .equals("butchercraft:runtime_authority/business_runtime_calendar")
                        && authority.ownerId().value().equals("butchercraft:business_runtime")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.persistenceDescriptors().stream().anyMatch(descriptor ->
                descriptor.id().equals("butchercraft:business_calendar_runtime")
                        && descriptor.ownerId().value().equals("butchercraft:business_runtime")
                        && descriptor.path().equals("butchercraft/business_calendar_runtime.json")));
        assertTrue(context.ownershipAssignments().stream().anyMatch(assignment ->
                assignment.responsibilityId().value().equals("butchercraft:responsibility/business_shift_observation")
                        && assignment.ownerId().value().equals("butchercraft:business_runtime")));
        assertTrue(context.ownershipAssignments().stream().anyMatch(assignment ->
                assignment.responsibilityId().value().equals("butchercraft:responsibility/production_deadline_status")
                        && assignment.ownerId().value().equals("butchercraft:production")));
        assertTrue(context.ownershipAssignments().stream().noneMatch(assignment ->
                assignment.ownerId().value().equals("butchercraft:business_runtime")
                        && assignment.responsibilityId().value()
                        .equals("butchercraft:responsibility/production_deadline_status")));
    }

    @Test
    void currentManifestRegistersEmployeeFoundationAsImplemented() {
        ValidationContext context = ArchitectureValidationTestFixtures.validContext();

        List<String> employeeContracts = List.of(
                "butchercraft:platform_contract/employee_identity_foundation",
                "butchercraft:platform_contract/employment_record_foundation",
                "butchercraft:platform_contract/employee_shift_presence_observation",
                "butchercraft:platform_contract/employee_entity_link_foundation",
                "butchercraft:platform_contract/employee_persistence_foundation",
                "butchercraft:platform_contract/employee_diagnostics_foundation",
                "butchercraft:platform_contract/employee_foundation_gametest_coverage"
        );

        for (String contractId : employeeContracts) {
            assertTrue(context.platformContracts().stream()
                    .anyMatch(contract -> contract.id().value().equals(contractId)
                            && contract.ownerId().value().equals("butchercraft:workforce")
                            && contract.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        }
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/workforce_world")
                        && authority.ownerId().value().equals("butchercraft:workforce")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.persistenceDescriptors().stream().anyMatch(descriptor ->
                descriptor.id().equals("butchercraft:employee_records")
                        && descriptor.ownerId().value().equals("butchercraft:workforce")
                        && descriptor.path().equals("butchercraft/employee_records.json")));
        assertTrue(context.dependencies().stream().anyMatch(dependency ->
                dependency.consumerId().value().equals("butchercraft:workforce")
                        && dependency.providerId().value().equals("butchercraft:business_runtime")));
        assertTrue(context.ownershipAssignments().stream().anyMatch(assignment ->
                assignment.responsibilityId().value().equals("butchercraft:responsibility/employee_identity")
                        && assignment.ownerId().value().equals("butchercraft:workforce")));
        assertTrue(context.ownershipAssignments().stream().anyMatch(assignment ->
                assignment.responsibilityId().value().equals("butchercraft:responsibility/employee_presence_observation")
                        && assignment.ownerId().value().equals("butchercraft:workforce")));
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
                effect.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.platformIdentities().stream().allMatch(identity ->
                identity.disposition() == ArchitectureValidationDisposition.DOCUMENTATION_ONLY));
        assertTrue(context.platformContracts().stream()
                .filter(contract -> !List.of(
                        "butchercraft:platform_contract/execution_independent_of_allocation",
                        "butchercraft:platform_contract/execution_authorization_evidence",
                        "butchercraft:platform_contract/execution_private_authorization_consumption",
                        "butchercraft:platform_contract/execution_lifecycle_runtime",
                        "butchercraft:platform_contract/execution_handler_boundary",
                        "butchercraft:platform_contract/execution_scheduler_handler_boundary",
                        "butchercraft:platform_contract/execution_owner_result_evidence",
                        "butchercraft:platform_contract/execution_duplicate_conflict_behavior",
                        "butchercraft:platform_contract/execution_unknown_outcome_runtime",
                        "butchercraft:platform_contract/execution_minimal_persistence",
                        "butchercraft:platform_contract/evidence_classification_foundation",
                        "butchercraft:platform_contract/evidence_retention_policy_foundation",
                        "butchercraft:platform_contract/evidence_retention_decision_foundation",
                        "butchercraft:platform_contract/checkpoint_generation_identity_foundation",
                        "butchercraft:platform_contract/checkpoint_owner_snapshot_metadata_foundation",
                        "butchercraft:platform_contract/checkpoint_generation_manifest_foundation",
                        "butchercraft:platform_contract/checkpoint_integrity_validation_foundation",
                        "butchercraft:platform_contract/checkpoint_recovery_selection_foundation",
                        "butchercraft:platform_contract/checkpoint_rollback_selection_foundation",
                        "butchercraft:platform_contract/checkpoint_filesystem_store_foundation",
                        "butchercraft:platform_contract/checkpoint_staged_generation_publication_foundation",
                        "butchercraft:platform_contract/checkpoint_immutable_generation_publication_foundation",
                        "butchercraft:platform_contract/checkpoint_head_publication_foundation",
                        "butchercraft:platform_contract/checkpoint_filesystem_digest_validation_foundation",
                        "butchercraft:platform_contract/checkpoint_filesystem_recovery_selection_foundation",
                        "butchercraft:platform_contract/checkpoint_storage_artifact_classification_foundation",
                        "butchercraft:platform_contract/checkpoint_live_clock_owner_snapshot_provider_foundation",
                        "butchercraft:platform_contract/checkpoint_live_scheduler_owner_snapshot_provider_foundation",
                        "butchercraft:platform_contract/checkpoint_owner_controlled_restoration_candidate_foundation",
                        "butchercraft:platform_contract/checkpoint_clock_scheduler_cross_owner_validation_foundation",
                        "butchercraft:platform_contract/checkpoint_coordinated_restoration_boundary_foundation",
                        "butchercraft:platform_contract/world_identity_external_root_validation_foundation",
                        "butchercraft:platform_contract/checkpoint_development_capture_invocation_foundation",
                        "butchercraft:platform_contract/checkpoint_development_generation_inspection_foundation",
                        "butchercraft:platform_contract/checkpoint_development_integrity_validation_foundation",
                        "butchercraft:platform_contract/checkpoint_development_root_world_scoping_foundation",
                        "butchercraft:platform_contract/checkpoint_development_controlled_restoration_harness_foundation",
                        "butchercraft:platform_contract/checkpoint_development_live_restore_safety_gate",
                        "butchercraft:platform_contract/transaction_proposal_identity_foundation",
                        "butchercraft:platform_contract/inventory_freshness_identity_foundation",
                        "butchercraft:platform_contract/transaction_validation_plan_identity_foundation",
                        "butchercraft:platform_contract/transaction_validation_binding_foundation",
                        "butchercraft:platform_contract/validation_consumption_authority_foundation",
                        "butchercraft:platform_contract/transaction_result_evidence_foundation",
                        "butchercraft:platform_contract/transaction_duplicate_conflict_foundation",
                        "butchercraft:platform_contract/transaction_binding_validation_checks_foundation",
                        "butchercraft:platform_contract/transaction_validation_binding",
                        "butchercraft:platform_contract/transaction_consumption_authority",
                        "butchercraft:platform_contract/serialized_transaction_owner_boundary",
                        "butchercraft:platform_contract/transaction_live_duplicate_conflict_behavior",
                        "butchercraft:platform_contract/transaction_live_result_evidence",
                        "butchercraft:platform_contract/planning_cadence",
                        "butchercraft:platform_contract/planning_live_periodic_cadence",
                        "butchercraft:platform_contract/planning_live_trigger_cadence",
                        "butchercraft:platform_contract/planning_live_no_burst_catch_up",
                        "butchercraft:platform_contract/planning_effect_classification_blocker",
                        "butchercraft:platform_contract/scheduler_runtime_authority",
                        "butchercraft:platform_contract/scheduler_observes_domain_results",
                        "butchercraft:platform_contract/scheduler_live_effect_enforcement",
                        "butchercraft:platform_contract/scheduler_invocation_identity_runtime",
                        "butchercraft:platform_contract/scheduler_effect_identity_runtime",
                        "butchercraft:platform_contract/scheduler_effect_retry_matrix",
                        "butchercraft:platform_contract/scheduler_unknown_outcome_runtime",
                        "butchercraft:platform_contract/scheduler_owner_result_observation",
                        "butchercraft:platform_contract/scheduler_parallel_reentrancy_prohibition",
                        "butchercraft:platform_contract/production_transaction_backed_scheduler_conformance",
                        "butchercraft:platform_contract/workstation_player_execution_slice",
                        "butchercraft:platform_contract/workstation_owner_result_publication",
                        "butchercraft:platform_contract/workstation_itemstack_mutation_boundary",
                        "butchercraft:platform_contract/execution_first_player_facing_handler",
                        "butchercraft:platform_contract/scheduler_dispatched_workstation_execution",
                        "butchercraft:platform_contract/grinder_gametest_registration_verification",
                        "butchercraft:platform_contract/grinder_gametest_placement_verification",
                        "butchercraft:platform_contract/grinder_gametest_end_to_end_execution",
                        "butchercraft:platform_contract/grinder_gametest_duplicate_safety",
                        "butchercraft:platform_contract/grinder_gametest_save_load_safety",
                        "butchercraft:platform_contract/grinder_gametest_uncertain_state_safety",
                        "butchercraft:platform_contract/grinder_promoted_gameplay_content",
                        "butchercraft:platform_contract/grinder_six_promoted_processes",
                        "butchercraft:platform_contract/grinder_deterministic_multi_process_resolution",
                        "butchercraft:platform_contract/grinder_process_specific_execution_identity",
                        "butchercraft:platform_contract/grinder_process_specific_owner_results",
                        "butchercraft:platform_contract/grinder_recipe_catalog_gametest_coverage",
                        "butchercraft:platform_contract/grinder_survival_obtainability",
                        "butchercraft:platform_contract/grinder_player_visible_status_sync",
                        "butchercraft:platform_contract/grinder_active_break_preservation",
                        "butchercraft:platform_contract/production_grinder_assignment_binding",
                        "butchercraft:platform_contract/production_grinder_completion_evidence",
                        "butchercraft:platform_contract/production_grinder_authority_boundary",
                        "butchercraft:platform_contract/production_grinder_duplicate_safety",
                        "butchercraft:platform_contract/production_grinder_persistence_references",
                        "butchercraft:platform_contract/patty_former_gameplay_workstation",
                        "butchercraft:platform_contract/patty_former_ground_beef_process",
                        "butchercraft:platform_contract/patty_former_execution_handler",
                        "butchercraft:platform_contract/patty_former_owner_result_publication",
                        "butchercraft:platform_contract/patty_former_duplicate_safety",
                        "butchercraft:platform_contract/production_two_step_workstation_chain",
                        "butchercraft:platform_contract/production_manual_transfer_boundary",
                        "butchercraft:platform_contract/production_chain_product_flow_validation",
                        "butchercraft:platform_contract/production_chain_persistence_references",
                        "butchercraft:platform_contract/patty_former_chain_gametest_coverage",
                        "butchercraft:platform_contract/production_order_beef_patties_run_creation",
                        "butchercraft:platform_contract/production_order_fixed_two_step_template",
                        "butchercraft:platform_contract/production_order_workstation_assignment",
                        "butchercraft:platform_contract/production_order_read_only_progress_presentation",
                        "butchercraft:platform_contract/production_order_manual_transfer_guidance",
                        "butchercraft:platform_contract/production_order_failure_guidance",
                        "butchercraft:platform_contract/production_order_gametest_coverage",
                        "butchercraft:platform_contract/configurable_minecraft_day_length",
                        "butchercraft:platform_contract/deterministic_scaled_day_time_accumulator",
                        "butchercraft:platform_contract/business_calendar_day_time_derivation",
                        "butchercraft:platform_contract/world_time_no_catch_up_rule",
                        "butchercraft:platform_contract/world_time_dimension_policy",
                        "butchercraft:platform_contract/world_time_client_display_synchronization",
                        "butchercraft:platform_contract/world_time_diagnostics",
                        "butchercraft:platform_contract/business_runtime_configurable_operating_hours",
                        "butchercraft:platform_contract/business_runtime_open_closed_observation",
                        "butchercraft:platform_contract/business_runtime_configurable_shift_definitions",
                        "butchercraft:platform_contract/business_runtime_active_next_shift_observation",
                        "butchercraft:platform_contract/business_runtime_identity_foundation",
                        "butchercraft:platform_contract/business_runtime_time_jump_observation",
                        "butchercraft:platform_contract/production_deadline_identity",
                        "butchercraft:platform_contract/production_deadline_status",
                        "butchercraft:platform_contract/production_deadline_completion_timing",
                        "butchercraft:platform_contract/production_order_deadline_display",
                        "butchercraft:platform_contract/business_hours_shift_deadline_persistence",
                        "butchercraft:platform_contract/business_runtime_diagnostics",
                        "butchercraft:platform_contract/employee_identity_foundation",
                        "butchercraft:platform_contract/employment_record_foundation",
                        "butchercraft:platform_contract/employee_shift_presence_observation",
                        "butchercraft:platform_contract/employee_entity_link_foundation",
                        "butchercraft:platform_contract/employee_persistence_foundation",
                        "butchercraft:platform_contract/employee_diagnostics_foundation",
                        "butchercraft:platform_contract/employee_foundation_gametest_coverage"
                ).contains(contract.id().value()))
                .allMatch(contract ->
                        contract.disposition() == ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED));
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/scheduler_world")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/planning_world")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
        assertTrue(context.runtimeAuthorities().stream()
                .anyMatch(authority -> authority.id().value().equals("butchercraft:runtime_authority/execution_world")
                        && authority.disposition() == ArchitectureValidationDisposition.ENFORCED_NOW));
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
