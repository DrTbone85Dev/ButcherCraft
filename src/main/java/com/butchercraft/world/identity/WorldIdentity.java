package com.butchercraft.world.identity;

import com.butchercraft.world.property.BuiltInCommercialPropertyCatalog;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorldIdentity(
        int schemaVersion,
        String id,
        long worldSeed,
        Region region,
        List<County> counties,
        List<CommercialProperty> commercialProperties
) {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public WorldIdentity(int schemaVersion, String id, long worldSeed, Region region, List<County> counties) {
        this(
                schemaVersion,
                id,
                worldSeed,
                region,
                counties,
                BuiltInCommercialPropertyCatalog.generate(worldSeed, settlementsFrom(counties))
        );
    }

    public WorldIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world identity schema version: " + schemaVersion);
        }
        id = requireNonBlank(id, "id");
        region = Objects.requireNonNull(region, "region");
        counties = List.copyOf(Objects.requireNonNull(counties, "counties"));
        if (counties.isEmpty()) {
            throw new IllegalArgumentException("World identity counties must not be empty");
        }
        Set<String> countyIds = new HashSet<>();
        Set<String> settlementIds = new HashSet<>();
        for (County county : counties) {
            Objects.requireNonNull(county, "county");
            if (!region.id().equals(county.regionId())) {
                throw new IllegalArgumentException("County " + county.id() + " belongs to " + county.regionId()
                        + " instead of region " + region.id());
            }
            if (!countyIds.add(county.id())) {
                throw new IllegalArgumentException("Duplicate county id: " + county.id());
            }
            for (Settlement settlement : county.settlements()) {
                if (!settlementIds.add(settlement.id())) {
                    throw new IllegalArgumentException("Duplicate settlement id: " + settlement.id());
                }
            }
        }
        commercialProperties = List.copyOf(Objects.requireNonNull(commercialProperties, "commercialProperties"));
        validateCommercialProperties(commercialProperties, settlementIds);
    }

    public List<Settlement> settlements() {
        return counties.stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    public List<CommercialProperty> commercialPropertiesForSettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return commercialProperties.stream()
                .filter(property -> property.settlementId().equals(settlementId))
                .toList();
    }

    private static List<Settlement> settlementsFrom(List<County> counties) {
        return Objects.requireNonNull(counties, "counties").stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    private static void validateCommercialProperties(List<CommercialProperty> properties, Set<String> settlementIds) {
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("World identity commercial properties must not be empty");
        }
        Set<CommercialPropertyId> propertyIds = new HashSet<>();
        Map<String, Set<String>> namesBySettlement = new HashMap<>();
        for (CommercialProperty property : properties) {
            Objects.requireNonNull(property, "commercialProperty");
            if (!settlementIds.contains(property.settlementId())) {
                throw new IllegalArgumentException("Commercial property " + property.id().value()
                        + " references unknown settlement: " + property.settlementId());
            }
            if (!propertyIds.add(property.id())) {
                throw new IllegalArgumentException("Duplicate commercial property id: " + property.id().value());
            }
            Set<String> names = namesBySettlement.computeIfAbsent(property.settlementId(), ignored -> new HashSet<>());
            if (!names.add(property.displayName())) {
                throw new IllegalArgumentException("Duplicate commercial property name in settlement "
                        + property.settlementId() + ": " + property.displayName());
            }
        }
        for (String settlementId : settlementIds) {
            if (!namesBySettlement.containsKey(settlementId)) {
                throw new IllegalArgumentException("Settlement has no commercial properties: " + settlementId);
            }
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("World identity " + fieldName + " must not be blank");
        }
        return value;
    }
}
