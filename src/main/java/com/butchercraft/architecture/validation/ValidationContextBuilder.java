package com.butchercraft.architecture.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ValidationContextBuilder {
    private final ArchitectureId id;
    private final List<ArchitectureComponent> components = new ArrayList<>();
    private final List<ArchitectureDocumentDescriptor> architectureDocuments = new ArrayList<>();
    private final List<PlatformIdentityDescriptor> platformIdentities = new ArrayList<>();
    private final List<PlatformContractDescriptor> platformContracts = new ArrayList<>();
    private final List<RuntimeAuthorityDescriptor> runtimeAuthorities = new ArrayList<>();
    private final List<OwnershipAssignment> ownershipAssignments = new ArrayList<>();
    private final List<OwnershipContract> ownershipContracts = new ArrayList<>();
    private final List<DependencyDescriptor> dependencies = new ArrayList<>();
    private final List<DependencyConstraint> dependencyConstraints = new ArrayList<>();
    private final List<RegistryDescriptor> registries = new ArrayList<>();
    private final List<PersistenceDescriptor> persistenceDescriptors = new ArrayList<>();
    private final List<SchedulerEffectDeclaration> schedulerEffects = new ArrayList<>();
    private final List<SchedulerDescriptor> schedulers = new ArrayList<>();
    private final List<SimulationInvariantDescriptor> simulationInvariants = new ArrayList<>();

    ValidationContextBuilder(ArchitectureId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public ValidationContextBuilder component(ArchitectureComponent component) {
        components.add(Objects.requireNonNull(component, "component"));
        return this;
    }

    public ValidationContextBuilder architectureDocument(ArchitectureDocumentDescriptor document) {
        architectureDocuments.add(Objects.requireNonNull(document, "document"));
        return this;
    }

    public ValidationContextBuilder platformIdentity(PlatformIdentityDescriptor identity) {
        platformIdentities.add(Objects.requireNonNull(identity, "identity"));
        return this;
    }

    public ValidationContextBuilder platformContract(PlatformContractDescriptor contract) {
        platformContracts.add(Objects.requireNonNull(contract, "contract"));
        return this;
    }

    public ValidationContextBuilder runtimeAuthority(RuntimeAuthorityDescriptor authority) {
        runtimeAuthorities.add(Objects.requireNonNull(authority, "authority"));
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

    public ValidationContextBuilder schedulerEffect(SchedulerEffectDeclaration effect) {
        schedulerEffects.add(Objects.requireNonNull(effect, "effect"));
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
                architectureDocuments,
                platformIdentities,
                platformContracts,
                runtimeAuthorities,
                ownershipAssignments,
                ownershipContracts,
                dependencies,
                dependencyConstraints,
                registries,
                persistenceDescriptors,
                schedulerEffects,
                schedulers,
                simulationInvariants
        );
    }
}
