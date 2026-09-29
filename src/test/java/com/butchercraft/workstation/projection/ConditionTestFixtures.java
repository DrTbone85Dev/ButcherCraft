package com.butchercraft.workstation.projection;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.endpoint.*;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConditionTestFixtures {
    public static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world/root/condition-test", 1, "sha256:" + "3".repeat(64));
    public static final String ALLOCATION = "butchercraft:workstation_instance_configuration/v1/condition_test";
    private ConditionTestFixtures() { }

    public static WorkstationInstanceRecord instance(String type, long generation, int position) {
        return WorkstationInstanceRecord.pending(WORLD, new WorkstationEndpointKey(type, "minecraft:overworld", position, 64, 0),
                generation, ALLOCATION, 1).transition(WorkstationInstanceLifecycle.ACTIVE, 2, Optional.empty(), List.of());
    }

    public static DurableWorkstationProjection projection(WorkstationInstanceRecord instance, long revision,
            long inventoryRevision, List<ItemStack> stacks, Optional<String> operation, Optional<String> result) {
        ExactItemStackCodec codec = new ExactItemStackCodec();
        List<WorkstationProjectionSlot> slots = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            slots.add(new WorkstationProjectionSlot(i, 64, stack.isEmpty() ? 64 : Math.min(64, stack.getMaxStackSize()),
                    stack.isEmpty() ? Optional.empty() : Optional.of(codec.encode(RegistryAccess.EMPTY, stack))));
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("FixtureInventoryRevision", inventoryRevision);
        return DurableWorkstationProjection.active(WORLD, instance.instanceId(), instance.endpointKey(), instance.generation(),
                ALLOCATION, instance.endpointKey().workstationTypeIdentity(), 2, revision, inventoryRevision, 0, 0,
                "butchercraft:slot_capacity/v1/test", slots, WorkstationProjectionNbtCodec.encode(tag),
                Optional.empty(), Optional.empty(), Optional.empty(), operation, result, Optional.empty());
    }

    public static DurableWorkstationProjection legacy(WorkstationInstanceRecord instance) {
        return projection(instance, 1, 0, List.of(ItemStack.EMPTY, ItemStack.EMPTY), Optional.empty(), Optional.empty());
    }
}
