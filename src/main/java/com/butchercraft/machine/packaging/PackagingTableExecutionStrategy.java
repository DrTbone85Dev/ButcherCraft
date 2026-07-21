package com.butchercraft.machine.packaging;

import com.butchercraft.content.ContentSnapshot;
import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.FailureReason;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.engine.transaction.TransactionState;
import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.component.ProductStackPackagingData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.ResolvedWorkstationOperation;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Packaging Table execution bridge that validates and consumes packaging supplies through data definitions.
 */
public final class PackagingTableExecutionStrategy implements WorkstationExecutionStrategy {
    private static final WorkstationExecutionStrategy PROCESSING = WorkstationExecutionStrategy.legacy();

    @Override
    public OperationResult prepare(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        return PROCESSING.prepare(capability, operation);
    }

    @Override
    public OperationResult commit(WorkstationCapability capability, ResolvedWorkstationOperation operation) {
        return PROCESSING.commit(capability, operation);
    }

    @Override
    public OperationResult prepare(
            WorkstationCapability capability,
            ResolvedWorkstationOperation operation,
            WorkstationInventory inventory,
            DevelopmentProductItemMapping outputMapping
    ) {
        OperationResult prepared = PROCESSING.prepare(capability, operation);
        if (!prepared.succeeded()) {
            return prepared;
        }
        Optional<PackagingPlan> plan = packagingPlan(operation, inventory);
        if (plan.isEmpty()) {
            return failure(operation, "packaging_metadata_missing", "Output product does not declare packaging metadata");
        }
        Optional<FailureReason> validation = plan.orElseThrow().validate();
        if (validation.isPresent()) {
            return failure(operation, validation.orElseThrow());
        }
        if (!outputMapping.canCreate(operation.engineOperation().outputProductType())) {
            return failure(operation, "packaging_output_mapping_missing", "No development item mapping exists for packaged output");
        }
        return prepared;
    }

    @Override
    public OperationResult commit(
            WorkstationCapability capability,
            ResolvedWorkstationOperation operation,
            WorkstationInventory inventory,
            DevelopmentProductItemMapping outputMapping
    ) {
        Optional<PackagingPlan> plan = packagingPlan(operation, inventory);
        if (plan.isEmpty()) {
            return failure(operation, "packaging_metadata_missing", "Output product does not declare packaging metadata");
        }
        Optional<FailureReason> validation = plan.orElseThrow().validate();
        if (validation.isPresent()) {
            return failure(operation, validation.orElseThrow());
        }
        return PROCESSING.commit(capability, operation);
    }

    @Override
    public List<Integer> consumedInputSlots(
            WorkstationCapability capability,
            ResolvedWorkstationOperation operation,
            WorkstationInventory inventory
    ) {
        return packagingPlan(operation, inventory)
                .map(PackagingPlan::consumedInputSlots)
                .orElseGet(() -> List.of(inventory.firstInputSlot()));
    }

    @Override
    public Optional<ItemStack> createOutputStack(
            ResolvedWorkstationOperation operation,
            Product outputProduct,
            ItemStack inputStack,
            DevelopmentProductItemMapping outputMapping
    ) {
        Optional<ItemStack> created = outputMapping.createStack(outputProduct);
        if (created.isEmpty()) {
            return Optional.empty();
        }
        Optional<PackagingPlan> plan = packagingPlan(operation, null);
        if (plan.isEmpty()) {
            return Optional.empty();
        }

        ProductDataResult<ProductStackData> inputData = ProductStackAdapter.readProductData(inputStack);
        if (!inputData.succeeded()) {
            return Optional.empty();
        }
        ProductStackPackagingData packagingData = new ProductStackPackagingData(
                plan.orElseThrow().packagingDefinition().id().value(),
                plan.orElseThrow().packagingDefinition().format().id(),
                plan.orElseThrow().sourceProductId().value()
        );
        ProductStackData packagedData = inputData.orThrow()
                .withProduct(outputProduct)
                .withPackaging(packagingData);
        ProductDataResult<ProductStackData> writeResult =
                ProductStackAdapter.writeProductData(created.orElseThrow(), packagedData);
        return writeResult.succeeded() ? created : Optional.empty();
    }

    private static Optional<PackagingPlan> packagingPlan(
            ResolvedWorkstationOperation operation,
            WorkstationInventory inventory
    ) {
        Objects.requireNonNull(operation, "operation");
        if (operation.definition().outputProducts().size() != 1) {
            return Optional.empty();
        }
        var metadata = operation.outputProductDefinition().packaging();
        if (metadata.isEmpty()) {
            return Optional.empty();
        }

        ContentSnapshot snapshot = ContentSnapshotService.currentSnapshot();
        EngineId packagingDefinitionId = EngineId.of(metadata.orElseThrow().definition().toString());
        Optional<PackagingDefinition> packagingDefinition = snapshot.packaging().find(packagingDefinitionId);
        if (packagingDefinition.isEmpty()) {
            return Optional.of(PackagingPlan.failure(
                    "packaging_definition_missing",
                    "Packaging definition is not loaded: " + packagingDefinitionId.value()
            ));
        }

        EngineId sourceProductId = EngineId.of(metadata.orElseThrow().sourceProduct().toString());
        if (!sourceProductId.equals(operation.inputProduct().typeId())) {
            return Optional.of(PackagingPlan.failure(
                    "packaging_metadata_missing",
                    "Packaging source product does not match the operation input product"
            ));
        }
        if (snapshot.products().find(sourceProductId).isEmpty()) {
            return Optional.of(PackagingPlan.failure(
                    "packaging_definition_missing",
                    "Packaging source product is not loaded: " + sourceProductId.value()
            ));
        }

        List<ItemStack> supplyStacks = inventory == null
                ? List.of()
                : inventory.inputs().stream().skip(1).toList();
        List<Integer> supplySlots = inventory == null
                ? List.of()
                : inputSlotsAfterPrimary(inventory);

        return Optional.of(new PackagingPlan(
                packagingDefinition.orElseThrow(),
                sourceProductId,
                supplyStacks,
                supplySlots,
                inventory == null ? 1 : inventory.inputSlotCount()
        ));
    }

    private static List<Integer> inputSlotsAfterPrimary(WorkstationInventory inventory) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = inventory.firstInputSlot() + 1; slot < inventory.firstOutputSlot(); slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private static OperationResult failure(ResolvedWorkstationOperation operation, FailureReason reason) {
        return OperationResult.failure(
                operation.inputProduct(),
                TransactionState.REJECTED,
                reason,
                Optional.empty(),
                List.of(),
                List.of()
        );
    }

    private static OperationResult failure(ResolvedWorkstationOperation operation, String code, String message) {
        return failure(operation, new FailureReason(code, message));
    }

    private record PackagingPlan(
            PackagingDefinition packagingDefinition,
            EngineId sourceProductId,
            List<ItemStack> supplyStacks,
            List<Integer> supplySlots,
            int inputSlotCount,
            FailureReason failure
    ) {
        PackagingPlan(
                PackagingDefinition packagingDefinition,
                EngineId sourceProductId,
                List<ItemStack> supplyStacks,
                List<Integer> supplySlots,
                int inputSlotCount
        ) {
            this(
                    Objects.requireNonNull(packagingDefinition, "packagingDefinition"),
                    Objects.requireNonNull(sourceProductId, "sourceProductId"),
                    List.copyOf(Objects.requireNonNull(supplyStacks, "supplyStacks")),
                    List.copyOf(Objects.requireNonNull(supplySlots, "supplySlots")),
                    inputSlotCount,
                    null
            );
        }

        static PackagingPlan failure(String code, String message) {
            return new PackagingPlan(null, null, List.of(), List.of(), 0, new FailureReason(code, message));
        }

        Optional<FailureReason> validate() {
            if (failure != null) {
                return Optional.of(failure);
            }
            if (packagingDefinition.requiredSupplyItems().size() > Math.max(0, inputSlotCount - 1)) {
                return Optional.of(new FailureReason(
                        "missing_required_supply",
                        "Packaging definition requires more supply items than this workstation can hold"
                ));
            }

            Set<EngineId> required = new LinkedHashSet<>(packagingDefinition.requiredSupplyItems());
            List<EngineId> present = supplyStacks.stream()
                    .filter(stack -> !stack.isEmpty())
                    .map(PackagingSupplyItemMappings::identify)
                    .map(optional -> optional.orElse(null))
                    .toList();
            if (present.stream().anyMatch(Objects::isNull)) {
                return Optional.of(new FailureReason(
                        "invalid_supply_item",
                        "One or more packaging supply slots contains an invalid supply item"
                ));
            }
            if (present.size() != required.size() || !required.containsAll(present)) {
                return Optional.of(new FailureReason(
                        "missing_required_supply",
                        "Required packaging supplies are missing"
                ));
            }
            return Optional.empty();
        }

        List<Integer> consumedInputSlots() {
            ArrayList<Integer> consumed = new ArrayList<>();
            consumed.add(0);
            for (int index = 0; index < supplyStacks.size(); index++) {
                if (!supplyStacks.get(index).isEmpty()) {
                    consumed.add(supplySlots.get(index));
                }
            }
            return List.copyOf(consumed);
        }
    }
}
