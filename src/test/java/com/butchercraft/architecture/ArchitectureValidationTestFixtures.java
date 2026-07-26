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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withArchitectureDocuments(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.ArchitectureDocumentDescriptor> documents
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                documents,
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withPlatformIdentities(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.PlatformIdentityDescriptor> identities
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.architectureDocuments(),
                identities,
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withPlatformContracts(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.PlatformContractDescriptor> contracts
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.architectureDocuments(),
                source.platformIdentities(),
                contracts,
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withRuntimeAuthorities(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.RuntimeAuthorityDescriptor> authorities
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                authorities,
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                assignments,
                contracts,
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                dependencies,
                constraints,
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                registries,
                source.persistenceDescriptors(),
                source.schedulerEffects(),
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                persistence,
                source.schedulerEffects(),
                source.schedulers(),
                source.simulationInvariants()
        );
    }

    static ValidationContext withSchedulerEffects(
            ValidationContext source,
            java.util.List<com.butchercraft.architecture.validation.SchedulerEffectDeclaration> effects
    ) {
        return new ValidationContext(
                source.id(),
                source.components(),
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                effects,
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
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
                source.architectureDocuments(),
                source.platformIdentities(),
                source.platformContracts(),
                source.runtimeAuthorities(),
                source.ownershipAssignments(),
                source.ownershipContracts(),
                source.dependencies(),
                source.dependencyConstraints(),
                source.registries(),
                source.persistenceDescriptors(),
                source.schedulerEffects(),
                source.schedulers(),
                invariants
        );
    }
}
