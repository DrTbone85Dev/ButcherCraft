package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryRegistry;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.inventory.InventoryStatus;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.butchercraft.world.inventory.InventoryType;
import com.butchercraft.world.inventory.StorageCapacity;
import com.butchercraft.world.inventory.StorageNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionExecutionTest {
    @Test
    void managerAppliesAddRemoveTransferAndAdjustmentThroughExecutor() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager manager = new TransactionManager(inventory);

        assertTrue(manager.submit(TransactionTestFixtures.beefTransaction(
                "test:add", TransactionType.INVENTORY_ADD, 5L, 26L
        )).success());
        assertTrue(manager.submit(TransactionTestFixtures.beefTransaction(
                "test:remove", TransactionType.INVENTORY_REMOVE, 3L, 27L
        )).success());
        TransactionResult transfer = manager.submit(TransactionTestFixtures.beefTransaction(
                "test:transfer", TransactionType.INVENTORY_TRANSFER, 4L, 28L
        ));
        assertTrue(transfer.success());
        assertEquals(2, transfer.appliedChanges().size());
        assertTrue(manager.submit(TransactionTestFixtures.beefTransaction(
                "test:adjust", TransactionType.INVENTORY_ADJUSTMENT, 2L, 29L
        )).success());

        assertEquals(20L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
        assertEquals(4L, inventory.quantityIn(InventoryTestFixtures.GRAIN_INVENTORY, InventoryTestFixtures.BEEF));
        assertEquals(4, manager.size());
        assertEquals(4, manager.findByStatus(TransactionStatus.APPLIED).size());
    }

    @Test
    void executorRequiresTheMatchingPreviouslyAcceptedValidation() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionValidator validator = new TransactionValidator(inventory);
        TransactionExecutor executor = new TransactionExecutor(inventory);
        EconomicTransaction pending = TransactionTestFixtures.beefTransaction(
                "test:accepted", TransactionType.INVENTORY_ADD, 5L, 26L
        );
        TransactionValidation accepted = validator.validateForSubmission(pending);

        assertFalse(executor.execute(pending, accepted).success());
        assertFalse(executor.execute(
                pending.withStatus(TransactionStatus.VALIDATED),
                TransactionValidation.rejected(
                        pending.id(),
                        TransactionFailureCode.VALIDATION_FAILED,
                        "Not accepted"
                )
        ).success());
        assertEquals(20L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));

        assertTrue(executor.execute(pending.withStatus(TransactionStatus.VALIDATED), accepted).success());
        assertEquals(25L, inventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
    }

    @Test
    void failedTransferIsAtomicWhenDestinationCapacityIsExceeded() {
        InventoryManager inventory = boundedTransferManager();
        TransactionManager manager = new TransactionManager(inventory);
        EconomicTransaction transfer = EconomicTransaction.builder()
                .id(TransactionId.of("test:bounded_transfer"))
                .type(TransactionType.INVENTORY_TRANSFER)
                .sourceActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .sourceInventoryId(InventoryId.of("test:source"))
                .destinationInventoryId(InventoryId.of("test:target"))
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(11L)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(1L)
                .build();

        TransactionResult result = manager.submit(transfer);

        assertFalse(result.success());
        assertEquals(TransactionFailureCode.CAPACITY_EXCEEDED, result.failureCode().orElseThrow());
        assertEquals(20L, inventory.quantityIn(InventoryId.of("test:source"), InventoryTestFixtures.BEEF));
        assertEquals(0L, inventory.quantityIn(InventoryId.of("test:target"), InventoryTestFixtures.BEEF));
        assertEquals(TransactionStatus.REJECTED, manager.find(transfer.id()).orElseThrow().status());
    }

    @Test
    void duplicateAndStructurallyInvalidSubmissionsDoNotEnterHistory() {
        TransactionManager manager = new TransactionManager(TransactionTestFixtures.manager());
        EconomicTransaction valid = TransactionTestFixtures.beefTransaction(
                "test:once", TransactionType.INVENTORY_ADD, 1L, 26L
        );
        assertTrue(manager.submit(valid).success());
        assertFalse(manager.submit(valid).success());
        assertFalse(manager.submit(EconomicTransaction.builder()
                .id(TransactionId.of("test:unknown"))
                .type(TransactionType.INVENTORY_ADD)
                .destinationInventoryId(InventoryId.of("test:missing"))
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(1L)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(27L)
                .build()).success());
        assertEquals(1, manager.size());
    }

    private static InventoryManager boundedTransferManager() {
        StorageNode sourceNode = StorageNode.builder()
                .id("test:source_node")
                .displayName("Source")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .build();
        StorageNode targetNode = StorageNode.builder()
                .id("test:target_node")
                .displayName("Target")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .build();
        InventoryContainer source = InventoryContainer.builder()
                .id("test:source")
                .displayName("Source")
                .ownerActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .storageNodeId(sourceNode.id())
                .inventoryType(InventoryType.WAREHOUSE)
                .build();
        InventoryContainer target = InventoryContainer.builder()
                .id("test:target")
                .displayName("Target")
                .ownerActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .storageNodeId(targetNode.id())
                .inventoryType(InventoryType.WAREHOUSE)
                .capacity(StorageCapacity.builder().maximumWeight(10, UnitOfMeasure.POUND).build())
                .build();
        InventoryRegistry registry = InventoryRegistry.of(
                List.of(source, target),
                List.of(sourceNode, targetNode),
                InventoryTestFixtures.goods(),
                InventoryTestFixtures.actors()
        );
        InventoryRuntime sourceRuntime = new InventoryRuntime(
                source.id(),
                InventoryStatus.ACTIVE,
                List.of(new InventoryEntry(InventoryTestFixtures.BEEF, 20L, UnitOfMeasure.POUND)),
                0L,
                com.butchercraft.world.inventory.InventorySchema.CURRENT_VERSION
        );
        return new InventoryManager(registry, List.of(sourceRuntime));
    }
}
