package com.butchercraft.world.execution;

public interface ExecutionOperationHandler {
    ExecutionHandlerContract contract();

    ExecutionHandlerValidation validateAuthorization(ExecutionAuthorizationEvidence evidence);

    ExecutionHandlerResult execute(ExecutionHandlerContext context);
}
