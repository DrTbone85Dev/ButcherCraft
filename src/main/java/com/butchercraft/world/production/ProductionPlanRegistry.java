package com.butchercraft.world.production;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.inventory.InventoryId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

public final class ProductionPlanRegistry {
    private final List<ProductionPlanDefinition> definitions;
    private final Map<ProductionPlanId, ProductionPlanDefinition> byId;
    private final Map<ProductionProcessId, List<ProductionPlanDefinition>> byProcess;
    private final Map<ActorId, List<ProductionPlanDefinition>> byActor;
    private final Map<BusinessId, List<ProductionPlanDefinition>> byBusiness;
    private final Map<OrderId, List<ProductionPlanDefinition>> byOrder;
    private final Map<ContractId, List<ProductionPlanDefinition>> byContract;
    private final Map<ProductionPriority, List<ProductionPlanDefinition>> byPriority;
    private final Map<InventoryId, List<ProductionPlanDefinition>> byInputInventory;
    private final Map<InventoryId, List<ProductionPlanDefinition>> byOutputInventory;
    private final NavigableMap<Long, List<ProductionPlanDefinition>> byCreatedTick;

    private ProductionPlanRegistry(List<ProductionPlanDefinition> source) {
        definitions = List.copyOf(source);
        Map<ProductionPlanId, ProductionPlanDefinition> ids = new LinkedHashMap<>();
        for (ProductionPlanDefinition definition : definitions) {
            if (ids.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate production plan id: " + definition.id().value());
            }
        }
        byId = Collections.unmodifiableMap(ids);
        byProcess = groups(definitions, definition -> List.of(definition.processId()));
        byActor = groups(definitions, definition -> List.of(definition.producerActorId()));
        byBusiness = groups(definitions, definition -> definition.businessId().stream().toList());
        byOrder = groups(definitions, definition -> definition.requestingOrderId().stream().toList());
        byContract = groups(definitions, definition -> definition.governingContractId().stream().toList());
        byPriority = groups(definitions, definition -> List.of(definition.priority()));
        byInputInventory = groups(definitions, definition -> definition.inputBindings().stream()
                .map(ProductionInventoryBinding::inventoryId).distinct().toList());
        byOutputInventory = groups(definitions, definition -> definition.outputBindings().stream()
                .map(ProductionInventoryBinding::inventoryId).distinct().toList());
        TreeMap<Long, List<ProductionPlanDefinition>> ticks = new TreeMap<>();
        definitions.forEach(definition -> ticks.computeIfAbsent(
                definition.createdSimulationTick(), ignored -> new ArrayList<>()).add(definition));
        ticks.replaceAll((ignored, values) -> List.copyOf(values));
        byCreatedTick = Collections.unmodifiableNavigableMap(ticks);
    }

    public static ProductionPlanRegistry empty() { return of(List.of()); }
    public static ProductionPlanRegistry of(Collection<ProductionPlanDefinition> source) {
        return new ProductionPlanRegistry(Objects.requireNonNull(source, "definitions").stream()
                .map(definition -> Objects.requireNonNull(definition, "definition")).toList());
    }
    public static ProductionPlanRegistryBuilder builder() { return new ProductionPlanRegistryBuilder(); }
    public boolean contains(ProductionPlanId id) { return byId.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<ProductionPlanDefinition> find(ProductionPlanId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }
    public int size() { return definitions.size(); }
    public List<ProductionPlanDefinition> definitions() { return definitions; }
    public Stream<ProductionPlanDefinition> stream() { return definitions.stream(); }
    public List<ProductionPlanDefinition> findByProcess(ProductionProcessId id) { return get(byProcess, id); }
    public List<ProductionPlanDefinition> findByActor(ActorId id) { return get(byActor, id); }
    public List<ProductionPlanDefinition> findByBusiness(BusinessId id) { return get(byBusiness, id); }
    public List<ProductionPlanDefinition> findByOrder(OrderId id) { return get(byOrder, id); }
    public List<ProductionPlanDefinition> findByContract(ContractId id) { return get(byContract, id); }
    public List<ProductionPlanDefinition> findByPriority(ProductionPriority value) { return get(byPriority, value); }
    public List<ProductionPlanDefinition> findByInputInventory(InventoryId id) {
        return get(byInputInventory, id);
    }
    public List<ProductionPlanDefinition> findByOutputInventory(InventoryId id) {
        return get(byOutputInventory, id);
    }
    public List<ProductionPlanDefinition> findCreatedBetween(long firstInclusive, long lastInclusive) {
        if (firstInclusive < 0L || lastInclusive < firstInclusive) {
            throw new IllegalArgumentException("Invalid production plan tick range");
        }
        return byCreatedTick.subMap(firstInclusive, true, lastInclusive, true).values().stream()
                .flatMap(List::stream).toList();
    }

    private static <K> List<ProductionPlanDefinition> get(
            Map<K, List<ProductionPlanDefinition>> groups,
            K key
    ) {
        return groups.getOrDefault(Objects.requireNonNull(key, "key"), List.of());
    }

    private static <K> Map<K, List<ProductionPlanDefinition>> groups(
            List<ProductionPlanDefinition> definitions,
            Function<ProductionPlanDefinition, Collection<K>> keys
    ) {
        Map<K, List<ProductionPlanDefinition>> mutable = new LinkedHashMap<>();
        for (ProductionPlanDefinition definition : definitions) {
            for (K key : keys.apply(definition)) {
                mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(definition);
            }
        }
        Map<K, List<ProductionPlanDefinition>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }
}
