package com.butchercraft.world.workforce;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WorkforceRegistry {
    private static final WorkforceRegistry EMPTY = new WorkforceRegistry(List.of(), Map.of(), Map.of());

    private final List<WorkforceDefinition> definitions;
    private final Map<WorkforceDefinitionId, WorkforceDefinition> definitionsById;
    private final Map<BusinessId, List<WorkforceDefinition>> definitionsByBusinessId;

    private WorkforceRegistry(
            List<WorkforceDefinition> definitions,
            Map<WorkforceDefinitionId, WorkforceDefinition> definitionsById,
            Map<BusinessId, List<WorkforceDefinition>> definitionsByBusinessId
    ) {
        this.definitions = definitions;
        this.definitionsById = definitionsById;
        this.definitionsByBusinessId = definitionsByBusinessId;
    }

    public static WorkforceRegistry empty() {
        return EMPTY;
    }

    public static WorkforceRegistry of(Collection<WorkforceDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        if (definitions.isEmpty()) {
            return EMPTY;
        }
        List<WorkforceDefinition> deterministicDefinitions = definitions.stream()
                .map(definition -> Objects.requireNonNull(definition, "definition"))
                .sorted(Comparator.comparing((WorkforceDefinition definition) -> definition.businessId().value())
                        .thenComparing(definition -> definition.workforceDefinitionId().value()))
                .toList();
        rejectDuplicateDefinitionIds(deterministicDefinitions);
        Map<WorkforceDefinitionId, WorkforceDefinition> byId = deterministicDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(WorkforceDefinition::workforceDefinitionId, Function.identity()));
        Map<BusinessId, List<WorkforceDefinition>> byBusinessId = deterministicDefinitions.stream()
                .collect(Collectors.groupingBy(
                        WorkforceDefinition::businessId,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                ));
        return new WorkforceRegistry(List.copyOf(deterministicDefinitions), byId, Map.copyOf(byBusinessId));
    }

    public WorkforceRegistry withMissingDefaults(
            Collection<Business> businesses,
            BusinessRuntimeRegistry runtimeRegistry
    ) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(runtimeRegistry, "runtimeRegistry");
        List<WorkforceDefinition> updated = new ArrayList<>(definitions);
        Set<BusinessId> businessesWithDefinitions = updated.stream()
                .map(WorkforceDefinition::businessId)
                .collect(Collectors.toSet());
        List<BusinessRuntimeState> runtimeStates = runtimeRegistry.states();
        Map<BusinessId, BusinessRuntimeState> runtimeByBusinessId = runtimeStates.stream()
                .collect(Collectors.toUnmodifiableMap(BusinessRuntimeState::businessId, Function.identity()));
        for (Business business : businesses.stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList()) {
            if (!businessesWithDefinitions.contains(business.id())) {
                BusinessRuntimeState runtimeState = runtimeByBusinessId.get(business.id());
                if (runtimeState == null) {
                    throw new IllegalArgumentException("Cannot create workforce definition without business runtime state: "
                            + business.id().value());
                }
                updated.add(BuiltInWorkforceDefinitions.defaultDefinition(business, runtimeState));
            }
        }
        WorkforceRegistry registry = of(updated);
        registry.validateReferences(businesses, runtimeRegistry);
        return registry;
    }

    public WorkforceRegistry with(WorkforceDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (contains(definition.workforceDefinitionId())) {
            throw new IllegalArgumentException("Duplicate workforce definition id: "
                    + definition.workforceDefinitionId().value());
        }
        List<WorkforceDefinition> updated = new ArrayList<>(definitions);
        updated.add(definition);
        return of(updated);
    }

    public boolean contains(WorkforceDefinitionId definitionId) {
        return definitionsById.containsKey(Objects.requireNonNull(definitionId, "definitionId"));
    }

    public Optional<WorkforceDefinition> find(WorkforceDefinitionId definitionId) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(definitionId, "definitionId")));
    }

    public List<WorkforceDefinition> findByBusinessId(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return definitionsByBusinessId.getOrDefault(businessId, List.of());
    }

    public int size() {
        return definitions.size();
    }

    public List<WorkforceDefinition> definitions() {
        return definitions;
    }

    public Stream<WorkforceDefinition> stream() {
        return definitions.stream();
    }

    public void validateReferences(Collection<Business> businesses, BusinessRuntimeRegistry runtimeRegistry) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(runtimeRegistry, "runtimeRegistry");
        Set<BusinessId> knownBusinessIds = businesses.stream()
                .map(business -> Objects.requireNonNull(business, "business").id())
                .collect(Collectors.toUnmodifiableSet());
        for (WorkforceDefinition definition : definitions) {
            if (!knownBusinessIds.contains(definition.businessId())) {
                throw new IllegalArgumentException("Workforce definition references unknown business: "
                        + definition.businessId().value());
            }
            BusinessRuntimeState runtimeState = runtimeRegistry.find(definition.businessId())
                    .orElseThrow(() -> new IllegalArgumentException("Workforce definition references business without runtime state: "
                            + definition.businessId().value()));
            definition.validateAgainst(runtimeState);
        }
    }

    private static void rejectDuplicateDefinitionIds(List<WorkforceDefinition> definitions) {
        Set<WorkforceDefinitionId> seen = new HashSet<>();
        Set<WorkforceDefinitionId> duplicates = new HashSet<>();
        for (WorkforceDefinition definition : definitions) {
            if (!seen.add(definition.workforceDefinitionId())) {
                duplicates.add(definition.workforceDefinitionId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate workforce definition ids: " + duplicates);
        }
    }
}
