package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureComponent;
import com.butchercraft.architecture.validation.ArchitectureDocumentDescriptor;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureReference;
import com.butchercraft.architecture.validation.ArchitectureValidationDisposition;
import com.butchercraft.architecture.validation.DependencyConstraint;
import com.butchercraft.architecture.validation.DependencyDescriptor;
import com.butchercraft.architecture.validation.OrderingPolicy;
import com.butchercraft.architecture.validation.OwnershipAssignment;
import com.butchercraft.architecture.validation.OwnershipContract;
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
import com.butchercraft.architecture.validation.SchedulerEffectKind;
import com.butchercraft.architecture.validation.SchedulerStageDescriptor;
import com.butchercraft.architecture.validation.SimulationInvariantDescriptor;
import com.butchercraft.architecture.validation.SimulationInvariantType;
import com.butchercraft.architecture.validation.ValidationCategory;
import com.butchercraft.architecture.validation.ValidationContext;
import com.butchercraft.architecture.validation.ValidationContextBuilder;
import com.butchercraft.world.execution.ExecutionSchema;
import com.butchercraft.world.execution.ExecutionWorkTypes;
import com.butchercraft.world.planning.EconomicPlanningWorkHandler;
import com.butchercraft.world.production.ProductionSchema;
import com.butchercraft.world.production.scheduler.ProductionWorkTypes;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.SimulationStageDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ButcherCraftArchitectureManifest {
    public static final ArchitectureId CONTEXT_ID = id("butchercraft:current_architecture");

    private static final ArchitectureId PLATFORM_ARCHITECTURE = id("butchercraft:platform_architecture");
    private static final ArchitectureId WORLD_IDENTITY = id("butchercraft:world_identity");
    private static final ArchitectureId SIMULATION = id("butchercraft:simulation");
    private static final ArchitectureId BUSINESS_RUNTIME = id("butchercraft:business_runtime");
    private static final ArchitectureId WORKFORCE = id("butchercraft:workforce");
    private static final ArchitectureId GOODS = id("butchercraft:goods");
    private static final ArchitectureId ACTORS = id("butchercraft:economic_actors");
    private static final ArchitectureId INVENTORY = id("butchercraft:inventory");
    private static final ArchitectureId TRANSACTIONS = id("butchercraft:transactions");
    private static final ArchitectureId ORDERS = id("butchercraft:orders_and_contracts");
    private static final ArchitectureId SCHEDULER = id("butchercraft:simulation_scheduler");
    private static final ArchitectureId PRODUCTION = id("butchercraft:production");
    private static final ArchitectureId PLANNING = id("butchercraft:planning");
    private static final ArchitectureId EVIDENCE_LIFECYCLE = id("butchercraft:evidence_lifecycle");
    private static final ArchitectureId CHECKPOINT_RECOVERY = id("butchercraft:checkpoint_recovery");
    private static final ArchitectureId ALLOCATION = id("butchercraft:allocation");
    private static final ArchitectureId EXECUTION = id("butchercraft:execution");
    private static final ArchitectureId WORKSTATION = id("butchercraft:workstation");
    private static final ArchitectureId RESOURCE_AUTHORITIES =
            id("butchercraft:resource_authorities");
    private static final ArchitectureId WORLD_SCOPE = id("butchercraft:scope/world");

    private static final String STAGE_REGISTRY_ID = "butchercraft:simulation_stages";
    private static final String WORK_TYPE_REGISTRY_ID = "butchercraft:simulation_work_types";
    private static final String ALLOCATION_DEFINITION_REGISTRY_ID =
            "butchercraft:allocation_definitions";
    private static final String ALLOCATION_RUNTIME_REGISTRY_ID =
            "butchercraft:allocation_runtime";
    private static final String ALLOCATION_REPORT_REGISTRY_ID =
            "butchercraft:allocation_reports";
    private static final String ALLOCATION_TRACE_REGISTRY_ID =
            "butchercraft:allocation_cycle_traces";
    private static final String ALLOCATION_PROVIDER_REGISTRY_ID =
            "butchercraft:allocation_providers";

    private ButcherCraftArchitectureManifest() {
    }

    public static ValidationContext current() {
        ValidationContextBuilder builder = ValidationContext.builder(CONTEXT_ID);
        addComponents(builder);
        addArchitectureDocuments(builder);
        addPlatformIdentities(builder);
        addPlatformContracts(builder);
        addRuntimeAuthorities(builder);
        addOwnership(builder);
        addDependencies(builder);
        addRegistries(builder);
        addPersistence(builder);
        addSchedulerEffects(builder);
        addScheduler(builder);
        addSimulationInvariants(builder);
        return builder.build();
    }

    private static void addComponents(ValidationContextBuilder builder) {
        builder.component(component(
                PLATFORM_ARCHITECTURE,
                "Platform Architecture",
                "com.butchercraft.architecture.platform"
        ));
        builder.component(component(WORLD_IDENTITY, "World Identity", "com.butchercraft.world.identity"));
        builder.component(component(SIMULATION, "Simulation Clock", "com.butchercraft.world.simulation"));
        builder.component(component(BUSINESS_RUNTIME, "Business Runtime", "com.butchercraft.world.business.runtime"));
        builder.component(component(WORKFORCE, "Workforce", "com.butchercraft.world.workforce"));
        builder.component(component(GOODS, "Economic Goods", "com.butchercraft.world.goods"));
        builder.component(component(ACTORS, "Economic Actors", "com.butchercraft.world.economy.actor"));
        builder.component(component(INVENTORY, "Economic Inventory", "com.butchercraft.world.inventory"));
        builder.component(component(TRANSACTIONS, "Economic Transactions", "com.butchercraft.world.transaction"));
        builder.component(component(ORDERS, "Orders And Contracts", "com.butchercraft.world.economy.order"));
        builder.component(component(SCHEDULER, "Simulation Scheduler", "com.butchercraft.world.simulation.scheduler"));
        builder.component(component(PRODUCTION, "Production", "com.butchercraft.world.production"));
        builder.component(component(PLANNING, "Economic Planning", "com.butchercraft.world.planning"));
        builder.component(component(EVIDENCE_LIFECYCLE, "Evidence Lifecycle", "com.butchercraft.world.evidence"));
        builder.component(component(
                CHECKPOINT_RECOVERY,
                "Checkpoint Recovery",
                "com.butchercraft.world.checkpoint"
        ));
        builder.component(component(ALLOCATION, "Resource Allocation", "com.butchercraft.world.allocation"));
        builder.component(component(EXECUTION, "Execution", "com.butchercraft.world.execution"));
        builder.component(component(WORKSTATION, "Workstations", "com.butchercraft.workstation"));
        builder.component(component(
                RESOURCE_AUTHORITIES,
                "External Resource Authorities",
                "external.resource.authorities"
        ));
    }

    private static void addArchitectureDocuments(ValidationContextBuilder builder) {
        document(builder, "butchercraft:document/platform_canonicalization_addendum",
                "docs/adr/ADR-PLATFORM-CANONICALIZATION-ADDENDUM.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_IMPLEMENTATION_NOT_AUTHORIZED",
                "AH-1",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/evidence_lifecycle_adr",
                "docs/adr/ADR-PROPOSED-EVIDENCE-LIFECYCLE.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_IMPLEMENTATION_NOT_AUTHORIZED",
                "AH-1-ADR-01",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/checkpoint_recovery_adr",
                "docs/adr/ADR-PROPOSED-CHECKPOINT-RECOVERY.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_IMPLEMENTATION_NOT_AUTHORIZED",
                "AH-1-ADR-02",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/transaction_validation_authority_adr",
                "docs/adr/ADR-PROPOSED-TRANSACTION-VALIDATION-AUTHORITY.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_IMPLEMENTATION_NOT_AUTHORIZED",
                "AH-1-ADR-03",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/planning_cadence_adr",
                "docs/adr/ADR-PROPOSED-PLANNING-CADENCE.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_CORE_CADENCE_IMPLEMENTED",
                "AH-1-ADR-04 IM-010",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/scheduler_effects_authority_adr",
                "docs/adr/ADR-PROPOSED-SCHEDULER-EFFECTS-AUTHORITY.md",
                "RATIFIED_ARCHITECTURAL_DIRECTION_IMPLEMENTATION_NOT_AUTHORIZED",
                "AH-1-ADR-05",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/rfc_0022",
                "docs/RFC-0022_RESOURCE_ALLOCATION_ENGINE.md",
                "ACCEPTED_M22A_M22D_IMPLEMENTED_M22E_M22F_GATED",
                "Revision 2",
                ArchitectureValidationDisposition.ENFORCED_NOW);
        document(builder, "butchercraft:document/rfc_0023",
                "docs/RFC-0023_DETERMINISTIC_EXECUTION_ENGINE.md",
                "ARCHITECTURE_SPECIFICATION_IMPLEMENTATION_NOT_AUTHORIZED",
                "Draft 2",
                ArchitectureValidationDisposition.ENFORCED_NOW);
    }

    private static void addPlatformIdentities(ValidationContextBuilder builder) {
        identity(builder, "butchercraft:identity/entity", PlatformIdentityKind.ENTITY,
                "Platform Canonicalization Addendum",
                "Durable entity, definition, runtime record, or external reference identity");
        identity(builder, "butchercraft:identity/content", PlatformIdentityKind.CONTENT,
                "Platform Canonicalization Addendum",
                "Digest or structural identity proving exact canonical content equality");
        identity(builder, "butchercraft:identity/freshness", PlatformIdentityKind.FRESHNESS,
                "Platform Canonicalization Addendum",
                "Source-owned identity of all authoritative state examined by a consumer");
        identity(builder, "butchercraft:identity/invocation", PlatformIdentityKind.INVOCATION,
                "Platform Canonicalization Addendum",
                "Deterministic identity of one bounded invocation attempt");
        identity(builder, "butchercraft:identity/generation", PlatformIdentityKind.GENERATION,
                "Platform Canonicalization Addendum",
                "Committed checkpoint lineage position identity");
        identity(builder, "butchercraft:identity/evidence", PlatformIdentityKind.EVIDENCE,
                "Platform Canonicalization Addendum",
                "Stable identity of immutable evidence independent of storage location");
        identity(builder, "butchercraft:identity/configuration", PlatformIdentityKind.CONFIGURATION,
                "Platform Canonicalization Addendum",
                "Replay-relevant configuration, schema, policy, registry, and migration identity");
    }

    private static void addPlatformContracts(ValidationContextBuilder builder) {
        platformContract(builder, "butchercraft:platform_contract/evidence_policy_ownership",
                ValidationCategory.OWNERSHIP, EVIDENCE_LIFECYCLE,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "ADR-01 Evidence Lifecycle",
                "Evidence Lifecycle owns classification, retention, archival, compaction records, "
                        + "integrity verification, and query policy");
        platformContract(builder, "butchercraft:platform_contract/evidence_classification_foundation",
                ValidationCategory.GENERAL, EVIDENCE_LIFECYCLE,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-002 Evidence Lifecycle Foundation",
                "Evidence Lifecycle provides canonical pure-Java evidence classification primitives");
        platformContract(builder, "butchercraft:platform_contract/evidence_retention_policy_foundation",
                ValidationCategory.GENERAL, EVIDENCE_LIFECYCLE,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-002 Evidence Lifecycle Foundation",
                "Evidence Lifecycle provides versioned explicit retention-policy input primitives");
        platformContract(builder, "butchercraft:platform_contract/evidence_retention_decision_foundation",
                ValidationCategory.GENERAL, EVIDENCE_LIFECYCLE,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-002 Evidence Lifecycle Foundation",
                "Evidence Lifecycle provides deterministic retention decisions and typed lifecycle failures");
        platformContract(builder, "butchercraft:platform_contract/evidence_not_fact_owner",
                ValidationCategory.OWNERSHIP, EVIDENCE_LIFECYCLE,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "ADR-01 Evidence Lifecycle",
                "Evidence policy does not transfer source facts or runtime state away from producing subsystems");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_publication",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "ADR-02 Checkpoint Recovery",
                "Checkpoint Recovery owns generation identity, committed-generation selection, rollback, "
                        + "atomic checkpoint visibility, and storage-artifact quarantine");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_owner_snapshots",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "ADR-02 Checkpoint Recovery",
                "Checkpoint Recovery coordinates owner snapshots but each owner retains snapshot content authority");
        platformContract(builder, "butchercraft:platform_contract/platform_determinism_manifest",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "Platform Canonicalization Addendum and ADR-02 Checkpoint Recovery",
                "Checkpoint Recovery publishes the Platform Determinism Manifest while each source owns entries");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_generation_identity_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery provides canonical pure-Java checkpoint generation identity primitives");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_owner_snapshot_metadata_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery describes owner snapshot metadata without owning owner snapshot content");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_generation_manifest_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery provides deterministic generation manifest and head-record metadata");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_integrity_validation_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery validates generation metadata, predecessor chains, heads, and required owners");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_recovery_selection_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery deterministically selects the latest valid committed generation from explicit inputs");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_rollback_selection_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-003 Checkpoint Recovery Foundation",
                "Checkpoint Recovery validates explicit rollback intent and selects committed rollback targets");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_filesystem_store_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery provides an explicit-root filesystem checkpoint store foundation");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_staged_generation_publication_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery writes owner payloads and manifests through a non-authoritative staging directory");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_immutable_generation_publication_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery publishes immutable final generation directories and rejects identity conflicts");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_head_publication_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery publishes dual-slot checkpoint head records with canonical head digests");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_filesystem_digest_validation_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery validates owner payload, owner manifest, generation manifest, and head digests");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_filesystem_recovery_selection_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery scans filesystem inputs deterministically and selects the last valid committed generation");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_storage_artifact_classification_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-005 Minimal Filesystem Checkpoint Publication",
                "Checkpoint Recovery classifies incomplete, corrupt, invalid, and uncommitted storage artifacts");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_live_clock_owner_snapshot_provider_foundation",
                ValidationCategory.PERSISTENCE, SIMULATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "Simulation Clock provides owner-controlled checkpoint snapshot capture and restoration foundation");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_live_scheduler_owner_snapshot_provider_foundation",
                ValidationCategory.PERSISTENCE, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "Simulation Scheduler provides owner-controlled checkpoint snapshot capture and restoration foundation");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_owner_controlled_restoration_candidate_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "Checkpoint Recovery coordinates owner-prepared restoration candidates without parsing owner payloads");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_clock_scheduler_cross_owner_validation_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "Checkpoint Recovery validates Clock and Scheduler snapshot relationships through owner metadata");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_coordinated_restoration_boundary_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "Checkpoint Recovery prepares all required owners before coordinated restoration publication and owner-supplied rollback");
        platformContract(builder, "butchercraft:platform_contract/world_identity_external_root_validation_foundation",
                ValidationCategory.PERSISTENCE, WORLD_IDENTITY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-006 Minimal Live Owner Snapshot Integration",
                "World Identity provides deterministic external-root identity and digest references for checkpoints");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_capture_invocation_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development-only invocation can capture Clock and Scheduler snapshots through existing checkpoint APIs");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_generation_inspection_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development-only invocation can list and inspect committed checkpoint generations without exposing payloads");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_integrity_validation_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development-only invocation can validate heads, manifests, owner payload digests, predecessor chains, "
                        + "World Identity, and Platform Determinism Manifest references");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_root_world_scoping_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development checkpoint roots are normalized, world-scoped, and separate from normal save files");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_controlled_restoration_harness_foundation",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development harness can prove coordinated Clock and Scheduler restoration without making live-world restore automatic");
        platformContract(builder, "butchercraft:platform_contract/checkpoint_development_live_restore_safety_gate",
                ValidationCategory.PERSISTENCE, CHECKPOINT_RECOVERY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-007 Controlled Development Checkpoint Invocation",
                "Development command surface rejects loaded-world restoration until a safe runtime boundary is authorized");
        platformContract(builder, "butchercraft:platform_contract/transaction_proposal_identity_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide a canonical Proposal Identity distinct from Transaction identity");
        platformContract(builder, "butchercraft:platform_contract/inventory_freshness_identity_foundation",
                ValidationCategory.TRANSACTIONS, INVENTORY,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Inventory provides source-owned Inventory Freshness Identity primitives without a global revision");
        platformContract(builder, "butchercraft:platform_contract/transaction_validation_plan_identity_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide canonical immutable Validation Plan Identity primitives");
        platformContract(builder, "butchercraft:platform_contract/transaction_validation_binding_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions bind Proposal Identity, Inventory Freshness Identity, Validation Plan Identity, "
                        + "and explicit validation inputs as pure foundation data");
        platformContract(builder, "butchercraft:platform_contract/validation_consumption_authority_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide private single-use Validation Consumption Authority primitives");
        platformContract(builder, "butchercraft:platform_contract/transaction_result_evidence_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide authoritative result evidence binding validation identities and terminal result");
        platformContract(builder, "butchercraft:platform_contract/transaction_duplicate_conflict_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide duplicate observation and conflicting proposal classification primitives");
        platformContract(builder, "butchercraft:platform_contract/transaction_binding_validation_checks_foundation",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-004 Transaction Validation Binding Foundation",
                "Transactions provide deterministic typed validation checks for binding and evidence mismatches");
        platformContract(builder, "butchercraft:platform_contract/transaction_validation_binding",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-008 Live Transaction Validation Binding Integration",
                "Live accepted Transaction validation binds the Proposal Identity, Inventory Freshness Identity, "
                        + "and Validation Plan Identity");
        platformContract(builder, "butchercraft:platform_contract/transaction_consumption_authority",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-008 Live Transaction Validation Binding Integration",
                "Live Validation Consumption Authority is private, single-use, runtime-only, and Transaction-owned");
        platformContract(builder, "butchercraft:platform_contract/serialized_transaction_owner_boundary",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-008 Live Transaction Validation Binding Integration",
                "Validation, freshness check, authority consumption, and mutation occur within a serialized "
                        + "Transaction-owner boundary");
        platformContract(builder, "butchercraft:platform_contract/transaction_live_duplicate_conflict_behavior",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-008 Live Transaction Validation Binding Integration",
                "Live duplicate observations and conflicting same-id proposals are classified before mutation");
        platformContract(builder, "butchercraft:platform_contract/transaction_live_result_evidence",
                ValidationCategory.TRANSACTIONS, TRANSACTIONS,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-008 Live Transaction Validation Binding Integration",
                "Live bound terminal Transaction results publish authoritative result evidence for observation");
        platformContract(builder, "butchercraft:platform_contract/planning_cadence",
                ValidationCategory.PLANNING, PLANNING,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "Planning Cycle eligibility, trigger ordering, input capture, and publication are Planning-owned");
        platformContract(builder, "butchercraft:platform_contract/planning_live_periodic_cadence",
                ValidationCategory.PLANNING, PLANNING,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "Live Planning cadence uses deterministic periodic eligibility with bounded minimum separation");
        platformContract(builder, "butchercraft:platform_contract/planning_live_trigger_cadence",
                ValidationCategory.PLANNING, PLANNING,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "Live Planning accepts source-owned trigger evidence, coalesces duplicate triggers, and rejects conflicts");
        platformContract(builder, "butchercraft:platform_contract/planning_live_no_burst_catch_up",
                ValidationCategory.PLANNING, PLANNING,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "Loaded overdue Planning cadence schedules one next eligible cycle instead of burst catch-up");
        platformContract(builder, "butchercraft:platform_contract/planning_effect_classification_blocker",
                ValidationCategory.SCHEDULER, PLANNING,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "Planning remains NON_REPEATABLE until Scheduler can represent cycle-scoped Effect Identity");
        platformContract(builder, "butchercraft:platform_contract/scheduler_runtime_authority",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Scheduler Runtime Authority owns Scheduler runtime, dispatch, invocation identity, "
                        + "effect policy, and Scheduler publication");
        platformContract(builder, "butchercraft:platform_contract/scheduler_observes_domain_results",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Scheduler observes domain effects and authoritative results but does not own or infer them");
        platformContract(builder, "butchercraft:platform_contract/scheduler_live_effect_enforcement",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Scheduler handler effect declarations are enforced as live runtime contracts");
        platformContract(builder, "butchercraft:platform_contract/scheduler_invocation_identity_runtime",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Every live Scheduler handler attempt receives deterministic Invocation Identity");
        platformContract(builder, "butchercraft:platform_contract/scheduler_effect_identity_runtime",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Consequential Scheduler effects use stable Effect Identity separate from Invocation Identity");
        platformContract(builder, "butchercraft:platform_contract/scheduler_effect_retry_matrix",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Scheduler retry, deferral, generated Work, and completion legality are gated by effect policy");
        platformContract(builder, "butchercraft:platform_contract/scheduler_unknown_outcome_runtime",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Consequential uncertainty is represented as Scheduler Unknown Outcome and blocks automatic retry");
        platformContract(builder, "butchercraft:platform_contract/scheduler_owner_result_observation",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Scheduler records owner-published result evidence without owning domain results");
        platformContract(builder, "butchercraft:platform_contract/scheduler_parallel_reentrancy_prohibition",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Parallel, recursive, and nested Scheduler dispatch for one world-scoped manager are rejected");
        platformContract(builder, "butchercraft:platform_contract/production_transaction_backed_scheduler_conformance",
                ValidationCategory.PRODUCTION, PRODUCTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "Production Scheduler Work completes only after APPLIED Transaction result evidence is observed");
        platformContract(builder, "butchercraft:platform_contract/execution_authorization_evidence",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution consumes explicit immutable Execution Authorization Evidence rather than requiring Allocation");
        platformContract(builder, "butchercraft:platform_contract/execution_private_authorization_consumption",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Live Execution authorization is private, single-use, runtime-only, and Execution-owned");
        platformContract(builder, "butchercraft:platform_contract/execution_lifecycle_runtime",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution owns operation lifecycle, attempts, input freeze identity, and terminal runtime state");
        platformContract(builder, "butchercraft:platform_contract/execution_handler_boundary",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution invokes deterministic registered handlers without transferring owner-domain fact authority");
        platformContract(builder, "butchercraft:platform_contract/execution_scheduler_handler_boundary",
                ValidationCategory.SCHEDULER, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution integrates with Scheduler through one generic idempotent Scheduler handler and owner observations");
        platformContract(builder, "butchercraft:platform_contract/execution_owner_result_evidence",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Successful Execution completion requires owner-published result evidence before Scheduler completion");
        platformContract(builder, "butchercraft:platform_contract/execution_duplicate_conflict_behavior",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution observes duplicate authorization content and rejects conflicting same-identity authorization");
        platformContract(builder, "butchercraft:platform_contract/execution_unknown_outcome_runtime",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution represents unresolved consequential outcomes explicitly as Unknown Outcome");
        platformContract(builder, "butchercraft:platform_contract/execution_minimal_persistence",
                ValidationCategory.PERSISTENCE, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "Execution persists versioned operation runtime, attempts, immutable authorization evidence, and results");
        platformContract(builder, "butchercraft:platform_contract/execution_independent_of_allocation",
                ValidationCategory.DEPENDENCIES, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "RFC-0023 Draft 2",
                "Execution must not acquire a direct architectural dependency on Allocation");
        platformContract(builder, "butchercraft:platform_contract/allocation_integration_gate",
                ValidationCategory.ALLOCATION, ALLOCATION,
                ArchitectureValidationDisposition.DECLARED_IMPLEMENTATION_GATED,
                "RFC-0022 M22E-M22F and RFC-0023 Draft 2",
                "Future Allocation integration depends on finalized Execution contracts and remains separately gated");
        platformContract(builder, "butchercraft:platform_contract/workstation_player_execution_slice",
                ValidationCategory.EXECUTION, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-012 First Player-Facing Workstation Execution Vertical Slice",
                "One grinder workstation operation issues workstation-owned Execution authorization and completes through generic Execution");
        platformContract(builder, "butchercraft:platform_contract/workstation_owner_result_publication",
                ValidationCategory.EXECUTION, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-012 First Player-Facing Workstation Execution Vertical Slice",
                "The workstation owner publishes immutable owner result evidence before Execution succeeds");
        platformContract(builder, "butchercraft:platform_contract/workstation_itemstack_mutation_boundary",
                ValidationCategory.OWNERSHIP, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-012 First Player-Facing Workstation Execution Vertical Slice",
                "The selected grinder slice mutates workstation-owned ItemStack slots, not economic Inventory runtime");
        platformContract(builder, "butchercraft:platform_contract/execution_first_player_facing_handler",
                ValidationCategory.EXECUTION, EXECUTION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-012 First Player-Facing Workstation Execution Vertical Slice",
                "Execution registers one live player-facing grinder handler through the generic Scheduler work type");
        platformContract(builder, "butchercraft:platform_contract/scheduler_dispatched_workstation_execution",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-012 First Player-Facing Workstation Execution Vertical Slice",
                "The selected workstation effect is applied only when Scheduler dispatches the generic Execution Work");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_registration_verification",
                ValidationCategory.GENERAL, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "ButcherCraft in-world grinder GameTests are registered and executed by runGameTestServer");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_placement_verification",
                ValidationCategory.OWNERSHIP, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "Automated server-world tests verify grinder placement, block entity creation, and idle owner state");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_end_to_end_execution",
                ValidationCategory.EXECUTION, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "Automated server-world tests verify one beef-trim grinder operation through live Execution completion");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_duplicate_safety",
                ValidationCategory.SCHEDULER, SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "Automated server-world tests verify repeated interaction and later ticks do not duplicate work or output");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_save_load_safety",
                ValidationCategory.PERSISTENCE, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "Automated serialization-level tests verify pre-effect resume and completed-state non-duplication");
        platformContract(builder, "butchercraft:platform_contract/grinder_gametest_uncertain_state_safety",
                ValidationCategory.EXECUTION, WORKSTATION,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-013 Grinder Vertical Slice GameTest and Runtime Hardening",
                "Automated server-world tests verify malformed and uncertain restored grinder states stop visibly without mutation");
    }

    private static void addRuntimeAuthorities(ValidationContextBuilder builder) {
        runtimeAuthority(builder, "butchercraft:runtime_authority/scheduler_world",
                SCHEDULER, ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-009 Scheduler Effects Live Enforcement",
                "One Scheduler Runtime Authority is declared for each loaded world");
        runtimeAuthority(builder, "butchercraft:runtime_authority/planning_world",
                PLANNING, ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-010 Planning Cadence Live Enforcement",
                "One Planning runtime authority is declared for each loaded world");
        runtimeAuthority(builder, "butchercraft:runtime_authority/allocation_world",
                ALLOCATION, ArchitectureValidationDisposition.ENFORCED_NOW,
                "RFC-0022 M22B",
                "Allocation runtime lifecycle authority is declared for each loaded world");
        runtimeAuthority(builder, "butchercraft:runtime_authority/execution_world",
                EXECUTION, ArchitectureValidationDisposition.ENFORCED_NOW,
                "IM-011 Generic Execution Runtime Foundation",
                "One generic Execution Runtime Authority is declared for each loaded world");
    }

    private static void addOwnership(ValidationContextBuilder builder) {
        own(builder, "butchercraft:responsibility/world_identity", WORLD_IDENTITY);
        own(builder, "butchercraft:responsibility/world_identity_external_root_digest", WORLD_IDENTITY);
        own(builder, "butchercraft:responsibility/simulation_time", SIMULATION);
        own(builder, "butchercraft:responsibility/simulation_clock_checkpoint_snapshot_content", SIMULATION);
        own(builder, "butchercraft:responsibility/business_runtime", BUSINESS_RUNTIME);
        own(builder, "butchercraft:responsibility/workforce_definitions", WORKFORCE);
        own(builder, "butchercraft:responsibility/good_definitions", GOODS);
        own(builder, "butchercraft:responsibility/economic_actor_definitions", ACTORS);
        own(builder, "butchercraft:responsibility/inventory_quantities", INVENTORY);
        own(builder, "butchercraft:responsibility/inventory_freshness_identity", INVENTORY);
        own(builder, "butchercraft:responsibility/economic_mutations", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_proposal_identity", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_validation_plan_identity", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_validation_bindings", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/validation_consumption_authority", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_result_evidence", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_duplicate_conflict_decisions", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/transaction_binding_failure_reporting", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/order_intent", ORDERS);
        own(builder, "butchercraft:responsibility/work_eligibility", SCHEDULER);
        own(builder, "butchercraft:responsibility/scheduler_checkpoint_snapshot_content", SCHEDULER);
        own(builder, "butchercraft:responsibility/scheduler_invocation_identity", SCHEDULER);
        own(builder, "butchercraft:responsibility/scheduler_effect_policy", SCHEDULER);
        own(builder, "butchercraft:responsibility/scheduler_effect_identity_observation", SCHEDULER);
        own(builder, "butchercraft:responsibility/scheduler_unknown_outcome_runtime", SCHEDULER);
        own(builder, "butchercraft:responsibility/production_processes", PRODUCTION);
        own(builder, "butchercraft:responsibility/production_plans", PRODUCTION);
        own(builder, "butchercraft:responsibility/production_run_runtime", PRODUCTION);
        own(builder, "butchercraft:responsibility/planning_cadence_configuration", PLANNING);
        own(builder, "butchercraft:responsibility/planning_trigger_identity", PLANNING);
        own(builder, "butchercraft:responsibility/planning_input_capture", PLANNING);
        own(builder, "butchercraft:responsibility/planning_cycle_publication", PLANNING);
        own(builder, "butchercraft:responsibility/planning_decisions", PLANNING);
        own(builder, "butchercraft:responsibility/approved_plans", PLANNING);
        own(builder, "butchercraft:responsibility/execution_operation_identity", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_authorization_consumption", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_lifecycle", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_attempts", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_handler_registry", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_domain_effect_identity", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_result_evidence", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_unknown_outcome_runtime", EXECUTION);
        own(builder, "butchercraft:responsibility/execution_persistence", EXECUTION);
        own(builder, "butchercraft:responsibility/workstation_state", WORKSTATION);
        own(builder, "butchercraft:responsibility/workstation_slot_inventory", WORKSTATION);
        own(builder, "butchercraft:responsibility/workstation_operation_preconditions", WORKSTATION);
        own(builder, "butchercraft:responsibility/workstation_execution_authorization_issuance", WORKSTATION);
        own(builder, "butchercraft:responsibility/workstation_owner_result_evidence", WORKSTATION);
        own(builder, "butchercraft:responsibility/allocation_requests", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_sets", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_commitments", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_lifecycle", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_registries", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_reports", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_history", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_cycles", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_capacity_accounting", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_commitment_selection", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_observation_snapshots", ALLOCATION);
        own(builder, "butchercraft:responsibility/allocation_provider_framework", ALLOCATION);
        own(builder, "butchercraft:responsibility/evidence_classification", EVIDENCE_LIFECYCLE);
        own(builder, "butchercraft:responsibility/evidence_retention_policy_inputs", EVIDENCE_LIFECYCLE);
        own(builder, "butchercraft:responsibility/evidence_retention_decisions", EVIDENCE_LIFECYCLE);
        own(builder, "butchercraft:responsibility/evidence_lifecycle_failure_reporting", EVIDENCE_LIFECYCLE);
        own(builder, "butchercraft:responsibility/checkpoint_generation_identity", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_owner_snapshot_metadata", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_generation_manifests", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_head_records", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_integrity_validation", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_recovery_selection", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_rollback_selection", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_recovery_diagnostics", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_filesystem_store", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_storage_layout", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_head_publication", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_storage_artifact_classification", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_owner_snapshot_coordination", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_cross_owner_validation", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/checkpoint_coordinated_restoration_boundary", CHECKPOINT_RECOVERY);
        own(builder, "butchercraft:responsibility/resource_definitions", RESOURCE_AUTHORITIES);
        own(builder, "butchercraft:responsibility/capacity_definitions", RESOURCE_AUTHORITIES);

        contract(
                builder,
                "butchercraft:responsibility/simulation_time",
                SIMULATION,
                ValidationCategory.OWNERSHIP,
                "AI-0022 assigns authoritative simulation time to the Simulation Clock"
        );
        contract(
                builder,
                "butchercraft:responsibility/world_identity_external_root_digest",
                WORLD_IDENTITY,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns checkpoint external-root identity and digest derivation to World Identity"
        );
        contract(
                builder,
                "butchercraft:responsibility/simulation_clock_checkpoint_snapshot_content",
                SIMULATION,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns Clock checkpoint payload content, schema, validation, and restoration to Clock"
        );
        contract(
                builder,
                "butchercraft:responsibility/scheduler_checkpoint_snapshot_content",
                SCHEDULER,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns Scheduler checkpoint payload content, schema, validation, and restoration to Scheduler"
        );
        contract(
                builder,
                "butchercraft:responsibility/scheduler_invocation_identity",
                SCHEDULER,
                ValidationCategory.SCHEDULER,
                "IM-009 assigns deterministic Scheduler handler attempt identity to Scheduler"
        );
        contract(
                builder,
                "butchercraft:responsibility/scheduler_effect_policy",
                SCHEDULER,
                ValidationCategory.SCHEDULER,
                "IM-009 assigns live Scheduler effect policy enforcement to Scheduler"
        );
        contract(
                builder,
                "butchercraft:responsibility/scheduler_effect_identity_observation",
                SCHEDULER,
                ValidationCategory.SCHEDULER,
                "IM-009 assigns Scheduler observation of Effect Identity to Scheduler without transferring domain facts"
        );
        contract(
                builder,
                "butchercraft:responsibility/scheduler_unknown_outcome_runtime",
                SCHEDULER,
                ValidationCategory.SCHEDULER,
                "IM-009 assigns Scheduler Unknown Outcome runtime state to Scheduler"
        );
        contract(
                builder,
                "butchercraft:responsibility/inventory_quantities",
                INVENTORY,
                ValidationCategory.OWNERSHIP,
                "AI-0007 and AI-0025 assign quantity state to Inventory"
        );
        contract(
                builder,
                "butchercraft:responsibility/inventory_freshness_identity",
                INVENTORY,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign source-owned Inventory Freshness Identity to Inventory"
        );
        contract(
                builder,
                "butchercraft:responsibility/economic_mutations",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "AI-0006 assigns economic mutation to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_proposal_identity",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign canonical Proposal Identity to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_validation_plan_identity",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign immutable Validation Plan Identity to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_validation_bindings",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign validation binding ownership to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/validation_consumption_authority",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 keep Validation Consumption Authority private, single-use, and Transaction-owned"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_result_evidence",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign authoritative Transaction result evidence to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_duplicate_conflict_decisions",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "ADR-03 and IM-004 assign duplicate observation and Transaction identity conflict policy to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/transaction_binding_failure_reporting",
                TRANSACTIONS,
                ValidationCategory.TRANSACTIONS,
                "IM-004 assigns typed validation binding failure reporting to Transactions"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_filesystem_store",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-005 assigns explicit-root filesystem checkpoint storage to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_storage_layout",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-005 assigns deterministic checkpoint staging, generation, head, and quarantine layout to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_head_publication",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-005 assigns dual head-slot publication mechanics to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_storage_artifact_classification",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-005 assigns filesystem Quarantined Artifact classification to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_owner_snapshot_coordination",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns explicit owner snapshot capture coordination to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_cross_owner_validation",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns Clock/Scheduler relationship validation to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_coordinated_restoration_boundary",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-006 assigns all-or-nothing owner restoration coordination and owner-supplied rollback orchestration to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/planning_cadence_configuration",
                PLANNING,
                ValidationCategory.PLANNING,
                "IM-010 assigns live cadence configuration identity to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/planning_trigger_identity",
                PLANNING,
                ValidationCategory.PLANNING,
                "IM-010 assigns Planning Trigger Identity consumption and conflict classification to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/planning_input_capture",
                PLANNING,
                ValidationCategory.PLANNING,
                "IM-010 assigns Planning Cycle input freeze identity to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/planning_cycle_publication",
                PLANNING,
                ValidationCategory.PLANNING,
                "IM-010 assigns bounded Planning Cycle publication to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/planning_decisions",
                PLANNING,
                ValidationCategory.PLANNING,
                "DEC-0074 assigns decision artifacts to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/approved_plans",
                PLANNING,
                ValidationCategory.PLANNING,
                "DEC-0074 assigns Approved Plans to Planning"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_operation_identity",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns deterministic operation identity to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_authorization_consumption",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 keeps live authorization consumption private, single-use, and Execution-owned"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_lifecycle",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns operation lifecycle and cancellation-before-start state to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_attempts",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns bounded attempt records to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_handler_registry",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns the explicit generic Execution handler registry to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_domain_effect_identity",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns domain Effect Identity to Execution while Scheduler owns Scheduler Effect Identity"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_result_evidence",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns terminal Execution result evidence publication to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_unknown_outcome_runtime",
                EXECUTION,
                ValidationCategory.EXECUTION,
                "IM-011 assigns explicit Unknown Outcome runtime state to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/execution_persistence",
                EXECUTION,
                ValidationCategory.PERSISTENCE,
                "IM-011 assigns versioned generic Execution operation persistence to Execution"
        );
        contract(
                builder,
                "butchercraft:responsibility/production_plans",
                PRODUCTION,
                ValidationCategory.PRODUCTION,
                "DEC-0073 assigns executable Production Plans to Production"
        );
        contract(
                builder,
                "butchercraft:responsibility/production_run_runtime",
                PRODUCTION,
                ValidationCategory.EXECUTION,
                "DEC-0073 assigns Production Run runtime to Production"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_requests",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22A assigns allocation requests to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_sets",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22A assigns immutable allocation sets to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_commitments",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22A assigns immutable commitments to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_lifecycle",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22B assigns AllocationSet lifecycle state to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_registries",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22B assigns canonical definition and runtime registries to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_reports",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22B assigns immutable cycle reports to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_history",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22B assigns immutable lifecycle history to the Allocation domain"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_cycles",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22C assigns deterministic Allocation Cycle behavior to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_capacity_accounting",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22C assigns detached cycle-local Capacity accounting to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_commitment_selection",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22C assigns deterministic Commitment selection and construction to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_observation_snapshots",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22D assigns immutable generic observation snapshots to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/allocation_provider_framework",
                ALLOCATION,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22D assigns provider contracts and observation aggregation to Allocation"
        );
        contract(
                builder,
                "butchercraft:responsibility/evidence_classification",
                EVIDENCE_LIFECYCLE,
                ValidationCategory.GENERAL,
                "IM-002 assigns canonical evidence classification primitives to Evidence Lifecycle"
        );
        contract(
                builder,
                "butchercraft:responsibility/evidence_retention_policy_inputs",
                EVIDENCE_LIFECYCLE,
                ValidationCategory.GENERAL,
                "IM-002 assigns retention-policy input primitives to Evidence Lifecycle"
        );
        contract(
                builder,
                "butchercraft:responsibility/evidence_retention_decisions",
                EVIDENCE_LIFECYCLE,
                ValidationCategory.GENERAL,
                "IM-002 assigns deterministic retention decisions to Evidence Lifecycle"
        );
        contract(
                builder,
                "butchercraft:responsibility/evidence_lifecycle_failure_reporting",
                EVIDENCE_LIFECYCLE,
                ValidationCategory.GENERAL,
                "IM-002 assigns typed lifecycle failure reporting to Evidence Lifecycle"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_generation_identity",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns checkpoint generation identity primitives to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_owner_snapshot_metadata",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns owner snapshot metadata descriptors to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_generation_manifests",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns generation manifest metadata to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_head_records",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns head record metadata to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_integrity_validation",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns metadata integrity validation to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_recovery_selection",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns deterministic recovery selection to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_rollback_selection",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns rollback target selection to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/checkpoint_recovery_diagnostics",
                CHECKPOINT_RECOVERY,
                ValidationCategory.PERSISTENCE,
                "IM-003 assigns typed checkpoint recovery diagnostics to Checkpoint Recovery"
        );
        contract(
                builder,
                "butchercraft:responsibility/resource_definitions",
                RESOURCE_AUTHORITIES,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22D preserves external authority over Resource definitions"
        );
        contract(
                builder,
                "butchercraft:responsibility/capacity_definitions",
                RESOURCE_AUTHORITIES,
                ValidationCategory.ALLOCATION,
                "RFC-0022 M22D preserves external authority over Capacity definitions"
        );
        contract(
                builder,
                "butchercraft:responsibility/workstation_state",
                WORKSTATION,
                ValidationCategory.OWNERSHIP,
                "IM-012 keeps workstation visible state owned by the workstation subsystem"
        );
        contract(
                builder,
                "butchercraft:responsibility/workstation_slot_inventory",
                WORKSTATION,
                ValidationCategory.OWNERSHIP,
                "IM-012 keeps grinder input and output ItemStack slots owned by the workstation subsystem"
        );
        contract(
                builder,
                "butchercraft:responsibility/workstation_operation_preconditions",
                WORKSTATION,
                ValidationCategory.OWNERSHIP,
                "IM-012 keeps recipe applicability and effect preconditions owned by the workstation subsystem"
        );
        contract(
                builder,
                "butchercraft:responsibility/workstation_execution_authorization_issuance",
                WORKSTATION,
                ValidationCategory.EXECUTION,
                "IM-012 allows one workstation-owned authorization source for the grinder vertical slice"
        );
        contract(
                builder,
                "butchercraft:responsibility/workstation_owner_result_evidence",
                WORKSTATION,
                ValidationCategory.EXECUTION,
                "IM-012 assigns the selected grinder Authoritative Result to the workstation owner"
        );
    }

    private static void addDependencies(ValidationContextBuilder builder) {
        depends(builder, ACTORS, GOODS);
        depends(builder, INVENTORY, ACTORS);
        depends(builder, INVENTORY, GOODS);
        depends(builder, TRANSACTIONS, ACTORS);
        depends(builder, TRANSACTIONS, GOODS);
        depends(builder, TRANSACTIONS, INVENTORY);
        depends(builder, ORDERS, ACTORS);
        depends(builder, ORDERS, GOODS);
        depends(builder, ORDERS, TRANSACTIONS);
        depends(builder, SCHEDULER, SIMULATION);
        depends(builder, PRODUCTION, BUSINESS_RUNTIME);
        depends(builder, PRODUCTION, WORKFORCE);
        depends(builder, PRODUCTION, ACTORS);
        depends(builder, PRODUCTION, GOODS);
        depends(builder, PRODUCTION, INVENTORY);
        depends(builder, PRODUCTION, TRANSACTIONS);
        depends(builder, PRODUCTION, ORDERS);
        depends(builder, PRODUCTION, SCHEDULER);
        depends(builder, PLANNING, BUSINESS_RUNTIME);
        depends(builder, PLANNING, WORKFORCE);
        depends(builder, PLANNING, ACTORS);
        depends(builder, PLANNING, GOODS);
        depends(builder, PLANNING, INVENTORY);
        depends(builder, PLANNING, TRANSACTIONS);
        depends(builder, PLANNING, ORDERS);
        depends(builder, PLANNING, PRODUCTION);
        depends(builder, PLANNING, SCHEDULER);
        depends(builder, EXECUTION, SCHEDULER);
        depends(builder, WORKSTATION, EXECUTION);
        depends(builder, WORKSTATION, SCHEDULER);
        depends(builder, WORKSTATION, SIMULATION);
        depends(builder, WORKSTATION, WORLD_IDENTITY);

        forbid(
                builder,
                INVENTORY,
                PRODUCTION,
                "Inventory cannot acquire Production policy or execution ownership"
        );
        forbid(
                builder,
                INVENTORY,
                TRANSACTIONS,
                "Inventory owns authoritative quantities but not Transaction validation or mutation authority"
        );
        forbid(
                builder,
                INVENTORY,
                PLANNING,
                "Inventory cannot acquire Planning decision authority"
        );
        forbid(
                builder,
                INVENTORY,
                ALLOCATION,
                "Inventory cannot acquire Allocation authorization authority"
        );
        forbid(
                builder,
                INVENTORY,
                EXECUTION,
                "Inventory cannot acquire generic Execution authority"
        );
        forbid(
                builder,
                TRANSACTIONS,
                PLANNING,
                "Transactions cannot depend upon Planning decisions"
        );
        forbid(
                builder,
                TRANSACTIONS,
                ALLOCATION,
                "Transactions cannot depend upon Allocation authorization"
        );
        forbid(
                builder,
                TRANSACTIONS,
                EXECUTION,
                "Transactions cannot depend upon Execution progress or lifecycle"
        );
        forbid(
                builder,
                TRANSACTIONS,
                SCHEDULER,
                "Transaction binding foundation cannot consume Scheduler runtime or effects"
        );
        forbid(
                builder,
                TRANSACTIONS,
                PRODUCTION,
                "Transaction binding foundation cannot consume Production semantics"
        );
        forbid(
                builder,
                TRANSACTIONS,
                CHECKPOINT_RECOVERY,
                "Transaction binding foundation cannot publish checkpoints or select persistence generations"
        );
        forbid(
                builder,
                TRANSACTIONS,
                EVIDENCE_LIFECYCLE,
                "Transaction binding foundation emits Transaction-owned evidence without consuming Evidence Lifecycle"
        );
        forbid(
                builder,
                SCHEDULER,
                PLANNING,
                "Scheduler eligibility remains independent from Planning policy"
        );
        forbid(
                builder,
                SCHEDULER,
                PRODUCTION,
                "Scheduler eligibility remains independent from Production behavior"
        );
        forbid(
                builder,
                SCHEDULER,
                TRANSACTIONS,
                "Scheduler dispatch observes authoritative results but cannot validate Transactions"
        );
        forbid(
                builder,
                EXECUTION,
                ALLOCATION,
                "RFC-0023 Draft 2 requires Execution to consume generic authorization evidence without "
                        + "requiring Allocation"
        );
        forbid(
                builder,
                ALLOCATION,
                PLANNING,
                "M22A-M22C Allocation references Planning artifacts only by stable external identity"
        );
        forbid(
                builder,
                ALLOCATION,
                PRODUCTION,
                "M22A-M22C Allocation references executable work only by stable external identity"
        );
        forbid(
                builder,
                ALLOCATION,
                SCHEDULER,
                "M22A-M22C does not register or execute Scheduler work"
        );
        forbid(
                builder,
                ALLOCATION,
                INVENTORY,
                "M22A-M22C models capacity evidence without owning inventory quantities"
        );
        forbid(
                builder,
                ALLOCATION,
                TRANSACTIONS,
                "M22A-M22C defines no economic mutation or transaction path"
        );
        forbid(
                builder,
                ALLOCATION,
                RESOURCE_AUTHORITIES,
                "M22D provider adapters translate external authority without a concrete Allocation dependency"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                PLANNING,
                "Evidence Lifecycle foundation classifies owner evidence without consuming Planning implementation"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                SCHEDULER,
                "Evidence Lifecycle foundation does not enforce Scheduler effects or runtime state"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                TRANSACTIONS,
                "Evidence Lifecycle foundation does not define Transaction validation or mutation authority"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                PRODUCTION,
                "Evidence Lifecycle foundation does not own Production facts or runtime state"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                ALLOCATION,
                "Evidence Lifecycle foundation does not consume Allocation implementation or authorization"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                EXECUTION,
                "Evidence Lifecycle foundation does not require Execution implementation"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                INVENTORY,
                "Evidence Lifecycle foundation does not inspect or own Inventory state"
        );
        forbid(
                builder,
                EVIDENCE_LIFECYCLE,
                CHECKPOINT_RECOVERY,
                "Evidence Lifecycle foundation does not publish checkpoint generations or recover state"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                PLANNING,
                "Checkpoint Recovery foundation references Planning only through owner snapshot metadata"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                SCHEDULER,
                "Checkpoint Recovery foundation references Scheduler only through owner snapshot metadata"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                TRANSACTIONS,
                "Checkpoint Recovery foundation references Transactions only through owner snapshot metadata"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                PRODUCTION,
                "Checkpoint Recovery foundation references Production only through owner snapshot metadata"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                INVENTORY,
                "Checkpoint Recovery foundation references Inventory only through owner snapshot metadata"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                ALLOCATION,
                "Checkpoint Recovery foundation does not consume Allocation implementation or authorization"
        );
        forbid(
                builder,
                CHECKPOINT_RECOVERY,
                EXECUTION,
                "Checkpoint Recovery foundation does not require Execution implementation"
        );
    }

    private static void addRegistries(ValidationContextBuilder builder) {
        List<RegistryEntryDescriptor> components = List.of(
                PLATFORM_ARCHITECTURE,
                WORLD_IDENTITY, SIMULATION, BUSINESS_RUNTIME, WORKFORCE, GOODS, ACTORS,
                INVENTORY, TRANSACTIONS, ORDERS, SCHEDULER, PRODUCTION, PLANNING,
                EVIDENCE_LIFECYCLE, CHECKPOINT_RECOVERY, ALLOCATION, EXECUTION, WORKSTATION,
                RESOURCE_AUTHORITIES
        ).stream()
                .sorted()
                .map(componentId -> RegistryEntryDescriptor.of(componentId.value()))
                .toList();
        builder.registry(new RegistryDescriptor(
                "butchercraft:architecture_components",
                OrderingPolicy.CANONICAL_ID,
                components
        ));

        List<RegistryEntryDescriptor> stages = BuiltInSimulationStages.definitions().stream()
                .sorted(Comparator.comparingInt(SimulationStageDefinition::executionOrder))
                .map(stage -> new RegistryEntryDescriptor(
                        stage.id().value(),
                        stage.executionOrder(),
                        List.of()
                ))
                .toList();
        builder.registry(new RegistryDescriptor(
                STAGE_REGISTRY_ID,
                OrderingPolicy.EXPLICIT_ORDER,
                stages
        ));

        builder.registry(new RegistryDescriptor(
                WORK_TYPE_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of(
                        workType(EconomicPlanningWorkHandler.TYPE.value(), BuiltInSimulationStages.PLANNING.value()),
                        workType(ExecutionWorkTypes.GENERIC_EXECUTION_OPERATION.value(),
                                BuiltInSimulationStages.EXECUTION.value()),
                        workType(ProductionWorkTypes.PRODUCTION_RUN.value(), BuiltInSimulationStages.EXECUTION.value())
                ).stream().sorted(Comparator.comparing(RegistryEntryDescriptor::id)).toList()
        ));

        builder.registry(new RegistryDescriptor(
                ALLOCATION_DEFINITION_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        ));
        builder.registry(new RegistryDescriptor(
                ALLOCATION_RUNTIME_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        ));
        builder.registry(new RegistryDescriptor(
                ALLOCATION_REPORT_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        ));
        builder.registry(new RegistryDescriptor(
                ALLOCATION_TRACE_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        ));
        builder.registry(new RegistryDescriptor(
                ALLOCATION_PROVIDER_REGISTRY_ID,
                OrderingPolicy.CANONICAL_ID,
                List.of()
        ));
    }

    private static void addPersistence(ValidationContextBuilder builder) {
        persistence(builder, "butchercraft:world_identity_state", "saved_data/butchercraft_world_identity",
                WORLD_IDENTITY, 1, PersistenceDataKind.IMMUTABLE_DEFINITIONS, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:simulation_state", "butchercraft/simulation_state.json",
                SIMULATION, 1, PersistenceDataKind.MUTABLE_RUNTIME, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:business_runtime", "butchercraft/business_runtime.json",
                BUSINESS_RUNTIME, 1, PersistenceDataKind.MUTABLE_RUNTIME, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:workforce_definitions", "butchercraft/workforce_definitions.json",
                WORKFORCE, 1, PersistenceDataKind.IMMUTABLE_DEFINITIONS, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:goods", "butchercraft/goods.json",
                GOODS, 1, PersistenceDataKind.IMMUTABLE_DEFINITIONS, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:economic_actors", "butchercraft/economic_actors.json",
                ACTORS, 1, PersistenceDataKind.IMMUTABLE_DEFINITIONS, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:inventory", "butchercraft/inventory.json",
                INVENTORY, 1, PersistenceDataKind.SEPARATED_DEFINITIONS_AND_RUNTIME, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:transactions", "butchercraft/transactions.json",
                TRANSACTIONS, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.INSERTION);
        persistence(builder, "butchercraft:orders", "butchercraft/orders.json",
                ORDERS, 1, PersistenceDataKind.SEPARATED_DEFINITIONS_AND_RUNTIME, OrderingPolicy.INSERTION);
        persistence(builder, "butchercraft:contracts", "butchercraft/contracts.json",
                ORDERS, 1, PersistenceDataKind.SEPARATED_DEFINITIONS_AND_RUNTIME, OrderingPolicy.INSERTION);
        persistence(
                builder,
                "butchercraft:simulation_scheduler",
                "butchercraft/" + SchedulerSchema.FILE_NAME,
                SCHEDULER,
                SchedulerSchema.CURRENT_VERSION,
                PersistenceDataKind.SEPARATED_DEFINITIONS_AND_RUNTIME,
                OrderingPolicy.INSERTION
        );
        persistence(builder, "butchercraft:production_processes",
                "butchercraft/" + ProductionSchema.PROCESSES_FILE_NAME, PRODUCTION,
                ProductionSchema.CURRENT_VERSION, PersistenceDataKind.IMMUTABLE_DEFINITIONS,
                OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:production_plans",
                "butchercraft/" + ProductionSchema.PLANS_FILE_NAME, PRODUCTION,
                ProductionSchema.CURRENT_VERSION, PersistenceDataKind.IMMUTABLE_DEFINITIONS,
                OrderingPolicy.CANONICAL_ID);
        persistence(
                builder,
                "butchercraft:production_runs",
                "butchercraft/" + ProductionSchema.RUNS_FILE_NAME,
                PRODUCTION,
                ProductionSchema.CURRENT_VERSION,
                PersistenceDataKind.MUTABLE_RUNTIME,
                OrderingPolicy.CANONICAL_ID,
                new ArchitectureReference(STAGE_REGISTRY_ID, BuiltInSimulationStages.EXECUTION.value())
        );
        persistence(builder, "butchercraft:planning_observations", "butchercraft/planning_observations.json",
                PLANNING, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:planning_needs", "butchercraft/planning_needs.json",
                PLANNING, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:planning_opportunities", "butchercraft/planning_opportunities.json",
                PLANNING, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:planning_candidates", "butchercraft/planning_candidates.json",
                PLANNING, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.CANONICAL_ID);
        persistence(builder, "butchercraft:planning_approved_plans", "butchercraft/planning_approved_plans.json",
                PLANNING, 1, PersistenceDataKind.IMMUTABLE_HISTORY, OrderingPolicy.CANONICAL_ID);
        persistence(
                builder,
                "butchercraft:planning_runtime",
                "butchercraft/planning_runtime.json",
                PLANNING,
                1,
                PersistenceDataKind.MUTABLE_RUNTIME,
                OrderingPolicy.CANONICAL_ID,
                new ArchitectureReference(STAGE_REGISTRY_ID, BuiltInSimulationStages.PLANNING.value())
        );
        persistence(
                builder,
                "butchercraft:planning_cadence",
                "butchercraft/planning_cadence.json",
                PLANNING,
                1,
                PersistenceDataKind.MUTABLE_RUNTIME,
                OrderingPolicy.CANONICAL_ID,
                new ArchitectureReference(STAGE_REGISTRY_ID, BuiltInSimulationStages.PLANNING.value())
        );
        persistence(
                builder,
                "butchercraft:execution_operations",
                "butchercraft/" + ExecutionSchema.FILE_NAME,
                EXECUTION,
                ExecutionSchema.CURRENT_VERSION,
                PersistenceDataKind.SEPARATED_DEFINITIONS_AND_RUNTIME,
                OrderingPolicy.CANONICAL_ID,
                new ArchitectureReference(STAGE_REGISTRY_ID, BuiltInSimulationStages.EXECUTION.value())
        );
    }

    private static void addSchedulerEffects(ValidationContextBuilder builder) {
        schedulerEffect(builder, "butchercraft:scheduler_effect/read_only",
                SchedulerEffectKind.READ_ONLY,
                "ADR-05 Scheduler Effects Authority",
                "Read-only Scheduler handler observation effect");
        schedulerEffect(builder, "butchercraft:scheduler_effect/idempotent",
                SchedulerEffectKind.IDEMPOTENT,
                "ADR-05 Scheduler Effects Authority",
                "Deterministically repeatable Scheduler handler effect");
        schedulerEffect(builder, "butchercraft:scheduler_effect/transaction_backed",
                SchedulerEffectKind.TRANSACTION_BACKED,
                "ADR-05 Scheduler Effects Authority",
                "Scheduler handler effect backed by authoritative Transaction result evidence");
        schedulerEffect(builder, "butchercraft:scheduler_effect/non_repeatable",
                SchedulerEffectKind.NON_REPEATABLE,
                "ADR-05 Scheduler Effects Authority",
                "Consequential Scheduler handler effect that must not be automatically reinvoked");
    }

    private static void addScheduler(ValidationContextBuilder builder) {
        List<SimulationStageDefinition> definitions = BuiltInSimulationStages.definitions();
        List<SchedulerStageDescriptor> stages = new ArrayList<>(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            SimulationStageDefinition stage = definitions.get(index);
            List<String> dependencies = index == 0
                    ? List.of()
                    : List.of(definitions.get(index - 1).id().value());
            stages.add(new SchedulerStageDescriptor(stage.id().value(), stage.executionOrder(), dependencies));
        }
        builder.scheduler(new SchedulerDescriptor(
                "butchercraft:simulation_scheduler",
                100,
                stages
        ));
    }

    private static void addSimulationInvariants(ValidationContextBuilder builder) {
        invariant(builder, "butchercraft:invariant/replay_compatibility",
                SimulationInvariantType.REPLAY_COMPATIBILITY,
                "Authoritative inputs and ordered events produce replay-compatible outcomes");
        invariant(builder, "butchercraft:invariant/deterministic_ordering",
                SimulationInvariantType.DETERMINISTIC_ORDERING,
                "Registries, Scheduler Work, and validation rules use stable ordering");
        invariant(builder, "butchercraft:invariant/stable_identifiers",
                SimulationInvariantType.STABLE_IDENTIFIERS,
                "Durable definitions and runtime records use stable identifiers");
        invariant(builder, "butchercraft:invariant/explicit_randomness",
                SimulationInvariantType.EXPLICIT_RANDOMNESS,
                "Authoritative randomness must be explicit and deliberately seeded");
        invariant(builder, "butchercraft:invariant/bounded_work",
                SimulationInvariantType.BOUNDED_WORK,
                "Simulation work is bounded by explicit budgets and ordering");
        invariant(builder, "butchercraft:invariant/transaction_validation",
                SimulationInvariantType.KNOWN_INVARIANT,
                "Economic mutation requires accepted Transaction validation");
        invariant(builder, "butchercraft:invariant/planning_cadence_bounded",
                SimulationInvariantType.KNOWN_INVARIANT,
                "Planning cadence is bounded by periodic eligibility, minimum separation, and no burst catch-up");
        invariant(builder, "butchercraft:invariant/execution_bounded_runtime",
                SimulationInvariantType.BOUNDED_WORK,
                "Execution runtime work, attempts, active operations, and pending owner results are bounded");
    }

    private static ArchitectureComponent component(ArchitectureId id, String name, String packageRoot) {
        return new ArchitectureComponent(id, name, packageRoot);
    }

    private static void own(ValidationContextBuilder builder, String responsibility, ArchitectureId owner) {
        builder.ownership(new OwnershipAssignment(id(responsibility), owner));
    }

    private static void contract(
            ValidationContextBuilder builder,
            String responsibility,
            ArchitectureId owner,
            ValidationCategory category,
            String rationale
    ) {
        builder.ownershipContract(new OwnershipContract(id(responsibility), owner, category, rationale));
    }

    private static void depends(
            ValidationContextBuilder builder,
            ArchitectureId consumer,
            ArchitectureId provider
    ) {
        builder.dependency(new DependencyDescriptor(consumer, provider));
    }

    private static void forbid(
            ValidationContextBuilder builder,
            ArchitectureId consumer,
            ArchitectureId provider,
            String rationale
    ) {
        builder.dependencyConstraint(new DependencyConstraint(consumer, provider, rationale));
    }

    private static void document(
            ValidationContextBuilder builder,
            String id,
            String path,
            String status,
            String revision,
            ArchitectureValidationDisposition disposition
    ) {
        builder.architectureDocument(new ArchitectureDocumentDescriptor(
                ArchitectureId.of(id),
                path,
                status,
                revision,
                disposition
        ));
    }

    private static void identity(
            ValidationContextBuilder builder,
            String id,
            PlatformIdentityKind kind,
            String source,
            String description
    ) {
        builder.platformIdentity(new PlatformIdentityDescriptor(
                ArchitectureId.of(id),
                kind,
                ArchitectureValidationDisposition.DOCUMENTATION_ONLY,
                source,
                description
        ));
    }

    private static void platformContract(
            ValidationContextBuilder builder,
            String id,
            ValidationCategory category,
            ArchitectureId owner,
            ArchitectureValidationDisposition disposition,
            String source,
            String description
    ) {
        builder.platformContract(new PlatformContractDescriptor(
                ArchitectureId.of(id),
                category,
                owner,
                disposition,
                source,
                description
        ));
    }

    private static void runtimeAuthority(
            ValidationContextBuilder builder,
            String id,
            ArchitectureId owner,
            ArchitectureValidationDisposition disposition,
            String source,
            String description
    ) {
        builder.runtimeAuthority(new RuntimeAuthorityDescriptor(
                ArchitectureId.of(id),
                owner,
                WORLD_SCOPE,
                disposition,
                source,
                description
        ));
    }

    private static void schedulerEffect(
            ValidationContextBuilder builder,
            String id,
            SchedulerEffectKind kind,
            String source,
            String description
    ) {
        builder.schedulerEffect(new SchedulerEffectDeclaration(
                ArchitectureId.of(id),
                kind.name(),
                SCHEDULER,
                ArchitectureValidationDisposition.ENFORCED_NOW,
                source,
                description
        ));
    }

    private static RegistryEntryDescriptor workType(String id, String stageId) {
        return new RegistryEntryDescriptor(
                id,
                0,
                List.of(new ArchitectureReference(STAGE_REGISTRY_ID, stageId))
        );
    }

    private static void persistence(
            ValidationContextBuilder builder,
            String id,
            String path,
            ArchitectureId owner,
            int schemaVersion,
            PersistenceDataKind kind,
            OrderingPolicy ordering,
            ArchitectureReference... references
    ) {
        builder.persistence(new PersistenceDescriptor(
                id,
                path,
                owner,
                schemaVersion,
                kind,
                ordering,
                List.of(references)
        ));
    }

    private static void invariant(
            ValidationContextBuilder builder,
            String id,
            SimulationInvariantType type,
            String description
    ) {
        builder.simulationInvariant(new SimulationInvariantDescriptor(
                ArchitectureId.of(id),
                type,
                true,
                description
        ));
    }

    private static ArchitectureId id(String value) {
        return ArchitectureId.of(value);
    }
}
