package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessOperationalStatus;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorRuntime;
import com.butchercraft.world.economy.order.EconomicContractDefinition;
import com.butchercraft.world.economy.order.EconomicOrderDefinition;
import com.butchercraft.world.goods.GoodDefinition;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.workforce.WorkforceDefinition;
import com.butchercraft.world.workforce.WorkforcePosition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProductionValidator {
    private final ProductionDependencies dependencies;

    public ProductionValidator(ProductionDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public List<ProductionFailure> validateProcess(ProductionProcessDefinition process) {
        Objects.requireNonNull(process, "process");
        List<ProductionFailure> failures = new ArrayList<>();
        GoodRegistry goods = dependencies.goodManager().registry();
        if (!goods.knownIndustries().contains(process.owningIndustryId())) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_INDUSTRY, "Unknown production industry",
                    process.owningIndustryId().value()));
        }
        process.inputs().forEach(input -> validateInput(process, input, goods, failures));
        process.outputs().forEach(output -> validateOutput(process, output, goods, failures));
        for (ProductionTransformationReference reference : process.transformationReferences()) {
            boolean resolves = goods.transformations().stream().anyMatch(reference::matches);
            if (!resolves) {
                failures.add(failure(
                        ProductionFailureCode.UNKNOWN_TRANSFORMATION,
                        "Production transformation reference does not resolve",
                        reference.inputGoodId().value()
                ));
            }
            boolean inputMatches = process.inputs().stream()
                    .anyMatch(input -> input.goodId().equals(reference.inputGoodId()));
            boolean outputMatches = process.outputs().stream()
                    .anyMatch(output -> output.goodId().equals(reference.outputGoodId()));
            if (!inputMatches || !outputMatches) {
                failures.add(failure(
                        ProductionFailureCode.VALIDATION_FAILED,
                        "Production transformation is incompatible with process lines",
                        process.id().value()
                ));
            }
        }
        validateWorkforceDefinition(process.workforceRequirement(), failures);
        return List.copyOf(failures);
    }

    public List<ProductionFailure> validatePlan(
            ProductionPlanDefinition plan,
            ProductionProcessRegistry processRegistry
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(processRegistry, "processRegistry");
        List<ProductionFailure> failures = new ArrayList<>();
        ProductionProcessDefinition process = processRegistry.find(plan.processId()).orElse(null);
        if (process == null) {
            return List.of(failure(ProductionFailureCode.UNKNOWN_PROCESS,
                    "Production plan references an unknown process", plan.processId().value()));
        }
        try {
            process.batchPolicy().validate(plan.batchCount());
            process.duration().requiredWorkUnits(plan.batchCount());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                    message(exception, "Invalid production batch"), plan.id().value()));
        }
        validateActor(plan, process, failures);
        validateBusinessReference(plan, process, failures);
        validateBindings(plan, process, failures);
        validateEconomicContext(plan, process, failures);
        return List.copyOf(failures);
    }

    public List<ProductionFailure> validateReadiness(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            long tick
    ) {
        if (tick < 0L) throw new IllegalArgumentException("Production readiness tick must not be negative");
        List<ProductionFailure> failures = new ArrayList<>();
        if (plan.latestCompletionTick().isPresent() && tick > plan.latestCompletionTick().orElseThrow()) {
            failures.add(failure(ProductionFailureCode.RUN_EXPIRED,
                    "Production plan is past its latest completion tick", plan.id().value()));
            return List.copyOf(failures);
        }
        EconomicActorRuntime actorRuntime = dependencies.actorManager().runtimeFor(plan.producerActorId()).orElse(null);
        if (actorRuntime == null || !actorRuntime.enabled() || !actorRuntime.operational()) {
            failures.add(failure(ProductionFailureCode.ACTOR_CAPABILITY_MISSING,
                    "Producer actor is not operational", plan.producerActorId().value()));
        }
        validateBusinessRuntime(plan, process.businessRequirement(), failures);
        validateWorkforceRuntime(plan, process.workforceRequirement(), failures);
        for (ProductionInputDefinition input : process.inputs()) {
            ProductionInventoryBinding binding = binding(plan, input.id(), ProductionBindingDirection.INPUT);
            if (binding == null) continue;
            long required;
            try {
                required = ProductionQuantityCalculator.toInventoryUnits(
                        ProductionQuantityCalculator.scaleInput(input.quantityPerBatch(), plan.batchCount())
                );
            } catch (IllegalArgumentException exception) {
                failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        exception.getMessage(), input.id().value()));
                continue;
            }
            InventoryRuntime runtime = dependencies.inventoryManager().runtimeFor(binding.inventoryId()).orElse(null);
            if (runtime == null) {
                failures.add(failure(ProductionFailureCode.UNKNOWN_INVENTORY,
                        "Production input inventory is missing", binding.inventoryId().value()));
            } else if (!runtime.status().canRelease()) {
                failures.add(failure(ProductionFailureCode.INVENTORY_STATUS_INVALID,
                        "Production input inventory cannot release Goods", binding.inventoryId().value()));
            } else if (runtime.quantityOf(input.goodId(), input.unit()) < required) {
                failures.add(failure(ProductionFailureCode.INSUFFICIENT_INPUT,
                        "Production input quantity is insufficient", input.id().value()));
            }
        }
        for (ProductionOutputDefinition output : process.outputs()) {
            ProductionInventoryBinding binding = binding(plan, output.id(), ProductionBindingDirection.OUTPUT);
            if (binding == null) continue;
            InventoryRuntime runtime = dependencies.inventoryManager().runtimeFor(binding.inventoryId()).orElse(null);
            if (runtime == null) {
                failures.add(failure(ProductionFailureCode.UNKNOWN_INVENTORY,
                        "Production output inventory is missing", binding.inventoryId().value()));
            } else if (!runtime.status().canReceive()) {
                failures.add(failure(ProductionFailureCode.INVENTORY_STATUS_INVALID,
                        "Production output inventory cannot receive Goods", binding.inventoryId().value()));
            }
        }
        return List.copyOf(failures);
    }

    private void validateInput(
            ProductionProcessDefinition process,
            ProductionInputDefinition input,
            GoodRegistry goods,
            List<ProductionFailure> failures
    ) {
        validateGood(input.goodId(), input.unit(), goods, failures, input.id().value());
        try {
            ProductionQuantityCalculator.toInventoryUnits(
                    ProductionQuantityCalculator.scaleInput(
                            input.quantityPerBatch(),
                            process.batchPolicy().maximumBatchCount()
                    )
            );
        } catch (IllegalArgumentException exception) {
            failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                    exception.getMessage(), input.id().value()));
        }
    }

    private void validateOutput(
            ProductionProcessDefinition process,
            ProductionOutputDefinition output,
            GoodRegistry goods,
            List<ProductionFailure> failures
    ) {
        validateGood(output.goodId(), output.unit(), goods, failures, output.id().value());
        try {
            ProductionQuantityCalculator.toInventoryUnits(
                    ProductionQuantityCalculator.scaleOutput(
                            output.baseQuantityPerBatch(),
                            process.batchPolicy().maximumBatchCount(),
                            output.yieldRatio()
                    )
            );
        } catch (IllegalArgumentException exception) {
            failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                    exception.getMessage(), output.id().value()));
        }
    }

    private static void validateGood(
            com.butchercraft.world.goods.GoodId goodId,
            com.butchercraft.world.goods.UnitOfMeasure unit,
            GoodRegistry goods,
            List<ProductionFailure> failures,
            String reference
    ) {
        GoodDefinition definition = goods.find(goodId).orElse(null);
        if (definition == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_GOOD,
                    "Production line references an unknown Good", reference));
        } else if (definition.unitOfMeasure() != unit) {
            failures.add(failure(ProductionFailureCode.UNIT_MISMATCH,
                    "Production line unit does not match its Good", reference));
        }
    }

    private void validateActor(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            List<ProductionFailure> failures
    ) {
        EconomicActorDefinition actor = dependencies.actorManager().find(plan.producerActorId()).orElse(null);
        if (actor == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_ACTOR,
                    "Production plan references an unknown actor", plan.producerActorId().value()));
            return;
        }
        if (!actor.industryId().equals(process.owningIndustryId())) {
            failures.add(failure(ProductionFailureCode.ACTOR_INDUSTRY_MISMATCH,
                    "Producer actor industry does not match process industry", actor.id().value()));
        }
        process.allRequiredCapabilities().stream()
                .filter(capability -> !actor.hasCapability(capability))
                .forEach(capability -> failures.add(failure(
                        ProductionFailureCode.ACTOR_CAPABILITY_MISSING,
                        "Producer actor lacks capability " + capability.serializedName(),
                        actor.id().value()
                )));
    }

    private void validateBusinessReference(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            List<ProductionFailure> failures
    ) {
        if (process.businessRequirement().businessRequired() && plan.businessId().isEmpty()) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_BUSINESS,
                    "Production process requires a business runtime", plan.id().value()));
        }
        plan.businessId().ifPresent(id -> {
            if (dependencies.businessRuntimeManager().registry().find(id).isEmpty()) {
                failures.add(failure(ProductionFailureCode.UNKNOWN_BUSINESS,
                        "Production plan references an unknown business", id.value()));
            }
        });
    }

    private void validateBindings(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            List<ProductionFailure> failures
    ) {
        Set<ProductionLineId> knownInputs = new HashSet<>();
        Set<ProductionLineId> knownOutputs = new HashSet<>();
        process.inputs().forEach(input -> knownInputs.add(input.id()));
        process.outputs().forEach(output -> knownOutputs.add(output.id()));
        for (ProductionInputDefinition input : process.inputs()) {
            ProductionInventoryBinding binding = binding(plan, input.id(), ProductionBindingDirection.INPUT);
            if (binding == null) {
                failures.add(failure(ProductionFailureCode.INPUT_BINDING_MISSING,
                        "Production input line has no inventory binding", input.id().value()));
            } else {
                validateBinding(plan, input.goodId(), input.unit(), input.sourceConstraint(), binding, failures);
            }
        }
        for (ProductionOutputDefinition output : process.outputs()) {
            ProductionInventoryBinding binding = binding(plan, output.id(), ProductionBindingDirection.OUTPUT);
            if (binding == null) {
                failures.add(failure(ProductionFailureCode.OUTPUT_BINDING_MISSING,
                        "Production output line has no inventory binding", output.id().value()));
            } else {
                validateBinding(plan, output.goodId(), output.unit(), output.destinationConstraint(), binding, failures);
            }
        }
        plan.inventoryBindings().stream()
                .filter(binding -> binding.direction() == ProductionBindingDirection.INPUT
                        ? !knownInputs.contains(binding.lineId()) : !knownOutputs.contains(binding.lineId()))
                .forEach(binding -> failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        "Production plan binds an unknown line", binding.lineId().value())));
    }

    private void validateBinding(
            ProductionPlanDefinition plan,
            com.butchercraft.world.goods.GoodId expectedGood,
            com.butchercraft.world.goods.UnitOfMeasure expectedUnit,
            ProductionInventoryConstraint constraint,
            ProductionInventoryBinding binding,
            List<ProductionFailure> failures
    ) {
        if (!binding.expectedGoodId().equals(expectedGood)) {
            failures.add(failure(
                    binding.direction() == ProductionBindingDirection.INPUT
                            ? ProductionFailureCode.INPUT_GOOD_MISMATCH
                            : ProductionFailureCode.OUTPUT_GOOD_MISMATCH,
                    "Production binding Good does not match the process line",
                    binding.lineId().value()
            ));
        }
        if (binding.expectedUnit() != expectedUnit) {
            failures.add(failure(ProductionFailureCode.UNIT_MISMATCH,
                    "Production binding unit does not match the process line", binding.lineId().value()));
        }
        InventoryContainer inventory = dependencies.inventoryManager().find(binding.inventoryId()).orElse(null);
        if (inventory == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_INVENTORY,
                    "Production binding references an unknown inventory", binding.inventoryId().value()));
        } else {
            if (!inventory.ownerActorId().equals(plan.producerActorId())) {
                failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        "Production inventory is not owned by the producer actor", binding.inventoryId().value()));
            }
            if (!constraint.accepts(inventory.inventoryType())) {
                failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        "Production inventory type violates the process constraint", binding.inventoryId().value()));
            }
        }
    }

    private void validateEconomicContext(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            List<ProductionFailure> failures
    ) {
        EconomicContractDefinition contract = plan.governingContractId()
                .flatMap(dependencies.contractManager()::find).orElse(null);
        if (plan.governingContractId().isPresent() && contract == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_CONTRACT,
                    "Production plan references an unknown contract",
                    plan.governingContractId().orElseThrow().value()));
        }
        EconomicOrderDefinition order = plan.requestingOrderId()
                .flatMap(dependencies.orderManager()::find).orElse(null);
        if (plan.requestingOrderId().isPresent() && order == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_ORDER,
                    "Production plan references an unknown order",
                    plan.requestingOrderId().orElseThrow().value()));
        }
        if (order != null) {
            if (plan.governingContractId().isPresent()
                    && !order.governingContractId().equals(plan.governingContractId())) {
                failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        "Production order and contract references do not match", order.id().value()));
            }
            boolean outputRequested = process.outputs().stream().anyMatch(output -> order.lines().stream()
                    .anyMatch(line -> line.goodId().equals(output.goodId())
                            && line.unitOfMeasure() == output.unit()));
            if (!outputRequested) {
                failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                        "Production outputs do not satisfy any referenced order line", order.id().value()));
            }
        }
        if (contract != null && !contract.principalActorId().equals(plan.producerActorId())
                && !contract.counterpartyActorId().equals(plan.producerActorId())) {
            failures.add(failure(ProductionFailureCode.VALIDATION_FAILED,
                    "Producer actor is not a party to the referenced contract", contract.id().value()));
        }
    }

    private void validateWorkforceDefinition(
            ProductionWorkforceRequirement requirement,
            List<ProductionFailure> failures
    ) {
        requirement.workforceDefinitionId().ifPresent(id -> {
            WorkforceDefinition definition = dependencies.workforceManager().registry().find(id).orElse(null);
            if (definition == null) {
                failures.add(failure(ProductionFailureCode.UNKNOWN_WORKFORCE_REFERENCE,
                        "Production process references an unknown workforce", id.value()));
                return;
            }
            Set<com.butchercraft.world.workforce.PositionId> available = definition.positions().stream()
                    .map(WorkforcePosition::positionId).collect(java.util.stream.Collectors.toSet());
            requirement.requiredPositions().stream().filter(position -> !available.contains(position))
                    .forEach(position -> failures.add(failure(ProductionFailureCode.REQUIRED_POSITION_MISSING,
                            "Production workforce position does not resolve", position.value())));
            for (WorkforcePosition position : definition.positions()) {
                if (requirement.requiredPositions().contains(position.positionId())) {
                    if (!position.requiredCertifications().containsAll(requirement.requiredCertifications())) {
                        failures.add(failure(ProductionFailureCode.REQUIRED_CERTIFICATION_MISSING,
                                "Production workforce certification is unavailable", position.positionId().value()));
                    }
                    requirement.minimumSkillLevel().ifPresent(level -> {
                        if (position.requiredSkillLevel().ordinal() < level.ordinal()) {
                            failures.add(failure(ProductionFailureCode.REQUIRED_SKILL_MISSING,
                                    "Production workforce skill level is insufficient", position.positionId().value()));
                        }
                    });
                }
            }
        });
    }

    private void validateBusinessRuntime(
            ProductionPlanDefinition plan,
            ProductionBusinessRequirement requirement,
            List<ProductionFailure> failures
    ) {
        if (!requirement.businessRequired()) return;
        BusinessRuntimeState state = plan.businessId()
                .flatMap(id -> dependencies.businessRuntimeManager().registry().find(id)).orElse(null);
        if (state == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_BUSINESS,
                    "Production business runtime is unavailable", plan.id().value()));
            return;
        }
        if (requirement.mustBeOperational() && state.operationalStatus() != BusinessOperationalStatus.OPERATING) {
            failures.add(failure(ProductionFailureCode.BUSINESS_NOT_OPERATIONAL,
                    "Production business is not operating", state.businessId().value()));
        }
        if (requirement.mustBeOpen() && !state.open()) {
            failures.add(failure(ProductionFailureCode.BUSINESS_CLOSED,
                    "Production business is closed", state.businessId().value()));
        }
        if (requirement.activeShiftRequired() && state.activeShiftId().isEmpty()) {
            failures.add(failure(ProductionFailureCode.NO_ACTIVE_SHIFT,
                    "Production business has no active shift", state.businessId().value()));
        }
        if (requirement.maintenanceMustBeFalse() && state.maintenance()) {
            failures.add(failure(ProductionFailureCode.BUSINESS_IN_MAINTENANCE,
                    "Production business is in maintenance", state.businessId().value()));
        }
        if (state.activeWorkforce() < requirement.minimumActiveWorkforce()) {
            failures.add(failure(ProductionFailureCode.WORKFORCE_INSUFFICIENT,
                    "Production business active workforce is insufficient", state.businessId().value()));
        }
        if (!requirement.allowedStatuses().isEmpty()
                && !requirement.allowedStatuses().contains(state.operationalStatus())) {
            failures.add(failure(ProductionFailureCode.BUSINESS_NOT_OPERATIONAL,
                    "Production business status is not allowed", state.businessId().value()));
        }
    }

    private void validateWorkforceRuntime(
            ProductionPlanDefinition plan,
            ProductionWorkforceRequirement requirement,
            List<ProductionFailure> failures
    ) {
        if (!requirement.required()) return;
        EconomicActorRuntime actor = dependencies.actorManager().runtimeFor(plan.producerActorId()).orElse(null);
        com.butchercraft.world.workforce.WorkforceDefinitionId workforceId =
                requirement.workforceDefinitionId().orElseGet(() -> actor == null
                        ? null : actor.assignedWorkforce().orElse(null));
        if (workforceId == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_WORKFORCE_REFERENCE,
                    "Producer actor has no workforce assignment", plan.producerActorId().value()));
            return;
        }
        WorkforceDefinition definition = dependencies.workforceManager().registry().find(workforceId).orElse(null);
        if (definition == null) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_WORKFORCE_REFERENCE,
                    "Producer workforce definition is unavailable", workforceId.value()));
            return;
        }
        if (plan.businessId().isPresent() && !definition.businessId().equals(plan.businessId().orElseThrow())) {
            failures.add(failure(ProductionFailureCode.UNKNOWN_WORKFORCE_REFERENCE,
                    "Producer workforce belongs to a different business", workforceId.value()));
        }
        plan.businessId().flatMap(id -> dependencies.businessRuntimeManager().registry().find(id))
                .ifPresent(state -> {
                    if (state.activeWorkforce() < requirement.minimumActiveWorkers()) {
                        failures.add(failure(ProductionFailureCode.WORKFORCE_INSUFFICIENT,
                                "Active workforce is below the production minimum", workforceId.value()));
                    }
                });
    }

    private static ProductionInventoryBinding binding(
            ProductionPlanDefinition plan,
            ProductionLineId lineId,
            ProductionBindingDirection direction
    ) {
        return plan.inventoryBindings().stream()
                .filter(candidate -> candidate.direction() == direction && candidate.lineId().equals(lineId))
                .findFirst().orElse(null);
    }

    private static ProductionFailure failure(ProductionFailureCode code, String message, String reference) {
        return ProductionFailure.of(code, message, reference);
    }

    private static String message(RuntimeException exception, String fallback) {
        return exception.getMessage() == null ? fallback : exception.getMessage();
    }
}
