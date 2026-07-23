package com.butchercraft.world.production.persistence;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessOperationalStatus;
import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.IndustryId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryType;
import com.butchercraft.world.production.ConsumptionPolicy;
import com.butchercraft.world.production.ProductionBatchPolicy;
import com.butchercraft.world.production.ProductionBindingDirection;
import com.butchercraft.world.production.ProductionBusinessRequirement;
import com.butchercraft.world.production.ProductionDependencies;
import com.butchercraft.world.production.ProductionDuration;
import com.butchercraft.world.production.ProductionExecutionPolicy;
import com.butchercraft.world.production.ProductionInputDefinition;
import com.butchercraft.world.production.ProductionInputRole;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionInventoryConstraint;
import com.butchercraft.world.production.ProductionLineId;
import com.butchercraft.world.production.ProductionLineMetadata;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionMetadata;
import com.butchercraft.world.production.ProductionOutputDefinition;
import com.butchercraft.world.production.ProductionOutputRole;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPlanMetadata;
import com.butchercraft.world.production.ProductionPlanRegistry;
import com.butchercraft.world.production.ProductionPlanRegistryBuilder;
import com.butchercraft.world.production.ProductionPriority;
import com.butchercraft.world.production.ProductionProcessDefinition;
import com.butchercraft.world.production.ProductionProcessId;
import com.butchercraft.world.production.ProductionProcessRegistry;
import com.butchercraft.world.production.ProductionProcessRegistryBuilder;
import com.butchercraft.world.production.ProductionRequirementLossPolicy;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionSchema;
import com.butchercraft.world.production.ProductionTransactionFailurePolicy;
import com.butchercraft.world.production.ProductionTransformationReference;
import com.butchercraft.world.production.ProductionWorkforceRequirement;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.workforce.CertificationType;
import com.butchercraft.world.workforce.PositionId;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import com.butchercraft.world.workforce.WorkforceSkillLevel;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public final class ProductionStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();
    private static final String SCHEMA_VERSION = "schema_version";

    private final Path processFile;
    private final Path planFile;
    private final Path runFile;
    private final ProductionDependencies dependencies;

    public ProductionStorage(
            Path processFile,
            Path planFile,
            Path runFile,
            ProductionDependencies dependencies
    ) {
        this.processFile = normalize(processFile, "processFile");
        this.planFile = normalize(planFile, "planFile");
        this.runFile = normalize(runFile, "runFile");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public ProductionManager load() {
        boolean processesExist = Files.exists(processFile);
        boolean plansExist = Files.exists(planFile);
        boolean runsExist = Files.exists(runFile);
        if (!processesExist && !plansExist && !runsExist) {
            return new ProductionManager(dependencies);
        }
        if (!(processesExist && plansExist && runsExist)) {
            throw new IllegalStateException("Production persistence set is incomplete");
        }
        try {
            ProductionPersistenceSnapshot snapshot = deserialize(
                    Files.readString(processFile, StandardCharsets.UTF_8),
                    Files.readString(planFile, StandardCharsets.UTF_8),
                    Files.readString(runFile, StandardCharsets.UTF_8)
            );
            return new ProductionManager(
                    dependencies,
                    snapshot.processRegistry(),
                    snapshot.planRegistry(),
                    snapshot.runs()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Production persistence", exception);
        }
    }

    public void save(ProductionManager manager) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        try {
            createParent(processFile);
            createParent(planFile);
            createParent(runFile);
            Path processTemporary = temporary(processFile);
            Path planTemporary = temporary(planFile);
            Path runTemporary = temporary(runFile);
            Files.writeString(processTemporary, serializeProcesses(manager.processRegistry()), StandardCharsets.UTF_8);
            Files.writeString(planTemporary, serializePlans(manager.planRegistry()), StandardCharsets.UTF_8);
            Files.writeString(runTemporary, serializeRuns(manager.runs()), StandardCharsets.UTF_8);
            moveIntoPlace(processTemporary, processFile);
            moveIntoPlace(planTemporary, planFile);
            moveIntoPlace(runTemporary, runFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save Production persistence", exception);
        }
    }

    public ProductionPersistenceSnapshot deserialize(String processes, String plans, String runs) {
        try {
            return new ProductionPersistenceSnapshot(
                    deserializeProcesses(processes),
                    deserializePlans(plans),
                    deserializeRuns(runs)
            );
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Production persistence", exception);
        }
    }

    public String serializeProcesses(ProductionProcessRegistry registry) {
        JsonObject root = root();
        JsonArray values = new JsonArray();
        registry.definitions().forEach(process -> values.add(serializeProcess(process)));
        root.add("processes", values);
        return json(root);
    }

    public ProductionProcessRegistry deserializeProcesses(String json) {
        JsonObject root = parseRoot(json, "process");
        ProductionProcessRegistryBuilder builder = ProductionProcessRegistry.builder();
        for (JsonElement element : array(root, "processes")) {
            builder.register(deserializeProcess(object(element, "production process")));
        }
        return builder.build();
    }

    public String serializePlans(ProductionPlanRegistry registry) {
        JsonObject root = root();
        JsonArray values = new JsonArray();
        registry.definitions().forEach(plan -> values.add(serializePlan(plan)));
        root.add("plans", values);
        return json(root);
    }

    public ProductionPlanRegistry deserializePlans(String json) {
        JsonObject root = parseRoot(json, "plan");
        ProductionPlanRegistryBuilder builder = ProductionPlanRegistry.builder();
        for (JsonElement element : array(root, "plans")) {
            builder.register(deserializePlan(object(element, "production plan")));
        }
        return builder.build();
    }

    public String serializeRuns(Collection<ProductionRunSnapshot> runs) {
        JsonObject root = root();
        JsonArray values = new JsonArray();
        Objects.requireNonNull(runs, "runs").forEach(run -> values.add(serializeRun(run)));
        root.add("runs", values);
        return json(root);
    }

    public List<ProductionRunSnapshot> deserializeRuns(String json) {
        JsonObject root = parseRoot(json, "run");
        List<ProductionRunSnapshot> values = new ArrayList<>();
        for (JsonElement element : array(root, "runs")) {
            values.add(deserializeRun(object(element, "production run")));
        }
        return List.copyOf(values);
    }

    private JsonObject serializeProcess(ProductionProcessDefinition value) {
        JsonObject object = record(value.schemaVersion());
        object.addProperty("id", value.id().value());
        object.addProperty("display_name", value.displayName());
        object.addProperty("owning_industry_id", value.owningIndustryId().value());
        object.addProperty("required_actor_capability", value.requiredActorCapability().serializedName());
        object.add("additional_required_capabilities", strings(value.additionalRequiredCapabilities().stream()
                .map(ActorCapability::serializedName).sorted().toList()));
        JsonArray inputs = new JsonArray();
        value.inputs().forEach(input -> inputs.add(serializeInput(input)));
        object.add("inputs", inputs);
        JsonArray outputs = new JsonArray();
        value.outputs().forEach(output -> outputs.add(serializeOutput(output)));
        object.add("outputs", outputs);
        JsonArray transformations = new JsonArray();
        value.transformationReferences().forEach(reference -> transformations.add(serializeTransformation(reference)));
        object.add("transformation_references", transformations);
        object.add("duration", serializeDuration(value.duration()));
        object.add("batch_policy", serializeBatchPolicy(value.batchPolicy()));
        object.add("workforce_requirement", serializeWorkforce(value.workforceRequirement()));
        object.add("business_requirement", serializeBusiness(value.businessRequirement()));
        object.add("execution_policy", serializePolicy(value.executionPolicy()));
        object.add("metadata", serializeMetadata(value.metadata()));
        return object;
    }

    private ProductionProcessDefinition deserializeProcess(JsonObject object) {
        ProductionProcessDefinition.Builder builder = ProductionProcessDefinition.builder()
                .schemaVersion(schema(object))
                .id(string(object, "id"))
                .displayName(string(object, "display_name"))
                .owningIndustryId(IndustryId.of(string(object, "owning_industry_id")))
                .requiredActorCapability(ActorCapability.fromSerializedName(
                        string(object, "required_actor_capability")))
                .duration(deserializeDuration(object(object.get("duration"), "production duration")))
                .batchPolicy(deserializeBatchPolicy(object(object.get("batch_policy"), "production batch policy")))
                .workforceRequirement(deserializeWorkforce(
                        object(object.get("workforce_requirement"), "production workforce requirement")))
                .businessRequirement(deserializeBusiness(
                        object(object.get("business_requirement"), "production business requirement")))
                .executionPolicy(deserializePolicy(
                        object(object.get("execution_policy"), "production execution policy")))
                .metadata(deserializeMetadata(object(object.get("metadata"), "production metadata")));
        for (String capability : stringList(object, "additional_required_capabilities")) {
            builder.additionalCapability(ActorCapability.fromSerializedName(capability));
        }
        for (JsonElement input : array(object, "inputs")) {
            builder.input(deserializeInput(object(input, "production input")));
        }
        for (JsonElement output : array(object, "outputs")) {
            builder.output(deserializeOutput(object(output, "production output")));
        }
        for (JsonElement reference : array(object, "transformation_references")) {
            builder.transformationReference(deserializeTransformation(object(reference, "transformation reference")));
        }
        return builder.build();
    }

    private JsonObject serializeInput(ProductionInputDefinition value) {
        JsonObject object = new JsonObject();
        object.addProperty("line_id", value.id().value());
        object.addProperty("good_id", value.goodId().value());
        object.addProperty("quantity_per_batch", value.quantityPerBatch().canonicalValue());
        object.addProperty("unit", value.unit().serializedName());
        object.addProperty("role", lower(value.role()));
        object.addProperty("consumption_policy", lower(value.consumptionPolicy()));
        optionalObject(object, "transformation_reference", value.transformationReference(),
                this::serializeTransformation);
        object.add("source_constraint", serializeConstraint(value.sourceConstraint()));
        object.add("metadata", serializeLineMetadata(value.metadata()));
        return object;
    }

    private ProductionInputDefinition deserializeInput(JsonObject object) {
        return new ProductionInputDefinition(
                ProductionLineId.of(string(object, "line_id")),
                GoodId.of(string(object, "good_id")),
                GoodQuantity.of(string(object, "quantity_per_batch")),
                UnitOfMeasure.fromSerializedName(string(object, "unit")),
                enumValue(ProductionInputRole.class, string(object, "role")),
                enumValue(ConsumptionPolicy.class, string(object, "consumption_policy")),
                optionalObject(object, "transformation_reference", this::deserializeTransformation),
                deserializeConstraint(object(object.get("source_constraint"), "source constraint")),
                deserializeLineMetadata(object(object.get("metadata"), "production line metadata"))
        );
    }

    private JsonObject serializeOutput(ProductionOutputDefinition value) {
        JsonObject object = new JsonObject();
        object.addProperty("line_id", value.id().value());
        object.addProperty("good_id", value.goodId().value());
        object.addProperty("base_quantity_per_batch", value.baseQuantityPerBatch().canonicalValue());
        object.addProperty("unit", value.unit().serializedName());
        object.addProperty("role", lower(value.role()));
        JsonObject ratio = new JsonObject();
        ratio.addProperty("numerator", value.yieldRatio().numerator());
        ratio.addProperty("denominator", value.yieldRatio().denominator());
        object.add("yield_ratio", ratio);
        optionalObject(object, "transformation_reference", value.transformationReference(),
                this::serializeTransformation);
        object.add("destination_constraint", serializeConstraint(value.destinationConstraint()));
        object.add("metadata", serializeLineMetadata(value.metadata()));
        return object;
    }

    private ProductionOutputDefinition deserializeOutput(JsonObject object) {
        JsonObject ratio = object(object.get("yield_ratio"), "production yield ratio");
        return new ProductionOutputDefinition(
                ProductionLineId.of(string(object, "line_id")),
                GoodId.of(string(object, "good_id")),
                GoodQuantity.of(string(object, "base_quantity_per_batch")),
                UnitOfMeasure.fromSerializedName(string(object, "unit")),
                enumValue(ProductionOutputRole.class, string(object, "role")),
                new GoodYieldRatio(longValue(ratio, "numerator"), longValue(ratio, "denominator")),
                optionalObject(object, "transformation_reference", this::deserializeTransformation),
                deserializeConstraint(object(object.get("destination_constraint"), "destination constraint")),
                deserializeLineMetadata(object(object.get("metadata"), "production line metadata"))
        );
    }

    private JsonObject serializeTransformation(ProductionTransformationReference value) {
        JsonObject object = new JsonObject();
        object.addProperty("input_good_id", value.inputGoodId().value());
        object.addProperty("output_good_id", value.outputGoodId().value());
        object.addProperty("owning_industry_id", value.owningIndustryId().value());
        return object;
    }

    private ProductionTransformationReference deserializeTransformation(JsonObject object) {
        return new ProductionTransformationReference(
                GoodId.of(string(object, "input_good_id")),
                GoodId.of(string(object, "output_good_id")),
                IndustryId.of(string(object, "owning_industry_id"))
        );
    }

    private static JsonObject serializeDuration(ProductionDuration value) {
        JsonObject object = new JsonObject();
        object.addProperty("base_duration_ticks", value.baseDurationTicks());
        object.addProperty("progress_quantum_ticks", value.progressQuantumTicks());
        return object;
    }

    private static ProductionDuration deserializeDuration(JsonObject object) {
        return new ProductionDuration(
                longValue(object, "base_duration_ticks"),
                longValue(object, "progress_quantum_ticks")
        );
    }

    private static JsonObject serializeBatchPolicy(ProductionBatchPolicy value) {
        JsonObject object = new JsonObject();
        object.addProperty("minimum_batch_count", value.minimumBatchCount());
        object.addProperty("maximum_batch_count", value.maximumBatchCount());
        object.addProperty("batch_increment", value.batchIncrement());
        object.addProperty("partial_batch_allowed", value.partialBatchAllowed());
        object.addProperty("exact_input_scaling_required", value.exactInputScalingRequired());
        return object;
    }

    private static ProductionBatchPolicy deserializeBatchPolicy(JsonObject object) {
        return new ProductionBatchPolicy(
                longValue(object, "minimum_batch_count"),
                longValue(object, "maximum_batch_count"),
                longValue(object, "batch_increment"),
                bool(object, "partial_batch_allowed"),
                bool(object, "exact_input_scaling_required")
        );
    }

    private static JsonObject serializeWorkforce(ProductionWorkforceRequirement value) {
        JsonObject object = new JsonObject();
        optionalString(object, "workforce_definition_id", value.workforceDefinitionId()
                .map(WorkforceDefinitionId::value));
        object.add("required_positions", strings(value.requiredPositions().stream()
                .map(PositionId::value).sorted().toList()));
        object.addProperty("minimum_active_workers", value.minimumActiveWorkers());
        object.add("required_certifications", strings(value.requiredCertifications().stream()
                .map(CertificationType::serializedName).sorted().toList()));
        optionalString(object, "minimum_skill_level", value.minimumSkillLevel()
                .map(WorkforceSkillLevel::serializedName));
        object.addProperty("required_throughout_execution", value.requiredThroughoutExecution());
        return object;
    }

    private static ProductionWorkforceRequirement deserializeWorkforce(JsonObject object) {
        return new ProductionWorkforceRequirement(
                optionalString(object, "workforce_definition_id").map(WorkforceDefinitionId::new),
                stringList(object, "required_positions").stream().map(PositionId::new)
                        .collect(java.util.stream.Collectors.toSet()),
                intValue(object, "minimum_active_workers"),
                stringList(object, "required_certifications").stream()
                        .map(CertificationType::fromSerializedName)
                        .collect(java.util.stream.Collectors.toSet()),
                optionalString(object, "minimum_skill_level").map(WorkforceSkillLevel::fromSerializedName),
                bool(object, "required_throughout_execution")
        );
    }

    private static JsonObject serializeBusiness(ProductionBusinessRequirement value) {
        JsonObject object = new JsonObject();
        object.addProperty("business_required", value.businessRequired());
        object.addProperty("must_be_operational", value.mustBeOperational());
        object.addProperty("must_be_open", value.mustBeOpen());
        object.addProperty("active_shift_required", value.activeShiftRequired());
        object.addProperty("maintenance_must_be_false", value.maintenanceMustBeFalse());
        object.addProperty("minimum_active_workforce", value.minimumActiveWorkforce());
        object.add("allowed_statuses", strings(value.allowedStatuses().stream()
                .map(BusinessOperationalStatus::serializedName).sorted().toList()));
        return object;
    }

    private static ProductionBusinessRequirement deserializeBusiness(JsonObject object) {
        return new ProductionBusinessRequirement(
                bool(object, "business_required"),
                bool(object, "must_be_operational"),
                bool(object, "must_be_open"),
                bool(object, "active_shift_required"),
                bool(object, "maintenance_must_be_false"),
                intValue(object, "minimum_active_workforce"),
                stringList(object, "allowed_statuses").stream()
                        .map(BusinessOperationalStatus::fromSerializedName)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    private static JsonObject serializePolicy(ProductionExecutionPolicy value) {
        JsonObject object = new JsonObject();
        object.addProperty("input_loss_policy", lower(value.inputLossPolicy()));
        object.addProperty("workforce_loss_policy", lower(value.workforceLossPolicy()));
        object.addProperty("business_loss_policy", lower(value.businessLossPolicy()));
        object.addProperty("destination_loss_policy", lower(value.destinationLossPolicy()));
        object.addProperty("transaction_failure_policy", lower(value.transactionFailurePolicy()));
        object.addProperty("blocked_retry_delay_ticks", value.blockedRetryDelayTicks());
        object.addProperty("maximum_execution_attempts", value.maximumExecutionAttempts());
        return object;
    }

    private static ProductionExecutionPolicy deserializePolicy(JsonObject object) {
        return new ProductionExecutionPolicy(
                enumValue(ProductionRequirementLossPolicy.class, string(object, "input_loss_policy")),
                enumValue(ProductionRequirementLossPolicy.class, string(object, "workforce_loss_policy")),
                enumValue(ProductionRequirementLossPolicy.class, string(object, "business_loss_policy")),
                enumValue(ProductionRequirementLossPolicy.class, string(object, "destination_loss_policy")),
                enumValue(ProductionTransactionFailurePolicy.class, string(object, "transaction_failure_policy")),
                longValue(object, "blocked_retry_delay_ticks"),
                intValue(object, "maximum_execution_attempts")
        );
    }

    private static JsonObject serializeMetadata(ProductionMetadata value) {
        JsonObject object = new JsonObject();
        object.add("tags", strings(value.tags().stream().sorted().toList()));
        optionalString(object, "description", value.description());
        return object;
    }

    private static ProductionMetadata deserializeMetadata(JsonObject object) {
        return new ProductionMetadata(Set.copyOf(stringList(object, "tags")),
                optionalString(object, "description"));
    }

    private static JsonObject serializeLineMetadata(ProductionLineMetadata value) {
        JsonObject object = new JsonObject();
        object.add("tags", strings(value.tags().stream().sorted().toList()));
        optionalString(object, "note", value.note());
        return object;
    }

    private static ProductionLineMetadata deserializeLineMetadata(JsonObject object) {
        return new ProductionLineMetadata(Set.copyOf(stringList(object, "tags")),
                optionalString(object, "note"));
    }

    private static JsonObject serializeConstraint(ProductionInventoryConstraint value) {
        JsonObject object = new JsonObject();
        object.add("allowed_inventory_types", strings(value.allowedTypes().stream()
                .map(InventoryType::serializedName).sorted().toList()));
        return object;
    }

    private static ProductionInventoryConstraint deserializeConstraint(JsonObject object) {
        return new ProductionInventoryConstraint(stringList(object, "allowed_inventory_types").stream()
                .map(InventoryType::fromSerializedName).collect(java.util.stream.Collectors.toSet()));
    }

    private JsonObject serializePlan(ProductionPlanDefinition value) {
        JsonObject object = record(value.schemaVersion());
        object.addProperty("id", value.id().value());
        object.addProperty("process_id", value.processId().value());
        object.addProperty("producer_actor_id", value.producerActorId().value());
        optionalString(object, "business_id", value.businessId().map(BusinessId::value));
        object.addProperty("batch_count", value.batchCount());
        JsonArray bindings = new JsonArray();
        value.inventoryBindings().forEach(binding -> bindings.add(serializeBinding(binding)));
        object.add("inventory_bindings", bindings);
        object.addProperty("created_simulation_tick", value.createdSimulationTick());
        object.addProperty("earliest_start_tick", value.earliestStartTick());
        optionalLong(object, "latest_completion_tick", value.latestCompletionTick());
        object.addProperty("priority", lower(value.priority()));
        optionalString(object, "requesting_order_id", value.requestingOrderId().map(OrderId::value));
        optionalString(object, "governing_contract_id", value.governingContractId().map(ContractId::value));
        object.add("metadata", serializePlanMetadata(value.metadata()));
        return object;
    }

    private ProductionPlanDefinition deserializePlan(JsonObject object) {
        ProductionPlanDefinition.Builder builder = ProductionPlanDefinition.builder()
                .schemaVersion(schema(object))
                .id(string(object, "id"))
                .processId(ProductionProcessId.of(string(object, "process_id")))
                .producerActorId(ActorId.of(string(object, "producer_actor_id")))
                .batchCount(longValue(object, "batch_count"))
                .createdSimulationTick(longValue(object, "created_simulation_tick"))
                .earliestStartTick(longValue(object, "earliest_start_tick"))
                .priority(enumValue(ProductionPriority.class, string(object, "priority")))
                .metadata(deserializePlanMetadata(object(object.get("metadata"), "production plan metadata")));
        optionalString(object, "business_id").map(BusinessId::new).ifPresent(builder::businessId);
        optionalLong(object, "latest_completion_tick").ifPresent(builder::latestCompletionTick);
        optionalString(object, "requesting_order_id").map(OrderId::of).ifPresent(builder::requestingOrderId);
        optionalString(object, "governing_contract_id").map(ContractId::of).ifPresent(builder::governingContractId);
        for (JsonElement binding : array(object, "inventory_bindings")) {
            builder.inventoryBinding(deserializeBinding(object(binding, "production inventory binding")));
        }
        return builder.build();
    }

    private static JsonObject serializeBinding(ProductionInventoryBinding value) {
        JsonObject object = new JsonObject();
        object.addProperty("line_id", value.lineId().value());
        object.addProperty("direction", lower(value.direction()));
        object.addProperty("inventory_id", value.inventoryId().value());
        object.addProperty("expected_good_id", value.expectedGoodId().value());
        object.addProperty("expected_unit", value.expectedUnit().serializedName());
        return object;
    }

    private static ProductionInventoryBinding deserializeBinding(JsonObject object) {
        return new ProductionInventoryBinding(
                ProductionLineId.of(string(object, "line_id")),
                enumValue(ProductionBindingDirection.class, string(object, "direction")),
                InventoryId.of(string(object, "inventory_id")),
                GoodId.of(string(object, "expected_good_id")),
                UnitOfMeasure.fromSerializedName(string(object, "expected_unit"))
        );
    }

    private static JsonObject serializePlanMetadata(ProductionPlanMetadata value) {
        JsonObject object = new JsonObject();
        object.add("tags", strings(value.tags().stream().sorted().toList()));
        optionalString(object, "correlation_id", value.correlationId());
        optionalString(object, "note", value.note());
        return object;
    }

    private static ProductionPlanMetadata deserializePlanMetadata(JsonObject object) {
        return new ProductionPlanMetadata(
                Set.copyOf(stringList(object, "tags")),
                optionalString(object, "correlation_id"),
                optionalString(object, "note")
        );
    }

    private static JsonObject serializeRun(ProductionRunSnapshot value) {
        JsonObject object = record(value.schemaVersion());
        object.addProperty("id", value.id().value());
        object.addProperty("plan_id", value.planId().value());
        object.addProperty("status", lower(value.status()));
        object.addProperty("last_updated_simulation_tick", value.lastUpdatedSimulationTick());
        optionalLong(object, "started_tick", value.startedTick());
        optionalLong(object, "paused_tick", value.pausedTick());
        optionalLong(object, "completed_tick", value.completedTick());
        object.addProperty("required_work_units", value.requiredWorkUnits());
        object.addProperty("current_work_units", value.currentWorkUnits());
        object.addProperty("execution_attempt_count", value.executionAttemptCount());
        optionalLong(object, "next_eligible_tick", value.nextEligibleTick());
        optionalString(object, "scheduled_work_id", value.scheduledWorkId().map(SimulationWorkId::value));
        optionalString(object, "completion_transaction_id",
                value.completionTransactionId().map(TransactionId::value));
        optionalString(object, "failure_code", value.failureCode().map(ProductionStorage::lower));
        optionalString(object, "failure_summary", value.failureSummary());
        object.addProperty("revision", value.revision());
        return object;
    }

    private static ProductionRunSnapshot deserializeRun(JsonObject object) {
        return new ProductionRunSnapshot(
                ProductionRunId.of(string(object, "id")),
                ProductionPlanId.of(string(object, "plan_id")),
                enumValue(ProductionRunStatus.class, string(object, "status")),
                longValue(object, "last_updated_simulation_tick"),
                optionalLong(object, "started_tick"),
                optionalLong(object, "paused_tick"),
                optionalLong(object, "completed_tick"),
                longValue(object, "required_work_units"),
                longValue(object, "current_work_units"),
                intValue(object, "execution_attempt_count"),
                optionalLong(object, "next_eligible_tick"),
                optionalString(object, "scheduled_work_id").map(SimulationWorkId::of),
                optionalString(object, "completion_transaction_id").map(TransactionId::of),
                optionalString(object, "failure_code")
                        .map(value -> enumValue(com.butchercraft.world.production.ProductionFailureCode.class, value)),
                optionalString(object, "failure_summary"),
                longValue(object, "revision"),
                schema(object)
        );
    }

    private static JsonObject root() {
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, ProductionSchema.CURRENT_VERSION);
        return root;
    }

    private static JsonObject record(int schemaVersion) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, schemaVersion);
        return object;
    }

    private static JsonObject parseRoot(String json, String label) {
        JsonObject root = object(JsonParser.parseString(Objects.requireNonNull(json, "json")),
                "production " + label + " root");
        int schema = schema(root);
        if (schema != ProductionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported production " + label + " schema: " + schema);
        }
        return root;
    }

    private static int schema(JsonObject object) {
        int value = intValue(object, SCHEMA_VERSION);
        if (value != ProductionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported production record schema: " + value);
        }
        return value;
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static List<String> stringList(JsonObject object, String name) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array(object, name)) values.add(string(element, name));
        return List.copyOf(values);
    }

    private static JsonArray array(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonArray()) throw new IllegalArgumentException("Production field must be an array: " + name);
        return element.getAsJsonArray();
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static String string(JsonObject object, String name) { return string(field(object, name), name); }
    private static String string(JsonElement element, String name) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Production field must be a string: " + name);
        }
        String value = element.getAsString();
        if (value.isBlank()) throw new IllegalArgumentException("Production field must not be blank: " + name);
        return value;
    }

    private static long longValue(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Production field must be a number: " + name);
        }
        try {
            return element.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Production field must be an exact whole number: " + name, exception);
        }
    }

    private static int intValue(JsonObject object, String name) {
        long value = longValue(object, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Production field must be an exact integer: " + name);
        }
        return (int) value;
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement element = field(object, name);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Production field must be a boolean: " + name);
        }
        return element.getAsBoolean();
    }

    private static Optional<String> optionalString(JsonObject object, String name) {
        JsonElement element = field(object, name);
        return element.isJsonNull() ? Optional.empty() : Optional.of(string(element, name));
    }

    private static void optionalString(JsonObject object, String name, Optional<String> value) {
        if (value.isPresent()) object.addProperty(name, value.orElseThrow());
        else object.add(name, JsonNull.INSTANCE);
    }

    private static OptionalLong optionalLong(JsonObject object, String name) {
        JsonElement element = field(object, name);
        return element.isJsonNull() ? OptionalLong.empty() : OptionalLong.of(longValue(object, name));
    }

    private static void optionalLong(JsonObject object, String name, OptionalLong value) {
        if (value.isPresent()) object.addProperty(name, value.orElseThrow());
        else object.add(name, JsonNull.INSTANCE);
    }

    private static <T> Optional<T> optionalObject(
            JsonObject object,
            String name,
            Function<JsonObject, T> deserializer
    ) {
        JsonElement element = field(object, name);
        return element.isJsonNull() ? Optional.empty()
                : Optional.of(deserializer.apply(object(element, name)));
    }

    private static <T> void optionalObject(
            JsonObject object,
            String name,
            Optional<T> value,
            Function<T, JsonObject> serializer
    ) {
        if (value.isPresent()) object.add(name, serializer.apply(value.orElseThrow()));
        else object.add(name, JsonNull.INSTANCE);
    }

    private static JsonElement field(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null) throw new IllegalArgumentException("Missing production field: " + name);
        return element;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown production " + type.getSimpleName() + ": " + value, exception);
        }
    }

    private static String lower(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static String json(JsonObject root) { return GSON.toJson(root) + System.lineSeparator(); }
    private static Path normalize(Path path, String label) {
        return Objects.requireNonNull(path, label).toAbsolutePath().normalize();
    }
    private static Path temporary(Path path) { return path.resolveSibling(path.getFileName() + ".tmp"); }
    private static void createParent(Path path) throws IOException {
        if (path.getParent() != null) Files.createDirectories(path.getParent());
    }
    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
