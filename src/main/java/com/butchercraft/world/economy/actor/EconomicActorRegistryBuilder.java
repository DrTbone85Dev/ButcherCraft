package com.butchercraft.world.economy.actor;

import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.IndustryId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class EconomicActorRegistryBuilder {
    private final GoodRegistry goodRegistry;
    private final Collection<IndustryId> knownIndustries;
    private final Map<ActorId, EconomicActorDefinition> definitions = new LinkedHashMap<>();

    EconomicActorRegistryBuilder(GoodRegistry goodRegistry, Collection<IndustryId> knownIndustries) {
        this.goodRegistry = Objects.requireNonNull(goodRegistry, "goodRegistry");
        this.knownIndustries = Objects.requireNonNull(knownIndustries, "knownIndustries");
    }

    public EconomicActorRegistryBuilder register(EconomicActorDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        EconomicActorDefinition previous = definitions.putIfAbsent(definition.id(), definition);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate economic actor id: " + definition.id().value());
        }
        return this;
    }

    public EconomicActorRegistryBuilder registerAll(Collection<EconomicActorDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions").forEach(this::register);
        return this;
    }

    public EconomicActorRegistry build() {
        return EconomicActorRegistry.of(definitions.values(), goodRegistry, knownIndustries);
    }
}
