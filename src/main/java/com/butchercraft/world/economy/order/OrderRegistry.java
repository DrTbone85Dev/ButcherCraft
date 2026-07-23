package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;

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

public final class OrderRegistry {
    private final Map<OrderId, EconomicOrderDefinition> definitions;
    private final List<EconomicOrderDefinition> orderedDefinitions;
    private final Map<ActorId, List<EconomicOrderDefinition>> byRequester;
    private final Map<ActorId, List<EconomicOrderDefinition>> byCounterparty;
    private final Map<ActorId, List<EconomicOrderDefinition>> byParty;
    private final Map<GoodId, List<EconomicOrderDefinition>> byGood;
    private final Map<OrderType, List<EconomicOrderDefinition>> byType;
    private final Map<ContractId, List<EconomicOrderDefinition>> byContract;

    private OrderRegistry(List<EconomicOrderDefinition> definitions) {
        LinkedHashMap<OrderId, EconomicOrderDefinition> indexed = new LinkedHashMap<>();
        for (EconomicOrderDefinition definition : definitions) {
            EconomicOrderDefinition previous = indexed.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate order id: " + definition.id().value());
            }
        }
        this.definitions = Collections.unmodifiableMap(indexed);
        this.orderedDefinitions = List.copyOf(indexed.values());
        this.byRequester = groups(orderedDefinitions, EconomicOrderDefinition::requesterActorId);
        this.byCounterparty = optionalGroups(orderedDefinitions, EconomicOrderDefinition::counterpartyActorId);
        this.byParty = partyGroups(orderedDefinitions);
        this.byGood = goodGroups(orderedDefinitions);
        this.byType = groups(orderedDefinitions, EconomicOrderDefinition::type);
        this.byContract = optionalGroups(orderedDefinitions, EconomicOrderDefinition::governingContractId);
    }

    public static OrderRegistry empty() { return of(List.of()); }
    public static OrderRegistry of(Collection<EconomicOrderDefinition> definitions) {
        return new OrderRegistry(List.copyOf(Objects.requireNonNull(definitions, "definitions")));
    }
    public static OrderRegistryBuilder builder() { return new OrderRegistryBuilder(); }

    public OrderRegistry withDefinition(EconomicOrderDefinition definition) {
        List<EconomicOrderDefinition> updated = new ArrayList<>(orderedDefinitions);
        updated.add(Objects.requireNonNull(definition, "definition"));
        return of(updated);
    }

    public boolean contains(OrderId id) { return definitions.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<EconomicOrderDefinition> find(OrderId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }
    public int size() { return definitions.size(); }
    public List<EconomicOrderDefinition> definitions() { return orderedDefinitions; }
    public Stream<EconomicOrderDefinition> stream() { return orderedDefinitions.stream(); }
    public List<EconomicOrderDefinition> findByRequester(ActorId id) { return get(byRequester, id); }
    public List<EconomicOrderDefinition> findByCounterparty(ActorId id) { return get(byCounterparty, id); }
    public List<EconomicOrderDefinition> findByParty(ActorId id) { return get(byParty, id); }
    public List<EconomicOrderDefinition> findByGood(GoodId id) { return get(byGood, id); }
    public List<EconomicOrderDefinition> findByType(OrderType type) { return get(byType, type); }
    public List<EconomicOrderDefinition> findByContract(ContractId id) { return get(byContract, id); }

    public List<EconomicOrderDefinition> findCreatedBetween(long firstInclusive, long lastInclusive) {
        requireRange(firstInclusive, lastInclusive);
        return orderedDefinitions.stream().filter(order -> order.createdSimulationTick() >= firstInclusive
                && order.createdSimulationTick() <= lastInclusive).toList();
    }

    public List<EconomicOrderDefinition> findRequestedBetween(long firstInclusive, long lastInclusive) {
        requireRange(firstInclusive, lastInclusive);
        return orderedDefinitions.stream().filter(order -> order.requestedFulfillmentTick().isPresent()
                && order.requestedFulfillmentTick().orElseThrow() >= firstInclusive
                && order.requestedFulfillmentTick().orElseThrow() <= lastInclusive).toList();
    }

    private static void requireRange(long first, long last) {
        DomainValidation.requireTick(first, "Query first tick");
        DomainValidation.requireTick(last, "Query last tick");
        if (last < first) throw new IllegalArgumentException("Query tick range is reversed");
    }

    private static Map<ActorId, List<EconomicOrderDefinition>> partyGroups(List<EconomicOrderDefinition> source) {
        Map<ActorId, List<EconomicOrderDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicOrderDefinition value : source) {
            mutable.computeIfAbsent(value.requesterActorId(), ignored -> new ArrayList<>()).add(value);
            value.counterpartyActorId().filter(id -> !id.equals(value.requesterActorId()))
                    .ifPresent(id -> mutable.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value));
        }
        return immutableGroups(mutable);
    }

    private static Map<GoodId, List<EconomicOrderDefinition>> goodGroups(List<EconomicOrderDefinition> source) {
        Map<GoodId, List<EconomicOrderDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicOrderDefinition value : source) {
            value.lines().stream().map(OrderLineDefinition::goodId).distinct()
                    .forEach(id -> mutable.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value));
        }
        return immutableGroups(mutable);
    }

    private static <K> Map<K, List<EconomicOrderDefinition>> groups(
            List<EconomicOrderDefinition> source, Function<EconomicOrderDefinition, K> key
    ) {
        Map<K, List<EconomicOrderDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicOrderDefinition value : source) {
            mutable.computeIfAbsent(key.apply(value), ignored -> new ArrayList<>()).add(value);
        }
        return immutableGroups(mutable);
    }

    private static <K> Map<K, List<EconomicOrderDefinition>> optionalGroups(
            List<EconomicOrderDefinition> source,
            Function<EconomicOrderDefinition, Optional<K>> key
    ) {
        Map<K, List<EconomicOrderDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicOrderDefinition value : source) {
            key.apply(value).ifPresent(id -> mutable.computeIfAbsent(id, ignored -> new ArrayList<>()).add(value));
        }
        return immutableGroups(mutable);
    }

    private static <K> Map<K, List<EconomicOrderDefinition>> immutableGroups(
            Map<K, List<EconomicOrderDefinition>> source
    ) {
        Map<K, List<EconomicOrderDefinition>> immutable = new LinkedHashMap<>();
        source.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private static <K> List<EconomicOrderDefinition> get(
            Map<K, List<EconomicOrderDefinition>> source, K key
    ) {
        return source.getOrDefault(Objects.requireNonNull(key, "key"), List.of());
    }
}
