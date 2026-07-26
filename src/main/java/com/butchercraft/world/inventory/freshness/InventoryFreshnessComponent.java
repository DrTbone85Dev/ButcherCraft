package com.butchercraft.world.inventory.freshness;

import java.util.Objects;
import java.util.Optional;

public record InventoryFreshnessComponent(
        int schemaVersion,
        String stateScopeIdentity,
        String sourceStateIdentity,
        String stateContentDigest,
        Optional<String> configurationIdentity,
        long representedSimulationTick
) implements Comparable<InventoryFreshnessComponent> {
    public InventoryFreshnessComponent {
        schemaVersion = InventoryFreshnessValidation.positive(schemaVersion, "schemaVersion");
        stateScopeIdentity = InventoryFreshnessValidation.id(stateScopeIdentity, "stateScopeIdentity");
        sourceStateIdentity = InventoryFreshnessValidation.id(sourceStateIdentity, "sourceStateIdentity");
        stateContentDigest = InventoryFreshnessValidation.digest(stateContentDigest, "stateContentDigest");
        configurationIdentity = Objects.requireNonNull(configurationIdentity, "configurationIdentity")
                .map(candidate -> InventoryFreshnessValidation.id(candidate, "configurationIdentity"));
        representedSimulationTick = InventoryFreshnessValidation.nonNegative(
                representedSimulationTick,
                "representedSimulationTick"
        );
    }

    public static InventoryFreshnessComponent of(
            String stateScopeIdentity,
            String sourceStateIdentity,
            String stateContentDigest,
            long representedSimulationTick
    ) {
        return new InventoryFreshnessComponent(
                InventoryFreshnessSchema.CURRENT_VERSION,
                stateScopeIdentity,
                sourceStateIdentity,
                stateContentDigest,
                Optional.empty(),
                representedSimulationTick
        );
    }

    @Override
    public int compareTo(InventoryFreshnessComponent other) {
        Objects.requireNonNull(other, "other");
        int scopeComparison = stateScopeIdentity.compareTo(other.stateScopeIdentity);
        if (scopeComparison != 0) {
            return scopeComparison;
        }
        int sourceComparison = sourceStateIdentity.compareTo(other.sourceStateIdentity);
        if (sourceComparison != 0) {
            return sourceComparison;
        }
        int digestComparison = stateContentDigest.compareTo(other.stateContentDigest);
        if (digestComparison != 0) {
            return digestComparison;
        }
        return Long.compare(representedSimulationTick, other.representedSimulationTick);
    }
}
