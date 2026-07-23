package com.butchercraft.world.production;

import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionMetadata;
import com.butchercraft.world.transaction.TransactionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ProductionTransactionFactory {
    private final ProductionDependencies dependencies;

    public ProductionTransactionFactory(ProductionDependencies dependencies) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }

    public ProductionOperationResult<EconomicTransaction> build(
            ProductionRunSnapshot run,
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            SimulationWorkId workId,
            long tick
    ) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(process, "process");
        Objects.requireNonNull(workId, "workId");
        try {
            List<InventoryChange> changes = new ArrayList<>();
            for (ProductionInputDefinition input : process.inputs()) {
                if (input.consumptionPolicy() == ConsumptionPolicy.REQUIRE_ONLY) continue;
                ProductionInventoryBinding binding = requireBinding(
                        plan, input.id(), ProductionBindingDirection.INPUT
                );
                long quantity = ProductionQuantityCalculator.toInventoryUnits(
                        ProductionQuantityCalculator.scaleInput(input.quantityPerBatch(), plan.batchCount())
                );
                changes.addAll(removals(binding, input, quantity));
            }
            for (ProductionOutputDefinition output : process.outputs()) {
                ProductionInventoryBinding binding = requireBinding(
                        plan, output.id(), ProductionBindingDirection.OUTPUT
                );
                long quantity = ProductionQuantityCalculator.toInventoryUnits(
                        ProductionQuantityCalculator.scaleOutput(
                                output.baseQuantityPerBatch(), plan.batchCount(), output.yieldRatio()
                        )
                );
                changes.add(InventoryChange.add(
                        binding.inventoryId(),
                        new InventoryEntry(output.goodId(), quantity, output.unit())
                ));
            }
            if (changes.isEmpty()) {
                return ProductionOperationResult.rejected(ProductionFailure.of(
                        ProductionFailureCode.TRANSACTION_BUILD_FAILED,
                        "Production completion has no inventory changes",
                        run.id().value()
                ));
            }
            com.butchercraft.world.inventory.InventoryChangeValidation validation =
                    dependencies.inventoryManager().validateChanges(changes, tick);
            if (!validation.isAllowed()) {
                return ProductionOperationResult.rejected(ProductionFailure.of(
                        mapInventoryFailure(validation.code()),
                        validation.message(),
                        run.id().value()
                ));
            }
            ProductionOutputDefinition summary = process.outputs().getFirst();
            long summaryQuantity = ProductionQuantityCalculator.toInventoryUnits(
                    ProductionQuantityCalculator.scaleOutput(
                            summary.baseQuantityPerBatch(), plan.batchCount(), summary.yieldRatio()
                    )
            );
            TransactionId transactionId = TransactionId.of(
                    run.id().value() + "/completion_attempt_" + Math.max(1, run.executionAttemptCount())
            );
            EconomicTransaction transaction = EconomicTransaction.builder()
                    .id(transactionId)
                    .type(TransactionType.PRODUCTION)
                    .sourceActorId(plan.producerActorId())
                    .destinationActorId(plan.producerActorId())
                    .goodId(summary.goodId())
                    .quantity(summaryQuantity)
                    .unitOfMeasure(summary.unit())
                    .simulationTick(tick)
                    .metadata(TransactionMetadata.builder()
                            .reason("production_completion")
                            .referenceId(run.id().value())
                            .externalSystem("butchercraft:production")
                            .comments(context(plan, process, workId, tick))
                            .build())
                    .inventoryChangePlan(changes)
                    .build();
            return ProductionOperationResult.accepted(transaction);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return ProductionOperationResult.rejected(ProductionFailure.of(
                    ProductionFailureCode.TRANSACTION_BUILD_FAILED,
                    exception.getMessage() == null ? "Production completion transaction could not be built"
                            : exception.getMessage(),
                    run.id().value()
            ));
        }
    }

    private List<InventoryChange> removals(
            ProductionInventoryBinding binding,
            ProductionInputDefinition input,
            long required
    ) {
        InventoryRuntime runtime = dependencies.inventoryManager().runtimeFor(binding.inventoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown production input inventory: " + binding.inventoryId().value()
                ));
        List<InventoryChange> changes = new ArrayList<>();
        long remaining = required;
        for (InventoryEntry entry : runtime.entries()) {
            if (remaining == 0L) break;
            if (!entry.goodId().equals(input.goodId()) || entry.unitOfMeasure() != input.unit()) continue;
            long quantity = Math.min(remaining, entry.quantity());
            if (quantity > 0L) {
                changes.add(InventoryChange.remove(binding.inventoryId(), entry.withQuantity(quantity)));
                remaining -= quantity;
            }
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException("Production input quantity changed before completion");
        }
        return changes;
    }

    private static ProductionInventoryBinding requireBinding(
            ProductionPlanDefinition plan,
            ProductionLineId lineId,
            ProductionBindingDirection direction
    ) {
        return plan.inventoryBindings().stream()
                .filter(binding -> binding.direction() == direction && binding.lineId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing production binding: " + lineId.value()));
    }

    private static ProductionFailureCode mapInventoryFailure(
            com.butchercraft.world.inventory.InventoryChangeCode code
    ) {
        return switch (code) {
            case INSUFFICIENT_QUANTITY -> ProductionFailureCode.INSUFFICIENT_INPUT;
            case CAPACITY_EXCEEDED -> ProductionFailureCode.DESTINATION_CAPACITY_EXCEEDED;
            case INVENTORY_UNAVAILABLE -> ProductionFailureCode.INVENTORY_STATUS_INVALID;
            default -> ProductionFailureCode.TRANSACTION_BUILD_FAILED;
        };
    }

    private static String context(
            ProductionPlanDefinition plan,
            ProductionProcessDefinition process,
            SimulationWorkId workId,
            long tick
    ) {
        StringBuilder value = new StringBuilder()
                .append("plan=").append(plan.id().value())
                .append(";process=").append(process.id().value())
                .append(";actor=").append(plan.producerActorId().value())
                .append(";work=").append(workId.value())
                .append(";tick=").append(tick);
        plan.requestingOrderId().ifPresent(id -> value.append(";order=").append(id.value()));
        plan.governingContractId().ifPresent(id -> value.append(";contract=").append(id.value()));
        return value.toString();
    }
}
