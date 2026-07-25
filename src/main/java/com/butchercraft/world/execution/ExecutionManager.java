package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectObservation;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkOutcome;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkReference;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionManager {
    private final ExecutionHandlerRegistry handlerRegistry;
    private final ExecutionRuntimeConfiguration configuration;
    private final Map<ExecutionOperationId, ExecutionOperationRecord> operations = new LinkedHashMap<>();
    private final Map<String, ExecutionOperationId> operationByAuthorization = new LinkedHashMap<>();
    private final AtomicBoolean authorityExecuting = new AtomicBoolean();

    public ExecutionManager(ExecutionHandlerRegistry handlerRegistry, ExecutionRuntimeConfiguration configuration) {
        this(handlerRegistry, configuration, List.of());
    }

    public ExecutionManager(
            ExecutionHandlerRegistry handlerRegistry,
            ExecutionRuntimeConfiguration configuration,
            Collection<ExecutionOperationSnapshot> loadedOperations
    ) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        for (ExecutionOperationSnapshot snapshot : Objects.requireNonNull(loadedOperations, "loadedOperations")) {
            ExecutionOperationRecord record = ExecutionOperationRecord.fromSnapshot(snapshot);
            addLoaded(record);
        }
        validateCapacity();
    }

    public synchronized ExecutionHandlerRegistry handlerRegistry() {
        return handlerRegistry;
    }

    public synchronized ExecutionRuntimeConfiguration configuration() {
        return configuration;
    }

    public synchronized List<ExecutionOperationSnapshot> operations() {
        return operations.values().stream()
                .map(ExecutionOperationRecord::snapshot)
                .sorted(Comparator.comparing(ExecutionOperationSnapshot::operationId))
                .toList();
    }

    public synchronized Optional<ExecutionOperationSnapshot> find(ExecutionOperationId id) {
        ExecutionOperationRecord record = operations.get(Objects.requireNonNull(id, "id"));
        return record == null ? Optional.empty() : Optional.of(record.snapshot());
    }

    public synchronized ExecutionOperationResult<ExecutionOperationSnapshot> cancelBeforeStart(
            ExecutionOperationId operationId,
            long tick,
            String reason
    ) {
        ExecutionValidation.requireTick(tick, "Execution cancellation tick");
        ExecutionOperationRecord record = operations.get(Objects.requireNonNull(operationId, "operationId"));
        if (record == null) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.UNKNOWN_OPERATION,
                    "Unknown Execution operation: " + operationId.value()
            );
        }
        ExecutionOperationSnapshot snapshot = record.snapshot();
        if (snapshot.schedulerInvocationStarted()) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.CANCEL_UNSUPPORTED_AFTER_START,
                    "Execution cancellation is supported only before invocation starts"
            );
        }
        if (snapshot.status().terminal()) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.INVALID_STATUS,
                    "Execution operation is already terminal: " + snapshot.status()
            );
        }
        record.cancelBeforeStart(tick, reason);
        return ExecutionOperationResult.accepted(record.snapshot());
    }

    public synchronized ExecutionOperationResult<ExecutionOperationSnapshot> acceptAuthorization(
            ExecutionAuthorization authorization,
            long tick
    ) {
        Objects.requireNonNull(authorization, "authorization");
        ExecutionValidation.requireTick(tick, "Execution authorization acceptance tick");
        ExecutionAuthorizationEvidence evidence = authorization.evidence();
        ExecutionOperationResult<Boolean> evidenceValidation = validateEvidence(evidence, tick);
        if (!evidenceValidation.accepted()) {
            return ExecutionOperationResult.rejected(
                    evidenceValidation.failureCode().orElseThrow(),
                    evidenceValidation.messages().getFirst()
            );
        }
        ExecutionOperationId operationId = ExecutionOperationId.derive(evidence);
        ExecutionOperationId existingForAuthorization = operationByAuthorization.get(evidence.authorizationIdentity());
        if (existingForAuthorization != null) {
            ExecutionOperationRecord existing = operations.get(existingForAuthorization);
            if (existing == null) {
                return ExecutionOperationResult.rejected(
                        ExecutionFailureCode.INTERNAL_INVARIANT_VIOLATION,
                        "Execution authorization index references a missing operation"
                );
            }
            if (!existing.authorizationEvidence().authorizationContentDigest()
                    .equals(evidence.authorizationContentDigest())
                    || !existing.operationId().equals(operationId)) {
                return ExecutionOperationResult.rejected(
                        ExecutionFailureCode.AUTHORIZATION_IDENTITY_CONFLICT,
                        "Same Execution authorization identity has conflicting canonical content"
                );
            }
            return ExecutionOperationResult.accepted(existing.snapshot());
        }
        ExecutionOperationRecord existingOperation = operations.get(operationId);
        if (existingOperation != null) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.OPERATION_IDENTITY_CONFLICT,
                    "Execution operation identity already exists with different authorization"
            );
        }
        if (!authorization.consume()) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.AUTHORIZATION_ALREADY_CONSUMED,
                    "Execution authorization was already consumed by a live runtime"
            );
        }
        if (activeOperationCount() >= configuration.maximumActiveOperations()) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.RUNTIME_CAPACITY_EXHAUSTED,
                    "Execution runtime active operation capacity is exhausted"
            );
        }
        ExecutionOperationRecord record = ExecutionOperationRecord.create(evidence, tick);
        operations.put(record.operationId(), record);
        operationByAuthorization.put(evidence.authorizationIdentity(), record.operationId());
        return ExecutionOperationResult.accepted(record.snapshot());
    }

    public synchronized ExecutionOperationResult<SimulationWorkRequest> prepareSchedulerWork(
            ExecutionOperationId operationId,
            long submissionTick,
            long scheduledTick
    ) {
        ExecutionOperationRecord record = operations.get(Objects.requireNonNull(operationId, "operationId"));
        if (record == null) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.UNKNOWN_OPERATION,
                    "Unknown Execution operation: " + operationId.value()
            );
        }
        ExecutionOperationSnapshot snapshot = record.snapshot();
        if (snapshot.status() == ExecutionStatus.AUTHORIZED) {
            record.ready(submissionTick);
            snapshot = record.snapshot();
        }
        if (snapshot.status() != ExecutionStatus.READY) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.INVALID_STATUS,
                    "Execution operation must be AUTHORIZED or READY before Scheduler Work is prepared"
            );
        }
        ExecutionAuthorizationEvidence evidence = snapshot.authorizationEvidence();
        SimulationWorkId workId = workIdFor(operationId);
        SimulationWorkRequest request = SimulationWorkRequest.builder()
                .id(workId)
                .typeId(ExecutionWorkTypes.GENERIC_EXECUTION_OPERATION)
                .stageId(BuiltInSimulationStages.EXECUTION)
                .scheduledTick(scheduledTick)
                .origin(new WorkOrigin(
                        "butchercraft:execution",
                        Optional.of("butchercraft:execution_operation"),
                        Optional.of(operationId.value()),
                        submissionTick,
                        "butchercraft:execution_authority",
                        Optional.of(evidence.frozenInputIdentity()),
                        Optional.empty()
                ))
                .payload(new WorkPayload(List.of(WorkPayloadEntry.identifier(
                        ExecutionWorkTypes.OPERATION_ID_PAYLOAD_KEY,
                        operationId.value()
                ))))
                .retryPolicy(RetryPolicy.never())
                .maximumAttempts(configuration.maximumAttemptsPerOperation())
                .references(List.of(
                        new WorkReference("butchercraft:execution_operation", operationId.value()),
                        new WorkReference("butchercraft:execution_authorization", evidence.authorizationIdentity()),
                        new WorkReference(evidence.executableWorkReferenceType(), evidence.executableWorkReferenceId())
                ))
                .build();
        return ExecutionOperationResult.accepted(request);
    }

    public SimulationWorkResult executeScheduledOperation(
            ExecutionOperationId operationId,
            SimulationExecutionContext context
    ) {
        Objects.requireNonNull(context, "context");
        if (!authorityExecuting.compareAndSet(false, true)) {
            return failedWork(
                    context.authoritativeSimulationTick(),
                    WorkFailureCode.INTERNAL_INVARIANT_VIOLATION,
                    "The world-scoped Execution Authority is already executing",
                    0
            );
        }
        try {
            return executeScheduledOperationExclusive(operationId, context);
        } finally {
            authorityExecuting.set(false);
        }
    }

    public synchronized void validateForPersistence() {
        operations.values().forEach(ExecutionOperationRecord::validateForPersistence);
        validateCapacity();
    }

    private synchronized SimulationWorkResult executeScheduledOperationExclusive(
            ExecutionOperationId operationId,
            SimulationExecutionContext context
    ) {
        long tick = context.authoritativeSimulationTick();
        ExecutionOperationRecord record = operations.get(Objects.requireNonNull(operationId, "operationId"));
        if (record == null) {
            return failedWork(tick, WorkFailureCode.UNKNOWN_WORK, "Unknown Execution operation", 1);
        }
        ExecutionOperationSnapshot snapshot = record.snapshot();
        if (!context.work().id().equals(workIdFor(operationId))) {
            return failedWork(tick, WorkFailureCode.INVALID_PAYLOAD,
                    "Scheduler Work identity does not match Execution operation", 1);
        }
        if (context.effectIdentity().isEmpty()) {
            return failedWork(tick, WorkFailureCode.MISSING_REQUIRED_EFFECT_IDENTITY,
                    "Execution Scheduler Work requires Effect Identity", 1);
        }
        if (snapshot.status().terminal()) {
            return terminalWorkResult(snapshot, tick);
        }
        if (snapshot.status() != ExecutionStatus.READY && snapshot.status() != ExecutionStatus.AWAITING_OWNER_RESULT) {
            return failOperation(record, tick, WorkFailureCode.INVALID_STATUS,
                    ExecutionFailureCode.INVALID_STATUS,
                    "Execution operation is not eligible for Scheduler dispatch", 1);
        }
        ExecutionOperationHandler handler = handlerRegistry.findByOperationType(
                snapshot.authorizationEvidence().operationType()
        ).orElse(null);
        if (handler == null) {
            return failOperation(record, tick, WorkFailureCode.HANDLER_NOT_REGISTERED,
                    ExecutionFailureCode.HANDLER_NOT_REGISTERED,
                    "No Execution handler is registered for the operation type", 1);
        }
        if (!handler.contract().handlerId().equals(snapshot.authorizationEvidence().handlerId())) {
            return failOperation(record, tick, WorkFailureCode.INVALID_PAYLOAD,
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Execution handler id does not match authorization evidence", 1);
        }
        if (snapshot.authorizationEvidence().expiredAt(tick)) {
            return failOperation(record, tick, WorkFailureCode.HANDLER_REJECTED,
                    ExecutionFailureCode.STALE_AUTHORIZATION_EVIDENCE,
                    "Execution authorization evidence is stale", 1);
        }
        ExecutionHandlerValidation authorizationValidation = handler.validateAuthorization(
                snapshot.authorizationEvidence()
        );
        if (!authorizationValidation.accepted()) {
            return failOperation(record, tick, WorkFailureCode.HANDLER_REJECTED,
                    authorizationValidation.failureCode().orElse(ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION),
                    first(authorizationValidation.messages(), "Execution handler rejected authorization evidence"),
                    1);
        }

        ExecutionStatus startingStatus = snapshot.status();
        ExecutionAttemptId attemptId = record.beginAttempt(
                tick,
                context.invocationIdentity(),
                context.effectIdentity().orElseThrow(),
                handler.contract().handlerId()
        );
        ExecutionHandlerResult handlerResult;
        try {
            handlerResult = Objects.requireNonNull(handler.execute(new ExecutionHandlerContext(
                    tick,
                    record.snapshot(),
                    context.invocationIdentity(),
                    context.effectIdentity().orElseThrow(),
                    Math.min(context.remainingWorkUnits(), handler.contract().maximumWorkUnits())
            )), "Execution handler result");
        } catch (RuntimeException exception) {
            record.unknownOutcome(
                    tick,
                    "Execution handler threw after invocation began: "
                            + exception.getClass().getSimpleName(),
                    Optional.of(context.invocationIdentity()),
                    context.effectIdentity()
            );
            throw exception;
        }
        if (handlerResult.workUnits() > context.remainingWorkUnits()
                || handlerResult.workUnits() > handler.contract().maximumWorkUnits()) {
            ExecutionFailure failure = ExecutionFailure.of(
                    ExecutionFailureCode.PUBLICATION_REJECTED,
                    "Execution handler exceeded deterministic work-unit budget",
                    operationId.value()
            );
            record.publishAttemptResult(
                    attemptId,
                    tick,
                    context.invocationIdentity(),
                    context.effectIdentity().orElseThrow(),
                    startingStatus,
                    ExecutionStatus.FAILED,
                    handler.contract().handlerId(),
                    Optional.empty(),
                    Optional.of(failure),
                0
            );
            return failedWork(tick, WorkFailureCode.BUDGET_EXHAUSTED, failure.message(), 0);
        }
        if (handlerResult.outcome() == ExecutionHandlerOutcome.WAITING_FOR_OWNER_RESULT
                && startingStatus != ExecutionStatus.AWAITING_OWNER_RESULT
                && pendingOwnerResultCount() >= configuration.maximumPendingOwnerResults()) {
            ExecutionFailure failure = ExecutionFailure.of(
                    ExecutionFailureCode.RUNTIME_CAPACITY_EXHAUSTED,
                    "Execution pending owner-result capacity is exhausted",
                    operationId.value()
            );
            record.publishAttemptResult(
                    attemptId,
                    tick,
                    context.invocationIdentity(),
                    context.effectIdentity().orElseThrow(),
                    startingStatus,
                    ExecutionStatus.FAILED,
                    handler.contract().handlerId(),
                    Optional.empty(),
                    Optional.of(failure),
                    handlerResult.workUnits()
            );
            return failedWork(tick, WorkFailureCode.BUDGET_EXHAUSTED, failure.message(), handlerResult.workUnits());
        }
        return publishHandlerResult(record, attemptId, startingStatus, handler, handlerResult, context);
    }

    private SimulationWorkResult publishHandlerResult(
            ExecutionOperationRecord record,
            ExecutionAttemptId attemptId,
            ExecutionStatus startingStatus,
            ExecutionOperationHandler handler,
            ExecutionHandlerResult handlerResult,
            SimulationExecutionContext context
    ) {
        long tick = context.authoritativeSimulationTick();
        ExecutionStatus endingStatus = switch (handlerResult.outcome()) {
            case OWNER_RESULT_PUBLISHED -> ExecutionStatus.SUCCEEDED;
            case WAITING_FOR_OWNER_RESULT -> ExecutionStatus.AWAITING_OWNER_RESULT;
            case REJECTED -> ExecutionStatus.REJECTED;
            case FAILED -> ExecutionStatus.FAILED;
        };
        record.publishAttemptResult(
                attemptId,
                tick,
                context.invocationIdentity(),
                context.effectIdentity().orElseThrow(),
                startingStatus,
                endingStatus,
                handler.contract().handlerId(),
                handlerResult.ownerResultEvidence(),
                handlerResult.failure(),
                handlerResult.workUnits()
        );
        ExecutionOperationSnapshot snapshot = record.snapshot();
        return switch (handlerResult.outcome()) {
            case OWNER_RESULT_PUBLISHED -> {
                ExecutionResultEvidence resultEvidence = snapshot.resultEvidence().orElseThrow();
                SchedulerEffectObservation observation = SchedulerEffectObservation.of(
                        context.effectIdentity().orElseThrow(),
                        HandlerEffectType.IDEMPOTENT,
                        "butchercraft:execution",
                        resultEvidence.evidenceIdentity(),
                        resultEvidence.resultContentDigest()
                );
                yield SimulationWorkResult.completed(tick, handlerResult.workUnits(), resultPayload(snapshot), observation);
            }
            case WAITING_FOR_OWNER_RESULT -> new SimulationWorkResult(
                    SimulationWorkOutcome.DEFERRED,
                    Optional.empty(),
                    handlerResult.diagnostics(),
                    handlerResult.nextEligibleTick(),
                    List.of(),
                    resultPayload(snapshot),
                    handlerResult.workUnits(),
                    tick
            );
            case REJECTED, FAILED -> failedWork(
                    tick,
                    WorkFailureCode.HANDLER_REJECTED,
                    handlerResult.failure().orElseThrow().message(),
                    handlerResult.workUnits()
            );
        };
    }

    private SimulationWorkResult terminalWorkResult(ExecutionOperationSnapshot snapshot, long tick) {
        if (snapshot.status() == ExecutionStatus.SUCCEEDED && snapshot.resultEvidence().isPresent()) {
            SchedulerEffectObservation observation = SchedulerEffectObservation.of(
                    snapshot.resultEvidence().orElseThrow().schedulerEffectIdentity().orElseThrow(),
                    HandlerEffectType.IDEMPOTENT,
                    "butchercraft:execution",
                    snapshot.resultEvidence().orElseThrow().evidenceIdentity(),
                    snapshot.resultEvidence().orElseThrow().resultContentDigest()
            );
            return SimulationWorkResult.completed(tick, 1, resultPayload(snapshot), observation);
        }
        return failedWork(tick, WorkFailureCode.INVALID_STATUS,
                "Execution operation is already terminal: " + snapshot.status(), 1);
    }

    private SimulationWorkResult failOperation(
            ExecutionOperationRecord record,
            long tick,
            WorkFailureCode workCode,
            ExecutionFailureCode executionCode,
            String message,
            int workUnits
    ) {
        ExecutionOperationSnapshot before = record.snapshot();
        ExecutionFailure failure = ExecutionFailure.of(executionCode, message, before.operationId().value());
        ExecutionStatus terminal = executionCode == ExecutionFailureCode.INVALID_AUTHORIZATION_EVIDENCE
                || executionCode == ExecutionFailureCode.STALE_AUTHORIZATION_EVIDENCE
                || executionCode == ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION
                ? ExecutionStatus.REJECTED
                : ExecutionStatus.FAILED;
        if (before.status() == ExecutionStatus.AUTHORIZED) {
            record.ready(tick);
        }
        if (record.snapshot().status() == ExecutionStatus.READY) {
            record.terminalBeforeInvocation(tick, terminal, failure);
        }
        return failedWork(tick, workCode, message, workUnits);
    }

    private ExecutionOperationResult<Boolean> validateEvidence(ExecutionAuthorizationEvidence evidence, long tick) {
        if (!evidence.digestMatches()) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.INVALID_AUTHORIZATION_EVIDENCE,
                    "Execution authorization evidence digest does not match content"
            );
        }
        if (evidence.expiredAt(tick)) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.STALE_AUTHORIZATION_EVIDENCE,
                    "Execution authorization evidence expired before acceptance"
            );
        }
        ExecutionOperationHandler handler = handlerRegistry.findByOperationType(evidence.operationType()).orElse(null);
        if (handler == null) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.HANDLER_NOT_REGISTERED,
                    "No Execution handler registered for operation type: " + evidence.operationType()
            );
        }
        if (!handler.contract().handlerId().equals(evidence.handlerId())) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Execution authorization handler id is not registered for its operation type"
            );
        }
        if (!handler.contract().configurationIdentity().equals(evidence.configurationIdentity())) {
            return ExecutionOperationResult.rejected(
                    ExecutionFailureCode.INVALID_AUTHORIZATION_EVIDENCE,
                    "Execution authorization configuration identity does not match handler contract"
            );
        }
        ExecutionHandlerValidation validation = handler.validateAuthorization(evidence);
        if (!validation.accepted()) {
            return ExecutionOperationResult.rejected(
                    validation.failureCode().orElse(ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION),
                    first(validation.messages(), "Execution handler rejected authorization")
            );
        }
        return ExecutionOperationResult.accepted(Boolean.TRUE);
    }

    private void addLoaded(ExecutionOperationRecord record) {
        if (operations.putIfAbsent(record.operationId(), record) != null) {
            throw new IllegalArgumentException("Duplicate Execution operation id: " + record.operationId().value());
        }
        String authorizationIdentity = record.authorizationEvidence().authorizationIdentity();
        ExecutionOperationId duplicate = operationByAuthorization.putIfAbsent(authorizationIdentity, record.operationId());
        if (duplicate != null && !duplicate.equals(record.operationId())) {
            throw new IllegalArgumentException("Execution authorization identity maps to multiple operations");
        }
    }

    private void validateCapacity() {
        if (activeOperationCount() > configuration.maximumActiveOperations()) {
            throw new IllegalArgumentException("Loaded Execution operations exceed active operation capacity");
        }
        if (pendingOwnerResultCount() > configuration.maximumPendingOwnerResults()) {
            throw new IllegalArgumentException("Loaded Execution operations exceed pending owner-result capacity");
        }
        long terminal = operations.values().stream().map(ExecutionOperationRecord::snapshot)
                .filter(operation -> operation.status().terminal()).count();
        if (terminal > configuration.maximumRetainedTerminalOperations()) {
            throw new IllegalArgumentException("Loaded Execution operations exceed terminal retention capacity");
        }
    }

    private long activeOperationCount() {
        return operations.values().stream().map(ExecutionOperationRecord::snapshot)
                .filter(operation -> !operation.status().terminal()).count();
    }

    private long pendingOwnerResultCount() {
        return operations.values().stream().map(ExecutionOperationRecord::snapshot)
                .filter(operation -> operation.status() == ExecutionStatus.AWAITING_OWNER_RESULT).count();
    }

    private static SimulationWorkResult failedWork(long tick, WorkFailureCode code, String message, int units) {
        return SimulationWorkResult.failed(tick, code, message, units);
    }

    private static WorkPayload resultPayload(ExecutionOperationSnapshot snapshot) {
        List<WorkPayloadEntry> entries = new ArrayList<>();
        entries.add(WorkPayloadEntry.identifier(
                ExecutionWorkTypes.OPERATION_ID_PAYLOAD_KEY,
                snapshot.operationId().value()
        ));
        entries.add(WorkPayloadEntry.identifier(
                "butchercraft:execution_status",
                "butchercraft:execution_status/" + snapshot.status().serializedName()
        ));
        snapshot.resultEvidence().ifPresent(evidence -> entries.add(WorkPayloadEntry.identifier(
                "butchercraft:execution_result_evidence",
                evidence.evidenceIdentity()
        )));
        return new WorkPayload(entries);
    }

    private static SimulationWorkId workIdFor(ExecutionOperationId operationId) {
        return SimulationWorkId.of(operationId.value() + "/work");
    }

    private static String first(List<String> messages, String fallback) {
        return messages.isEmpty() ? fallback : messages.getFirst();
    }
}
