package com.butchercraft.machine.bandsaw;

import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsawProcessingControllerTest {
    @Test
    void completionProducesAllForequarterOutputsInOrderExactlyOnce() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance());
        harness.tick();

        for (int i = 0; i < 120; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.COMPLETE, harness.controller.state());
        assertTrue(harness.inventory.input().isEmpty());
        assertEquals(List.of(
                        "butchercraft:beef_chuck",
                        "butchercraft:beef_rib",
                        "butchercraft:beef_packer_brisket",
                        "butchercraft:beef_plate",
                        "butchercraft:beef_shank",
                        "butchercraft:beef_trim",
                        "butchercraft:beef_fat",
                        "butchercraft:beef_bone"
                ),
                harness.inventory.outputs().stream()
                        .map(ProductStackAdapter::readProductData)
                        .map(result -> result.orThrow().productTypeId())
                        .toList());
        assertEquals(List.of(30_000L, 10_000L, 10_000L, 10_000L, 5_000L, 15_000L, 5_000L, 10_000L),
                harness.inventory.outputs().stream()
                        .map(ProductStackAdapter::readProductData)
                        .map(result -> result.orThrow().quantityValue())
                        .toList());

        harness.tick();
        assertEquals(8, harness.inventory.outputs().stream().filter(stack -> !stack.isEmpty()).count());
        assertEquals(30_000, ProductStackAdapter.readProductData(harness.inventory.output()).orThrow().quantityValue());
    }

    @Test
    void outputObstructionBlocksMultiOutputCompletionWithoutConsumingInput() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance());
        harness.tick();
        harness.inventory.setOutputInternal(ModItems.BEEF_CHUCK_TEST.get().getDefaultInstance());

        for (int i = 0; i < 120; i++) {
            harness.tick();
        }

        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED, harness.controller.lastFailure().orElseThrow().code());
        assertFalse(harness.inventory.input().isEmpty());
        assertFalse(harness.inventory.output().isEmpty());
    }

    @Test
    void saveLoadAfterCompletionDoesNotDuplicateMultiOutputs() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_FOREQUARTER_TEST.get().getDefaultInstance());
        harness.tick();
        for (int i = 0; i < 120; i++) {
            harness.tick();
        }

        Harness restored = Harness.restoreFrom(harness);
        restored.tick();

        assertEquals(WorkstationState.COMPLETE, restored.controller.state());
        assertTrue(restored.inventory.input().isEmpty());
        assertEquals(8, restored.inventory.outputs().stream().filter(stack -> !stack.isEmpty()).count());
        assertEquals(95_000L, restored.inventory.outputs().stream()
                .map(ProductStackAdapter::readProductData)
                .mapToLong(result -> result.orThrow().quantityValue())
                .sum());
    }

    private record Harness(
            WorkstationInventory inventory,
            WorkstationProcessingController controller,
            AtomicInteger changes
    ) {
        static Harness create() {
            AtomicInteger changes = new AtomicInteger();
            WorkstationCapability workstationCapability = BandsawWorkstation.capability();
            WorkstationInventory inventory = new WorkstationInventory(workstationCapability, changes::incrementAndGet);
            WorkstationOperationLookup lookup = (registryAccess, capability, stack) ->
                    new WorkstationOperationResolver().resolve(BuiltInProcessingDefinitions.builtInView(), capability, stack);
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
