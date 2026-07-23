package com.butchercraft.world.goods;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GoodManager {
    private GoodRegistry registry;

    public GoodManager(GoodRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public synchronized GoodDefinition register(GoodDefinition definition) {
        registry = registry.withDefinition(definition);
        return definition;
    }

    public synchronized void registerAll(Collection<? extends GoodDefinition> definitions) {
        registry = registry.withDefinitions(definitions);
    }

    public synchronized GoodTransformation registerTransformation(GoodTransformation transformation) {
        registry = registry.withTransformation(transformation);
        return transformation;
    }

    public synchronized boolean contains(GoodId id) {
        return registry.contains(id);
    }

    public synchronized Optional<GoodDefinition> find(GoodId id) {
        return registry.find(id);
    }

    public synchronized List<GoodDefinition> findByCategory(GoodCategory category) {
        return registry.findByCategory(category);
    }

    public synchronized List<GoodDefinition> findByIndustry(IndustryId industryId) {
        return registry.findByIndustry(industryId);
    }

    public synchronized List<GoodTransformation> transformationsFrom(GoodId inputGoodId) {
        return registry.transformationsFrom(inputGoodId);
    }

    public synchronized List<GoodTransformation> transformationsTo(GoodId outputGoodId) {
        return registry.transformationsTo(outputGoodId);
    }

    public synchronized List<GoodTransformation> transformationsForIndustry(IndustryId industryId) {
        return registry.transformationsForIndustry(industryId);
    }

    public synchronized GoodRegistry registry() {
        return registry;
    }

    public synchronized void validate() {
        registry.validate();
    }
}
