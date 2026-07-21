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
    private static final String RESERVED_INPUTS_TAG = "ReservedInputs";
    private static final String RESERVED_INPUT_INDEX_TAG = "InputIndex";
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
    private List<ItemStack> reservedInputSnapshots = List.of();
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
        if (state == WorkstationState.BLOCKED && selectedOperationId == null && !inventory.input().isEmpty()) {
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
            if (!reservedInputsMatchInventory()) {
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
        if (!reservedInputSnapshots.isEmpty()) {
            net.minecraft.nbt.ListTag inputsTag = new net.minecraft.nbt.ListTag();
            for (int inputIndex = 0; inputIndex < reservedInputSnapshots.size(); inputIndex++) {
                ItemStack stack = reservedInputSnapshots.get(inputIndex);
                if (!stack.isEmpty()) {
                    CompoundTag inputTag = (CompoundTag) stack.save(registries, new CompoundTag());
                    inputTag.putInt(RESERVED_INPUT_INDEX_TAG, inputIndex);
                    inputsTag.add(inputTag);
                }
            }
            if (!inputsTag.isEmpty()) {
                tag.put(RESERVED_INPUTS_TAG, inputsTag);
            }
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
            reservedInputSnapshots = loadReservedInputs(tag, registries);
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
                    failureCodeForResult(prepared, WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED),
                    prepared.failureReason().map(reason -> reason.message()).orElse("Processing validation rejected the input")
            ));
            return;
        }

        selectedOperationId = operation.operationId();
        elapsedTicks = 0;
        totalTicks = operation.totalTicks();
        reservedInputSnapshot = inventory.input().copy();
        reservedInputSnapshots = inventory.inputs().stream().map(ItemStack::copy).toList();
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
                    failureCodeForResult(committed, WorkstationFailureCode.RESULT_CREATION_FAILED),
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
            Optional<ItemStack> output = executionStrategy.createOutputStack(
                    operation,
                    outputProduct,
                    inventory.input(),
                    outputMapping
            );
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

        WorkstationInventoryCommitPlan commitPlan;
        try {
            commitPlan = new WorkstationInventoryCommitPlan(
                    inventory,
                    executionStrategy.consumedInputSlots(capability, operation, inventory),
                    outputStacks
            );
        } catch (RuntimeException exception) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    "Unable to create a workstation inventory commit plan: " + exception.getMessage()
            ));
            return;
        }

        try {
            completionCommitted = true;
            commitPlan.commit();
            elapsedTicks = totalTicks;
            clearFailure();
            setState(WorkstationState.COMPLETE);
        } catch (RuntimeException exception) {
            completionCommitted = false;
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
        reservedInputSnapshots = List.of();
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
            if (state == WorkstationState.PROCESSING && reservedInputSnapshots.isEmpty()) {
                state = WorkstationState.ERROR;
                lastFailure = WorkstationFailure.of(
                        WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                        "Active processing state had no reserved input snapshot after load"
                );
                resetRuntimeProgress();
            }
        }
        if (state == WorkstationState.COMPLETE && inventory.outputsEmpty()) {
            resetRuntimeProgress();
            state = WorkstationState.IDLE;
        }
    }

    private boolean reservedInputsMatchInventory() {
        if (reservedInputSnapshots.isEmpty()) {
            return ItemStack.isSameItemSameComponents(inventory.input(), reservedInputSnapshot);
        }
        List<ItemStack> currentInputs = inventory.inputs();
        if (currentInputs.size() != reservedInputSnapshots.size()) {
            return false;
        }
        for (int inputIndex = 0; inputIndex < currentInputs.size(); inputIndex++) {
            if (!ItemStack.isSameItemSameComponents(currentInputs.get(inputIndex), reservedInputSnapshots.get(inputIndex))) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> loadReservedInputs(CompoundTag tag, HolderLookup.Provider registries) {
        ArrayList<ItemStack> snapshots = new ArrayList<>();
        for (int inputIndex = 0; inputIndex < inventory.inputSlotCount(); inputIndex++) {
            snapshots.add(ItemStack.EMPTY);
        }
        if (tag.contains(RESERVED_INPUTS_TAG, Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag inputsTag = tag.getList(RESERVED_INPUTS_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < inputsTag.size(); index++) {
                CompoundTag inputTag = inputsTag.getCompound(index);
                int inputIndex = inputTag.getInt(RESERVED_INPUT_INDEX_TAG);
                if (inputIndex >= 0 && inputIndex < snapshots.size()) {
                    snapshots.set(inputIndex, ItemStack.parse(registries, inputTag).orElse(ItemStack.EMPTY));
                }
            }
            return List.copyOf(snapshots);
        }
        if (!reservedInputSnapshot.isEmpty()) {
            snapshots.set(0, reservedInputSnapshot);
            return List.copyOf(snapshots);
        }
        return List.of();
    }

    private static WorkstationFailureCode failureCodeForResult(OperationResult result, WorkstationFailureCode fallback) {
        return result.failureReason()
                .map(reason -> switch (reason.code()) {
                    case "missing_required_supply" -> WorkstationFailureCode.MISSING_REQUIRED_SUPPLY;
                    case "invalid_supply_item" -> WorkstationFailureCode.INVALID_SUPPLY_ITEM;
                    case "packaging_definition_missing", "packaging_metadata_missing" ->
                            WorkstationFailureCode.PACKAGING_DEFINITION_MISSING;
                    default -> fallback;
                })
                .orElse(fallback);
    }
}
