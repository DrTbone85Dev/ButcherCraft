package com.butchercraft.world.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExecutionRegistryCompatibilityClassifier {
    public static final String POLICY_IDENTITY =
            "butchercraft:execution_registry_compatibility_policy/v1/additive_exact_contract";

    private final Map<String, ExecutionLegacyRegistryCompatibilityProfile> profilesByRegistryIdentity;

    public ExecutionRegistryCompatibilityClassifier(
            Collection<ExecutionLegacyRegistryCompatibilityProfile> profiles
    ) {
        profilesByRegistryIdentity = Objects.requireNonNull(profiles, "profiles").stream()
                .map(profile -> Objects.requireNonNull(profile, "profile"))
                .collect(Collectors.toUnmodifiableMap(
                        ExecutionLegacyRegistryCompatibilityProfile::persistedRegistryIdentity,
                        Function.identity()
                ));
    }

    public static ExecutionRegistryCompatibilityClassifier standard() {
        return new ExecutionRegistryCompatibilityClassifier(ExecutionLegacyRegistryProfiles.all());
    }

    public ExecutionRegistryCompatibilityObservation classify(
            int documentSchemaVersion,
            String persistedRegistryIdentity,
            String persistedRuntimeConfigurationIdentity,
            ExecutionHandlerRegistry currentRegistry,
            ExecutionRuntimeConfiguration currentRuntimeConfiguration,
            Collection<ExecutionOperationSnapshot> retainedOperations
    ) {
        Objects.requireNonNull(currentRegistry, "currentRegistry");
        Objects.requireNonNull(currentRuntimeConfiguration, "currentRuntimeConfiguration");
        List<ExecutionOperationSnapshot> operations = Objects.requireNonNull(retainedOperations, "retainedOperations")
                .stream()
                .map(operation -> Objects.requireNonNull(operation, "operation"))
                .sorted(java.util.Comparator.comparing(ExecutionOperationSnapshot::operationId))
                .toList();
        String currentRegistryIdentity = currentRegistry.registryIdentity();
        List<ExecutionHandlerContractDescriptor> currentDescriptors = currentRegistry.contractDescriptors();
        Map<String, ExecutionHandlerContractDescriptor> currentByHandler = byHandlerId(currentDescriptors);

        if (documentSchemaVersion != ExecutionSchema.CURRENT_VERSION) {
            return blocked(
                    ExecutionRegistryCompatibilityClassification.INCOMPATIBLE,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    Optional.empty(),
                    0,
                    currentDescriptors.size(),
                    List.of(),
                    List.of(),
                    List.of(),
                    operations.size(),
                    ExecutionRetainedOperationValidation.NOT_EVALUATED,
                    List.of(ExecutionRegistryCompatibilityFailure.registry(
                            ExecutionRegistryCompatibilityFailureCode.UNSUPPORTED_EXECUTION_SCHEMA,
                            "Unsupported Execution persistence schema: " + documentSchemaVersion
                    ))
            );
        }

        if (!currentRuntimeConfiguration.configurationIdentity().equals(persistedRuntimeConfigurationIdentity)) {
            return blocked(
                    ExecutionRegistryCompatibilityClassification.INCOMPATIBLE,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    Optional.empty(),
                    0,
                    currentDescriptors.size(),
                    List.of(),
                    List.of(),
                    List.of(),
                    operations.size(),
                    ExecutionRetainedOperationValidation.NOT_EVALUATED,
                    List.of(ExecutionRegistryCompatibilityFailure.registry(
                            ExecutionRegistryCompatibilityFailureCode.RUNTIME_CONFIGURATION_MISMATCH,
                            "Persisted Execution runtime configuration does not match the current configuration"
                    ))
            );
        }

        boolean exactRegistryIdentity = currentRegistryIdentity.equals(persistedRegistryIdentity);
        Optional<ExecutionLegacyRegistryCompatibilityProfile> profile = exactRegistryIdentity
                ? Optional.empty()
                : Optional.ofNullable(profilesByRegistryIdentity.get(persistedRegistryIdentity));
        if (!exactRegistryIdentity && profile.isEmpty()) {
            return blocked(
                    ExecutionRegistryCompatibilityClassification.INDETERMINATE_RECOVERY_REQUIRED,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    Optional.empty(),
                    0,
                    currentDescriptors.size(),
                    List.of(),
                    List.of(),
                    List.of(),
                    operations.size(),
                    ExecutionRetainedOperationValidation.NOT_EVALUATED,
                    List.of(ExecutionRegistryCompatibilityFailure.registry(
                            ExecutionRegistryCompatibilityFailureCode.UNKNOWN_REGISTRY_OBSERVATION,
                            "Persisted Execution registry identity has no exact historical compatibility profile"
                    ))
            );
        }

        List<ExecutionHandlerContractDescriptor> historicalDescriptors = profile
                .map(ExecutionLegacyRegistryCompatibilityProfile::handlers)
                .orElse(currentDescriptors);
        Map<String, ExecutionHandlerContractDescriptor> historicalByHandler = byHandlerId(historicalDescriptors);
        List<String> addedHandlers = currentByHandler.keySet().stream()
                .filter(handlerId -> !historicalByHandler.containsKey(handlerId))
                .sorted()
                .toList();
        List<String> missingHandlers = historicalByHandler.keySet().stream()
                .filter(handlerId -> !currentByHandler.containsKey(handlerId))
                .sorted()
                .toList();
        List<String> incompatibleContracts = historicalByHandler.entrySet().stream()
                .filter(entry -> {
                    ExecutionHandlerContractDescriptor current = currentByHandler.get(entry.getKey());
                    return current != null && !current.equals(entry.getValue());
                })
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        List<ExecutionRegistryCompatibilityFailure> failures = new ArrayList<>();
        if (profile.isPresent()
                && !profile.orElseThrow().runtimeConfigurationIdentity()
                .equals(persistedRuntimeConfigurationIdentity)) {
            failures.add(ExecutionRegistryCompatibilityFailure.registry(
                    ExecutionRegistryCompatibilityFailureCode.RUNTIME_CONFIGURATION_MISMATCH,
                    "Historical Execution profile runtime configuration does not match persisted state"
            ));
        }
        missingHandlers.forEach(handlerId -> failures.add(ExecutionRegistryCompatibilityFailure.handler(
                ExecutionRegistryCompatibilityFailureCode.REFERENCED_HANDLER_MISSING,
                "Historical Execution handler is not registered in the current runtime",
                handlerId
        )));
        incompatibleContracts.forEach(handlerId -> {
            ExecutionHandlerContractDescriptor historical = historicalByHandler.get(handlerId);
            ExecutionHandlerContractDescriptor current = currentByHandler.get(handlerId);
            ExecutionRegistryCompatibilityFailureCode code = historical.operationType().equals(current.operationType())
                    ? ExecutionRegistryCompatibilityFailureCode.HANDLER_CONTRACT_MISMATCH
                    : ExecutionRegistryCompatibilityFailureCode.HANDLER_OPERATION_TYPE_MISMATCH;
            failures.add(ExecutionRegistryCompatibilityFailure.handler(
                    code,
                    "Historical Execution handler contract does not exactly match the current contract",
                    handlerId
            ));
        });

        int operationFailureStart = failures.size();
        operations.forEach(operation -> validateOperation(
                operation,
                historicalByHandler,
                currentByHandler,
                failures
        ));
        ExecutionRetainedOperationValidation operationValidation = failures.size() == operationFailureStart
                ? ExecutionRetainedOperationValidation.VALID
                : ExecutionRetainedOperationValidation.INVALID;

        if (!failures.isEmpty()) {
            return blocked(
                    ExecutionRegistryCompatibilityClassification.INCOMPATIBLE,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    profile.map(ExecutionLegacyRegistryCompatibilityProfile::profileIdentity),
                    historicalDescriptors.size(),
                    currentDescriptors.size(),
                    addedHandlers,
                    missingHandlers,
                    incompatibleContracts,
                    operations.size(),
                    operationValidation,
                    failures
            );
        }

        if (exactRegistryIdentity) {
            return compatible(
                    ExecutionRegistryCompatibilityClassification.IDENTICAL,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    Optional.empty(),
                    historicalDescriptors.size(),
                    currentDescriptors.size(),
                    List.of(),
                    operations.size()
            );
        }
        if (addedHandlers.isEmpty()) {
            return blocked(
                    ExecutionRegistryCompatibilityClassification.INCOMPATIBLE,
                    persistedRegistryIdentity,
                    currentRegistryIdentity,
                    profile.map(ExecutionLegacyRegistryCompatibilityProfile::profileIdentity),
                    historicalDescriptors.size(),
                    currentDescriptors.size(),
                    List.of(),
                    List.of(),
                    List.of(),
                    operations.size(),
                    ExecutionRetainedOperationValidation.VALID,
                    List.of(ExecutionRegistryCompatibilityFailure.registry(
                            ExecutionRegistryCompatibilityFailureCode.REGISTRY_METADATA_CORRUPT,
                            "Historical and current handler maps are equal but aggregate identities differ"
                    ))
            );
        }
        return compatible(
                ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE,
                persistedRegistryIdentity,
                currentRegistryIdentity,
                profile.map(ExecutionLegacyRegistryCompatibilityProfile::profileIdentity),
                historicalDescriptors.size(),
                currentDescriptors.size(),
                addedHandlers,
                operations.size()
        );
    }

    private static void validateOperation(
            ExecutionOperationSnapshot operation,
            Map<String, ExecutionHandlerContractDescriptor> historicalByHandler,
            Map<String, ExecutionHandlerContractDescriptor> currentByHandler,
            List<ExecutionRegistryCompatibilityFailure> failures
    ) {
        ExecutionAuthorizationEvidence evidence = operation.authorizationEvidence();
        String handlerId = evidence.handlerId();
        String operationId = operation.operationId().value();
        ExecutionHandlerContractDescriptor historical = historicalByHandler.get(handlerId);
        if (historical == null) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID,
                    "Retained operation references a handler absent from its persisted registry profile",
                    operationId,
                    handlerId
            ));
            return;
        }
        ExecutionHandlerContractDescriptor current = currentByHandler.get(handlerId);
        if (current == null) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.REFERENCED_HANDLER_MISSING,
                    "Retained operation references a historical handler missing from the current registry",
                    operationId,
                    handlerId
            ));
            return;
        }
        if (!historical.operationType().equals(evidence.operationType())) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.HANDLER_OPERATION_TYPE_MISMATCH,
                    "Retained operation type does not match its historical handler contract",
                    operationId,
                    handlerId
            ));
        }
        if (!historical.equals(current)) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.HANDLER_CONTRACT_MISMATCH,
                    "Retained operation handler contract changed after persistence",
                    operationId,
                    handlerId
            ));
        }
        if (!historical.configurationIdentity().equals(evidence.configurationIdentity())) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID,
                    "Retained operation configuration does not match its historical handler contract",
                    operationId,
                    handlerId
            ));
        }
        if (operation.schemaVersion() != historical.schemaVersion()
                || evidence.schemaVersion() != historical.schemaVersion()) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID,
                    "Retained operation schema does not match its historical handler contract",
                    operationId,
                    handlerId
            ));
        }
        if (!ExecutionOperationId.derive(evidence).equals(operation.operationId())) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID,
                    "Retained operation identity does not match its authorization evidence",
                    operationId,
                    handlerId
            ));
        }
        if (operation.attempts().stream().anyMatch(attempt -> !handlerId.equals(attempt.handlerId()))) {
            failures.add(ExecutionRegistryCompatibilityFailure.operation(
                    ExecutionRegistryCompatibilityFailureCode.OPERATION_CONTRACT_BINDING_INVALID,
                    "Retained operation attempt references a different handler",
                    operationId,
                    handlerId
            ));
        }
    }

    private static Map<String, ExecutionHandlerContractDescriptor> byHandlerId(
            Collection<ExecutionHandlerContractDescriptor> descriptors
    ) {
        Map<String, ExecutionHandlerContractDescriptor> result = new LinkedHashMap<>();
        descriptors.stream().sorted().forEach(descriptor -> {
            if (result.putIfAbsent(descriptor.handlerId(), descriptor) != null) {
                throw new IllegalArgumentException("Duplicate Execution handler descriptor: " + descriptor.handlerId());
            }
        });
        return Map.copyOf(result);
    }

    private static ExecutionRegistryCompatibilityObservation compatible(
            ExecutionRegistryCompatibilityClassification classification,
            String persistedRegistryIdentity,
            String currentRegistryIdentity,
            Optional<String> profileIdentity,
            int historicalHandlerCount,
            int currentHandlerCount,
            List<String> addedHandlers,
            int retainedOperationCount
    ) {
        return new ExecutionRegistryCompatibilityObservation(
                ExecutionSchema.CURRENT_VERSION,
                POLICY_IDENTITY,
                persistedRegistryIdentity,
                currentRegistryIdentity,
                classification,
                profileIdentity,
                historicalHandlerCount,
                currentHandlerCount,
                addedHandlers,
                List.of(),
                List.of(),
                retainedOperationCount,
                ExecutionRetainedOperationValidation.VALID,
                List.of()
        );
    }

    private static ExecutionRegistryCompatibilityObservation blocked(
            ExecutionRegistryCompatibilityClassification classification,
            String persistedRegistryIdentity,
            String currentRegistryIdentity,
            Optional<String> profileIdentity,
            int historicalHandlerCount,
            int currentHandlerCount,
            List<String> addedHandlers,
            List<String> missingHandlers,
            List<String> incompatibleContracts,
            int retainedOperationCount,
            ExecutionRetainedOperationValidation operationValidation,
            List<ExecutionRegistryCompatibilityFailure> failures
    ) {
        return new ExecutionRegistryCompatibilityObservation(
                ExecutionSchema.CURRENT_VERSION,
                POLICY_IDENTITY,
                persistedRegistryIdentity,
                currentRegistryIdentity,
                classification,
                profileIdentity,
                historicalHandlerCount,
                currentHandlerCount,
                addedHandlers,
                missingHandlers,
                incompatibleContracts,
                retainedOperationCount,
                operationValidation,
                failures
        );
    }
}
