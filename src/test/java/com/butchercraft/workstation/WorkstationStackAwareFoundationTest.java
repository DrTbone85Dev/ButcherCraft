package com.butchercraft.workstation;

import com.butchercraft.machine.cuttingtable.CuttingTableWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.machine.pattyformer.PattyFormerWorkstation;
import com.butchercraft.registration.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationStackAwareFoundationTest {
    @Test
    void capacityPolicySupportsDifferentSlotsAndStableConfigurationIdentity() {
        WorkstationSlotCapacityPolicy policy = WorkstationSlotCapacityPolicy.perSlot(64, 16, 1);

        assertEquals(64, policy.capacity(0));
        assertEquals(16, policy.capacity(1));
        assertEquals(1, policy.capacity(2));
        assertEquals(policy.configurationIdentity(),
                WorkstationSlotCapacityPolicy.perSlot(64, 16, 1).configurationIdentity());
        assertTrue(policy.configurationIdentity().startsWith("butchercraft:workstation_slot_capacity/v1/"));
    }

    @Test
    void effectiveCapacityUsesLowerItemAndWorkstationLimit() {
        assertEquals(16, WorkstationStackMutationPlan.effectiveCapacity(new ItemStack(Items.STONE), 16));
        assertEquals(64, WorkstationStackMutationPlan.effectiveCapacity(new ItemStack(Items.STONE), 128));
        assertEquals(64, WorkstationStackMutationPlan.effectiveCapacity(
                ModItems.BEEF_TRIM.get().getDefaultInstance(),
                64
        ));
    }

    @Test
    void partialWithdrawalPreservesExactComponentsAndConservesCount() {
        ItemStack source = new ItemStack(Items.STONE, 64);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Lot A"));

        WorkstationStackMutationPlan plan = WorkstationStackMutationPlan.withdrawal(source, 10, 64);

        assertEquals(64, plan.preStack().getCount());
        assertEquals(10, plan.transferStack().getCount());
        assertEquals(54, plan.postStack().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(source, plan.transferStack()));
        assertTrue(ItemStack.isSameItemSameComponents(source, plan.postStack()));
        assertEquals(64, plan.transferStack().getCount() + plan.postStack().getCount());
    }

    @Test
    void fullWithdrawalProducesCanonicalEmptyPostState() {
        WorkstationStackMutationPlan plan = WorkstationStackMutationPlan.withdrawal(
                new ItemStack(Items.STONE, 2),
                2,
                64
        );

        assertTrue(plan.postStack().isEmpty());
    }

    @Test
    void invalidWithdrawalQuantitiesFailBeforeMutation() {
        ItemStack source = new ItemStack(Items.STONE, 4);

        assertThrows(IllegalArgumentException.class, () -> WorkstationStackMutationPlan.withdrawal(source, 0, 64));
        assertThrows(IllegalArgumentException.class, () -> WorkstationStackMutationPlan.withdrawal(source, 5, 64));
    }

    @Test
    void compatibleDestinationMergeIsAllOrNothing() {
        ItemStack destination = new ItemStack(Items.STONE, 60);
        ItemStack payload = new ItemStack(Items.STONE, 4);

        WorkstationStackMutationPlan accepted = WorkstationStackMutationPlan.merge(destination, payload, 64);

        assertEquals(64, accepted.postStack().getCount());
        assertEquals(60, destination.getCount());
        assertEquals(4, payload.getCount());
        assertThrows(IllegalArgumentException.class,
                () -> WorkstationStackMutationPlan.merge(destination, new ItemStack(Items.STONE, 5), 64));
        assertEquals(60, destination.getCount());
    }

    @Test
    void mergeRejectsDifferentItemsAndDifferentComponents() {
        ItemStack named = new ItemStack(Items.STONE, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lot A"));
        ItemStack otherName = new ItemStack(Items.STONE, 1);
        otherName.set(DataComponents.CUSTOM_NAME, Component.literal("Lot B"));

        assertThrows(IllegalArgumentException.class,
                () -> WorkstationStackMutationPlan.merge(named, new ItemStack(Items.DIRT), 64));
        assertThrows(IllegalArgumentException.class,
                () -> WorkstationStackMutationPlan.merge(named, otherName, 64));
    }

    @Test
    void existingInventoryConstructorsRetainOneItemSlotPolicyWhileApprovedProductsStackTo64() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});

        assertEquals(1, inventory.getSlotLimit(WorkstationInventory.INPUT_SLOT));
        assertEquals(1, inventory.getSlotLimit(WorkstationInventory.OUTPUT_SLOT));
        assertEquals(64, ModItems.BEEF_TRIM.get().getDefaultInstance().getMaxStackSize());
        assertEquals(64, ModItems.GROUND_BEEF.get().getDefaultInstance().getMaxStackSize());
        assertEquals(64, ModItems.BEEF_PATTIES.get().getDefaultInstance().getMaxStackSize());
        assertEquals(1, ModItems.T_BONE_STEAK.get().getDefaultInstance().getMaxStackSize());
    }

    @Test
    void approvedTransferWorkstationsExposeTheirSelectiveLiveCapacities() {
        assertEquals(List.of(1, 1, 64), capacities(
                CuttingTableWorkstation.capability(), CuttingTableWorkstation.slotCapacityPolicy()));
        assertEquals(List.of(64, 64), capacities(
                GrinderWorkstation.capability(), GrinderWorkstation.slotCapacityPolicy()));
        assertEquals(List.of(64, 64), capacities(
                PattyFormerWorkstation.capability(), PattyFormerWorkstation.slotCapacityPolicy()));
    }

    private static List<Integer> capacities(
            WorkstationCapability capability,
            WorkstationSlotCapacityPolicy policy
    ) {
        WorkstationInventory inventory = new WorkstationInventory(capability, policy, () -> {});
        return java.util.stream.IntStream.range(0, inventory.totalSlotCount())
                .mapToObj(inventory::getSlotLimit)
                .toList();
    }
}
