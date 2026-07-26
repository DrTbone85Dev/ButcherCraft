package com.butchercraft.world.inventory.freshness;

import java.util.List;
import java.util.Objects;

public record InventoryFreshnessIdentity(
        int schemaVersion,
        String issuerIdentity,
        List<InventoryFreshnessComponent> components,
        String identityDigest
) {
    public InventoryFreshnessIdentity {
        schemaVersion = InventoryFreshnessValidation.positive(schemaVersion, "schemaVersion");
        issuerIdentity = InventoryFreshnessValidation.id(issuerIdentity, "issuerIdentity");
        components = Objects.requireNonNull(components, "components").stream()
                .map(component -> Objects.requireNonNull(component, "component"))
                .sorted()
                .toList();
        if (components.isEmpty()) {
            throw new IllegalArgumentException("Inventory Freshness Identity requires at least one component");
        }
        identityDigest = InventoryFreshnessValidation.digest(identityDigest, "identityDigest");
    }

    public static InventoryFreshnessIdentity fromComponents(
            String issuerIdentity,
            List<InventoryFreshnessComponent> components
    ) {
        InventoryFreshnessIdentity candidate = new InventoryFreshnessIdentity(
                InventoryFreshnessSchema.CURRENT_VERSION,
                issuerIdentity,
                components,
                InventoryFreshnessValidation.zeroDigest()
        );
        return candidate.withCalculatedDigest();
    }

    public InventoryFreshnessIdentity withCalculatedDigest() {
        return new InventoryFreshnessIdentity(
                schemaVersion,
                issuerIdentity,
                components,
                calculateDigest()
        );
    }

    public String calculateDigest() {
        InventoryFreshnessCanonicalDigest digest = InventoryFreshnessCanonicalDigest.create(
                "butchercraft:inventory_freshness_identity"
        );
        digest.add(schemaVersion)
                .add(issuerIdentity)
                .add(components.size());
        for (InventoryFreshnessComponent component : components) {
            digest.add(component.schemaVersion())
                    .add(component.stateScopeIdentity())
                    .add(component.sourceStateIdentity())
                    .add(component.stateContentDigest())
                    .add(component.configurationIdentity().isPresent());
            component.configurationIdentity().ifPresent(digest::add);
            digest.add(component.representedSimulationTick());
        }
        return digest.finish();
    }

    public boolean digestMatches() {
        return identityDigest.equals(calculateDigest());
    }
}
