package com.butchercraft.world.execution;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ExecutionHandlerRegistry {
    private final Map<String, ExecutionOperationHandler> handlersByOperationType;
    private final Map<String, ExecutionOperationHandler> handlersByHandlerId;

    public ExecutionHandlerRegistry(Collection<? extends ExecutionOperationHandler> handlers) {
        List<? extends ExecutionOperationHandler> ordered = Objects.requireNonNull(handlers, "handlers").stream()
                .map(handler -> Objects.requireNonNull(handler, "handler"))
                .sorted(Comparator.comparing(handler -> handler.contract().operationType()))
                .toList();
        Map<String, ExecutionOperationHandler> byType = new LinkedHashMap<>();
        Map<String, ExecutionOperationHandler> byId = new LinkedHashMap<>();
        for (ExecutionOperationHandler handler : ordered) {
            ExecutionHandlerContract contract = Objects.requireNonNull(handler.contract(), "handler contract");
            if (byType.putIfAbsent(contract.operationType(), handler) != null) {
                throw new IllegalArgumentException("Duplicate Execution operation type: " + contract.operationType());
            }
            if (byId.putIfAbsent(contract.handlerId(), handler) != null) {
                throw new IllegalArgumentException("Duplicate Execution handler id: " + contract.handlerId());
            }
        }
        handlersByOperationType = java.util.Collections.unmodifiableMap(byType);
        handlersByHandlerId = java.util.Collections.unmodifiableMap(byId);
    }

    public static ExecutionHandlerRegistry empty() {
        return new ExecutionHandlerRegistry(List.of());
    }

    public Optional<ExecutionOperationHandler> findByOperationType(String operationType) {
        return Optional.ofNullable(handlersByOperationType.get(
                ExecutionValidation.requireId(operationType, "Execution operation type")
        ));
    }

    public Optional<ExecutionOperationHandler> findByHandlerId(String handlerId) {
        return Optional.ofNullable(handlersByHandlerId.get(
                ExecutionValidation.requireId(handlerId, "Execution handler id")
        ));
    }

    public List<ExecutionOperationHandler> handlers() {
        return List.copyOf(handlersByOperationType.values());
    }

    public int size() {
        return handlersByOperationType.size();
    }

    public String registryIdentity() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_handler_registry");
        digest.add(ExecutionSchema.CURRENT_VERSION).add(handlersByOperationType.size());
        handlersByOperationType.values().forEach(handler -> digest.add(handler.contract().contractIdentity()));
        return "butchercraft:execution_handler_registry/v" + ExecutionSchema.CURRENT_VERSION + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish());
    }
}
