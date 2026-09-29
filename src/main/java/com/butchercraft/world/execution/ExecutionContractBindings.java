package com.butchercraft.world.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** DG-003 schema-2 persistence metadata. Historical operation/authorization schemas remain unchanged. */
public record ExecutionContractBindings(
        List<ExecutionHandlerContractDescriptor> handlers,
        List<OperationBinding> operations,
        String compatibilityPolicyIdentity,
        List<Evolution> evolutions,
        Optional<String> determinismManifestReference,
        String digest
) {
    public record OperationBinding(String operationIdentity, String handlerIdentity, String contractIdentity) {
        public OperationBinding {
            ExecutionValidation.requireId(operationIdentity, "bound operation identity");
            ExecutionValidation.requireId(handlerIdentity, "bound handler identity");
            ExecutionValidation.requireId(contractIdentity, "bound handler contract identity");
        }
    }

    public record Evolution(String priorRegistry, String currentRegistry,
            ExecutionRegistryCompatibilityClassification classification, Optional<String> legacyProfile,
            String identity) {
        public Evolution {
            ExecutionValidation.requireId(priorRegistry, "prior registry");
            ExecutionValidation.requireId(currentRegistry, "current registry");
            Objects.requireNonNull(legacyProfile, "legacyProfile");
            if (!classification.permitsExecutionAuthority()) throw new IllegalArgumentException("Unapproved registry evolution");
            if (!calculate(priorRegistry, currentRegistry, classification, legacyProfile).equals(identity)) {
                throw new IllegalArgumentException("Registry evolution identity mismatch");
            }
        }

        public static Evolution from(ExecutionRegistryCompatibilityObservation observation) {
            return new Evolution(observation.persistedRegistryIdentity(), observation.currentRegistryIdentity(),
                    observation.classification(), observation.historicalProfileIdentity(),
                    calculate(observation.persistedRegistryIdentity(), observation.currentRegistryIdentity(),
                            observation.classification(), observation.historicalProfileIdentity()));
        }

        private static String calculate(String prior, String current,
                ExecutionRegistryCompatibilityClassification classification, Optional<String> profile) {
            String hash = ExecutionCanonicalDigest.create("butchercraft:execution_registry_evolution")
                    .add(1).add(prior).add(current).add(classification.name())
                    .add(profile.orElse("none")).add(ExecutionRegistryCompatibilityClassifier.POLICY_IDENTITY).finish();
            return "butchercraft:execution_registry_evolution/v1/" + ExecutionValidation.digestIdSuffix(hash);
        }
    }

    public ExecutionContractBindings {
        handlers = Objects.requireNonNull(handlers, "handlers").stream().sorted().toList();
        operations = Objects.requireNonNull(operations, "operations").stream()
                .sorted(Comparator.comparing(OperationBinding::operationIdentity)).toList();
        evolutions = List.copyOf(Objects.requireNonNull(evolutions, "evolutions"));
        determinismManifestReference = Objects.requireNonNull(determinismManifestReference, "determinismManifestReference");
        if (!ExecutionRegistryCompatibilityClassifier.POLICY_IDENTITY.equals(compatibilityPolicyIdentity)
                || operations.stream().map(OperationBinding::operationIdentity).distinct().count() != operations.size()
                || evolutions.stream().map(Evolution::identity).distinct().count() != evolutions.size()) {
            throw new IllegalArgumentException("Invalid Execution contract binding metadata");
        }
        if (!calculate(handlers, operations, compatibilityPolicyIdentity, evolutions, determinismManifestReference).equals(digest)) {
            throw new IllegalArgumentException("Execution contract binding digest mismatch");
        }
    }

    public static ExecutionContractBindings create(ExecutionHandlerRegistry registry,
            Collection<ExecutionOperationSnapshot> operations, List<Evolution> priorEvolutions,
            Optional<ExecutionRegistryCompatibilityObservation> observation, Optional<String> manifest) {
        List<OperationBinding> bindings = operations.stream().map(operation -> new OperationBinding(
                operation.operationId().value(), operation.authorizationEvidence().handlerId(),
                registry.findByHandlerId(operation.authorizationEvidence().handlerId()).orElseThrow()
                        .contract().contractIdentity())).sorted(Comparator.comparing(OperationBinding::operationIdentity)).toList();
        List<Evolution> evolutions = new ArrayList<>(priorEvolutions);
        observation.filter(value -> value.classification() == ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE)
                .map(Evolution::from).filter(value -> !evolutions.contains(value)).ifPresent(evolutions::add);
        List<ExecutionHandlerContractDescriptor> handlers = registry.contractDescriptors();
        return new ExecutionContractBindings(handlers, bindings, ExecutionRegistryCompatibilityClassifier.POLICY_IDENTITY,
                evolutions, manifest, calculate(handlers, bindings, ExecutionRegistryCompatibilityClassifier.POLICY_IDENTITY,
                        evolutions, manifest));
    }

    public ExecutionRegistryCompatibilityObservation validate(String registryIdentity, String configurationIdentity,
            ExecutionHandlerRegistry current, ExecutionRuntimeConfiguration configuration,
            List<ExecutionOperationSnapshot> snapshots) {
        // The persisted descriptor digest proves its own registry; it is never inferred from current handlers.
        var nativeMetadata = ExecutionLegacyRegistryCompatibilityProfile.create(1, registryIdentity, configurationIdentity, handlers);
        if (operations.size() != snapshots.size()) throw new IllegalArgumentException("Incomplete per-operation contract bindings");
        for (ExecutionOperationSnapshot snapshot : snapshots) {
            OperationBinding binding = operations.stream()
                    .filter(value -> value.operationIdentity().equals(snapshot.operationId().value())).findFirst().orElseThrow();
            ExecutionHandlerContractDescriptor descriptor = handlers.stream()
                    .filter(value -> value.handlerId().equals(binding.handlerIdentity())).findFirst().orElseThrow();
            if (!binding.handlerIdentity().equals(snapshot.authorizationEvidence().handlerId())
                    || !binding.contractIdentity().equals(descriptor.contractIdentity())
                    || !descriptor.operationType().equals(snapshot.authorizationEvidence().operationType())
                    || !descriptor.configurationIdentity().equals(snapshot.authorizationEvidence().configurationIdentity())) {
                throw new IllegalArgumentException("Operation lost its exact historical handler contract binding");
            }
        }
        return new ExecutionRegistryCompatibilityClassifier(List.of(nativeMetadata)).classify(1, registryIdentity,
                configurationIdentity, current, configuration, snapshots);
    }

    private static String calculate(List<ExecutionHandlerContractDescriptor> handlers, List<OperationBinding> operations,
            String policy, List<Evolution> evolutions, Optional<String> manifest) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_contract_bindings")
                .add(1).add(policy).add(handlers.size());
        handlers.forEach(handler -> digest.add(handler.handlerId()).add(handler.operationType())
                .add(handler.contractIdentity()).add(handler.configurationIdentity()));
        digest.add(operations.size());
        operations.forEach(operation -> digest.add(operation.operationIdentity()).add(operation.handlerIdentity())
                .add(operation.contractIdentity()));
        digest.add(evolutions.size());
        evolutions.forEach(evolution -> digest.add(evolution.identity()));
        digest.add(manifest.orElse("none"));
        return digest.finish();
    }
}
