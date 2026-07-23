package com.butchercraft.world.goods;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GoodRegistryBuilder {
    private final Collection<IndustryId> knownIndustries;
    private final Map<GoodId, GoodDefinition> definitions = new LinkedHashMap<>();
    private final List<GoodTransformation> transformations = new ArrayList<>();

    GoodRegistryBuilder(Collection<IndustryId> knownIndustries) {
        this.knownIndustries = List.copyOf(Objects.requireNonNull(knownIndustries, "knownIndustries"));
    }

    public GoodRegistryBuilder register(GoodDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        GoodDefinition previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate good id: " + definition.id().value());
        }
        return this;
    }

    public GoodRegistryBuilder registerAll(Collection<? extends GoodDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions").forEach(this::register);
        return this;
    }

    public GoodRegistryBuilder registerTransformation(GoodTransformation transformation) {
        transformations.add(Objects.requireNonNull(transformation, "transformation"));
        return this;
    }

    public GoodRegistryBuilder registerTransformations(Collection<GoodTransformation> transformations) {
        Objects.requireNonNull(transformations, "transformations").forEach(this::registerTransformation);
        return this;
    }

    public GoodRegistry build() {
        return GoodRegistry.of(definitions.values(), transformations, knownIndustries);
    }
}
