package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.IndustryId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EconomicActorRegistry {
    private static final Comparator<EconomicActorDefinition> DEFINITION_ORDER =
            Comparator.comparing(EconomicActorDefinition::id);

    private final GoodRegistry goodRegistry;
    private final Set<IndustryId> knownIndustries;
    private final List<EconomicActorDefinition> definitions;
    private final Map<ActorId, EconomicActorDefinition> definitionsById;
    private final Map<ActorType, List<EconomicActorDefinition>> definitionsByType;
    private final Map<IndustryId, List<EconomicActorDefinition>> definitionsByIndustry;
    private final Map<ActorCapability, List<EconomicActorDefinition>> definitionsByCapability;
    private final Map<GoodId, List<EconomicActorDefinition>> definitionsByGood;

    private EconomicActorRegistry(
            GoodRegistry goodRegistry,
            Set<IndustryId> knownIndustries,
            List<EconomicActorDefinition> definitions,
            Map<ActorId, EconomicActorDefinition> definitionsById,
            Map<ActorType, List<EconomicActorDefinition>> definitionsByType,
            Map<IndustryId, List<EconomicActorDefinition>> definitionsByIndustry,
            Map<ActorCapability, List<EconomicActorDefinition>> definitionsByCapability,
            Map<GoodId, List<EconomicActorDefinition>> definitionsByGood
    ) {
        this.goodRegistry = goodRegistry;
        this.knownIndustries = knownIndustries;
        this.definitions = definitions;
        this.definitionsById = definitionsById;
        this.definitionsByType = definitionsByType;
        this.definitionsByIndustry = definitionsByIndustry;
        this.definitionsByCapability = definitionsByCapability;
        this.definitionsByGood = definitionsByGood;
    }

    public static EconomicActorRegistry empty(GoodRegistry goodRegistry, Collection<IndustryId> knownIndustries) {
        return of(List.of(), goodRegistry, knownIndustries);
    }

    public static EconomicActorRegistry of(
            Collection<EconomicActorDefinition> definitions,
            GoodRegistry goodRegistry,
            Collection<IndustryId> knownIndustries
    ) {
        Objects.requireNonNull(goodRegistry, "goodRegistry");
        Set<IndustryId> normalizedIndustries = copyKnownIndustries(knownIndustries);
        List<EconomicActorDefinition> orderedDefinitions = Objects.requireNonNull(definitions, "definitions").stream()
                .map(definition -> Objects.requireNonNull(definition, "definition"))
                .sorted(DEFINITION_ORDER)
                .toList();
        rejectDuplicateActorIds(orderedDefinitions);
        validateDefinitions(orderedDefinitions, goodRegistry, normalizedIndustries);
        rejectCircularDependencies(orderedDefinitions);

        Map<ActorId, EconomicActorDefinition> byId = orderedDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(EconomicActorDefinition::id, Function.identity()));
        return new EconomicActorRegistry(
                goodRegistry,
                normalizedIndustries,
                List.copyOf(orderedDefinitions),
                byId,
                immutableGroups(orderedDefinitions, EconomicActorDefinition::actorType),
                immutableGroups(orderedDefinitions, EconomicActorDefinition::industryId),
                groupByCapability(orderedDefinitions),
                groupByGood(orderedDefinitions)
        );
    }

    public static EconomicActorRegistryBuilder builder(
            GoodRegistry goodRegistry,
            Collection<IndustryId> knownIndustries
    ) {
        return new EconomicActorRegistryBuilder(goodRegistry, knownIndustries);
    }

    public EconomicActorRegistry withDefinition(EconomicActorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (contains(definition.id())) {
            throw new IllegalArgumentException("Duplicate economic actor id: " + definition.id().value());
        }
        List<EconomicActorDefinition> updated = new ArrayList<>(definitions);
        updated.add(definition);
        return of(updated, goodRegistry, knownIndustries);
    }

    public boolean contains(ActorId actorId) {
        return definitionsById.containsKey(Objects.requireNonNull(actorId, "actorId"));
    }

    public Optional<EconomicActorDefinition> find(ActorId actorId) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(actorId, "actorId")));
    }

    public int size() {
        return definitions.size();
    }

    public int relationshipCount() {
        return definitions.stream().mapToInt(definition -> definition.relationships().size()).sum();
    }

    public List<EconomicActorDefinition> definitions() {
        return definitions;
    }

    public Stream<EconomicActorDefinition> stream() {
        return definitions.stream();
    }

    public List<EconomicActorDefinition> findByType(ActorType actorType) {
        return definitionsByType.getOrDefault(Objects.requireNonNull(actorType, "actorType"), List.of());
    }

    public List<EconomicActorDefinition> findByIndustry(IndustryId industryId) {
        return definitionsByIndustry.getOrDefault(Objects.requireNonNull(industryId, "industryId"), List.of());
    }

    public List<EconomicActorDefinition> findByCapability(ActorCapability capability) {
        return definitionsByCapability.getOrDefault(
                Objects.requireNonNull(capability, "capability"),
                List.of()
        );
    }

    public List<EconomicActorDefinition> findByGood(GoodId goodId) {
        return definitionsByGood.getOrDefault(Objects.requireNonNull(goodId, "goodId"), List.of());
    }

    public List<EconomicActorDefinition> findByGoodRole(GoodId goodId, GoodRole goodRole) {
        Objects.requireNonNull(goodRole, "goodRole");
        return findByGood(goodId).stream()
                .filter(definition -> definition.relationshipsFor(goodId).stream()
                        .anyMatch(relationship -> relationship.goodRole() == goodRole))
                .toList();
    }

    public List<ActorRelationship> relationshipsFor(ActorId actorId) {
        return find(actorId).map(EconomicActorDefinition::relationships).orElse(List.of());
    }

    public List<EconomicActorDefinition> dependenciesOf(ActorId actorId) {
        EconomicActorDefinition definition = find(actorId).orElse(null);
        if (definition == null) {
            return List.of();
        }
        return definition.relationships().stream()
                .map(ActorRelationship::dependsOnActorId)
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .map(definitionsById::get)
                .toList();
    }

    public GoodRegistry goodRegistry() {
        return goodRegistry;
    }

    public Set<IndustryId> knownIndustries() {
        return knownIndustries;
    }

    public void validate() {
        of(definitions, goodRegistry, knownIndustries);
    }

    private static Set<IndustryId> copyKnownIndustries(Collection<IndustryId> source) {
        Objects.requireNonNull(source, "knownIndustries");
        List<IndustryId> sorted = source.stream()
                .map(industryId -> Objects.requireNonNull(industryId, "industryId"))
                .sorted()
                .toList();
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static void rejectDuplicateActorIds(List<EconomicActorDefinition> definitions) {
        Set<ActorId> seen = new HashSet<>();
        Set<ActorId> duplicates = new LinkedHashSet<>();
        for (EconomicActorDefinition definition : definitions) {
            if (!seen.add(definition.id())) {
                duplicates.add(definition.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate economic actor ids: " + duplicates);
        }
    }

    private static void validateDefinitions(
            List<EconomicActorDefinition> definitions,
            GoodRegistry goodRegistry,
            Set<IndustryId> knownIndustries
    ) {
        Set<ActorId> actorIds = definitions.stream()
                .map(EconomicActorDefinition::id)
                .collect(Collectors.toUnmodifiableSet());
        for (EconomicActorDefinition definition : definitions) {
            if (!knownIndustries.contains(definition.industryId())) {
                throw new IllegalArgumentException("Unknown industry for economic actor "
                        + definition.id().value() + ": " + definition.industryId().value());
            }
            for (ActorRelationship relationship : definition.relationships()) {
                if (!goodRegistry.contains(relationship.goodId())) {
                    throw new IllegalArgumentException("Economic actor relationship references unknown good: "
                            + definition.id().value() + "/" + relationship.goodId().value());
                }
                for (IndustryId supportedIndustryId : relationship.supportedIndustryIds()) {
                    if (!knownIndustries.contains(supportedIndustryId)) {
                        throw new IllegalArgumentException("Economic actor relationship references unknown industry: "
                                + definition.id().value() + "/" + supportedIndustryId.value());
                    }
                }
                relationship.dependsOnActorId().ifPresent(dependencyActorId -> {
                    if (dependencyActorId.equals(definition.id())) {
                        throw new IllegalArgumentException("Economic actor must not depend on itself: "
                                + definition.id().value());
                    }
                    if (!actorIds.contains(dependencyActorId)) {
                        throw new IllegalArgumentException("Economic actor relationship references unknown actor: "
                                + dependencyActorId.value());
                    }
                });
            }
        }
    }

    private static void rejectCircularDependencies(List<EconomicActorDefinition> definitions) {
        Map<ActorId, Integer> indegree = new HashMap<>();
        Map<ActorId, Set<ActorId>> adjacency = new HashMap<>();
        for (EconomicActorDefinition definition : definitions) {
            indegree.put(definition.id(), 0);
        }
        for (EconomicActorDefinition definition : definitions) {
            for (ActorRelationship relationship : definition.relationships()) {
                relationship.dependsOnActorId().ifPresent(dependency -> {
                    if (adjacency.computeIfAbsent(definition.id(), ignored -> new LinkedHashSet<>()).add(dependency)) {
                        indegree.compute(dependency, (ignored, degree) -> degree + 1);
                    }
                });
            }
        }

        PriorityQueue<ActorId> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        int visited = 0;
        while (!ready.isEmpty()) {
            ActorId current = ready.remove();
            visited++;
            for (ActorId dependency : adjacency.getOrDefault(current, Set.of())) {
                int remaining = indegree.compute(dependency, (ignored, degree) -> degree - 1);
                if (remaining == 0) {
                    ready.add(dependency);
                }
            }
        }
        if (visited != definitions.size()) {
            throw new IllegalArgumentException("Circular economic actor dependency chain");
        }
    }

    private static Map<ActorCapability, List<EconomicActorDefinition>> groupByCapability(
            List<EconomicActorDefinition> definitions
    ) {
        Map<ActorCapability, List<EconomicActorDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicActorDefinition definition : definitions) {
            for (ActorCapability capability : definition.capabilities()) {
                mutable.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(definition);
            }
        }
        return immutableMapOfLists(mutable);
    }

    private static Map<GoodId, List<EconomicActorDefinition>> groupByGood(
            List<EconomicActorDefinition> definitions
    ) {
        Map<GoodId, List<EconomicActorDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicActorDefinition definition : definitions) {
            definition.relationships().stream()
                    .map(ActorRelationship::goodId)
                    .distinct()
                    .forEach(goodId -> mutable.computeIfAbsent(goodId, ignored -> new ArrayList<>()).add(definition));
        }
        return immutableMapOfLists(mutable);
    }

    private static <K> Map<K, List<EconomicActorDefinition>> immutableGroups(
            List<EconomicActorDefinition> definitions,
            Function<EconomicActorDefinition, K> keyFunction
    ) {
        Map<K, List<EconomicActorDefinition>> mutable = new LinkedHashMap<>();
        for (EconomicActorDefinition definition : definitions) {
            mutable.computeIfAbsent(keyFunction.apply(definition), ignored -> new ArrayList<>()).add(definition);
        }
        return immutableMapOfLists(mutable);
    }

    private static <K> Map<K, List<EconomicActorDefinition>> immutableMapOfLists(
            Map<K, List<EconomicActorDefinition>> mutable
    ) {
        Map<K, List<EconomicActorDefinition>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }
}
