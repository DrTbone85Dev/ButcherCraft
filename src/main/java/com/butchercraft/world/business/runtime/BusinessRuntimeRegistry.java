package com.butchercraft.world.business.runtime;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.simulation.SimulationConfiguration;

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

public final class BusinessRuntimeRegistry {
    private static final BusinessRuntimeRegistry EMPTY = new BusinessRuntimeRegistry(List.of(), Map.of());

    private final List<BusinessRuntimeState> states;
    private final Map<BusinessId, BusinessRuntimeState> statesByBusinessId;

    private BusinessRuntimeRegistry(
            List<BusinessRuntimeState> states,
            Map<BusinessId, BusinessRuntimeState> statesByBusinessId
    ) {
        this.states = states;
        this.statesByBusinessId = statesByBusinessId;
    }

    public static BusinessRuntimeRegistry empty() {
        return EMPTY;
    }

    public static BusinessRuntimeRegistry of(Collection<BusinessRuntimeState> states) {
        Objects.requireNonNull(states, "states");
        if (states.isEmpty()) {
            return EMPTY;
        }
        List<BusinessRuntimeState> deterministicStates = states.stream()
                .map(state -> Objects.requireNonNull(state, "state"))
                .sorted(Comparator.comparing(state -> state.businessId().value()))
                .toList();
        rejectDuplicateBusinessIds(deterministicStates);
        Map<BusinessId, BusinessRuntimeState> byId = deterministicStates.stream()
                .collect(Collectors.toUnmodifiableMap(BusinessRuntimeState::businessId, Function.identity()));
        return new BusinessRuntimeRegistry(List.copyOf(deterministicStates), byId);
    }

    public static BusinessRuntimeRegistry fromBusinesses(
            Collection<Business> businesses,
            SimulationConfiguration configuration
    ) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(configuration, "configuration");
        BusinessRuntimeRegistry registry = of(businesses.stream()
                .map(business -> BusinessRuntimeState.defaultFor(business, configuration))
                .toList());
        registry.validate(configuration);
        registry.validateReferences(businesses);
        return registry;
    }

    public BusinessRuntimeRegistry withMissingDefaults(
            Collection<Business> businesses,
            SimulationConfiguration configuration
    ) {
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(configuration, "configuration");
        List<BusinessRuntimeState> updated = new ArrayList<>(states);
        for (Business business : businesses.stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList()) {
            if (!contains(business.id())) {
                updated.add(BusinessRuntimeState.defaultFor(business, configuration));
            }
        }
        BusinessRuntimeRegistry registry = of(updated);
        registry.validate(configuration);
        registry.validateReferences(businesses);
        return registry;
    }

    public BusinessRuntimeRegistry with(BusinessRuntimeState state) {
        Objects.requireNonNull(state, "state");
        List<BusinessRuntimeState> updated = states.stream()
                .filter(existing -> !existing.businessId().equals(state.businessId()))
                .collect(Collectors.toCollection(ArrayList::new));
        updated.add(state);
        return of(updated);
    }

    public boolean contains(BusinessId businessId) {
        return statesByBusinessId.containsKey(Objects.requireNonNull(businessId, "businessId"));
    }

    public boolean contains(String businessId) {
        return contains(new BusinessId(businessId));
    }

    public Optional<BusinessRuntimeState> find(BusinessId businessId) {
        return Optional.ofNullable(statesByBusinessId.get(Objects.requireNonNull(businessId, "businessId")));
    }

    public Optional<BusinessRuntimeState> find(String businessId) {
        return find(new BusinessId(businessId));
    }

    public int size() {
        return states.size();
    }

    public List<BusinessRuntimeState> states() {
        return states;
    }

    public Stream<BusinessRuntimeState> stream() {
        return states.stream();
    }

    public List<BusinessRuntimeState> findByStatus(BusinessOperationalStatus status) {
        Objects.requireNonNull(status, "status");
        return states.stream()
                .filter(state -> state.operationalStatus() == status)
                .toList();
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        for (BusinessRuntimeState state : states) {
            state.validate(configuration);
        }
    }

    public void validateReferences(Collection<Business> businesses) {
        Objects.requireNonNull(businesses, "businesses");
        Set<BusinessId> knownIds = businesses.stream()
                .map(business -> Objects.requireNonNull(business, "business").id())
                .collect(Collectors.toUnmodifiableSet());
        for (BusinessRuntimeState state : states) {
            if (!knownIds.contains(state.businessId())) {
                throw new IllegalArgumentException("Business runtime references unknown business: "
                        + state.businessId().value());
            }
        }
    }

    private static void rejectDuplicateBusinessIds(List<BusinessRuntimeState> states) {
        Set<BusinessId> seen = new HashSet<>();
        Set<BusinessId> duplicates = new HashSet<>();
        for (BusinessRuntimeState state : states) {
            if (!seen.add(state.businessId())) {
                duplicates.add(state.businessId());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate business runtime ids: " + duplicates);
        }
    }
}
