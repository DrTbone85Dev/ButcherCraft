package com.butchercraft.world.production;

import com.butchercraft.world.inventory.InventoryType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record ProductionInventoryConstraint(Set<InventoryType> allowedTypes) {
    private static final ProductionInventoryConstraint ANY = new ProductionInventoryConstraint(Set.of());

    public ProductionInventoryConstraint {
        Objects.requireNonNull(allowedTypes, "allowedTypes");
        EnumSet<InventoryType> copied = EnumSet.noneOf(InventoryType.class);
        allowedTypes.forEach(type -> copied.add(Objects.requireNonNull(type, "inventoryType")));
        allowedTypes = Collections.unmodifiableSet(copied);
    }

    public static ProductionInventoryConstraint any() {
        return ANY;
    }

    public boolean accepts(InventoryType type) {
        return allowedTypes.isEmpty() || allowedTypes.contains(Objects.requireNonNull(type, "type"));
    }
}
