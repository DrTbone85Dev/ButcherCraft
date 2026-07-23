package com.butchercraft.world.allocation;

import java.util.Map;
import java.util.TreeMap;

public final class AllocationRegistryBuilder {
    private final Map<RequirementId, RequirementDefinition> requirements = new TreeMap<>();
    private final Map<AllocationRequestId, AllocationRequestDefinition> requests = new TreeMap<>();
    private final Map<AllocationSetId, AllocationSetDefinition> sets = new TreeMap<>();
    private final Map<AllocationCommitmentId, AllocationCommitmentDefinition> commitments =
            new TreeMap<>();

    public AllocationRegistryBuilder registerRequirement(RequirementDefinition definition) {
        RequirementDefinition value = AllocationValidation.required(definition, "definition");
        register(requirements, value.id(), value, "Requirement");
        return this;
    }

    public AllocationRegistryBuilder registerRequest(AllocationRequestDefinition definition) {
        AllocationRequestDefinition value = AllocationValidation.required(definition, "definition");
        register(requests, value.id(), value, "AllocationRequest");
        return this;
    }

    public AllocationRegistryBuilder registerSet(AllocationSetDefinition definition) {
        AllocationSetDefinition value = AllocationValidation.required(definition, "definition");
        register(sets, value.id(), value, "AllocationSet");
        return this;
    }

    public AllocationRegistryBuilder registerCommitment(
            AllocationCommitmentDefinition definition
    ) {
        AllocationCommitmentDefinition value = AllocationValidation.required(
                definition,
                "definition"
        );
        register(commitments, value.id(), value, "AllocationCommitment");
        return this;
    }

    public AllocationRegistryBuilder registerAll(AllocationRegistry registry) {
        AllocationRegistry source = AllocationValidation.required(registry, "registry");
        source.requirements().forEach(this::registerRequirement);
        source.requests().forEach(this::registerRequest);
        source.sets().forEach(this::registerSet);
        source.commitments().forEach(this::registerCommitment);
        return this;
    }

    public AllocationRegistry build() {
        return new AllocationRegistry(
                requirements.values(),
                requests.values(),
                sets.values(),
                commitments.values()
        );
    }

    private static <I, T> void register(
            Map<I, T> values,
            I id,
            T value,
            String label
    ) {
        if (values.putIfAbsent(id, value) != null) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                    id.toString(),
                    "Duplicate " + label + " identity"
            );
        }
    }
}
