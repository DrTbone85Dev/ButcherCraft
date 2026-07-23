package com.butchercraft.world.economy.order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ContractRegistryBuilder {
    private final Map<ContractId, EconomicContractDefinition> definitions = new LinkedHashMap<>();

    public ContractRegistryBuilder register(EconomicContractDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate contract id: " + definition.id().value());
        }
        return this;
    }

    public ContractRegistry build() {
        return ContractRegistry.of(definitions.values());
    }
}
