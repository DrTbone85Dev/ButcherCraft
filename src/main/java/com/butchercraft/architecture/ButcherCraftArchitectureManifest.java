package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureComponent;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureReference;
import com.butchercraft.architecture.validation.DependencyConstraint;
import com.butchercraft.architecture.validation.DependencyDescriptor;
import com.butchercraft.architecture.validation.OrderingPolicy;
import com.butchercraft.architecture.validation.OwnershipAssignment;
import com.butchercraft.architecture.validation.OwnershipContract;
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
import com.butchercraft.architecture.validation.ValidationContextBuilder;
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
    private static final ArchitectureId ALLOCATION = id("butchercraft:allocation");
    private static final ArchitectureId RESOURCE_AUTHORITIES =
            id("butchercraft:resource_authorities");

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
        addOwnership(builder);
        addDependencies(builder);
        addRegistries(builder);
        addPersistence(builder);
        addScheduler(builder);
        addSimulationInvariants(builder);
        return builder.build();
    }

    private static void addComponents(ValidationContextBuilder builder) {
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
        builder.component(component(ALLOCATION, "Resource Allocation", "com.butchercraft.world.allocation"));
        builder.component(component(
                RESOURCE_AUTHORITIES,
                "External Resource Authorities",
                "external.resource.authorities"
        ));
    }

    private static void addOwnership(ValidationContextBuilder builder) {
        own(builder, "butchercraft:responsibility/world_identity", WORLD_IDENTITY);
        own(builder, "butchercraft:responsibility/simulation_time", SIMULATION);
        own(builder, "butchercraft:responsibility/business_runtime", BUSINESS_RUNTIME);
        own(builder, "butchercraft:responsibility/workforce_definitions", WORKFORCE);
        own(builder, "butchercraft:responsibility/good_definitions", GOODS);
        own(builder, "butchercraft:responsibility/economic_actor_definitions", ACTORS);
        own(builder, "butchercraft:responsibility/inventory_quantities", INVENTORY);
        own(builder, "butchercraft:responsibility/economic_mutations", TRANSACTIONS);
        own(builder, "butchercraft:responsibility/order_intent", ORDERS);
        own(builder, "butchercraft:responsibility/work_eligibility", SCHEDULER);
        own(builder, "butchercraft:responsibility/production_processes", PRODUCTION);
        own(builder, "butchercraft:responsibility/production_plans", PRODUCTION);
        own(builder, "butchercraft:responsibility/production_run_runtime", PRODUCTION);
        own(builder, "butchercraft:responsibility/planning_decisions", PLANNING);
        own(builder, "butchercraft:responsibility/approved_plans", PLANNING);
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
                "butchercraft:responsibility/inventory_quantities",
                INVENTORY,
                ValidationCategory.OWNERSHIP,
                "AI-0007 and AI-0025 assign quantity state to Inventory"
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

        forbid(
                builder,
                INVENTORY,
                PRODUCTION,
                "Inventory cannot acquire Production policy or execution ownership"
        );
        forbid(
                builder,
                TRANSACTIONS,
                PLANNING,
                "Transactions cannot depend upon Planning decisions"
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
    }

    private static void addRegistries(ValidationContextBuilder builder) {
        List<RegistryEntryDescriptor> components = List.of(
                WORLD_IDENTITY, SIMULATION, BUSINESS_RUNTIME, WORKFORCE, GOODS, ACTORS,
                INVENTORY, TRANSACTIONS, ORDERS, SCHEDULER, PRODUCTION, PLANNING,
                ALLOCATION, RESOURCE_AUTHORITIES
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
