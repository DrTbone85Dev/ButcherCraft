package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ValidationContext;

final class ArchitectureValidationTestFixtures {
    private ArchitectureValidationTestFixtures() {
    }

    static ValidationContext validContext() {
        return ButcherCraftArchitectureManifest.current();
    }

    static ValidationContext withComponents(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.ArchitectureComponent> components
    ) {
        return new ValidationContext(
                source.id(),
                components,
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withOwnership(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.OwnershipAssignment> assignments,
            java.util.List<com.butchercraft.architecture.validation.OwnershipContract> contracts
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                assignments,
                contracts,
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withDependencies(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.DependencyDescriptor> dependencies,
            java.util.List<com.butchercraft.architecture.validation.DependencyConstraint> constraints
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                dependencies,
                constraints,
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withRegistries(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.RegistryDescriptor> registries
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                registries,
                source.persistenceDescriptors(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withPersistence(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.PersistenceDescriptor> persistence
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                persistence,
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withSchedulers(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.SchedulerDescriptor> schedulers
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                schedulers,
                source.simulationInvariants()
        );
    }

    static ValidationContext withSimulation(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.SimulationInvariantDescriptor> invariants
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulers(),
                invariants
        );
    }
}
