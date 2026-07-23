package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ContractRegistry {
    private final Map<ContractId, EconomicContractDefinition> definitions;
    private final List<EconomicContractDefinition> orderedDefinitions;
    private final Map<ActorId, List<EconomicContractDefinition>> byPrincipal;
    private final Map<ActorId, List<EconomicContractDefinition>> byCounterparty;
    private final Map<ActorId, List<EconomicContractDefinition>> byParty;
    private final Map<GoodId, List<EconomicContractDefinition>> byGood;
    private final Map<ContractType, List<EconomicContractDefinition>> byType;
    private final Map<ContractScheduleType, List<EconomicContractDefinition>> bySchedule;
    private final Map<IndustryId, List<EconomicContractDefinition>> byIndustry;

    private ContractRegistry(List<EconomicContractDefinition> source) {
        LinkedHashMap<ContractId, EconomicContractDefinition> indexed = new LinkedHashMap<>();
        for (EconomicContractDefinition definition : source) {
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate contract id: " + definition.id().value());
            }
        }
        definitions = Collections.unmodifiableMap(indexed);
        orderedDefinitions = List.copyOf(indexed.values());
        byPrincipal = groups(orderedDefinitions, EconomicContractDefinition::principalActorId);
        byCounterparty = groups(orderedDefinitions, EconomicContractDefinition::counterpartyActorId);
        byParty = partyGroups(orderedDefinitions);
        byGood = goodGroups(orderedDefinitions);
        byType = groups(orderedDefinitions, EconomicContractDefinition::type);
        bySchedule = groups(orderedDefinitions, definition -> definition.schedule().type());
        byIndustry = industryGroups(orderedDefinitions);
    }

    public static ContractRegistry empty() { return of(List.of()); }
    public static ContractRegistry of(Collection<EconomicContractDefinition> source) {
        return new ContractRegistry(List.copyOf(Objects.requireNonNull(source, "definitions")));
    }
    public static ContractRegistryBuilder builder() { return new ContractRegistryBuilder(); }
    public ContractRegistry withDefinition(EconomicContractDefinition definition) {
        List<EconomicContractDefinition> updated = new ArrayList<>(orderedDefinitions);
        updated.add(Objects.requireNonNull(definition, "definition"));
        return of(updated);
    }
    public boolean contains(ContractId id) { return definitions.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<EconomicContractDefinition> find(ContractId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }
    public int size() { return definitions.size(); }
    public List<EconomicContractDefinition> definitions() { return orderedDefinitions; }
    public Stream<EconomicContractDefinition> stream() { return orderedDefinitions.stream(); }
    public List<EconomicContractDefinition> findByPrincipal(ActorId id) { return get(byPrincipal, id); }
    public List<EconomicContractDefinition> findByCounterparty(ActorId id) { return get(byCounterparty, id); }
    public List<EconomicContractDefinition> findByParty(ActorId id) { return get(byParty, id); }
    public List<EconomicContractDefinition> findByGood(GoodId id) { return get(byGood, id); }
    public List<EconomicContractDefinition> findByType(ContractType type) { return get(byType, type); }
    public List<EconomicContractDefinition> findBySchedule(ContractScheduleType type) { return get(bySchedule, type); }
    public List<EconomicContractDefinition> findByIndustry(IndustryId id) { return get(byIndustry, id); }

    public List<EconomicContractDefinition> activeAt(long tick) {
        DomainValidation.requireTick(tick, "Contract active query tick");
        return orderedDefinitions.stream().filter(contract -> contract.effectiveSimulationTick() <= tick
                && (contract.expirationSimulationTick().isEmpty()
                || contract.expirationSimulationTick().orElseThrow() >= tick)).toList();
    }

    public List<EconomicContractDefinition> expiringBetween(long firstInclusive, long lastInclusive) {
        DomainValidation.requireTick(firstInclusive, "Contract expiry query first tick");
        DomainValidation.requireTick(lastInclusive, "Contract expiry query last tick");
        if (lastInclusive < firstInclusive) throw new IllegalArgumentException("Query tick range is reversed");
        return orderedDefinitions.stream().filter(contract -> contract.expirationSimulationTick().isPresent()
                && contract.expirationSimulationTick().orElseThrow() >= firstInclusive
                && contract.expirationSimulationTick().orElseThrow() <= lastInclusive).toList();
    }

    private static Map<ActorId, List<EconomicContractDefinition>> partyGroups(
            List<EconomicContractDefinition> source
    ) {
        Map<ActorId, List<EconomicContractDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicContractDefinition value : source) {
            mutable.computeIfAbsent(value.principalActorId(), ignored -> new ArrayList<>()).add(value);
            if (!value.counterpartyActorId().equals(value.principalActorId())) {
                mutable.computeIfAbsent(value.counterpartyActorId(), ignored -> new ArrayList<>()).add(value);
            }
        }
        return immutableGroups(mutable);
    }

    private static Map<GoodId, List<EconomicContractDefinition>> goodGroups(
            List<EconomicContractDefinition> source
    ) {
        Map<GoodId, List<EconomicContractDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicContractDefinition value : source) {
            value.lines().stream().map(ContractLineDefinition::goodId).distinct()
                    .forEach(id -> mutable.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value));
        }
        return immutableGroups(mutable);
    }

    private static Map<IndustryId, List<EconomicContractDefinition>> industryGroups(
            List<EconomicContractDefinition> source
    ) {
        Map<IndustryId, List<EconomicContractDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicContractDefinition value : source) {
            value.supportedIndustries().forEach(id ->
                    mutable.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value));
        }
        return immutableGroups(mutable);
    }

    private static <K> Map<K, List<EconomicContractDefinition>> groups(
            List<EconomicContractDefinition> source, Function<EconomicContractDefinition, K> key
    ) {
        Map<K, List<EconomicContractDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicContractDefinition value : source) {
            mutable.computeIfAbsent(key.apply(value), ignored -> new ArrayList<>()).add(value);
        }
        return immutableGroups(mutable);
    }

    private static <K> Map<K, List<EconomicContractDefinition>> immutableGroups(
            Map<K, List<EconomicContractDefinition>> source
    ) {
        Map<K, List<EconomicContractDefinition>> immutable = new LinkedHashMap<>();
        source.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private static <K> List<EconomicContractDefinition> get(
            Map<K, List<EconomicContractDefinition>> source, K key
    ) {
        return source.getOrDefault(Objects.requireNonNull(key, "key"), List.of());
    }
}
