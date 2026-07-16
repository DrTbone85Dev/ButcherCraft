package com.butchercraft.machine.grinder;

import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationLookup;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderProcessingControllerTest {
    @Test
    void inputStartsProcessingAndDoesNotCompleteEarly() {
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
    void completionProducesAdjustedGroundBeefExactlyOnce() {
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
        assertEquals("butchercraft:beef", data.sourceCategoryId());
        assertEquals(900, data.quantityValue());
        assertEquals(695, data.qualityScore());

        harness.tick();
        assertEquals(1, harness.inventory.output().getCount());
        assertEquals(900, ProductStackAdapter.readProductData(harness.inventory.output()).orThrow().quantityValue());
    }

    @Test
    void completionProducesAdjustedPorkAndBisonThroughSameControllerPath() {
        assertCompletesTo(
                ModItems.PORK_TRIM_TEST.get().getDefaultInstance(),
                "butchercraft:ground_pork",
                "butchercraft:pork"
        );
        assertCompletesTo(
                ModItems.BISON_TRIM_TEST.get().getDefaultInstance(),
                "butchercraft:ground_bison",
                "butchercraft:bison"
        );
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
        assertEquals(900, ProductStackAdapter.readProductData(restored.inventory.output()).orThrow().quantityValue());
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

    private static void assertCompletesTo(net.minecraft.world.item.ItemStack input, String outputProductId, String sourceId) {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(input);
        harness.tick();

        for (int i = 0; i < 60; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.COMPLETE, harness.controller.state());
        assertTrue(harness.inventory.input().isEmpty());
        var data = ProductStackAdapter.readProductData(harness.inventory.output()).orThrow();
        assertEquals(outputProductId, data.productTypeId());
        assertEquals(sourceId, data.sourceCategoryId());
        assertEquals(900, data.quantityValue());
        assertEquals(695, data.qualityScore());
    }

    private record Harness(
            WorkstationInventory inventory,
            WorkstationProcessingController controller,
            AtomicInteger changes
    ) {
        static Harness create() {
            AtomicInteger changes = new AtomicInteger();
            WorkstationInventory inventory = new WorkstationInventory(changes::incrementAndGet);
            WorkstationOperationLookup lookup = (registryAccess, capability, stack) ->
                    new WorkstationOperationResolver().resolve(BuiltInProcessingDefinitions.builtInView(), capability, stack);
            WorkstationProcessingController controller = new WorkstationProcessingController(
                    inventory,
                    GrinderWorkstation.capability(),
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
