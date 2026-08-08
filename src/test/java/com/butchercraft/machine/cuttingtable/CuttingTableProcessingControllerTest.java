package com.butchercraft.machine.cuttingtable;

import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.product.integration.DevelopmentProductItemMappings;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationResolution;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuttingTableProcessingControllerTest {
    @Test
    void exactlyOneAcceptedCuttingTableRecipeUsesExistingProducts() {
        var operation = BuiltInProcessingDefinitions.fabricateTBoneSteakOperation();

        assertEquals(BuiltInDefinitionIds.BEEF_SHORT_LOIN, operation.inputProduct());
        assertEquals(CuttingTableWorkstation.CAPABILITY_ID, operation.workstationCapability().orElseThrow());
        assertEquals(List.of(BuiltInDefinitionIds.T_BONE_STEAK, BuiltInDefinitionIds.BEEF_TRIM),
                operation.outputs().stream().map(output -> output.product()).toList());
        assertEquals(1L, BuiltInProcessingDefinitions.builtInView().operations().values().stream()
                .filter(candidate -> candidate.workstationCapability()
                        .filter(CuttingTableWorkstation.CAPABILITY_ID::equals).isPresent())
                .count());
        assertFalse(operation.inputProduct().equals(BuiltInDefinitionIds.BEEF_TRIM));
    }

    @Test
    void resolverAcceptsShortLoinAndRejectsWrongInput() {
        WorkstationOperationResolver resolver = new WorkstationOperationResolver();
        WorkstationOperationResolution valid = resolver.resolve(
                BuiltInProcessingDefinitions.builtInView(),
                CuttingTableWorkstation.capability(),
                ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance()
        );
        WorkstationOperationResolution wrong = resolver.resolve(
                BuiltInProcessingDefinitions.builtInView(),
                CuttingTableWorkstation.capability(),
                ModItems.BEEF_TRIM.get().getDefaultInstance()
        );

        assertTrue(valid.succeeded(), valid.toString());
        assertEquals(BuiltInDefinitionIds.FABRICATE_T_BONE_STEAK,
                valid.operation().orElseThrow().operationId());
        assertEquals(WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH,
                wrong.failure().orElseThrow().code());
    }

    @Test
    void successfulCompletionConsumesOneInputAndPublishesBothOutputsExactlyOnce() {
        Harness harness = Harness.create();
        harness.inventory.setInputInternal(ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance());

        harness.tickThroughCompletion();

        assertEquals(
                WorkstationState.COMPLETE,
                harness.controller.state(),
                () -> harness.controller.lastFailure().map(Object::toString).orElse("no failure")
        );
        assertTrue(harness.inventory.input().isEmpty());
        assertTrue(harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot()).is(ModItems.T_BONE_STEAK.get()));
        assertTrue(harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot() + 1).is(ModItems.BEEF_TRIM.get()));
        assertEquals(1, harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot()).getCount());
        assertEquals(1, harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot() + 1).getCount());

        harness.tickThroughCompletion();
        assertEquals(1, harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot()).getCount());
        assertEquals(1, harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot() + 1).getCount());
    }

    @Test
    void eitherBlockedOutputPreventsInputConsumption() {
        assertBlockedOutputPreservesInput(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        assertBlockedOutputPreservesInput(1, ModItems.BEEF_TRIM.get().getDefaultInstance());
    }

    @Test
    void bothBlockedOutputsPreventInputConsumption() {
        Harness harness = Harness.create();
        ItemStack input = ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance();
        ItemStack primary = ModItems.T_BONE_STEAK.get().getDefaultInstance();
        ItemStack trim = ModItems.BEEF_TRIM.get().getDefaultInstance();
        harness.inventory.setOutputInternal(0, primary.copy());
        harness.inventory.setOutputInternal(1, trim.copy());
        harness.inventory.setInputInternal(input.copy());

        harness.tickThroughCompletion();

        assertTrue(ItemStack.isSameItemSameComponents(input, harness.inventory.input()));
        assertTrue(ItemStack.isSameItemSameComponents(
                primary,
                harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot())
        ));
        assertTrue(ItemStack.isSameItemSameComponents(
                trim,
                harness.inventory.getStackInSlot(harness.inventory.firstOutputSlot() + 1)
        ));
        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED,
                harness.controller.lastFailure().orElseThrow().code());
    }

    @Test
    void saveReloadPreservesAllThreeSlots() {
        WorkstationInventory original = new WorkstationInventory(CuttingTableWorkstation.capability(), () -> {});
        ItemStack input = ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance();
        ItemStack primary = ModItems.T_BONE_STEAK.get().getDefaultInstance();
        ItemStack trim = ModItems.BEEF_TRIM.get().getDefaultInstance();
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Persisted Short Loin"));
        primary.set(DataComponents.CUSTOM_NAME, Component.literal("Persisted T-Bone"));
        trim.set(DataComponents.CUSTOM_NAME, Component.literal("Persisted Beef Trim"));
        original.setInputInternal(input.copy());
        original.setOutputInternal(0, primary.copy());
        original.setOutputInternal(1, trim.copy());
        CompoundTag saved = original.serializeNBT(RegistryAccess.EMPTY);

        WorkstationInventory restored = new WorkstationInventory(CuttingTableWorkstation.capability(), () -> {});
        restored.deserializeNBT(RegistryAccess.EMPTY, saved);

        assertTrue(ItemStack.isSameItemSameComponents(input, restored.input()));
        assertTrue(ItemStack.isSameItemSameComponents(
                primary,
                restored.getStackInSlot(restored.firstOutputSlot())
        ));
        assertTrue(ItemStack.isSameItemSameComponents(
                trim,
                restored.getStackInSlot(restored.firstOutputSlot() + 1)
        ));
    }

    private static void assertBlockedOutputPreservesInput(int outputIndex, ItemStack blockingStack) {
        Harness harness = Harness.create();
        ItemStack input = ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance();
        harness.inventory.setOutputInternal(outputIndex, blockingStack);
        harness.inventory.setInputInternal(input.copy());

        harness.tickThroughCompletion();

        assertTrue(ItemStack.isSameItemSameComponents(input, harness.inventory.input()));
        assertEquals(1, harness.inventory.input().getCount());
        assertEquals(WorkstationState.BLOCKED, harness.controller.state());
        assertEquals(WorkstationFailureCode.OUTPUT_OCCUPIED,
                harness.controller.lastFailure().orElseThrow().code());
    }

    private record Harness(WorkstationInventory inventory, WorkstationProcessingController controller) {
        static Harness create() {
            AtomicInteger changes = new AtomicInteger();
            WorkstationInventory inventory = new WorkstationInventory(
                    CuttingTableWorkstation.capability(),
                    changes::incrementAndGet
            );
            WorkstationProcessingController controller = new WorkstationProcessingController(
                    inventory,
                    CuttingTableWorkstation.capability(),
                    (registryAccess, capability, stack) -> new WorkstationOperationResolver().resolve(
                            BuiltInProcessingDefinitions.builtInView(),
                            capability,
                            stack
                    ),
                    DevelopmentProductItemMappings.fixtureMapping(),
                    WorkstationExecutionStrategy.atomicTransformation(),
                    changes::incrementAndGet
            );
            inventory.setInputLocked(controller::inputLocked);
            inventory.setOutputExtractionAllowed(controller::outputExtractionAllowed);
            return new Harness(inventory, controller);
        }

        void tickThroughCompletion() {
            for (int tick = 0; tick < 90; tick++) {
                controller.serverTick(null);
            }
        }
    }
}
