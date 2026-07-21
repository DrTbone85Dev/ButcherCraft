package com.butchercraft.workstation;

import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.machine.packaging.PackagingTableWorkstation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationProcessingControllerTest {
    @Test
    void controllerStartsOnceAndDoesNotCompleteEarly() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());

        harness.tick();

        assertEquals(WorkstationState.PROCESSING, harness.controller.state());
        assertEquals(0, harness.controller.elapsedTicks());
        assertEquals(60, harness.controller.totalTicks());
        assertTrue(harness.inventory.output().isEmpty());

        for (int i = 0; i < 59; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.PROCESSING, harness.controller.state());
        assertEquals(59, harness.controller.elapsedTicks());
        assertTrue(harness.inventory.output().isEmpty());
    }

    @Test
    void completionConsumesInputOnceAndProducesAdjustedGroundBeef() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        harness.tick();

        for (int i = 0; i < 60; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.COMPLETE, harness.controller.state());
        assertTrue(harness.inventory.input().isEmpty());
        var data = ProductStackAdapter.readProductData(harness.inventory.output()).orThrow();
        assertEquals("butchercraft:ground_beef", data.productTypeId());
        assertEquals(900, data.quantityValue());
        assertEquals(695, data.qualityScore());

        harness.tick();
        assertEquals(900, ProductStackAdapter.readProductData(harness.inventory.output()).orThrow().quantityValue());
    }

    @Test
    void outputObstructionBlocksCompletionSafely() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        harness.tick();
        harness.inventory.setOutputInternal(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());

        for (int i = 0; i < 60; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED, harness.controller.lastFailure().orElseThrow().code());
        assertFalse(harness.inventory.input().isEmpty());
        assertFalse(harness.inventory.output().isEmpty());
    }

    @Test
    void cancellationPreservesRecoverableInput() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        harness.tick();

        harness.controller.cancelPreservingInput();

        assertEquals(WorkstationState.IDLE, harness.controller.state());
        assertFalse(harness.inventory.input().isEmpty());
        assertTrue(harness.inventory.output().isEmpty());
    }

    @Test
    void saveLoadMidProcessPreservesProgressAndCompletes() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        harness.tick();
        for (int i = 0; i < 10; i++) {
            harness.tick();
        }

        Harness restored = Harness.restoreFrom(harness);

        assertEquals(WorkstationState.PROCESSING, restored.controller.state());
        assertEquals(10, restored.controller.elapsedTicks());
        assertEquals(60, restored.controller.totalTicks());
        assertFalse(restored.inventory.input().isEmpty());

        for (int i = 0; i < 50; i++) {
            restored.tick();
        }

        assertEquals(WorkstationState.COMPLETE, restored.controller.state());
        assertTrue(restored.inventory.input().isEmpty());
        var data = ProductStackAdapter.readProductData(restored.inventory.output()).orThrow();
        assertEquals("butchercraft:ground_beef", data.productTypeId());
        assertEquals(900, data.quantityValue());
    }

    @Test
    void saveLoadAfterCompletionDoesNotDuplicateOutput() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        harness.tick();
        for (int i = 0; i < 60; i++) {
            harness.tick();
        }

        Harness restored = Harness.restoreFrom(harness);
        restored.tick();

        assertEquals(WorkstationState.COMPLETE, restored.controller.state());
        assertTrue(restored.inventory.input().isEmpty());
        assertEquals(1, restored.inventory.output().getCount());
        assertEquals(900, ProductStackAdapter.readProductData(restored.inventory.output()).orThrow().quantityValue());
    }

    @Test
    void malformedPersistedActiveStateStopsProcessingAndPreservesInventory() {
        Harness restored = Harness.create();
        restored.inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        CompoundTag malformedControllerTag = new CompoundTag();
        malformedControllerTag.putString("State", WorkstationState.PROCESSING.name());
        malformedControllerTag.putInt("ElapsedTicks", 12);

        restored.controller.loadAdditional(malformedControllerTag, RegistryAccess.EMPTY);

        assertEquals(WorkstationState.ERROR, restored.controller.state());
        assertEquals(WorkstationFailureCode.INVALID_WORKSTATION_STATE, restored.controller.lastFailure().orElseThrow().code());
        assertFalse(restored.inventory.input().isEmpty());
        assertTrue(restored.inventory.output().isEmpty());
    }

    @Test
    void processingControllerRejectsMultiInputInventoryLayouts() {
        WorkstationInventory inventory = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});

        assertThrows(IllegalArgumentException.class, () -> new WorkstationProcessingController(
                inventory,
                PackagingTableWorkstation.capability(),
                (registryAccess, capability, stack) -> WorkstationOperationResolution.failure(WorkstationFailure.of(
                        WorkstationFailureCode.NO_COMPATIBLE_OPERATION,
                        "No packaging operation exists"
                )),
                DevelopmentProductItemMappings.fixtureMapping(),
                () -> {}
        ));
    }

    private record Harness(
            WorkstationInventory inventory,
            WorkstationProcessingController controller,
            AtomicInteger changes
    ) {
        static Harness create() {
            AtomicInteger changes = new AtomicInteger();
            WorkstationCapability workstationCapability = DevelopmentWorkstationFixtures.capability();
            WorkstationInventory inventory = new WorkstationInventory(workstationCapability, changes::incrementAndGet);
            WorkstationOperationLookup lookup = (registryAccess, capability, stack) ->
                    new WorkstationOperationResolver().resolve(
                            com.butchercraft.processing.definition.BuiltInProcessingDefinitions.builtInView(),
                            capability,
                            stack
                    );
            WorkstationProcessingController controller = new WorkstationProcessingController(
                    inventory,
                    workstationCapability,
                    lookup,
                    DevelopmentProductItemMappings.fixtureMapping(),
                    changes::incrementAndGet
            );
            inventory.setInputLocked(controller::inputLocked);
            inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
            return new Harness(inventory, controller, changes);
        }

        static Harness restoreFrom(Harness source) {
            CompoundTag inventoryTag = source.inventory.serializeNBT(RegistryAccess.EMPTY);
            CompoundTag controllerTag = new CompoundTag();
            source.controller.saveAdditional(controllerTag, RegistryAccess.EMPTY);

            Harness restored = create();
            restored.inventory.deserializeNBT(RegistryAccess.EMPTY, inventoryTag);
            restored.controller.loadAdditional(controllerTag, RegistryAccess.EMPTY);
            return restored;
        }

        void tick() {
            controller.serverTick(null);
        }
    }
}
