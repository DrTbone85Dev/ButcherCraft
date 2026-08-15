package com.butchercraft.machine.pattyformer;

import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationOperationStartPolicy;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PattyFormerProcessingControllerTest {
    @Test
    void multipleInputWaitsForExplicitRequestThenConsumesAndMergesOne() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(withCount(ModItems.GROUND_BEEF.get().getDefaultInstance(), 20));
        harness.inventory.setOutputInternal(withCount(ModItems.BEEF_PATTIES.get().getDefaultInstance(), 10));

        for (int tick = 0; tick < 120; tick++) harness.tick();
        assertEquals(20, harness.inventory.input().getCount());
        assertEquals(10, harness.inventory.output().getCount());

        harness.controller.requestProcessing(RegistryAccess.EMPTY);
        for (int tick = 0; tick < 100; tick++) harness.tick();

        assertEquals(WorkstationState.COMPLETE, harness.controller.state());
        assertEquals(19, harness.inventory.input().getCount());
        assertEquals(11, harness.inventory.output().getCount());
        for (int tick = 0; tick < 120; tick++) harness.tick();
        assertEquals(19, harness.inventory.input().getCount());
        assertEquals(11, harness.inventory.output().getCount());
    }

    @Test
    void fullOutputRejectsExplicitOperationWithoutConsumingInput() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(withCount(ModItems.GROUND_BEEF.get().getDefaultInstance(), 10));
        harness.inventory.setOutputInternal(withCount(ModItems.BEEF_PATTIES.get().getDefaultInstance(), 64));

        var result = harness.controller.requestProcessing(RegistryAccess.EMPTY);

        assertFalse(result.accepted());
        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED, harness.controller.lastFailure().orElseThrow().code());
        assertEquals(10, harness.inventory.input().getCount());
        assertEquals(64, harness.inventory.output().getCount());
    }

    private record Harness(WorkstationInventory inventory, WorkstationProcessingController controller) {
        static Harness create() {
            WorkstationInventory inventory = new WorkstationInventory(
                    PattyFormerWorkstation.capability(), PattyFormerWorkstation.slotCapacityPolicy(), () -> {}
            );
            WorkstationProcessingController controller = new WorkstationProcessingController(
                    inventory,
                    PattyFormerWorkstation.capability(),
                    (registries, capability, stack) -> new WorkstationOperationResolver().resolve(
                            BuiltInProcessingDefinitions.builtInView(), capability, stack),
                    DevelopmentProductItemMappings.fixtureMapping(),
                    WorkstationExecutionStrategy.transformation(),
                    WorkstationOperationStartPolicy.EXPLICIT_REQUEST,
                    () -> {}
            );
            inventory.setInputLocked(controller::inputLocked);
            inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
            return new Harness(inventory, controller);
        }

        void tick() {
            controller.serverTick(null);
        }
    }

    private static ItemStack withCount(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }
}
