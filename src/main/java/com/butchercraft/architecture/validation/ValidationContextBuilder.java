package com.butchercraft.architecture.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ValidationContextBuilder {
    private final ArchitectureId id;
    private final List<ArchitectureComponent> components = new ArrayList<>();
    private final List<OwnershipAssignment> ownershipAssignments = new ArrayList<>();
    private final List<OwnershipContract> ownershipContracts = new ArrayList<>();
    private final List<DependencyDescriptor> dependencies = new ArrayList<>();
    private final List<DependencyConstraint> dependencyConstraints = new ArrayList<>();
    private final List<RegistryDescriptor> registries = new ArrayList<>();
    private final List<PersistenceDescriptor> persistenceDescriptors = new ArrayList<>();
    private final List<SchedulerDescriptor> schedulers = new ArrayList<>();
    private final List<SimulationInvariantDescriptor> simulationInvariants = new ArrayList<>();

    ValidationContextBuilder(ArchitectureId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public ValidationContextBuilder component(ArchitectureComponent component) {
        components.add(Objects.requireNonNull(component, "component"));
        return this;
    }

    public ValidationContextBuilder ownership(OwnershipAssignment assignment) {
        ownershipAssignments.add(Objects.requireNonNull(assignment, "assignment"));
        return this;
    }

    public ValidationContextBuilder ownershipContract(OwnershipContract contract) {
        ownershipContracts.add(Objects.requireNonNull(contract, "contract"));
        return this;
    }

    public ValidationContextBuilder dependency(DependencyDescriptor dependency) {
        dependencies.add(Objects.requireNonNull(dependency, "dependency"));
        return this;
    }

    public ValidationContextBuilder dependencyConstraint(DependencyConstraint constraint) {
        dependencyConstraints.add(Objects.requireNonNull(constraint, "constraint"));
        return this;
    }

    public ValidationContextBuilder registry(RegistryDescriptor registry) {
        registries.add(Objects.requireNonNull(registry, "registry"));
        return this;
    }

    public ValidationContextBuilder persistence(PersistenceDescriptor descriptor) {
        persistenceDescriptors.add(Objects.requireNonNull(descriptor, "descriptor"));
        return this;
    }

    public ValidationContextBuilder scheduler(SchedulerDescriptor scheduler) {
        schedulers.add(Objects.requireNonNull(scheduler, "scheduler"));
        return this;
    }

    public ValidationContextBuilder simulationInvariant(SimulationInvariantDescriptor invariant) {
        simulationInvariants.add(Objects.requireNonNull(invariant, "invariant"));
        return this;
    }

    public ValidationContext build() {
        return new ValidationContext(
                id,
                components,
                ownershipAssignments,
                ownershipContracts,
                dependencies,
                dependencyConstraints,
                registries,
                persistenceDescriptors,
                schedulers,
                simulationInvariants
        );
    }
}
