package com.butchercraft.world.execution;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ExecutionLegacyRegistryCompatibilityProfile(
        int profileSchemaVersion,
        String profileIdentity,
        int executionSchemaVersion,
        String persistedRegistryIdentity,
        String runtimeConfigurationIdentity,
        List<ExecutionHandlerContractDescriptor> handlers
) {
    public static final int CURRENT_PROFILE_SCHEMA_VERSION = 1;

    public ExecutionLegacyRegistryCompatibilityProfile {
        if (profileSchemaVersion != CURRENT_PROFILE_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Execution legacy profile schema: " + profileSchemaVersion
            );
        }
        executionSchemaVersion = ExecutionValidation.requireSchema(
                executionSchemaVersion,
                "Execution legacy profile"
        );
        persistedRegistryIdentity = ExecutionValidation.requireId(
                persistedRegistryIdentity,
                "Persisted Execution registry identity"
        );
        runtimeConfigurationIdentity = ExecutionValidation.requireId(
                runtimeConfigurationIdentity,
                "Execution runtime configuration identity"
        );
        handlers = Objects.requireNonNull(handlers, "handlers").stream()
                .map(handler -> Objects.requireNonNull(handler, "handler"))
                .sorted()
                .toList();
        if (handlers.stream().map(ExecutionHandlerContractDescriptor::handlerId).distinct().count()
                != handlers.size()) {
            throw new IllegalArgumentException("Duplicate handler id in Execution legacy profile");
        }
        if (handlers.stream().map(ExecutionHandlerContractDescriptor::operationType).distinct().count()
                != handlers.size()) {
            throw new IllegalArgumentException("Duplicate operation type in Execution legacy profile");
        }
        String calculatedRegistryIdentity = ExecutionHandlerRegistry.registryIdentity(
                executionSchemaVersion,
                handlers
        );
        if (!persistedRegistryIdentity.equals(calculatedRegistryIdentity)) {
            throw new IllegalArgumentException("Execution legacy profile registry digest mismatch");
        }
        String calculatedProfileIdentity = calculateProfileIdentity(
                profileSchemaVersion,
                executionSchemaVersion,
                persistedRegistryIdentity,
                runtimeConfigurationIdentity,
                handlers
        );
        profileIdentity = ExecutionValidation.requireId(profileIdentity, "Execution legacy profile identity");
        if (!profileIdentity.equals(calculatedProfileIdentity)) {
            throw new IllegalArgumentException("Execution legacy profile identity mismatch");
        }
    }

    public static ExecutionLegacyRegistryCompatibilityProfile create(
            int executionSchemaVersion,
            String persistedRegistryIdentity,
            String runtimeConfigurationIdentity,
            List<ExecutionHandlerContractDescriptor> handlers
    ) {
        List<ExecutionHandlerContractDescriptor> immutableHandlers = List.copyOf(handlers);
        return new ExecutionLegacyRegistryCompatibilityProfile(
                CURRENT_PROFILE_SCHEMA_VERSION,
                calculateProfileIdentity(
                        CURRENT_PROFILE_SCHEMA_VERSION,
                        executionSchemaVersion,
                        persistedRegistryIdentity,
                        runtimeConfigurationIdentity,
                        immutableHandlers
                ),
                executionSchemaVersion,
                persistedRegistryIdentity,
                runtimeConfigurationIdentity,
                immutableHandlers
        );
    }

    private static String calculateProfileIdentity(
            int profileSchemaVersion,
            int executionSchemaVersion,
            String persistedRegistryIdentity,
            String runtimeConfigurationIdentity,
            List<ExecutionHandlerContractDescriptor> handlers
    ) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create(
                "butchercraft:execution_legacy_registry_compatibility_profile"
        );
        digest.add(profileSchemaVersion)
                .add(executionSchemaVersion)
                .add(persistedRegistryIdentity)
                .add(runtimeConfigurationIdentity);
        List<ExecutionHandlerContractDescriptor> ordered = Objects.requireNonNull(handlers, "handlers").stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        digest.add(ordered.size());
        ordered.forEach(handler -> digest.add(handler.schemaVersion())
                .add(handler.handlerId())
                .add(handler.operationType())
                .add(handler.contractIdentity())
                .add(handler.configurationIdentity()));
        return "butchercraft:execution_legacy_registry_profile/v" + profileSchemaVersion + "/"
                + ExecutionValidation.digestIdSuffix(digest.finish());
    }
}
