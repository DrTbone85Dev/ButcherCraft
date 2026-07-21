package com.butchercraft.workstation.block;

import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationOperationLookup;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractProcessingWorkstationBlockEntity extends AbstractInventoryWorkstationBlockEntity {
    private static final String CONTROLLER_TAG = "Controller";

    private final WorkstationOperationLookup resolver;
    private final WorkstationProcessingController controller;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> controller.state().ordinal();
                case 1 -> controller.elapsedTicks();
                case 2 -> controller.totalTicks();
                case 3 -> controller.lastFailure().map(failure -> failure.code().ordinal()).orElse(-1);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Server-owned view data; client writes are ignored.
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping
    ) {
        this(type, pos, blockState, capability, resolver, outputMapping, WorkstationExecutionStrategy.legacy());
    }

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy
    ) {
        super(type, pos, blockState, capability);
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.controller = new WorkstationProcessingController(
                inventory(),
                capability,
                resolver,
                Objects.requireNonNull(outputMapping, "outputMapping"),
                Objects.requireNonNull(executionStrategy, "executionStrategy"),
                this::markChanged
        );
        inventory().setInputLocked(controller::inputLocked);
        inventory().setOutputExtractionAllowed(controller::outputExtractionAllowed);
        inventory().setInputSlotValidator(this::canAcceptInput);
    }

    public WorkstationState workstationState() {
        return controller.state();
    }

    public Optional<WorkstationFailure> lastFailure() {
        return controller.lastFailure();
    }

    public ContainerData menuData() {
        return menuData;
    }

    public static <T extends AbstractProcessingWorkstationBlockEntity> void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            T blockEntity
    ) {
        if (!level.isClientSide) {
            blockEntity.tickController(level.registryAccess());
        }
    }

    @Override
    protected void beforeDropContents() {
        controller.cancelPreservingInput();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag controllerTag = new CompoundTag();
        controller.saveAdditional(controllerTag, registries);
        tag.put(CONTROLLER_TAG, controllerTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(CONTROLLER_TAG, Tag.TAG_COMPOUND)) {
            controller.loadAdditional(tag.getCompound(CONTROLLER_TAG), registries);
        }
    }

    protected boolean canAcceptInput(int slot, ItemStack stack) {
        if (slot != inventory().firstInputSlot()) {
            return false;
        }
        return canAcceptPrimaryInput(stack);
    }

    protected final boolean canAcceptPrimaryInput(ItemStack stack) {
        if (stack.isEmpty() || !ProductStackAdapter.readProductData(stack).succeeded()) {
            return false;
        }
        if (level == null) {
            return true;
        }
        return resolver.resolve(level.registryAccess(), capability(), stack).succeeded();
    }

    protected final void tickController(RegistryAccess registryAccess) {
        controller.serverTick(registryAccess);
    }

    @Override
    protected void onInventoryChanged() {
        controller.onInventoryChanged();
    }

    private void markChanged() {
        setChanged();
    }
}
