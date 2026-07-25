package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Function;

final class ExecutionTestFixtures {
    static final String AUTHORITY_OWNER = "test:execution_authority";
    static final String EXECUTABLE_REFERENCE_TYPE = "test:executable_work";
    static final String OPERATION_TYPE = "test:proof_operation";
    static final String HANDLER_ID = "test:proof_handler";
    static final String WORLD_IDENTITY = "test:world/identity";
    static final ExecutionRuntimeConfiguration CONFIGURATION =
            new ExecutionRuntimeConfiguration(16, 64, 3, 16,
                    "test:execution_runtime_configuration/standard");

    private ExecutionTestFixtures() {
    }

    static ExecutionHandlerRegistry registry(Function<ExecutionHandlerContext, ExecutionHandlerResult> execution) {
        return new ExecutionHandlerRegistry(List.of(handler(execution)));
    }

    static ExecutionOperationHandler handler(Function<ExecutionHandlerContext, ExecutionHandlerResult> execution) {
        return new ExecutionOperationHandler() {
            private final ExecutionHandlerContract contract = ExecutionHandlerContract.idempotent(
                    HANDLER_ID,
                    OPERATION_TYPE,
                    25,
                    CONFIGURATION.configurationIdentity()
            );

            @Override
            public ExecutionHandlerContract contract() {
                return contract;
            }

            @Override
            public ExecutionHandlerValidation validateAuthorization(ExecutionAuthorizationEvidence evidence) {
                if (!evidence.operationType().equals(OPERATION_TYPE)) {
                    return ExecutionHandlerValidation.rejected(
                            ExecutionFailureCode.UNSUPPORTED_OPERATION_TYPE,
                            "unsupported operation type"
                    );
                }
                if (!evidence.handlerId().equals(HANDLER_ID)) {
                    return ExecutionHandlerValidation.rejected(
                            ExecutionFailureCode.HANDLER_REJECTED_AUTHORIZATION,
                            "handler mismatch"
                    );
                }
                return ExecutionHandlerValidation.acceptedResult();
            }

            @Override
            public ExecutionHandlerResult execute(ExecutionHandlerContext context) {
                return execution.apply(context);
            }
        };
    }

    static ExecutionAuthorization authorization(String executableReferenceId, String frozenInputId, long tick) {
        return new ExecutionAuthorization(evidence(executableReferenceId, frozenInputId, tick));
    }

    static ExecutionAuthorizationEvidence evidence(
            String executableReferenceId,
            String frozenInputId,
            long tick
    ) {
        return ExecutionAuthorizationEvidence.issued(
                AUTHORITY_OWNER,
                EXECUTABLE_REFERENCE_TYPE,
                executableReferenceId,
                OPERATION_TYPE,
                HANDLER_ID,
                frozenInputId,
                "test:freshness/" + executableReferenceId.substring(executableReferenceId.lastIndexOf('/') + 1),
                CONFIGURATION.configurationIdentity(),
                WORLD_IDENTITY,
                tick,
                OptionalLong.empty(),
                List.of(frozenInputId)
        );
    }

    static ExecutionHandlerResult ownerResult(ExecutionHandlerContext context) {
        return ExecutionHandlerResult.ownerResult(ownerResultEvidence(context, "test:owner_result/proof"), 3);
    }

    static ExecutionOwnerResultEvidence ownerResultEvidence(ExecutionHandlerContext context, String ownerResultId) {
        return ExecutionOwnerResultEvidence.of(
                "test:owner",
                ownerResultId,
                context.operation().domainEffectIdentity(),
                digest(ownerResultId + "/" + context.operation().operationId().value())
        );
    }

    static String digest(String value) {
        return ExecutionCanonicalDigest.create("test:digest").add(value).finish();
    }
}
