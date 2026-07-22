package com.butchercraft.world.goods;

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

public final class GoodRegistry {
    private static final Comparator<GoodDefinition> DEFINITION_ORDER = Comparator.comparing(GoodDefinition::id);
    private static final Comparator<GoodTransformation> TRANSFORMATION_ORDER = Comparator
            .comparing(GoodTransformation::inputGoodId)
            .thenComparing(GoodTransformation::outputGoodId)
            .thenComparing(GoodTransformation::owningIndustryId)
            .thenComparingLong(transformation -> transformation.yieldRatio().numerator())
            .thenComparingLong(transformation -> transformation.yieldRatio().denominator());

    private final Set<IndustryId> knownIndustries;
    private final List<GoodDefinition> definitions;
    private final Map<GoodId, GoodDefinition> definitionsById;
    private final Map<IndustryId, List<GoodDefinition>> definitionsByIndustry;
    private final List<GoodTransformation> transformations;
    private final Map<GoodId, List<GoodTransformation>> transformationsByInput;
    private final Map<GoodId, List<GoodTransformation>> transformationsByOutput;
    private final Map<IndustryId, List<GoodTransformation>> transformationsByIndustry;

    private GoodRegistry(
            Set<IndustryId> knownIndustries,
            List<GoodDefinition> definitions,
            Map<GoodId, GoodDefinition> definitionsById,
            Map<IndustryId, List<GoodDefinition>> definitionsByIndustry,
            List<GoodTransformation> transformations,
            Map<GoodId, List<GoodTransformation>> transformationsByInput,
            Map<GoodId, List<GoodTransformation>> transformationsByOutput,
            Map<IndustryId, List<GoodTransformation>> transformationsByIndustry
    ) {
        this.knownIndustries = knownIndustries;
        this.definitions = definitions;
        this.definitionsById = definitionsById;
        this.definitionsByIndustry = definitionsByIndustry;
        this.transformations = transformations;
        this.transformationsByInput = transformationsByInput;
        this.transformationsByOutput = transformationsByOutput;
        this.transformationsByIndustry = transformationsByIndustry;
    }

    public static GoodRegistry empty(Collection<IndustryId> knownIndustries) {
        return of(List.of(), List.of(), knownIndustries);
    }

    public static GoodRegistry of(
            Collection<? extends GoodDefinition> definitions,
            Collection<GoodTransformation> transformations,
            Collection<IndustryId> knownIndustries
    ) {
        Set<IndustryId> normalizedIndustries = copyKnownIndustries(knownIndustries);
        List<GoodDefinition> orderedDefinitions = Objects.requireNonNull(definitions, "definitions").stream()
                .map(definition -> Objects.requireNonNull(definition, "definition"))
                .sorted(DEFINITION_ORDER)
                .toList();
        rejectDuplicateDefinitionIds(orderedDefinitions);
        validateDefinitionIndustries(orderedDefinitions, normalizedIndustries);

        Map<GoodId, GoodDefinition> byId = orderedDefinitions.stream()
                .collect(Collectors.toUnmodifiableMap(GoodDefinition::id, Function.identity()));
        Map<IndustryId, List<GoodDefinition>> byIndustry = immutableGroups(
                orderedDefinitions,
                GoodDefinition::industryId
        );

        List<GoodTransformation> orderedTransformations = Objects.requireNonNull(transformations, "transformations")
                .stream()
                .map(transformation -> Objects.requireNonNull(transformation, "transformation"))
                .sorted(TRANSFORMATION_ORDER)
                .toList();
        rejectDuplicateTransformations(orderedTransformations);
        validateTransformations(orderedTransformations, byId.keySet(), normalizedIndustries);
        rejectCircularTransformations(orderedDefinitions, orderedTransformations);

        return new GoodRegistry(
                normalizedIndustries,
                List.copyOf(orderedDefinitions),
                byId,
                byIndustry,
                List.copyOf(orderedTransformations),
                immutableGroups(orderedTransformations, GoodTransformation::inputGoodId),
                immutableGroups(orderedTransformations, GoodTransformation::outputGoodId),
                immutableGroups(orderedTransformations, GoodTransformation::owningIndustryId)
        );
    }

    public static GoodRegistryBuilder builder(Collection<IndustryId> knownIndustries) {
        return new GoodRegistryBuilder(knownIndustries);
    }

    public GoodRegistry withDefinition(GoodDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (contains(definition.id())) {
            throw new IllegalArgumentException("Duplicate good id: " + definition.id().value());
        }
        List<GoodDefinition> updated = new ArrayList<>(definitions);
        updated.add(definition);
        return of(updated, transformations, knownIndustries);
    }

    public GoodRegistry withDefinitions(Collection<? extends GoodDefinition> addedDefinitions) {
        Objects.requireNonNull(addedDefinitions, "addedDefinitions");
        List<GoodDefinition> updated = new ArrayList<>(definitions);
        updated.addAll(addedDefinitions);
        return of(updated, transformations, knownIndustries);
    }

    public GoodRegistry withTransformation(GoodTransformation transformation) {
        Objects.requireNonNull(transformation, "transformation");
        List<GoodTransformation> updated = new ArrayList<>(transformations);
        updated.add(transformation);
        return of(definitions, updated, knownIndustries);
    }

    public boolean contains(GoodId id) {
        return definitionsById.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Optional<GoodDefinition> find(GoodId id) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(id, "id")));
    }

    public int size() {
        return definitions.size();
    }

    public int transformationCount() {
        return transformations.size();
    }

    public List<GoodDefinition> definitions() {
        return definitions;
    }

    public List<GoodTransformation> transformations() {
        return transformations;
    }

    public Set<IndustryId> knownIndustries() {
        return knownIndustries;
    }

    public Stream<GoodDefinition> stream() {
        return definitions.stream();
    }

    public List<GoodDefinition> findByCategory(GoodCategory category) {
        Objects.requireNonNull(category, "category");
        return definitions.stream().filter(definition -> definition.category() == category).toList();
    }

    public List<GoodDefinition> findByIndustry(IndustryId industryId) {
        return definitionsByIndustry.getOrDefault(Objects.requireNonNull(industryId, "industryId"), List.of());
    }

    public List<GoodTransformation> transformationsFrom(GoodId inputGoodId) {
        return transformationsByInput.getOrDefault(
                Objects.requireNonNull(inputGoodId, "inputGoodId"),
                List.of()
        );
    }

    public List<GoodTransformation> transformationsTo(GoodId outputGoodId) {
        return transformationsByOutput.getOrDefault(
                Objects.requireNonNull(outputGoodId, "outputGoodId"),
                List.of()
        );
    }

    public List<GoodTransformation> transformationsForIndustry(IndustryId industryId) {
        return transformationsByIndustry.getOrDefault(
                Objects.requireNonNull(industryId, "industryId"),
                List.of()
        );
    }

    public void validate() {
        of(definitions, transformations, knownIndustries);
    }

    private static Set<IndustryId> copyKnownIndustries(Collection<IndustryId> source) {
        Objects.requireNonNull(source, "knownIndustries");
        List<IndustryId> sorted = source.stream()
                .map(industryId -> Objects.requireNonNull(industryId, "industryId"))
                .sorted()
                .toList();
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static void rejectDuplicateDefinitionIds(List<GoodDefinition> definitions) {
        Set<GoodId> seen = new HashSet<>();
        Set<GoodId> duplicates = new LinkedHashSet<>();
        for (GoodDefinition definition : definitions) {
            if (!seen.add(definition.id())) {
                duplicates.add(definition.id());
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate good ids: " + duplicates);
        }
    }

    private static void validateDefinitionIndustries(
            List<GoodDefinition> definitions,
            Set<IndustryId> knownIndustries
    ) {
        for (GoodDefinition definition : definitions) {
            requireKnownIndustry(definition.industryId(), knownIndustries, "good " + definition.id().value());
            if (definition instanceof ProductDefinition product) {
                requireKnownIndustry(
                        product.sourceIndustryId(),
                        knownIndustries,
                        "product source " + definition.id().value()
                );
            }
        }
    }

    private static void rejectDuplicateTransformations(List<GoodTransformation> transformations) {
        Set<TransformationKey> seen = new HashSet<>();
        Set<TransformationKey> duplicates = new LinkedHashSet<>();
        for (GoodTransformation transformation : transformations) {
            TransformationKey key = new TransformationKey(
                    transformation.inputGoodId(),
                    transformation.outputGoodId(),
                    transformation.owningIndustryId()
            );
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate good transformations: " + duplicates);
        }
    }

    private static void validateTransformations(
            List<GoodTransformation> transformations,
            Set<GoodId> knownGoodIds,
            Set<IndustryId> knownIndustries
    ) {
        for (GoodTransformation transformation : transformations) {
            if (!knownGoodIds.contains(transformation.inputGoodId())) {
                throw new IllegalArgumentException("Good transformation references unknown input: "
                        + transformation.inputGoodId().value());
            }
            if (!knownGoodIds.contains(transformation.outputGoodId())) {
                throw new IllegalArgumentException("Good transformation references unknown output: "
                        + transformation.outputGoodId().value());
            }
            requireKnownIndustry(
                    transformation.owningIndustryId(),
                    knownIndustries,
                    "transformation " + transformation.inputGoodId().value()
                            + " -> " + transformation.outputGoodId().value()
            );
        }
    }

    private static void rejectCircularTransformations(
            List<GoodDefinition> definitions,
            List<GoodTransformation> transformations
    ) {
        if (transformations.isEmpty()) {
            return;
        }
        Map<GoodId, Integer> indegree = new HashMap<>();
        Map<GoodId, List<GoodId>> adjacency = new HashMap<>();
        for (GoodDefinition definition : definitions) {
            indegree.put(definition.id(), 0);
        }
        for (GoodTransformation transformation : transformations) {
            adjacency.computeIfAbsent(transformation.inputGoodId(), ignored -> new ArrayList<>())
                    .add(transformation.outputGoodId());
            indegree.compute(transformation.outputGoodId(), (ignored, degree) -> degree + 1);
        }

        PriorityQueue<GoodId> ready = new PriorityQueue<>();
        indegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        int visited = 0;
        while (!ready.isEmpty()) {
            GoodId current = ready.remove();
            visited++;
            for (GoodId output : adjacency.getOrDefault(current, List.of())) {
                int remaining = indegree.compute(output, (ignored, degree) -> degree - 1);
                if (remaining == 0) {
                    ready.add(output);
                }
            }
        }
        if (visited != definitions.size()) {
            throw new IllegalArgumentException("Circular good transformation graph");
        }
    }

    private static void requireKnownIndustry(
            IndustryId industryId,
            Set<IndustryId> knownIndustries,
            String owner
    ) {
        if (!knownIndustries.contains(industryId)) {
            throw new IllegalArgumentException("Unknown industry for " + owner + ": " + industryId.value());
        }
    }

    private static <K, V> Map<K, List<V>> immutableGroups(List<V> values, Function<V, K> keyFunction) {
        Map<K, List<V>> mutable = new LinkedHashMap<>();
        for (V value : values) {
            mutable.computeIfAbsent(keyFunction.apply(value), ignored -> new ArrayList<>()).add(value);
        }
        Map<K, List<V>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, groupedValues) -> immutable.put(key, List.copyOf(groupedValues)));
        return Collections.unmodifiableMap(immutable);
    }

    private record TransformationKey(
            GoodId inputGoodId,
            GoodId outputGoodId,
            IndustryId owningIndustryId
    ) {
    }
}
