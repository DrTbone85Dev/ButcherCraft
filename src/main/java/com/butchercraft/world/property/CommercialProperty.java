package com.butchercraft.world.property;

import java.util.Objects;

public record CommercialProperty(
        CommercialPropertyId id,
        String displayName,
        String settlementId,
        CommercialPropertyType propertyType,
        int constructionYear,
        PropertyCondition condition,
        PropertyStatus status,
        LotSize lotSize,
        BuildingSize buildingSize,
        UtilityProfile utilityProfile,
        ExpansionCapacity expansionCapacity,
        PropertyHistory history
) {
    public CommercialProperty {
        id = Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        settlementId = requireNonBlank(settlementId, "settlementId");
        propertyType = Objects.requireNonNull(propertyType, "propertyType");
        if (constructionYear < 1850 || constructionYear > 2026) {
            throw new IllegalArgumentException("Commercial property construction year is outside the supported range: " + constructionYear);
        }
        condition = Objects.requireNonNull(condition, "condition");
        status = Objects.requireNonNull(status, "status");
        lotSize = Objects.requireNonNull(lotSize, "lotSize");
        buildingSize = Objects.requireNonNull(buildingSize, "buildingSize");
        if (buildingSize.squareMeters() > lotSize.squareMeters()) {
            throw new IllegalArgumentException("Commercial property building size must not exceed lot size");
        }
        if (propertyType == CommercialPropertyType.EMPTY_COMMERCIAL_LOT && buildingSize.squareMeters() != 0) {
            throw new IllegalArgumentException("Empty commercial lots must not have an existing building size");
        }
        if (propertyType != CommercialPropertyType.EMPTY_COMMERCIAL_LOT && buildingSize.squareMeters() == 0) {
            throw new IllegalArgumentException("Built commercial properties must have a building size");
        }
        utilityProfile = Objects.requireNonNull(utilityProfile, "utilityProfile");
        expansionCapacity = Objects.requireNonNull(expansionCapacity, "expansionCapacity");
        history = Objects.requireNonNull(history, "history");
        if (history.ownershipHistory().getFirst().startYear() < constructionYear) {
            throw new IllegalArgumentException("Commercial property ownership history starts before construction");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Commercial property " + fieldName + " must not be blank");
        }
        return value;
    }
}
