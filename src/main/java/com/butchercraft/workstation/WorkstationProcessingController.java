package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.OperationResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class WorkstationProcessingController {
    private static final String STATE_TAG = "State";
    private static final String SELECTED_OPERATION_TAG = "SelectedOperation";
    private static final String ELAPSED_TICKS_TAG = "ElapsedTicks";
    private static final String TOTAL_TICKS_TAG = "TotalTicks";
    private static final String LAST_FAILURE_TAG = "LastFailure";
    private static final String RESERVED_INPUT_TAG = "ReservedInput";
    private static final String COMPLETION_COMMITTED_TAG = "CompletionCommitted";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final WorkstationOperationLookup resolver;
    private final DevelopmentProductItemMapping outputMapping;
    private final WorkstationExecutionStrategy executionStrategy;
    private final Runnable changed;

    private WorkstationState state = WorkstationState.IDLE;
    private WorkstationFailure lastFailure;
    private ResourceLocation selectedOperationId;
    private int elapsedTicks;
    private int totalTicks;
    private ItemStack reservedInputSnapshot = ItemStack.EMPTY;
    private boolean completionCommitted;

    public WorkstationProcessingController(
            WorkstationInventory inventory,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            Runnable changed
    ) {
        this(inventory, capability, resolver, outputMapping, WorkstationExecutionStrategy.legacy(), changed);
    }

    public WorkstationProcessingController(
            WorkstationInventory inventory,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            Runnable changed
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.outputMapping = Objects.requireNonNull(outputMapping, "outputMapping");
        this.executionStrategy = Objects.requireNonNull(executionStrategy, "executionStrategy");
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    public WorkstationState state() {
        return state;
    }

    public Optional<WorkstationFailure> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    public Optional<ResourceLocation> selectedOperationId() {
        return Optional.ofNullable(selectedOperationId);
    }

    public int elapsedTicks() {
        return elapsedTicks;
    }

    public int totalTicks() {
        return totalTicks;
    }

    public boolean inputLocked() {
        return state == WorkstationState.PROCESSING;
    }

    public boolean outputExtractionAllowed() {
        return state == WorkstationState.COMPLETE;
    }

    public void onInventoryChanged() {
        if (state == WorkstationState.COMPLETE && inventory.outputsEmpty()) {
            resetToIdle();
            return;
        }
        if ((state == WorkstationState.IDLE || state == WorkstationState.BLOCKED) && inventory.input().isEmpty()) {
            resetToIdle();
            return;
        }
        if (state == WorkstationState.IDLE && !inventory.input().isEmpty()) {
            setState(WorkstationState.READY);
        }
        changed.run();
    }

    public void serverTick(RegistryAccess registryAccess) {
        if (state == WorkstationState.IDLE) {
            if (!inventory.input().isEmpty()) {
                setState(WorkstationState.READY);
            } else {
                return;
            }
        }

        if (state == WorkstationState.READY) {
            startProcessing(registryAccess);
            return;
        }

        if (state == WorkstationState.PROCESSING) {
            if (inventory.input().isEmpty()) {
                block(WorkstationFailure.of(WorkstationFailureCode.NO_INPUT, "Reserved input is missing during processing"));
                return;
            }
            if (!ItemStack.isSameItemSameComponents(inventory.input(), reservedInputSnapshot)) {
                block(WorkstationFailure.of(WorkstationFailureCode.PRODUCT_DATA_MISMATCH, "Reserved input changed during processing"));
                return;
            }
            elapsedTicks = Math.min(totalTicks, elapsedTicks + 1);
            if (elapsedTicks >= totalTicks) {
                complete(registryAccess);
            } else {
                changed.run();
            }
            return;
        }

        if (state == WorkstationState.BLOCKED) {
            retryBlockedCompletion(registryAccess);
        }
    }

    public void cancelPreservingInput() {
        if (state == WorkstationState.PROCESSING || state == WorkstationState.BLOCKED || state == WorkstationState.READY) {
            resetRuntimeProgress();
            state = WorkstationState.IDLE;
            changed.run();
        }
    }

    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(STATE_TAG, state.name());
        if (selectedOperationId != null) {
            tag.putString(SELECTED_OPERATION_TAG, selectedOperationId.toString());
        }
        tag.putInt(ELAPSED_TICKS_TAG, elapsedTicks);
        tag.putInt(TOTAL_TICKS_TAG, totalTicks);
        if (lastFailure != null) {
            tag.putString(LAST_FAILURE_TAG, lastFailure.code().name());
        }
        if (!reservedInputSnapshot.isEmpty()) {
            tag.put(RESERVED_INPUT_TAG, reservedInputSnapshot.save(registries, new CompoundTag()));
        }
        tag.putBoolean(COMPLETION_COMMITTED_TAG, completionCommitted);
    }

    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            state = tag.contains(STATE_TAG, Tag.TAG_STRING)
                    ? WorkstationState.valueOf(tag.getString(STATE_TAG))
                    : WorkstationState.IDLE;
            selectedOperationId = tag.contains(SELECTED_OPERATION_TAG, Tag.TAG_STRING)
                    ? ResourceLocation.parse(tag.getString(SELECTED_OPERATION_TAG))
                    : null;
            elapsedTicks = Math.max(0, tag.getInt(ELAPSED_TICKS_TAG));
            totalTicks = Math.max(0, tag.getInt(TOTAL_TICKS_TAG));
            lastFailure = tag.contains(LAST_FAILURE_TAG, Tag.TAG_STRING)
                    ? WorkstationFailure.of(WorkstationFailureCode.valueOf(tag.getString(LAST_FAILURE_TAG)), "Restored persisted failure code")
                    : null;
            reservedInputSnapshot = tag.contains(RESERVED_INPUT_TAG, Tag.TAG_COMPOUND)
                    ? ItemStack.parse(registries, tag.getCompound(RESERVED_INPUT_TAG)).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
            completionCommitted = tag.getBoolean(COMPLETION_COMMITTED_TAG);
            validateLoadedRuntimeState();
        } catch (RuntimeException exception) {
            state = WorkstationState.ERROR;
            lastFailure = WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Malformed workstation state was preserved but processing was stopped"
            );
            resetRuntimeProgress();
        }
    }

    private void startProcessing(RegistryAccess registryAccess) {
        if (state == WorkstationState.PROCESSING) {
            block(WorkstationFailure.of(WorkstationFailureCode.TRANSACTION_ALREADY_ACTIVE, "Processing is already active"));
            return;
        }
        if (!inventory.outputsEmpty()) {
            block(WorkstationFailure.of(WorkstationFailureCode.OUTPUT_OCCUPIED, "Output slot must be empty before processing starts"));
            return;
        }

        WorkstationOperationResolution resolution = resolver.resolve(registryAccess, capability, inventory.input());
        if (!resolution.succeeded()) {
            block(resolution.failure().orElseThrow());
            return;
        }

        ResolvedWorkstationOperation operation = resolution.operation().orElseThrow();
        OperationResult prepared = executionStrategy.prepare(capability, operation, inventory, outputMapping);
        if (!prepared.succeeded()) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                    prepared.failureReason().map(reason -> reason.message()).orElse("Processing validation rejected the input")
            ));
            return;
        }

        selectedOperationId = operation.operationId();
        elapsedTicks = 0;
        totalTicks = operation.totalTicks();
        reservedInputSnapshot = inventory.input().copy();
        completionCommitted = false;
        clearFailure();
        setState(WorkstationState.PROCESSING);
    }

    private void retryBlockedCompletion(RegistryAccess registryAccess) {
        if (inventory.input().isEmpty()) {
            resetToIdle();
            return;
        }
        if (selectedOperationId != null && elapsedTicks >= totalTicks && inventory.outputsEmpty()) {
            state = WorkstationState.PROCESSING;
            complete(registryAccess);
        }
    }

    private void complete(RegistryAccess registryAccess) {
        if (completionCommitted) {
            block(WorkstationFailure.of(WorkstationFailureCode.TRANSACTION_ALREADY_ACTIVE, "Completion was already committed"));
            return;
        }
        if (!inventory.outputsEmpty()) {
            block(WorkstationFailure.of(WorkstationFailureCode.OUTPUT_OCCUPIED, "Output slot is occupied at completion"));
            return;
        }

        WorkstationOperationResolution resolution = resolver.resolve(registryAccess, capability, inventory.input());
        if (!resolution.succeeded()) {
            block(resolution.failure().orElseThrow());
            return;
        }
        ResolvedWorkstationOperation operation = resolution.operation().orElseThrow();
        if (selectedOperationId != null && !selectedOperationId.equals(operation.operationId())) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                    operation.operationId(),
                    "Resolved operation changed before completion"
            ));
            return;
        }

        OperationResult committed = executionStrategy.commit(capability, operation, inventory, outputMapping);
        if (!committed.succeeded() || committed.committedOutputs().isEmpty()) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    committed.failureReason().map(reason -> reason.message()).orElse("Processing transaction did not produce committed outputs")
            ));
            return;
        }
        if (committed.committedOutputs().size() > capability.outputSlots()
                || committed.committedOutputs().size() > inventory.outputSlotCount()) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    "Processing transaction produced more outputs than this workstation can hold"
            ));
            return;
        }

        List<ItemStack> outputStacks = new ArrayList<>();
        for (Product outputProduct : committed.committedOutputs()) {
            Optional<ItemStack> output = outputMapping.createStack(outputProduct);
            if (output.isEmpty()) {
                block(WorkstationFailure.of(
                        WorkstationFailureCode.RESULT_CREATION_FAILED,
                        ResourceLocation.parse(outputProduct.typeId().value()),
                        "No development item mapping exists for output product"
                ));
                return;
            }
            outputStacks.add(output.orElseThrow());
        }

        ItemStack inputSnapshot = inventory.input().copy();
        List<ItemStack> outputSnapshot = inventory.outputs().stream()
                .map(ItemStack::copy)
                .toList();
        try {
            completionCommitted = true;
            inventory.clearInputInternal();
            inventory.setOutputsInternal(outputStacks);
            elapsedTicks = totalTicks;
            clearFailure();
            setState(WorkstationState.COMPLETE);
        } catch (RuntimeException exception) {
            completionCommitted = false;
            inventory.setInputInternal(inputSnapshot);
            inventory.setOutputsInternal(outputSnapshot);
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    "Output insertion failed and workstation inventory was restored"
            ));
        }
    }

    private void block(WorkstationFailure failure) {
        lastFailure = Objects.requireNonNull(failure, "failure");
        setState(WorkstationState.BLOCKED);
    }

    private void clearFailure() {
        lastFailure = null;
    }

    private void resetToIdle() {
        resetRuntimeProgress();
        lastFailure = null;
        state = WorkstationState.IDLE;
        changed.run();
    }

    private void resetRuntimeProgress() {
        selectedOperationId = null;
        elapsedTicks = 0;
        totalTicks = 0;
        reservedInputSnapshot = ItemStack.EMPTY;
        completionCommitted = false;
    }

    private void setState(WorkstationState next) {
        if (state != next) {
            state = state.transitionTo(next);
        }
        changed.run();
    }

    private void validateLoadedRuntimeState() {
        if (state == WorkstationState.PROCESSING) {
            if (selectedOperationId == null || totalTicks <= 0 || inventory.input().isEmpty()) {
                state = WorkstationState.ERROR;
                lastFailure = WorkstationFailure.of(
                        WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                        "Active processing state was incomplete after load"
                );
                resetRuntimeProgress();
            }
        }
        if (state == WorkstationState.COMPLETE && inventory.outputsEmpty()) {
            resetRuntimeProgress();
            state = WorkstationState.IDLE;
        }
    }
}
