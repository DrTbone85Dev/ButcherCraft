package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.result.OperationResult;
import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import com.butchercraft.world.execution.ExecutionStatus;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private static final String ACTIVE_EXECUTION_OPERATION_TAG = "ActiveExecutionOperation";
    private static final String ACTIVE_DOMAIN_EFFECT_TAG = "ActiveDomainEffect";
    private static final String EXECUTION_WORK_SUBMITTED_TAG = "ExecutionWorkSubmitted";
    private static final String FROZEN_INPUT_IDENTITY_TAG = "FrozenInputIdentity";
    private static final String EXPECTED_OUTPUT_IDENTITY_TAG = "ExpectedOutputIdentity";
    private static final String SOURCE_FRESHNESS_IDENTITY_TAG = "SourceFreshnessIdentity";
    private static final String OWNER_RESULT_IDENTITY_TAG = "OwnerResultIdentity";
    private static final String OWNER_RESULT_DIGEST_TAG = "OwnerResultDigest";
    private static final String OWNER_RESULT_CONTENT_DIGEST_TAG = "OwnerResultContentDigest";
    private static final String OWNER_SUBSYSTEM_ID = "butchercraft:workstation";

    private final WorkstationInventory inventory;
    private final WorkstationCapability capability;
    private final WorkstationOperationLookup resolver;
    private final DevelopmentProductItemMapping outputMapping;
    private final WorkstationExecutionStrategy executionStrategy;
    private final Optional<WorkstationExecutionCoordinator> executionCoordinator;
    private final Runnable changed;

    private WorkstationState state = WorkstationState.IDLE;
    private WorkstationFailure lastFailure;
    private ResourceLocation selectedOperationId;
    private int elapsedTicks;
    private int totalTicks;
    private ItemStack reservedInputSnapshot = ItemStack.EMPTY;
    private List<ItemStack> reservedInputSnapshots = List.of();
    private boolean completionCommitted;
    private ExecutionOperationId activeExecutionOperationId;
    private ExecutionDomainEffectIdentity activeDomainEffectIdentity;
    private boolean executionWorkSubmitted;
    private String frozenInputIdentity;
    private String expectedOutputIdentity;
    private String sourceFreshnessIdentity;
    private ExecutionOwnerResultEvidence ownerResultEvidence;

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
        this(inventory, capability, resolver, outputMapping, executionStrategy, Optional.empty(), changed);
    }

    public WorkstationProcessingController(
            WorkstationInventory inventory,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            WorkstationExecutionCoordinator executionCoordinator,
            Runnable changed
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.outputMapping = Objects.requireNonNull(outputMapping, "outputMapping");
        this.executionStrategy = Objects.requireNonNull(executionStrategy, "executionStrategy");
        this.executionCoordinator = Optional.of(Objects.requireNonNull(executionCoordinator, "executionCoordinator"));
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    private WorkstationProcessingController(
            WorkstationInventory inventory,
            WorkstationCapability capability,
            WorkstationOperationLookup resolver,
            DevelopmentProductItemMapping outputMapping,
            WorkstationExecutionStrategy executionStrategy,
            Optional<WorkstationExecutionCoordinator> executionCoordinator,
            Runnable changed
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.outputMapping = Objects.requireNonNull(outputMapping, "outputMapping");
        this.executionStrategy = Objects.requireNonNull(executionStrategy, "executionStrategy");
        this.executionCoordinator = Objects.requireNonNull(executionCoordinator, "executionCoordinator");
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

    public WorkstationProductionSnapshot productionSnapshot() {
        return new WorkstationProductionSnapshot(
                state,
                Optional.ofNullable(selectedOperationId),
                Optional.ofNullable(activeExecutionOperationId),
                Optional.ofNullable(ownerResultEvidence),
                Optional.ofNullable(lastFailure)
        );
    }

    public WorkstationProductionRequestResult requestProductionProcessing(WorkstationTickContext tickContext) {
        Objects.requireNonNull(tickContext, "tickContext");
        if (state == WorkstationState.IDLE) {
            if (inventory.input().isEmpty()) {
                WorkstationFailure failure = WorkstationFailure.of(
                        WorkstationFailureCode.NO_INPUT,
                        "Production requested workstation processing without workstation input"
                );
                return WorkstationProductionRequestResult.rejected(productionSnapshot(), failure);
            }
            setState(WorkstationState.READY);
        }
        if (state == WorkstationState.READY) {
            startProcessing(tickContext.registryAccess(), tickContext);
        }
        WorkstationProductionSnapshot snapshot = productionSnapshot();
        if (state == WorkstationState.BLOCKED || state == WorkstationState.ERROR) {
            WorkstationFailure failure = lastFailure().orElseGet(() -> WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Production-requested workstation processing stopped without a typed workstation failure"
            ));
            return WorkstationProductionRequestResult.rejected(snapshot, failure);
        }
        return WorkstationProductionRequestResult.accepted(snapshot);
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
        serverTick(registryAccess, null);
    }

    public void serverTickWithContext(WorkstationTickContext tickContext) {
        Objects.requireNonNull(tickContext, "tickContext");
        serverTick(tickContext.registryAccess(), tickContext);
    }

    private void serverTick(RegistryAccess registryAccess, WorkstationTickContext tickContext) {
        if (state == WorkstationState.IDLE) {
            if (!inventory.input().isEmpty()) {
                setState(WorkstationState.READY);
            } else {
                return;
            }
        }

        if (state == WorkstationState.READY) {
            startProcessing(registryAccess, tickContext);
            return;
        }

        if (state == WorkstationState.PROCESSING) {
            observeExecutionTerminalState(tickContext);
            if (state != WorkstationState.PROCESSING) {
                return;
            }
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
                if (executionCoordinator.isPresent()) {
                    dispatchScheduledEffect(tickContext);
                } else {
                    complete(registryAccess);
                }
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
        cancelPreservingInput(null);
    }

    public void cancelPreservingInput(WorkstationTickContext tickContext) {
        if (state == WorkstationState.PROCESSING || state == WorkstationState.BLOCKED || state == WorkstationState.READY) {
            if (activeExecutionOperationId != null && executionCoordinator.isPresent() && tickContext != null) {
                WorkstationExecutionCancelResult cancelled = executionCoordinator.orElseThrow().cancel(
                        new WorkstationExecutionCancelRequest(
                                tickContext,
                                activeExecutionOperationId,
                                "Workstation processing was cancelled before effect application"
                        )
                );
                if (!cancelled.accepted()) {
                    lastFailure = cancelled.failure().orElseThrow();
                }
            }
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
        if (activeExecutionOperationId != null) {
            tag.putString(ACTIVE_EXECUTION_OPERATION_TAG, activeExecutionOperationId.value());
        }
        if (activeDomainEffectIdentity != null) {
            tag.putString(ACTIVE_DOMAIN_EFFECT_TAG, activeDomainEffectIdentity.value());
        }
        tag.putBoolean(EXECUTION_WORK_SUBMITTED_TAG, executionWorkSubmitted);
        if (frozenInputIdentity != null) {
            tag.putString(FROZEN_INPUT_IDENTITY_TAG, frozenInputIdentity);
        }
        if (expectedOutputIdentity != null) {
            tag.putString(EXPECTED_OUTPUT_IDENTITY_TAG, expectedOutputIdentity);
        }
        if (sourceFreshnessIdentity != null) {
            tag.putString(SOURCE_FRESHNESS_IDENTITY_TAG, sourceFreshnessIdentity);
        }
        if (ownerResultEvidence != null) {
            tag.putString(OWNER_RESULT_IDENTITY_TAG, ownerResultEvidence.ownerResultIdentity());
            tag.putString(OWNER_RESULT_DIGEST_TAG, ownerResultEvidence.ownerResultDigest());
            tag.putString(OWNER_RESULT_CONTENT_DIGEST_TAG, ownerResultEvidence.contentDigest());
        }
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
            activeExecutionOperationId = tag.contains(ACTIVE_EXECUTION_OPERATION_TAG, Tag.TAG_STRING)
                    ? ExecutionOperationId.of(tag.getString(ACTIVE_EXECUTION_OPERATION_TAG))
                    : null;
            activeDomainEffectIdentity = tag.contains(ACTIVE_DOMAIN_EFFECT_TAG, Tag.TAG_STRING)
                    ? new ExecutionDomainEffectIdentity(tag.getString(ACTIVE_DOMAIN_EFFECT_TAG))
                    : null;
            executionWorkSubmitted = tag.getBoolean(EXECUTION_WORK_SUBMITTED_TAG);
            frozenInputIdentity = tag.contains(FROZEN_INPUT_IDENTITY_TAG, Tag.TAG_STRING)
                    ? tag.getString(FROZEN_INPUT_IDENTITY_TAG)
                    : null;
            expectedOutputIdentity = tag.contains(EXPECTED_OUTPUT_IDENTITY_TAG, Tag.TAG_STRING)
                    ? tag.getString(EXPECTED_OUTPUT_IDENTITY_TAG)
                    : null;
            sourceFreshnessIdentity = tag.contains(SOURCE_FRESHNESS_IDENTITY_TAG, Tag.TAG_STRING)
                    ? tag.getString(SOURCE_FRESHNESS_IDENTITY_TAG)
                    : null;
            ownerResultEvidence = loadOwnerResultEvidence(tag);
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

    private void startProcessing(RegistryAccess registryAccess, WorkstationTickContext tickContext) {
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
        if (executionCoordinator.isPresent() && tickContext == null) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Execution-backed workstation processing requires an authoritative server tick context"
            ));
            return;
        }

        selectedOperationId = operation.operationId();
        elapsedTicks = 0;
        totalTicks = operation.totalTicks();
        reservedInputSnapshot = inventory.input().copy();
        reservedInputSnapshots = inventory.inputs().stream().map(ItemStack::copy).toList();
        completionCommitted = false;
        activeExecutionOperationId = null;
        activeDomainEffectIdentity = null;
        executionWorkSubmitted = false;
        frozenInputIdentity = null;
        expectedOutputIdentity = null;
        sourceFreshnessIdentity = null;
        ownerResultEvidence = null;
        if (executionCoordinator.isPresent()) {
            WorkstationExecutionStartResult startResult = executionCoordinator.orElseThrow().start(
                    new WorkstationExecutionStartRequest(
                            tickContext,
                            capability,
                            operation,
                            reservedInputSnapshots,
                            prepared.proposedOutputs()
                    )
            );
            if (!startResult.accepted()) {
                resetRuntimeProgress();
                block(startResult.failure().orElseThrow());
                return;
            }
            activeExecutionOperationId = startResult.operationId().orElseThrow();
            activeDomainEffectIdentity = startResult.domainEffectIdentity().orElseThrow();
            frozenInputIdentity = startResult.frozenInputIdentity().orElseThrow();
            expectedOutputIdentity = startResult.expectedOutputIdentity().orElseThrow();
            sourceFreshnessIdentity = startResult.sourceFreshnessIdentity().orElseThrow();
        }
        clearFailure();
        setState(WorkstationState.PROCESSING);
    }

    private void retryBlockedCompletion(RegistryAccess registryAccess) {
        if (activeExecutionOperationId != null) {
            return;
        }
        if (inventory.input().isEmpty()) {
            resetToIdle();
            return;
        }
        if (selectedOperationId != null && elapsedTicks >= totalTicks && inventory.outputsEmpty()) {
            state = WorkstationState.PROCESSING;
            complete(registryAccess);
        }
    }

    private void observeExecutionTerminalState(WorkstationTickContext tickContext) {
        if (activeExecutionOperationId == null || executionCoordinator.isEmpty() || tickContext == null) {
            return;
        }
        Optional<WorkstationExecutionObservation> observation =
                executionCoordinator.orElseThrow().observe(activeExecutionOperationId, tickContext);
        if (observation.isEmpty()) {
            return;
        }
        ExecutionStatus status = observation.orElseThrow().status();
        if (status == ExecutionStatus.SUCCEEDED) {
            if (state != WorkstationState.COMPLETE) {
                block(WorkstationFailure.of(
                        WorkstationFailureCode.EXECUTION_RESULT_REJECTED,
                        "Execution succeeded but workstation completion state was not published"
                ));
            }
            return;
        }
        if (status.terminal()) {
            block(observation.orElseThrow().terminalFailure().orElseGet(() -> WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_RESULT_REJECTED,
                    "Execution operation reached terminal status " + status.serializedName()
            )));
        }
    }

    private void dispatchScheduledEffect(WorkstationTickContext tickContext) {
        if (activeExecutionOperationId == null) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Execution-backed workstation reached completion without an active Execution operation"
            ));
            return;
        }
        if (tickContext == null) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Execution-backed workstation completion requires an authoritative server tick context"
            ));
            return;
        }
        WorkstationExecutionDispatchResult dispatched = executionCoordinator.orElseThrow().dispatch(
                new WorkstationExecutionDispatchRequest(tickContext, activeExecutionOperationId)
        );
        if (!dispatched.accepted()) {
            block(dispatched.failure().orElseThrow());
            return;
        }
        executionWorkSubmitted = true;
        changed.run();
    }

    public WorkstationExecutionEffectResult completeScheduledExecution(
            RegistryAccess registryAccess,
            ExecutionOperationId operationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        if (activeExecutionOperationId == null || !activeExecutionOperationId.equals(operationId)) {
            return WorkstationExecutionEffectResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_RESULT_REJECTED,
                    "Scheduled Execution operation does not match the active workstation operation"
            ));
        }
        if (activeDomainEffectIdentity == null || !activeDomainEffectIdentity.equals(domainEffectIdentity)) {
            return WorkstationExecutionEffectResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_RESULT_REJECTED,
                    "Scheduled Execution domain Effect Identity does not match the workstation operation"
            ));
        }
        if (state == WorkstationState.COMPLETE && ownerResultEvidence != null) {
            return WorkstationExecutionEffectResult.accepted(ownerResultEvidence);
        }
        if (state != WorkstationState.PROCESSING) {
            return WorkstationExecutionEffectResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Workstation is not processing when Execution effect is dispatched"
            ));
        }
        if (elapsedTicks < totalTicks) {
            return WorkstationExecutionEffectResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Workstation received Execution effect before processing duration completed"
            ));
        }
        Optional<ExecutionOwnerResultEvidence> completed = complete(registryAccess, operationId, domainEffectIdentity,
                authoritativeTick);
        return completed
                .map(WorkstationExecutionEffectResult::accepted)
                .orElseGet(() -> WorkstationExecutionEffectResult.rejected(lastFailure().orElseGet(() ->
                        WorkstationFailure.of(
                                WorkstationFailureCode.RESULT_CREATION_FAILED,
                                "Workstation effect failed without a typed failure"
                        ))));
    }

    private void complete(RegistryAccess registryAccess) {
        complete(registryAccess, activeExecutionOperationId, activeDomainEffectIdentity, -1L);
    }

    private Optional<ExecutionOwnerResultEvidence> complete(
            RegistryAccess registryAccess,
            ExecutionOperationId executionOperationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            long authoritativeTick
    ) {
        if (completionCommitted) {
            block(WorkstationFailure.of(WorkstationFailureCode.TRANSACTION_ALREADY_ACTIVE, "Completion was already committed"));
            return Optional.empty();
        }
        if (!inventory.outputsEmpty()) {
            block(WorkstationFailure.of(WorkstationFailureCode.OUTPUT_OCCUPIED, "Output slot is occupied at completion"));
            return Optional.empty();
        }

        WorkstationOperationResolution resolution = resolver.resolve(registryAccess, capability, inventory.input());
        if (!resolution.succeeded()) {
            block(resolution.failure().orElseThrow());
            return Optional.empty();
        }
        ResolvedWorkstationOperation operation = resolution.operation().orElseThrow();
        if (selectedOperationId != null && !selectedOperationId.equals(operation.operationId())) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                    operation.operationId(),
                    "Resolved operation changed before completion"
            ));
            return Optional.empty();
        }

        OperationResult committed = executionStrategy.commit(capability, operation, inventory, outputMapping);
        if (!committed.succeeded() || committed.committedOutputs().isEmpty()) {
            block(WorkstationFailure.of(
                    failureCodeForResult(committed, WorkstationFailureCode.RESULT_CREATION_FAILED),
                    committed.failureReason().map(reason -> reason.message()).orElse("Processing transaction did not produce committed outputs")
            ));
            return Optional.empty();
        }
        if (committed.committedOutputs().size() > capability.outputSlots()
                || committed.committedOutputs().size() > inventory.outputSlotCount()) {
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    "Processing transaction produced more outputs than this workstation can hold"
            ));
            return Optional.empty();
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
                return Optional.empty();
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
            return Optional.empty();
        }

        ExecutionOwnerResultEvidence resultEvidence = executionOperationId == null || domainEffectIdentity == null
                ? null
                : ownerResultEvidence(
                        executionOperationId,
                        domainEffectIdentity,
                        operation,
                        committed.committedOutputs(),
                        authoritativeTick
                );
        try {
            completionCommitted = true;
            commitPlan.commit();
            elapsedTicks = totalTicks;
            if (resultEvidence != null) {
                ownerResultEvidence = resultEvidence;
            }
            clearFailure();
            setState(WorkstationState.COMPLETE);
        } catch (RuntimeException exception) {
            completionCommitted = false;
            block(WorkstationFailure.of(
                    WorkstationFailureCode.RESULT_CREATION_FAILED,
                    "Output insertion failed and workstation inventory was restored"
            ));
            return Optional.empty();
        }
        return Optional.ofNullable(resultEvidence);
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
        activeExecutionOperationId = null;
        activeDomainEffectIdentity = null;
        executionWorkSubmitted = false;
        frozenInputIdentity = null;
        expectedOutputIdentity = null;
        sourceFreshnessIdentity = null;
        ownerResultEvidence = null;
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
            if (state == WorkstationState.PROCESSING && completionCommitted) {
                state = WorkstationState.ERROR;
                lastFailure = WorkstationFailure.of(
                        WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                        "Active processing state had an unresolved committed effect after load"
                );
                resetRuntimeProgress();
            }
            if (state == WorkstationState.PROCESSING && activeExecutionOperationId != null) {
                if (activeDomainEffectIdentity == null
                        || frozenInputIdentity == null
                        || expectedOutputIdentity == null
                        || sourceFreshnessIdentity == null) {
                    state = WorkstationState.ERROR;
                    lastFailure = WorkstationFailure.of(
                            WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                            "Execution-backed processing state was incomplete after load"
                    );
                    resetRuntimeProgress();
                }
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

    private ExecutionOwnerResultEvidence loadOwnerResultEvidence(CompoundTag tag) {
        if (activeDomainEffectIdentity == null
                || !tag.contains(OWNER_RESULT_IDENTITY_TAG, Tag.TAG_STRING)
                || !tag.contains(OWNER_RESULT_DIGEST_TAG, Tag.TAG_STRING)
                || !tag.contains(OWNER_RESULT_CONTENT_DIGEST_TAG, Tag.TAG_STRING)) {
            return null;
        }
        return new ExecutionOwnerResultEvidence(
                com.butchercraft.world.execution.ExecutionSchema.CURRENT_VERSION,
                OWNER_SUBSYSTEM_ID,
                tag.getString(OWNER_RESULT_IDENTITY_TAG),
                activeDomainEffectIdentity,
                tag.getString(OWNER_RESULT_DIGEST_TAG),
                tag.getString(OWNER_RESULT_CONTENT_DIGEST_TAG)
        );
    }

    private ExecutionOwnerResultEvidence ownerResultEvidence(
            ExecutionOperationId executionOperationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            ResolvedWorkstationOperation operation,
            List<Product> outputProducts,
            long authoritativeTick
    ) {
        String ownerResultDigest = ownerResultDigest(
                executionOperationId,
                domainEffectIdentity,
                operation,
                outputProducts,
                authoritativeTick
        );
        String ownerResultIdentity = "butchercraft:workstation_result/v1/" + digestIdSuffix(ownerResultDigest);
        return ExecutionOwnerResultEvidence.of(
                OWNER_SUBSYSTEM_ID,
                ownerResultIdentity,
                domainEffectIdentity,
                ownerResultDigest
        );
    }

    private String ownerResultDigest(
            ExecutionOperationId executionOperationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            ResolvedWorkstationOperation operation,
            List<Product> outputProducts,
            long authoritativeTick
    ) {
        CanonicalDigest digest = CanonicalDigest.create("butchercraft:workstation_owner_result")
                .add(executionOperationId.value())
                .add(domainEffectIdentity.value())
                .add(selectedOperationId == null ? "" : selectedOperationId.toString())
                .add(operation.operationId().toString())
                .add(frozenInputIdentity == null ? "" : frozenInputIdentity)
                .add(expectedOutputIdentity == null ? "" : expectedOutputIdentity)
                .add(sourceFreshnessIdentity == null ? "" : sourceFreshnessIdentity)
                .add(authoritativeTick)
                .add(outputProducts.size());
        for (Product product : outputProducts) {
            digest.add(product.typeId().value())
                    .add(product.sourceCategory().id().value())
                    .add(product.processingState().id().value())
                    .add(product.quantity().amount())
                    .add(product.quantity().unit().id())
                    .add(product.quality().score());
        }
        return digest.finish();
    }

    private static String digestIdSuffix(String digest) {
        return digest.substring("sha256:".length());
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

    private static final class CanonicalDigest {
        private final MessageDigest digest;

        private CanonicalDigest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        static CanonicalDigest create(String domain) {
            return new CanonicalDigest(domain);
        }

        CanonicalDigest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "value").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) bytes.length);
            digest.update((byte) (bytes.length >>> 8));
            digest.update((byte) (bytes.length >>> 16));
            digest.update((byte) (bytes.length >>> 24));
            digest.update(bytes);
            return this;
        }

        CanonicalDigest add(long value) {
            return add(Long.toString(value));
        }

        CanonicalDigest add(int value) {
            return add(Integer.toString(value));
        }

        String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
