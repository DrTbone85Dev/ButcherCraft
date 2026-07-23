package com.butchercraft.architecture.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ArchitectureRules {
    private static final Pattern CANONICAL_ID = Pattern.compile(
            "[a-z][a-z0-9_]*(?::[a-z][a-z0-9_]*(?:/[a-z][a-z0-9_]*)*)?"
    );

    private ArchitectureRules() {
    }

    public static ValidationRuleRegistry standardRegistry() {
        return ValidationRuleRegistry.builder()
                .register(componentIntegrity())
                .register(singularOwnership())
                .register(ownershipContracts(ValidationCategory.OWNERSHIP))
                .register(ownershipContracts(ValidationCategory.TRANSACTIONS))
                .register(ownershipContracts(ValidationCategory.PLANNING))
                .register(ownershipContracts(ValidationCategory.PRODUCTION))
                .register(ownershipContracts(ValidationCategory.ALLOCATION))
                .register(ownershipContracts(ValidationCategory.EXECUTION))
                .register(dependencyIntegrity())
                .register(forbiddenDependencies())
                .register(dependencyCycles())
                .register(registryIdentity())
                .register(registryOrdering())
                .register(registryReferences())
                .register(persistenceIdentity())
                .register(persistenceSeparation())
                .register(persistenceReferences())
                .register(schedulerIdentity())
                .register(schedulerOrdering())
                .register(schedulerDependencies())
                .register(simulationInvariants())
                .build();
    }

    public static ValidationRule componentIntegrity() {
        return rule(
                "butchercraft:architecture/component_integrity",
                "Architecture components use unique stable ids and package roots",
                ValidationCategory.GENERAL,
                context -> {
                    List<String> details = new ArrayList<>();
                    if (context.components().isEmpty()) {
                        details.add("No architecture components were declared");
                    }
                    duplicateValues(context.components(), component -> component.id().value())
                            .forEach(value -> details.add("Duplicate component id: " + value));
                    duplicateValues(context.components(), ArchitectureComponent::packageRoot)
                            .forEach(value -> details.add("Duplicate component package root: " + value));
                    return details;
                }
        );
    }

    public static ValidationRule singularOwnership() {
        return rule(
                "butchercraft:architecture/singular_ownership",
                "Every declared responsibility has exactly one known owner",
                ValidationCategory.OWNERSHIP,
                context -> {
                    List<String> details = new ArrayList<>();
                    Set<ArchitectureId> componentIds = componentIds(context);
                    Map<ArchitectureId, Set<ArchitectureId>> owners = new LinkedHashMap<>();
                    for (OwnershipAssignment assignment : context.ownershipAssignments()) {
                        owners.computeIfAbsent(assignment.responsibilityId(), ignored -> new LinkedHashSet<>())
                                .add(assignment.ownerId());
                        if (!componentIds.contains(assignment.ownerId())) {
                            details.add("Unknown owner " + assignment.ownerId().value()
                                    + " for " + assignment.responsibilityId().value());
                        }
                    }
                    duplicateValues(
                            context.ownershipAssignments(),
                            assignment -> assignment.responsibilityId().value() + "->" + assignment.ownerId().value()
                    ).forEach(value -> details.add("Duplicate ownership assignment: " + value));
                    owners.forEach((responsibility, assignedOwners) -> {
                        if (assignedOwners.size() > 1) {
                            details.add("Multiple owners for " + responsibility.value() + ": "
                                    + joinIds(assignedOwners));
                        }
                    });
                    return details;
                }
        );
    }

    public static ValidationRule ownershipContracts(ValidationCategory category) {
        Objects.requireNonNull(category, "category");
        return rule(
                "butchercraft:architecture/ownership_contracts/" + category.name().toLowerCase(java.util.Locale.ROOT),
                "Declared " + category.name().toLowerCase(java.util.Locale.ROOT)
                        + " ownership matches its constitutional contract",
                category,
                context -> {
                    List<String> details = new ArrayList<>();
                    Map<ArchitectureId, Set<ArchitectureId>> owners = context.ownershipAssignments().stream()
                            .collect(Collectors.groupingBy(
                                    OwnershipAssignment::responsibilityId,
                                    LinkedHashMap::new,
                                    Collectors.mapping(
                                            OwnershipAssignment::ownerId,
                                            Collectors.toCollection(LinkedHashSet::new)
                                    )
                            ));
                    List<OwnershipContract> contracts = context.ownershipContracts().stream()
                            .filter(contract -> contract.category() == category)
                            .sorted(Comparator.comparing(OwnershipContract::responsibilityId))
                            .toList();
                    duplicateValues(contracts, contract -> contract.responsibilityId().value())
                            .forEach(value -> details.add("Duplicate ownership contract: " + value));
                    for (OwnershipContract contract : contracts) {
                        Set<ArchitectureId> assigned = owners.getOrDefault(contract.responsibilityId(), Set.of());
                        if (assigned.isEmpty()) {
                            details.add("Missing owner for " + contract.responsibilityId().value()
                                    + "; expected " + contract.expectedOwnerId().value());
                        } else if (assigned.size() != 1 || !assigned.contains(contract.expectedOwnerId())) {
                            details.add("Ownership mismatch for " + contract.responsibilityId().value()
                                    + "; expected " + contract.expectedOwnerId().value()
                                    + " but found " + joinIds(assigned));
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule dependencyIntegrity() {
        return rule(
                "butchercraft:architecture/dependency_integrity",
                "Dependencies reference known components and are declared once",
                ValidationCategory.DEPENDENCIES,
                context -> {
                    List<String> details = new ArrayList<>();
                    Set<ArchitectureId> componentIds = componentIds(context);
                    duplicateValues(
                            context.dependencies(),
                            dependency -> dependency.consumerId().value() + "->" + dependency.providerId().value()
                    ).forEach(value -> details.add("Duplicate dependency: " + value));
                    for (DependencyDescriptor dependency : context.dependencies()) {
                        if (!componentIds.contains(dependency.consumerId())) {
                            details.add("Unknown dependency consumer: " + dependency.consumerId().value());
                        }
                        if (!componentIds.contains(dependency.providerId())) {
                            details.add("Unknown dependency provider: " + dependency.providerId().value());
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule forbiddenDependencies() {
        return rule(
                "butchercraft:architecture/forbidden_dependencies",
                "Observed dependencies do not violate declared direction constraints",
                ValidationCategory.DEPENDENCIES,
                context -> {
                    Set<String> observed = context.dependencies().stream()
                            .map(dependency -> dependency.consumerId().value() + "->"
                                    + dependency.providerId().value())
                            .collect(Collectors.toSet());
                    return context.dependencyConstraints().stream()
                            .filter(constraint -> observed.contains(
                                    constraint.consumerId().value() + "->"
                                            + constraint.forbiddenProviderId().value()
                            ))
                            .map(constraint -> "Forbidden dependency "
                                    + constraint.consumerId().value() + "->"
                                    + constraint.forbiddenProviderId().value()
                                    + ": " + constraint.rationale())
                            .sorted()
                            .toList();
                }
        );
    }

    public static ValidationRule dependencyCycles() {
        return rule(
                "butchercraft:architecture/dependency_cycles",
                "The declared component dependency graph is acyclic",
                ValidationCategory.DEPENDENCIES,
                context -> {
                    Set<ArchitectureId> nodes = componentIds(context);
                    List<ArchitectureId> residual = cyclicResidual(
                            nodes,
                            context.dependencies(),
                            DependencyDescriptor::consumerId,
                            DependencyDescriptor::providerId
                    );
                    return residual.isEmpty()
                            ? List.of()
                            : List.of("Dependency loop includes: " + joinIds(residual));
                }
        );
    }

    public static ValidationRule registryIdentity() {
        return rule(
                "butchercraft:architecture/registry_identity",
                "Registries and entries use canonical unique identifiers",
                ValidationCategory.REGISTRIES,
                context -> {
                    List<String> details = new ArrayList<>();
                    duplicateValues(context.registries(), RegistryDescriptor::id)
                            .forEach(value -> details.add("Duplicate registry id: " + value));
                    for (RegistryDescriptor registry : context.registries()) {
                        if (!canonical(registry.id())) {
                            details.add("Non-canonical registry id: " + registry.id());
                        }
                        duplicateValues(registry.entries(), RegistryEntryDescriptor::id)
                                .forEach(value -> details.add("Duplicate entry in " + registry.id() + ": " + value));
                        for (RegistryEntryDescriptor entry : registry.entries()) {
                            if (!canonical(entry.id())) {
                                details.add("Non-canonical entry id in " + registry.id() + ": " + entry.id());
                            }
                            duplicateValues(
                                    entry.references(),
                                    reference -> reference.registryId() + "->" + reference.entryId()
                            ).forEach(value -> details.add("Duplicate reference on " + entry.id() + ": " + value));
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule registryOrdering() {
        return rule(
                "butchercraft:architecture/registry_ordering",
                "Registry ordering is explicit and deterministic",
                ValidationCategory.REGISTRIES,
                context -> {
                    List<String> details = new ArrayList<>();
                    for (RegistryDescriptor registry : context.registries()) {
                        switch (registry.orderingPolicy()) {
                            case UNSPECIFIED -> details.add("Unspecified ordering for registry: " + registry.id());
                            case INSERTION -> {
                                // List.copyOf in RegistryDescriptor freezes authoritative insertion order.
                            }
                            case CANONICAL_ID -> {
                                List<String> actual = registry.entries().stream()
                                        .map(RegistryEntryDescriptor::id)
                                        .toList();
                                List<String> canonical = actual.stream().sorted().toList();
                                if (!actual.equals(canonical)) {
                                    details.add("Registry is not in canonical id order: " + registry.id());
                                }
                            }
                            case EXPLICIT_ORDER -> {
                                duplicateValues(
                                        registry.entries(),
                                        entry -> Integer.toString(entry.explicitOrder())
                                ).forEach(value -> details.add(
                                        "Duplicate explicit order in " + registry.id() + ": " + value
                                ));
                                if (registry.entries().stream().anyMatch(entry -> entry.explicitOrder() < 0)) {
                                    details.add("Negative explicit order in registry: " + registry.id());
                                }
                                List<RegistryEntryDescriptor> canonical = registry.entries().stream()
                                        .sorted(Comparator.comparingInt(RegistryEntryDescriptor::explicitOrder)
                                                .thenComparing(RegistryEntryDescriptor::id))
                                        .toList();
                                if (!registry.entries().equals(canonical)) {
                                    details.add("Registry is not in explicit order: " + registry.id());
                                }
                            }
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule registryReferences() {
        return rule(
                "butchercraft:architecture/registry_references",
                "Registry references resolve against the candidate registry set",
                ValidationCategory.REGISTRIES,
                context -> {
                    List<String> details = new ArrayList<>();
                    Map<String, Set<String>> entriesByRegistry = new HashMap<>();
                    for (RegistryDescriptor registry : context.registries()) {
                        entriesByRegistry.computeIfAbsent(registry.id(), ignored -> new HashSet<>())
                                .addAll(registry.entries().stream().map(RegistryEntryDescriptor::id).toList());
                    }
                    for (RegistryDescriptor registry : context.registries()) {
                        for (RegistryEntryDescriptor entry : registry.entries()) {
                            for (ArchitectureReference reference : entry.references()) {
                                Set<String> targetEntries = entriesByRegistry.get(reference.registryId());
                                if (targetEntries == null) {
                                    details.add("Unknown target registry " + reference.registryId()
                                            + " referenced by " + entry.id());
                                } else if (!targetEntries.contains(reference.entryId())) {
                                    details.add("Unknown target entry " + reference.registryId() + "/"
                                            + reference.entryId() + " referenced by " + entry.id());
                                }
                            }
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule persistenceIdentity() {
        return rule(
                "butchercraft:architecture/persistence_identity",
                "Persistence surfaces have unique ids, paths, owners, and supported schemas",
                ValidationCategory.PERSISTENCE,
                context -> {
                    List<String> details = new ArrayList<>();
                    Set<ArchitectureId> componentIds = componentIds(context);
                    duplicateValues(context.persistenceDescriptors(), PersistenceDescriptor::id)
                            .forEach(value -> details.add("Duplicate persistence id: " + value));
                    duplicateValues(context.persistenceDescriptors(), PersistenceDescriptor::path)
                            .forEach(value -> details.add("Duplicate persistence path: " + value));
                    for (PersistenceDescriptor descriptor : context.persistenceDescriptors()) {
                        if (!canonical(descriptor.id())) {
                            details.add("Non-canonical persistence id: " + descriptor.id());
                        }
                        if (descriptor.schemaVersion() <= 0) {
                            details.add("Unsupported schema version for " + descriptor.id() + ": "
                                    + descriptor.schemaVersion());
                        }
                        if (!componentIds.contains(descriptor.ownerId())) {
                            details.add("Unknown persistence owner for " + descriptor.id() + ": "
                                    + descriptor.ownerId().value());
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule persistenceSeparation() {
        return rule(
                "butchercraft:architecture/persistence_separation",
                "Persistence keeps immutable definitions and mutable runtime under explicit ownership",
                ValidationCategory.PERSISTENCE,
                context -> {
                    List<String> details = new ArrayList<>();
                    for (PersistenceDescriptor descriptor : context.persistenceDescriptors()) {
                        if (descriptor.dataKind() == PersistenceDataKind.MIXED_AUTHORITY) {
                            details.add("Mixed immutable and mutable authority in: " + descriptor.id());
                        }
                        if (descriptor.orderingPolicy() == OrderingPolicy.UNSPECIFIED) {
                            details.add("Unspecified persistence ordering in: " + descriptor.id());
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule persistenceReferences() {
        return rule(
                "butchercraft:architecture/persistence_references",
                "Persistence references resolve against known registry entries",
                ValidationCategory.PERSISTENCE,
                context -> {
                    List<String> details = new ArrayList<>();
                    Map<String, Set<String>> entriesByRegistry = context.registries().stream()
                            .collect(Collectors.toMap(
                                    RegistryDescriptor::id,
                                    registry -> registry.entries().stream()
                                            .map(RegistryEntryDescriptor::id)
                                            .collect(Collectors.toSet()),
                                    (left, right) -> {
                                        Set<String> merged = new HashSet<>(left);
                                        merged.addAll(right);
                                        return merged;
                                    }
                            ));
                    for (PersistenceDescriptor descriptor : context.persistenceDescriptors()) {
                        for (ArchitectureReference reference : descriptor.references()) {
                            Set<String> entries = entriesByRegistry.get(reference.registryId());
                            if (entries == null || !entries.contains(reference.entryId())) {
                                details.add("Unknown persisted reference in " + descriptor.id() + ": "
                                        + reference.registryId() + "/" + reference.entryId());
                            }
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule schedulerIdentity() {
        return rule(
                "butchercraft:architecture/scheduler_identity",
                "Schedulers and stages use canonical unique ids and execution orders",
                ValidationCategory.SCHEDULER,
                context -> {
                    List<String> details = new ArrayList<>();
                    if (context.schedulers().isEmpty()) {
                        details.add("No scheduler was declared");
                    }
                    duplicateValues(context.schedulers(), SchedulerDescriptor::id)
                            .forEach(value -> details.add("Duplicate scheduler id: " + value));
                    for (SchedulerDescriptor scheduler : context.schedulers()) {
                        if (!canonical(scheduler.id())) {
                            details.add("Non-canonical scheduler id: " + scheduler.id());
                        }
                        duplicateValues(scheduler.stages(), SchedulerStageDescriptor::id)
                                .forEach(value -> details.add("Duplicate stage id in " + scheduler.id() + ": " + value));
                        duplicateValues(
                                scheduler.stages(),
                                stage -> Integer.toString(stage.executionOrder())
                        ).forEach(value -> details.add(
                                "Duplicate stage order in " + scheduler.id() + ": " + value
                        ));
                        for (SchedulerStageDescriptor stage : scheduler.stages()) {
                            if (!canonical(stage.id())) {
                                details.add("Non-canonical stage id in " + scheduler.id() + ": " + stage.id());
                            }
                            if (stage.executionOrder() <= 0) {
                                details.add("Non-positive stage order for " + stage.id());
                            }
                            duplicateValues(stage.dependencyIds(), Function.identity())
                                    .forEach(value -> details.add(
                                            "Duplicate dependency on stage " + stage.id() + ": " + value
                                    ));
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule schedulerOrdering() {
        return rule(
                "butchercraft:architecture/scheduler_ordering",
                "Scheduler stages use stable ascending order without gaps",
                ValidationCategory.SCHEDULER,
                context -> {
                    List<String> details = new ArrayList<>();
                    for (SchedulerDescriptor scheduler : context.schedulers()) {
                        List<SchedulerStageDescriptor> sorted = scheduler.stages().stream()
                                .sorted(Comparator.comparingInt(SchedulerStageDescriptor::executionOrder)
                                        .thenComparing(SchedulerStageDescriptor::id))
                                .toList();
                        if (!scheduler.stages().equals(sorted)) {
                            details.add("Stages are not stored in execution order: " + scheduler.id());
                        }
                        for (int index = 1; index < sorted.size(); index++) {
                            int gap = sorted.get(index).executionOrder() - sorted.get(index - 1).executionOrder();
                            if (gap != scheduler.expectedOrderStep()) {
                                details.add("Ordering gap in " + scheduler.id() + " between "
                                        + sorted.get(index - 1).id() + " and " + sorted.get(index).id()
                                        + ": expected " + scheduler.expectedOrderStep() + " but found " + gap);
                            }
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule schedulerDependencies() {
        return rule(
                "butchercraft:architecture/scheduler_dependencies",
                "Scheduler stage dependencies are known, earlier, and acyclic",
                ValidationCategory.SCHEDULER,
                context -> {
                    List<String> details = new ArrayList<>();
                    for (SchedulerDescriptor scheduler : context.schedulers()) {
                        Map<String, SchedulerStageDescriptor> byId = scheduler.stages().stream()
                                .collect(Collectors.toMap(
                                        SchedulerStageDescriptor::id,
                                        Function.identity(),
                                        (first, ignored) -> first
                                ));
                        List<DependencyDescriptor> edges = new ArrayList<>();
                        Map<String, ArchitectureId> normalizedIds = new HashMap<>();
                        for (SchedulerStageDescriptor stage : scheduler.stages()) {
                            if (canonical(stage.id())) {
                                normalizedIds.put(stage.id(), ArchitectureId.of(stage.id()));
                            }
                        }
                        for (SchedulerStageDescriptor stage : scheduler.stages()) {
                            for (String dependencyId : stage.dependencyIds()) {
                                SchedulerStageDescriptor dependency = byId.get(dependencyId);
                                if (dependency == null) {
                                    details.add("Unknown stage dependency in " + scheduler.id() + ": "
                                            + stage.id() + "->" + dependencyId);
                                } else {
                                    if (dependency.executionOrder() >= stage.executionOrder()) {
                                        details.add("Stage dependency does not precede consumer in "
                                                + scheduler.id() + ": " + stage.id() + "->" + dependencyId);
                                    }
                                    ArchitectureId stageId = normalizedIds.get(stage.id());
                                    ArchitectureId dependencyArchitectureId = normalizedIds.get(dependencyId);
                                    if (stageId != null && dependencyArchitectureId != null
                                            && !stageId.equals(dependencyArchitectureId)) {
                                        edges.add(new DependencyDescriptor(stageId, dependencyArchitectureId));
                                    }
                                }
                            }
                        }
                        List<ArchitectureId> residual = cyclicResidual(
                                normalizedIds.values(),
                                edges,
                                DependencyDescriptor::consumerId,
                                DependencyDescriptor::providerId
                        );
                        if (!residual.isEmpty()) {
                            details.add("Scheduler dependency loop in " + scheduler.id() + ": "
                                    + joinIds(residual));
                        }
                    }
                    return details;
                }
        );
    }

    public static ValidationRule simulationInvariants() {
        return rule(
                "butchercraft:architecture/simulation_invariants",
                "Simulation declarations preserve replay, deterministic ordering, and stable ids",
                ValidationCategory.SIMULATION,
                context -> {
                    List<String> details = new ArrayList<>();
                    duplicateValues(
                            context.simulationInvariants(),
                            invariant -> invariant.id().value()
                    ).forEach(value -> details.add("Duplicate simulation invariant: " + value));
                    Set<SimulationInvariantType> present = context.simulationInvariants().stream()
                            .map(SimulationInvariantDescriptor::type)
                            .collect(Collectors.toSet());
                    for (SimulationInvariantType required : List.of(
                            SimulationInvariantType.REPLAY_COMPATIBILITY,
                            SimulationInvariantType.DETERMINISTIC_ORDERING,
                            SimulationInvariantType.STABLE_IDENTIFIERS
                    )) {
                        if (!present.contains(required)) {
                            details.add("Missing required simulation declaration: " + required);
                        }
                    }
                    context.simulationInvariants().stream()
                            .filter(invariant -> !invariant.satisfied())
                            .map(invariant -> "Unsatisfied simulation invariant "
                                    + invariant.id().value() + ": " + invariant.description())
                            .sorted()
                            .forEach(details::add);
                    return details;
                }
        );
    }

    private static ValidationRule rule(
            String id,
            String description,
            ValidationCategory category,
            Function<ValidationContext, List<String>> evaluator
    ) {
        return new DeclarativeValidationRule(
                id,
                description,
                category,
                ValidationSeverity.ERROR,
                evaluator
        );
    }

    private static Set<ArchitectureId> componentIds(ValidationContext context) {
        return context.components().stream()
                .map(ArchitectureComponent::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean canonical(String id) {
        return CANONICAL_ID.matcher(id).matches();
    }

    private static <T> List<String> duplicateValues(
            Collection<T> values,
            Function<T, String> keyExtractor
    ) {
        Map<String, Integer> counts = new HashMap<>();
        for (T value : values) {
            counts.merge(keyExtractor.apply(value), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static <T> List<ArchitectureId> cyclicResidual(
            Collection<ArchitectureId> nodes,
            Collection<T> edges,
            Function<T, ArchitectureId> source,
            Function<T, ArchitectureId> target
    ) {
        Map<ArchitectureId, Integer> incoming = new HashMap<>();
        Map<ArchitectureId, Set<ArchitectureId>> outgoing = new HashMap<>();
        for (ArchitectureId node : nodes) {
            incoming.put(node, 0);
            outgoing.put(node, new LinkedHashSet<>());
        }
        for (T edge : edges) {
            ArchitectureId from = source.apply(edge);
            ArchitectureId to = target.apply(edge);
            if (!incoming.containsKey(from) || !incoming.containsKey(to)) {
                continue;
            }
            if (outgoing.get(from).add(to)) {
                incoming.compute(to, (ignored, count) -> count + 1);
            }
        }
        ArrayDeque<ArchitectureId> ready = incoming.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .collect(Collectors.toCollection(ArrayDeque::new));
        int visited = 0;
        while (!ready.isEmpty()) {
            ArchitectureId node = ready.removeFirst();
            visited++;
            for (ArchitectureId dependent : outgoing.get(node).stream().sorted().toList()) {
                int remaining = incoming.compute(dependent, (ignored, count) -> count - 1);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }
        if (visited == incoming.size()) {
            return List.of();
        }
        return incoming.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    private static String joinIds(Collection<ArchitectureId> ids) {
        return ids.stream().sorted().map(ArchitectureId::value).collect(Collectors.joining(", "));
    }
}
