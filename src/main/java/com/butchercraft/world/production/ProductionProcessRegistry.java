package com.butchercraft.world.production;

import com.butchercraft.world.economy.actor.ActorCapability;
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

public final class ProductionProcessRegistry {
    private final List<ProductionProcessDefinition> definitions;
    private final Map<ProductionProcessId, ProductionProcessDefinition> byId;
    private final Map<IndustryId, List<ProductionProcessDefinition>> byIndustry;
    private final Map<ActorCapability, List<ProductionProcessDefinition>> byCapability;
    private final Map<GoodId, List<ProductionProcessDefinition>> byInput;
    private final Map<GoodId, List<ProductionProcessDefinition>> byOutput;
    private final Map<ProductionTransformationReference, List<ProductionProcessDefinition>> byTransformation;

    private ProductionProcessRegistry(List<ProductionProcessDefinition> source) {
        definitions = List.copyOf(source);
        Map<ProductionProcessId, ProductionProcessDefinition> ids = new LinkedHashMap<>();
        for (ProductionProcessDefinition definition : definitions) {
            if (ids.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate production process id: " + definition.id().value());
            }
        }
        byId = Collections.unmodifiableMap(ids);
        byIndustry = groups(definitions, definition -> List.of(definition.owningIndustryId()));
        byCapability = groups(definitions, ProductionProcessDefinition::allRequiredCapabilities);
        byInput = groups(definitions, definition -> definition.inputs().stream()
                .map(ProductionInputDefinition::goodId).distinct().toList());
        byOutput = groups(definitions, definition -> definition.outputs().stream()
                .map(ProductionOutputDefinition::goodId).distinct().toList());
        byTransformation = groups(definitions, ProductionProcessDefinition::transformationReferences);
    }

    public static ProductionProcessRegistry empty() {
        return of(List.of());
    }

    public static ProductionProcessRegistry of(Collection<ProductionProcessDefinition> definitions) {
        return new ProductionProcessRegistry(Objects.requireNonNull(definitions, "definitions").stream()
                .map(definition -> Objects.requireNonNull(definition, "definition")).toList());
    }

    public static ProductionProcessRegistryBuilder builder() {
        return new ProductionProcessRegistryBuilder();
    }

    public boolean contains(ProductionProcessId id) { return byId.containsKey(Objects.requireNonNull(id, "id")); }
    public Optional<ProductionProcessDefinition> find(ProductionProcessId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }
    public int size() { return definitions.size(); }
    public List<ProductionProcessDefinition> definitions() { return definitions; }
    public Stream<ProductionProcessDefinition> stream() { return definitions.stream(); }
    public List<ProductionProcessDefinition> findByIndustry(IndustryId id) { return get(byIndustry, id); }
    public List<ProductionProcessDefinition> findByCapability(ActorCapability value) {
        return get(byCapability, value);
    }
    public List<ProductionProcessDefinition> findByInputGood(GoodId id) { return get(byInput, id); }
    public List<ProductionProcessDefinition> findByOutputGood(GoodId id) { return get(byOutput, id); }
    public List<ProductionProcessDefinition> findByTransformation(ProductionTransformationReference reference) {
        return get(byTransformation, reference);
    }
    public List<ProductionProcessDefinition> findByTag(String tag) {
        String normalized = ProductionValidation.copyTags(java.util.Set.of(tag)).iterator().next();
        return definitions.stream().filter(definition -> definition.metadata().tags().contains(normalized)).toList();
    }

    private static <K> List<ProductionProcessDefinition> get(
            Map<K, List<ProductionProcessDefinition>> groups,
            K key
    ) {
        return groups.getOrDefault(Objects.requireNonNull(key, "key"), List.of());
    }

    private static <K> Map<K, List<ProductionProcessDefinition>> groups(
            List<ProductionProcessDefinition> definitions,
            Function<ProductionProcessDefinition, Collection<K>> keys
    ) {
        Map<K, List<ProductionProcessDefinition>> mutable = new LinkedHashMap<>();
        for (ProductionProcessDefinition definition : definitions) {
            for (K key : keys.apply(definition)) {
                mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(definition);
            }
        }
        Map<K, List<ProductionProcessDefinition>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }
}
