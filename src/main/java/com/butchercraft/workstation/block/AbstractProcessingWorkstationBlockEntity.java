package com.butchercraft.workstation.block;

import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.workstation.condition.ConditionProcessingPreparation;
import com.butchercraft.workstation.condition.ConditionProcessingCommit;
import com.butchercraft.workstation.condition.ConditionProcessingCandidates;
import com.butchercraft.workstation.condition.ConditionDigest;
import com.butchercraft.workstation.WorkstationConditionProcessingAccess;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import com.butchercraft.workstation.WorkstationCapability;
import com.butchercraft.workstation.WorkstationExecutionCoordinator;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationExecutionStrategy;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationOperationLookup;
import com.butchercraft.workstation.WorkstationOperationStartPolicy;
import com.butchercraft.workstation.WorkstationProcessingController;
import com.butchercraft.workstation.WorkstationProductionRequestResult;
import com.butchercraft.workstation.WorkstationProductionSnapshot;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import com.butchercraft.workstation.WorkstationTickContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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
        this(type, pos, blockState, capability, resolver, outputMapping, executionStrategy, null);
    }

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            WorkstationExecutionCoordinator executionCoordinator
    ) {
        this(
                type,
                pos,
                blockState,
                capability,
                resolver,
                outputMapping,
                executionStrategy,
                executionCoordinator,
                WorkstationOperationStartPolicy.AUTOMATIC_WHEN_READY
        );
    }

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            WorkstationExecutionCoordinator executionCoordinator,
            WorkstationOperationStartPolicy startPolicy
    ) {
        this(
                type,
                pos,
                blockState,
                capability,
                resolver,
                outputMapping,
                executionStrategy,
                executionCoordinator,
                startPolicy,
                WorkstationSlotCapacityPolicy.uniform(capability.inputSlots() + capability.outputSlots(), 1)
        );
    }

    protected AbstractProcessingWorkstationBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState blockState,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            WorkstationExecutionCoordinator executionCoordinator,
            WorkstationOperationStartPolicy startPolicy,
            WorkstationSlotCapacityPolicy slotCapacityPolicy
    ) {
        super(type, pos, blockState, capability, slotCapacityPolicy);
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.controller = executionCoordinator == null
                ? new WorkstationProcessingController(
                        inventory(),
                        capability,
                        resolver,
                        Objects.requireNonNull(outputMapping, "outputMapping"),
                        Objects.requireNonNull(executionStrategy, "executionStrategy"),
                        Objects.requireNonNull(startPolicy, "startPolicy"),
                        this::markChanged
                )
                : new WorkstationProcessingController(
                        inventory(),
                        capability,
                        resolver,
                        Objects.requireNonNull(outputMapping, "outputMapping"),
                        Objects.requireNonNull(executionStrategy, "executionStrategy"),
                        executionCoordinator,
                        Objects.requireNonNull(startPolicy, "startPolicy"),
                        this::markChanged
                );
        inventory().setInputLocked(controller::inputLocked);
        inventory().setOutputExtractionAllowed(controller::outputExtractionAllowed);
        inventory().setInputSlotValidator(this::canAcceptInput);
        controller.attachConditionAccess(new WorkstationConditionProcessingAccess() {
            @Override
            public Optional<ConditionProcessingPreparation> prepare() {
                if (!(level instanceof ServerLevel serverLevel)
                        || !(AbstractProcessingWorkstationBlockEntity.this
                        instanceof com.butchercraft.workstation.endpoint.runtime.WorkstationTransferEndpoint)) {
                    return Optional.empty();
                }
                var projection = DurableWorkstationProjectionService.INSTANCE.ensureConditionReady(
                        serverLevel, AbstractProcessingWorkstationBlockEntity.this);
                if (projection.condition().isEmpty()) {
                    throw new IllegalStateException("Historical child/endpoint must resolve before new condition-aware work");
                }
                return projection.condition().orElseThrow().state().map(ConditionProcessingPreparation::of);
            }

            @Override
            public ConditionProcessingCommit prepareCommit(ConditionProcessingPreparation preparation,
                    com.butchercraft.world.execution.ExecutionOperationId operation,
                    com.butchercraft.workstation.WorkstationInventoryCommitPlan inventoryPlan,
                    com.butchercraft.world.execution.ExecutionOwnerResultEvidence productResult, long tick) {
                if (!(level instanceof ServerLevel serverLevel)) throw new IllegalStateException("Server owner required");
                var owner = DurableWorkstationProjectionService.INSTANCE;
                var durable = owner.read(serverLevel.getServer(), preparation.preState().instanceId())
                        .projection().orElseThrow();
                var condition = durable.condition().orElseThrow();
                if (!conditionProjection().equals(durable.condition())) {
                    throw new IllegalStateException("Loaded condition differs from durable owner baseline");
                }
                ConditionDigest slots = new ConditionDigest("butchercraft:joint_processing_inventory/v1");
                var codec = new com.butchercraft.integration.materialhandling.ExactItemStackCodec();
                slots.add(inventoryPlan.postInputs().size() + inventoryPlan.postOutputs().size());
                for (ItemStack stack : inventoryPlan.postInputs()) {
                    slots.add(stack.isEmpty());
                    if (!stack.isEmpty()) slots.add(codec.encode(serverLevel.registryAccess(), stack).contentDigest());
                }
                for (ItemStack stack : inventoryPlan.postOutputs()) {
                    slots.add(stack.isEmpty());
                    if (!stack.isEmpty()) slots.add(codec.encode(serverLevel.registryAccess(), stack).contentDigest());
                }
                ConditionProcessingCommit candidate = ConditionProcessingCandidates.prepare(condition, preparation,
                        operation, slots.finish(), productResult, tick, Math.incrementExact(durable.projectionRevision()));
                owner.conditionReceipts(serverLevel.getServer()).stage(candidate.receipt());
                return candidate;
            }

            @Override
            public void installPreparedMirror(ConditionProcessingCommit candidate) {
                acceptConditionProjection(Optional.of(candidate.projection()));
            }
        });
    }

    public final Optional<String> conditionPreparationIdentity() {
        return controller.conditionPreparationIdentity();
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
            if (level instanceof ServerLevel serverLevel) {
                blockEntity.tickController(new WorkstationTickContext(serverLevel, pos));
            } else {
                blockEntity.tickController(level.registryAccess());
            }
        }
    }

    @Override
    protected void beforeDropContents() {
        beginDurableProjectionMutation();
        try {
            WorkstationTickContext context = currentTickContext();
            if (context == null) {
                controller.cancelPreservingInput();
            } else {
                controller.cancelPreservingInput(context);
            }
        } finally {
            endDurableProjectionMutation();
        }
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
        ensureDurableProjectionReady();
        beginDurableProjectionMutation();
        try {
            controller.serverTick(registryAccess);
        } finally {
            endDurableProjectionMutation();
        }
    }

    protected final void tickController(WorkstationTickContext tickContext) {
        ensureDurableProjectionReady();
        beginDurableProjectionMutation();
        try {
            controller.serverTickWithContext(tickContext);
        } finally {
            endDurableProjectionMutation();
        }
    }

    public final WorkstationProductionSnapshot productionSnapshot() {
        return controller.productionSnapshot();
    }

    public final WorkstationProductionRequestResult requestProductionProcessing(WorkstationTickContext tickContext) {
        ensureDurableProjectionReady();
        beginDurableProjectionMutation();
        try {
            return controller.requestProductionProcessing(tickContext);
        } finally {
            endDurableProjectionMutation();
        }
    }

    protected final WorkstationProductionRequestResult requestProductionProcessing(
            WorkstationTickContext tickContext,
            Function<com.butchercraft.workstation.WorkstationExecutionStartRequest,
                    com.butchercraft.workstation.WorkstationExecutionStartResult> executionStart
    ) {
        ensureDurableProjectionReady();
        beginDurableProjectionMutation();
        try {
            return controller.requestProductionProcessing(tickContext, executionStart);
        } finally {
            endDurableProjectionMutation();
        }
    }

    protected WorkstationExecutionEffectResult completeScheduledExecution(
            com.butchercraft.world.execution.ExecutionOperationId operationId,
            com.butchercraft.world.execution.ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        if (level == null) {
            return WorkstationExecutionEffectResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Workstation level is unavailable during scheduled Execution effect"
            ));
        }
        CompoundTag before = checkpointProjectionSnapshot(level.registryAccess());
        var conditionBefore = conditionProjection();
        beginDurableProjectionMutation();
        try {
            WorkstationExecutionEffectResult result = controller.completeScheduledExecution(
                    level.registryAccess(),
                    operationId,
                    domainEffectIdentity,
                    authoritativeTick
            );
            endDurableProjectionMutation();
            return result;
        } catch (RuntimeException exception) {
            failJointPublication(before, conditionBefore);
            throw exception;
        }
    }

    @Override
    protected void onInventoryChanged() {
        controller.onInventoryChanged();
    }

    @Override
    protected Optional<String> durableProcessingOperationIdentityView() {
        return controller.productionSnapshot().activeExecutionOperationId()
                .map(com.butchercraft.world.execution.ExecutionOperationId::value);
    }

    @Override
    protected Optional<String> durableProcessingOwnerResultIdentityView() {
        return controller.productionSnapshot().ownerResultEvidence()
                .map(com.butchercraft.world.execution.ExecutionOwnerResultEvidence::ownerResultIdentity);
    }

    private void markChanged() {
        setChanged();
    }

    private WorkstationTickContext currentTickContext() {
        return level instanceof ServerLevel serverLevel
                ? new WorkstationTickContext(serverLevel, worldPosition)
                : null;
    }
}
