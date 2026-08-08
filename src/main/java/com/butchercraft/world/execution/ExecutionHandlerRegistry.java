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

    public List<ExecutionHandlerContractDescriptor> contractDescriptors() {
        return handlersByHandlerId.values().stream()
                .map(handler -> ExecutionHandlerContractDescriptor.from(handler.contract()))
                .sorted()
                .toList();
    }

    public int size() {
        return handlersByOperationType.size();
    }

    public String registryIdentity() {
        return registryIdentity(ExecutionSchema.CURRENT_VERSION, contractDescriptors());
    }

    static String registryIdentity(
            int schemaVersion,
            Collection<ExecutionHandlerContractDescriptor> descriptors
    ) {
        ExecutionValidation.requireSchema(schemaVersion, "Execution handler registry identity");
        List<ExecutionHandlerContractDescriptor> ordered = Objects.requireNonNull(descriptors, "descriptors").stream()
                .map(descriptor -> Objects.requireNonNull(descriptor, "descriptor"))
                .sorted(Comparator.comparing(ExecutionHandlerContractDescriptor::operationType))
                .toList();
        if (ordered.stream().anyMatch(descriptor -> descriptor.schemaVersion() != schemaVersion)) {
            throw new IllegalArgumentException("Execution handler descriptor schema does not match registry schema");
        }
        if (ordered.stream().map(ExecutionHandlerContractDescriptor::handlerId).distinct().count() != ordered.size()) {
            throw new IllegalArgumentException("Duplicate Execution handler id in registry identity input");
        }
        if (ordered.stream().map(ExecutionHandlerContractDescriptor::operationType).distinct().count()
                != ordered.size()) {
            throw new IllegalArgumentException("Duplicate Execution operation type in registry identity input");
        }
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_handler_registry");
        digest.add(schemaVersion).add(ordered.size());
        ordered.forEach(descriptor -> digest.add(descriptor.contractIdentity()));
        return "butchercraft:execution_handler_registry/v" + schemaVersion + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish());
    }
}
