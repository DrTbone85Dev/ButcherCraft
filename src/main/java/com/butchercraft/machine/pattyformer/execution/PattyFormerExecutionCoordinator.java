package com.butchercraft.machine.pattyformer.execution;

import com.butchercraft.machine.pattyformer.PattyFormerWorkstation;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.execution.ExecutionAuthorization;
import com.butchercraft.world.execution.ExecutionAuthorizationEvidence;
import com.butchercraft.world.execution.ExecutionFailureCode;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationResult;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.simulation.SimulationClockService;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRuntime;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkSubmissionResult;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.workstation.WorkstationExecutionCancelRequest;
import com.butchercraft.workstation.WorkstationExecutionCancelResult;
import com.butchercraft.workstation.WorkstationExecutionCoordinator;
import com.butchercraft.workstation.WorkstationExecutionDispatchRequest;
import com.butchercraft.workstation.WorkstationExecutionDispatchResult;
import com.butchercraft.workstation.WorkstationExecutionObservation;
import com.butchercraft.workstation.WorkstationExecutionStartRequest;
import com.butchercraft.workstation.WorkstationExecutionStartResult;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationTickContext;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class PattyFormerExecutionCoordinator implements WorkstationExecutionCoordinator {
    public static final PattyFormerExecutionCoordinator INSTANCE = new PattyFormerExecutionCoordinator();

    private PattyFormerExecutionCoordinator() {
    }

    @Override
    public WorkstationExecutionStartResult start(WorkstationExecutionStartRequest request) {
        Objects.requireNonNull(request, "request");
        if (!PattyFormerExecutionConstants.PROMOTED_PATTY_FORMER_OPERATIONS.contains(request.operation().operationId())) {
            return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.NO_COMPATIBLE_OPERATION,
                    request.operation().operationId(),
                    "Patty Former Execution authorizes only the promoted beef patties operation"
            ));
        }
        if (request.operation().definition().operation().workstationCapability()
                .filter(PattyFormerWorkstation.CAPABILITY_ID::equals)
                .isEmpty()) {
            return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.NO_COMPATIBLE_OPERATION,
                    request.operation().operationId(),
                    "Patty Former Execution authorizes only operations that declare the patty former capability"
            ));
        }
        try {
            PattyFormerExecutionPreparation preparation = prepareAuthorization(request, List.of());
            MinecraftServer server = request.tickContext().level().getServer();
            long tick = simulationTick(server);
            ExecutionOperationResult<ExecutionOperationSnapshot> accepted = execution(server)
                    .acceptAuthorization(preparation.authorization(), tick);
            if (!accepted.accepted()) {
                return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                        WorkstationFailureCode.EXECUTION_AUTHORIZATION_REJECTED,
                        first(accepted.messages(), accepted.failureCode().orElse(ExecutionFailureCode.UNKNOWN).serializedName())
                ));
            }
            return acceptedResult(preparation, accepted.value().orElseThrow());
        } catch (RuntimeException exception) {
            return WorkstationExecutionStartResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_AUTHORIZATION_REJECTED,
                    exception.getMessage() == null
                            ? "Patty Former Execution authorization failed"
                            : exception.getMessage()
            ));
        }
    }

    public PattyFormerExecutionPreparation prepareAuthorization(
            WorkstationExecutionStartRequest request,
            List<String> additionalExplicitInputIdentities
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(additionalExplicitInputIdentities, "additionalExplicitInputIdentities");
        if (!PattyFormerExecutionConstants.PROMOTED_PATTY_FORMER_OPERATIONS
                .contains(request.operation().operationId())) {
            throw new IllegalArgumentException("Patty Former Execution authorizes only the beef patties operation");
        }
        if (request.operation().definition().operation().workstationCapability()
                .filter(PattyFormerWorkstation.CAPABILITY_ID::equals)
                .isEmpty()) {
            throw new IllegalArgumentException("Patty Former operation does not declare its capability");
        }
        MinecraftServer server = request.tickContext().level().getServer();
        long tick = simulationTick(server);
        String workstationIdentity = PattyFormerWorkstationReference.of(
                request.tickContext().level(),
                request.tickContext().blockPos()
        ).identity();
        String operationIdentity = PattyFormerExecutionIdentities.operationIdentity(request.operation());
        String frozenInputIdentity = PattyFormerExecutionIdentities.inputIdentity(request.frozenInputs());
        String expectedOutputIdentity = PattyFormerExecutionIdentities.expectedOutputIdentity(request.expectedOutputs());
        String sourceFreshnessIdentity = PattyFormerExecutionIdentities.sourceFreshnessIdentity(
                workstationIdentity,
                request.operation(),
                operationIdentity,
                frozenInputIdentity,
                expectedOutputIdentity
        );
        List<String> explicitInputs = new java.util.ArrayList<>(List.of(
                workstationIdentity,
                operationIdentity,
                frozenInputIdentity,
                expectedOutputIdentity
        ));
        additionalExplicitInputIdentities.forEach(identity -> {
            if (!explicitInputs.contains(identity)) explicitInputs.add(identity);
        });
        ExecutionAuthorizationEvidence evidence = ExecutionAuthorizationEvidence.issued(
                PattyFormerExecutionConstants.OWNER_SUBSYSTEM_ID,
                PattyFormerExecutionConstants.EXECUTABLE_REFERENCE_TYPE,
                workstationIdentity,
                PattyFormerExecutionConstants.OPERATION_TYPE,
                PattyFormerExecutionConstants.HANDLER_ID,
                frozenInputIdentity,
                sourceFreshnessIdentity,
                PattyFormerExecutionConstants.CONFIGURATION_IDENTITY,
                worldIdentity(server),
                tick,
                OptionalLong.of(Math.addExact(tick, request.operation().totalTicks() + 200L)),
                explicitInputs
        );
        return new PattyFormerExecutionPreparation(
                ExecutionAuthorization.issue(evidence),
                frozenInputIdentity,
                expectedOutputIdentity,
                sourceFreshnessIdentity
        );
    }

    public WorkstationExecutionStartResult acceptedResult(
            PattyFormerExecutionPreparation preparation,
            ExecutionOperationSnapshot operation
    ) {
        Objects.requireNonNull(preparation, "preparation");
        Objects.requireNonNull(operation, "operation");
        if (!operation.operationId().equals(preparation.authorization().operationId())) {
            throw new IllegalArgumentException("Accepted Patty Former operation does not match prepared authorization");
        }
        return WorkstationExecutionStartResult.accepted(
                operation.operationId(),
                operation.domainEffectIdentity(),
                preparation.frozenInputIdentity(),
                preparation.expectedOutputIdentity(),
                preparation.sourceFreshnessIdentity()
        );
    }

    @Override
    public WorkstationExecutionDispatchResult dispatch(WorkstationExecutionDispatchRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            MinecraftServer server = request.tickContext().level().getServer();
            long tick = simulationTick(server);
            ExecutionOperationResult<SimulationWorkRequest> prepared = execution(server)
                    .prepareSchedulerWork(request.operationId(), tick, tick);
            if (!prepared.accepted()) {
                return WorkstationExecutionDispatchResult.rejected(WorkstationFailure.of(
                        WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                        first(prepared.messages(), prepared.failureCode().orElse(ExecutionFailureCode.UNKNOWN).serializedName())
                ));
            }

            SimulationSchedulerManager scheduler = scheduler(server);
            SimulationWorkRequest workRequest = prepared.value().orElseThrow();
            WorkSubmissionResult submitted = scheduler.submit(workRequest, tick);
            if (submitted.accepted()
                    || submitted.failureCode().orElse(null) == WorkFailureCode.DUPLICATE_WORK_ID
                    && scheduler.runtimeFor(workRequest.id()).isPresent()) {
                return WorkstationExecutionDispatchResult.acceptedResult();
            }
            return WorkstationExecutionDispatchResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                    first(submitted.messages(), submitted.failureCode().orElse(WorkFailureCode.UNKNOWN).serializedName())
            ));
        } catch (RuntimeException exception) {
            return WorkstationExecutionDispatchResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                    exception.getMessage() == null ? "Patty Former Execution dispatch failed" : exception.getMessage()
            ));
        }
    }

    @Override
    public WorkstationExecutionCancelResult cancel(WorkstationExecutionCancelRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            MinecraftServer server = request.tickContext().level().getServer();
            long tick = simulationTick(server);
            SimulationSchedulerManager scheduler = scheduler(server);
            SimulationWorkId workId = workIdFor(request.operationId());
            Optional<SimulationWorkRuntime> runtime = scheduler.runtimeFor(workId);
            if (runtime.isPresent() && !runtime.orElseThrow().status().isTerminal()) {
                var cancelled = scheduler.cancel(workId, tick, request.reason());
                if (!cancelled.successful()) {
                    return WorkstationExecutionCancelResult.rejected(WorkstationFailure.of(
                            WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                            first(cancelled.messages(), cancelled.failureCode().orElse(WorkFailureCode.UNKNOWN).serializedName())
                    ));
                }
            }
            ExecutionOperationResult<ExecutionOperationSnapshot> cancelled = execution(server)
                    .cancelBeforeStart(request.operationId(), tick, request.reason());
            if (cancelled.accepted()
                    || cancelled.failureCode().orElse(null) == ExecutionFailureCode.UNKNOWN_OPERATION
                    || cancelled.failureCode().orElse(null) == ExecutionFailureCode.INVALID_STATUS) {
                return WorkstationExecutionCancelResult.acceptedResult();
            }
            return WorkstationExecutionCancelResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                    first(cancelled.messages(), cancelled.failureCode().orElse(ExecutionFailureCode.UNKNOWN).serializedName())
            ));
        } catch (RuntimeException exception) {
            return WorkstationExecutionCancelResult.rejected(WorkstationFailure.of(
                    WorkstationFailureCode.EXECUTION_DISPATCH_REJECTED,
                    exception.getMessage() == null
                            ? "Patty Former Execution cancellation failed"
                            : exception.getMessage()
            ));
        }
    }

    @Override
    public Optional<WorkstationExecutionObservation> observe(
            ExecutionOperationId operationId,
            WorkstationTickContext context
    ) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(context, "context");
        return execution(context.level().getServer()).find(operationId)
                .filter(snapshot -> snapshot.status().terminal())
                .map(snapshot -> new WorkstationExecutionObservation(
                        snapshot.status(),
                        terminalFailure(snapshot)
                ));
    }

    private static Optional<WorkstationFailure> terminalFailure(ExecutionOperationSnapshot snapshot) {
        if (snapshot.status() == ExecutionStatus.SUCCEEDED) {
            return Optional.empty();
        }
        WorkstationFailureCode code = snapshot.status() == ExecutionStatus.UNKNOWN_OUTCOME
                ? WorkstationFailureCode.EXECUTION_OUTCOME_UNKNOWN
                : WorkstationFailureCode.EXECUTION_RESULT_REJECTED;
        String message = snapshot.failure()
                .map(failure -> failure.message())
                .orElse("Execution reached terminal status " + snapshot.status().serializedName());
        return Optional.of(WorkstationFailure.of(code, message));
    }

    private static ExecutionManager execution(MinecraftServer server) {
        return ExecutionService.INSTANCE.managerFor(server);
    }

    private static SimulationSchedulerManager scheduler(MinecraftServer server) {
        return SimulationSchedulerService.INSTANCE.managerFor(server);
    }

    private static long simulationTick(MinecraftServer server) {
        return SimulationClockService.INSTANCE.clock(server).simulationTick();
    }

    private static String worldIdentity(MinecraftServer server) {
        return WorldIdentityRootIdentities.from(WorldIdentityService.INSTANCE.getOrCreate(server)).identity();
    }

    private static SimulationWorkId workIdFor(ExecutionOperationId operationId) {
        return SimulationWorkId.of(operationId.value() + "/work");
    }

    private static String first(List<String> messages, String fallback) {
        return messages.isEmpty() ? fallback : messages.getFirst();
    }
}
