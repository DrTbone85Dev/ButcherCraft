package com.butchercraft.architecture.validation;

import java.util.List;
import java.util.Objects;

public record ValidationContext(
        ArchitectureId id,
        List<ArchitectureComponent> components,
        List<ArchitectureDocumentDescriptor> architectureDocuments,
        List<PlatformIdentityDescriptor> platformIdentities,
        List<PlatformContractDescriptor> platformContracts,
        List<RuntimeAuthorityDescriptor> runtimeAuthorities,
        List<OwnershipAssignment> ownershipAssignments,
        List<OwnershipContract> ownershipContracts,
        List<DependencyDescriptor> dependencies,
        List<DependencyConstraint> dependencyConstraints,
        List<RegistryDescriptor> registries,
        List<PersistenceDescriptor> persistenceDescriptors,
        List<SchedulerEffectDeclaration> schedulerEffects,
        List<SchedulerDescriptor> schedulers,
        List<SimulationInvariantDescriptor> simulationInvariants
) {
    public ValidationContext {
        Objects.requireNonNull(id, "id");
        components = immutable(components, "components");
        architectureDocuments = immutable(architectureDocuments, "architectureDocuments");
        platformIdentities = immutable(platformIdentities, "platformIdentities");
        platformContracts = immutable(platformContracts, "platformContracts");
        runtimeAuthorities = immutable(runtimeAuthorities, "runtimeAuthorities");
        ownershipAssignments = immutable(ownershipAssignments, "ownershipAssignments");
        ownershipContracts = immutable(ownershipContracts, "ownershipContracts");
        dependencies = immutable(dependencies, "dependencies");
        dependencyConstraints = immutable(dependencyConstraints, "dependencyConstraints");
        registries = immutable(registries, "registries");
        persistenceDescriptors = immutable(persistenceDescriptors, "persistenceDescriptors");
        schedulerEffects = immutable(schedulerEffects, "schedulerEffects");
        schedulers = immutable(schedulers, "schedulers");
        simulationInvariants = immutable(simulationInvariants, "simulationInvariants");
    }

    public static ValidationContextBuilder builder(ArchitectureId id) {
        return new ValidationContextBuilder(id);
    }

    private static <T> List<T> immutable(List<T> source, String label) {
        return Objects.requireNonNull(source, label).stream()
                .map(value -> Objects.requireNonNull(value, label + " entry"))
                .toList();
    }
}
