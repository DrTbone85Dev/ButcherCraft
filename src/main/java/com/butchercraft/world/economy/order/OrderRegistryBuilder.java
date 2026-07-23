package com.butchercraft.world.economy.order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class OrderRegistryBuilder {
    private final Map<OrderId, EconomicOrderDefinition> definitions = new LinkedHashMap<>();

    public OrderRegistryBuilder register(EconomicOrderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate order id: " + definition.id().value());
        }
        return this;
    }

    public OrderRegistry build() {
        return OrderRegistry.of(definitions.values());
    }
}
