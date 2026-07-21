package com.butchercraft.machine.packaging;

import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationLookup;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingTableProcessingControllerTest {
    @BeforeEach
    void resetContentSnapshot() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    @Test
    void packagingTablePackagesGroundBeefAndConsumesRequiredSuppliesAtomically() {
        Harness harness = Harness.create();
        harness.insertValidPackagingInputs();

        harness.completeProcessing();

        assertEquals(WorkstationState.COMPLETE, harness.controller.state());
        assertTrue(harness.inventory.getStackInSlot(0).isEmpty());
        assertTrue(harness.inventory.getStackInSlot(1).isEmpty());
        assertTrue(harness.inventory.getStackInSlot(2).isEmpty());
        assertFalse(harness.inventory.output().isEmpty());

        var outputData = ProductStackAdapter.readProductData(harness.inventory.output()).orThrow();
        assertEquals("butchercraft:retail_ground_beef", outputData.productTypeId());
        assertEquals("butchercraft:retail_packaged", outputData.processingStateId());
        assertEquals(900, outputData.quantityValue());
        assertEquals(700, outputData.qualityScore());
        var packaging = outputData.packaging().orElseThrow();
        assertEquals("butchercraft:retail_package", packaging.packagingDefinitionId());
        assertEquals("tray_wrap", packaging.packagingFormatId());
        assertEquals("butchercraft:ground_beef", packaging.sourceProductId());
    }

    @Test
    void missingRequiredSupplyBlocksBeforeProgressAndPreservesInventory() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(0, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
        harness.inventory.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));

        harness.tick();

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.MISSING_REQUIRED_SUPPLY,
                harness.controller.lastFailure().orElseThrow().code());
        assertEquals(0, harness.controller.elapsedTicks());
        assertFalse(harness.inventory.getStackInSlot(0).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(1).isEmpty());
        assertTrue(harness.inventory.getStackInSlot(2).isEmpty());
        assertTrue(harness.inventory.output().isEmpty());
    }

    @Test
    void invalidSupplyBlocksAndConsumesNothing() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(0, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
        harness.inventory.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));
        harness.inventory.setInputInternal(2, new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()));

        harness.tick();

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.INVALID_SUPPLY_ITEM,
                harness.controller.lastFailure().orElseThrow().code());
        assertFalse(harness.inventory.getStackInSlot(0).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(1).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(2).isEmpty());
        assertTrue(harness.inventory.output().isEmpty());
    }

    @Test
    void occupiedOutputBlocksBeforeProcessingAndPreservesInputs() {
        Harness harness = Harness.create();
        harness.insertValidPackagingInputs();
        harness.inventory.setOutputInternal(ModItems.RETAIL_GROUND_BEEF_TEST.get().getDefaultInstance());

        harness.tick();

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED,
                harness.controller.lastFailure().orElseThrow().code());
        assertFalse(harness.inventory.getStackInSlot(0).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(1).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(2).isEmpty());
    }

    @Test
    void outputBlockedAtCompletionDoesNotConsumeProductOrSupplies() {
        Harness harness = Harness.create();
        harness.insertValidPackagingInputs();
        harness.tick();
        harness.inventory.setOutputInternal(ModItems.RETAIL_GROUND_BEEF_TEST.get().getDefaultInstance());

        for (int i = 0; i < 60; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED,
                harness.controller.lastFailure().orElseThrow().code());
        assertFalse(harness.inventory.getStackInSlot(0).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(1).isEmpty());
        assertFalse(harness.inventory.getStackInSlot(2).isEmpty());
    }

    @Test
    void suppliesInsertedAfterMissingSupplyFailureAllowRetry() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(0, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
        harness.inventory.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));
        harness.tick();

        harness.inventory.setInputInternal(2, new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get()));
        harness.controller.onInventoryChanged();
        harness.tick();

        assertEquals(WorkstationState.PROCESSING, harness.controller.state());
    }

    private record Harness(
            WorkstationInventory inventory,
            WorkstationProcessingController controller,
            AtomicInteger changes
    ) {
        static Harness create() {
            ContentSnapshotService.resetToBundledSnapshot();
            AtomicInteger changes = new AtomicInteger();
            WorkstationCapability capability = PackagingTableWorkstation.capability();
            WorkstationInventory inventory = new WorkstationInventory(capability, changes::incrementAndGet);
            WorkstationOperationLookup lookup = (registryAccess, workstationCapability, stack) ->
                    new WorkstationOperationResolver().resolve(
                            com.butchercraft.processing.definition.BuiltInProcessingDefinitions.builtInView(),
                            workstationCapability,
                            stack
                    );
            WorkstationProcessingController controller = new WorkstationProcessingController(
                    inventory,
                    capability,
                    lookup,
                    DevelopmentProductItemMappings.fixtureMapping(),
                    new PackagingTableExecutionStrategy(),
                    changes::incrementAndGet
            );
            inventory.setInputLocked(controller::inputLocked);
            inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
            inventory.setInputSlotValidator((slot, stack) -> slot == inventory.firstInputSlot()
                    ? ProductStackAdapter.readProductData(stack).succeeded()
                    : PackagingSupplyItemMappings.isKnownSupplyItem(stack));
            return new Harness(inventory, controller, changes);
        }

        void insertValidPackagingInputs() {
            inventory.setInputInternal(0, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
            inventory.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));
            inventory.setInputInternal(2, new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get()));
        }

        void completeProcessing() {
            tick();
            for (int i = 0; i < 60; i++) {
                tick();
            }
        }

        void tick() {
            controller.serverTick(null);
        }
    }
}
