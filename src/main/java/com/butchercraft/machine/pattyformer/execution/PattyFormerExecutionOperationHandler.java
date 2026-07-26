package com.butchercraft.machine.pattyformer.execution;

import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.world.execution.ExecutionAuthorizationEvidence;
import com.butchercraft.world.execution.ExecutionFailure;
import com.butchercraft.world.execution.ExecutionFailureCode;
import com.butchercraft.world.execution.ExecutionHandlerContext;
import com.butchercraft.world.execution.ExecutionHandlerContract;
import com.butchercraft.world.execution.ExecutionHandlerResult;
import com.butchercraft.world.execution.ExecutionHandlerValidation;
import com.butchercraft.world.execution.ExecutionOperationHandler;
import com.butchercraft.workstation.WorkstationExecutionEffectResult;
import com.butchercraft.workstation.WorkstationFailure;
import com.butchercraft.workstation.WorkstationFailureCode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class PattyFormerExecutionOperationHandler implements ExecutionOperationHandler {
    private final Supplier<MinecraftServer> serverSupplier;
    private final ExecutionHandlerContract contract = ExecutionHandlerContract.idempotent(
            PattyFormerExecutionConstants.HANDLER_ID,
            PattyFormerExecutionConstants.OPERATION_TYPE,
            50,
            PattyFormerExecutionConstants.CONFIGURATION_IDENTITY
    );

    public PattyFormerExecutionOperationHandler(MinecraftServer server) {
        this(() -> Objects.requireNonNull(server, "server"));
    }

    PattyFormerExecutionOperationHandler(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier");
    }

    @Override
    public ExecutionHandlerContract contract() {
        return contract;
    }

    @Override
    public ExecutionHandlerValidation validateAuthorization(ExecutionAuthorizationEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        if (!PattyFormerExecutionConstants.OPERATION_TYPE.equals(evidence.operationType())) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.UNSUPPORTED_OPERATION_TYPE,
                    "Patty Former handler supports only the selected operation type"
            );
        }
        if (!PattyFormerExecutionConstants.HANDLER_ID.equals(evidence.handlerId())) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Patty Former authorization targets a different handler"
            );
        }
        if (!PattyFormerExecutionConstants.OWNER_SUBSYSTEM_ID.equals(evidence.authorizationSourceOwner())) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Patty Former authorization source owner is not the workstation owner"
            );
        }
        if (!PattyFormerExecutionConstants.EXECUTABLE_REFERENCE_TYPE.equals(evidence.executableWorkReferenceType())) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Patty Former authorization reference type is unsupported"
            );
        }
        if (PattyFormerWorkstationReference.parse(evidence.executableWorkReferenceId()).isEmpty()) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Patty Former authorization does not identify a valid workstation instance"
            );
        }
        if (!evidence.explicitInputIdentities().contains(evidence.frozenInputIdentity())) {
            return ExecutionHandlerValidation.rejected(
                    ExecutionFailureCode.INVALID_FROZEN_INPUT,
                    "Patty Former authorization did not explicitly bind its frozen input identity"
            );
        }
        return ExecutionHandlerValidation.acceptedResult();
    }

    @Override
    public ExecutionHandlerResult execute(ExecutionHandlerContext context) {
        Objects.requireNonNull(context, "context");
        ExecutionAuthorizationEvidence evidence = context.operation().authorizationEvidence();
        Optional<PattyFormerWorkstationReference> parsed =
                PattyFormerWorkstationReference.parse(evidence.executableWorkReferenceId());
        if (parsed.isEmpty()) {
            return failed(
                    context,
                    ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                    "Patty Former Execution work reference is invalid",
                    1
            );
        }

        PattyFormerWorkstationReference reference = parsed.orElseThrow();
        ServerLevel level = serverSupplier.get().getLevel(reference.dimensionKey());
        if (level == null) {
            return failed(
                    context,
                    ExecutionFailureCode.HANDLER_REJECTED_INPUT,
                    "Patty Former Execution target dimension is not loaded",
                    1
            );
        }
        if (!(level.getBlockEntity(reference.blockPos()) instanceof PattyFormerBlockEntity pattyFormer)) {
            return failed(
                    context,
                    ExecutionFailureCode.HANDLER_REJECTED_INPUT,
                    "Patty Former Execution target block entity is unavailable",
                    1
            );
        }

        WorkstationExecutionEffectResult effect = pattyFormer.completeScheduledExecution(
                context.operation().operationId(),
                context.operation().domainEffectIdentity(),
                context.authoritativeSimulationTick()
        );
        if (effect.accepted()) {
            return ExecutionHandlerResult.ownerResult(effect.ownerResultEvidence().orElseThrow(), 10);
        }
        WorkstationFailure failure = effect.failure().orElseGet(() -> WorkstationFailure.of(
                WorkstationFailureCode.EXECUTION_RESULT_REJECTED,
                "Patty Former Execution effect failed without a workstation failure"
        ));
        return failed(context, mapFailure(failure.code()), failure.developerExplanation(), 6);
    }

    private static ExecutionHandlerResult failed(
            ExecutionHandlerContext context,
            ExecutionFailureCode code,
            String message,
            int workUnits
    ) {
        return ExecutionHandlerResult.failed(ExecutionFailure.of(
                code,
                message,
                context.operation().operationId().value()
        ), workUnits);
    }

    private static ExecutionFailureCode mapFailure(WorkstationFailureCode code) {
        return switch (code) {
            case NO_INPUT, PRODUCT_DATA_MISMATCH -> ExecutionFailureCode.INVALID_FROZEN_INPUT;
            case OUTPUT_OCCUPIED, OUTPUT_INCOMPATIBLE -> ExecutionFailureCode.HANDLER_REJECTED_INPUT;
            case INVALID_WORKSTATION_STATE -> ExecutionFailureCode.INVALID_STATUS;
            case EXECUTION_OUTCOME_UNKNOWN -> ExecutionFailureCode.HANDLER_EXCEPTION_UNKNOWN_OUTCOME;
            default -> ExecutionFailureCode.HANDLER_FAILED;
        };
    }
}
