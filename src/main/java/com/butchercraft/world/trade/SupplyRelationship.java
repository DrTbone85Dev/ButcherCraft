package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public record SupplyRelationship(
        SupplyRelationshipId id,
        BusinessId supplierBusinessId,
        BusinessId customerBusinessId,
        SupplyRelationshipType relationshipType,
        String preferredManufacturerId,
        List<ProductCategory> productCategories,
        int historicalStartYear,
        OptionalInt historicalEndYear,
        RelationshipStrength relationshipStrength,
        DistributionTerritoryId territoryId,
        String historicalNotes
) {
    public SupplyRelationship {
        id = Objects.requireNonNull(id, "id");
        supplierBusinessId = Objects.requireNonNull(supplierBusinessId, "supplierBusinessId");
        customerBusinessId = Objects.requireNonNull(customerBusinessId, "customerBusinessId");
        if (supplierBusinessId.equals(customerBusinessId)) {
            throw new IllegalArgumentException("Supply relationship must not reference the same supplier and customer");
        }
        relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
        preferredManufacturerId = TradeValidation.requireNonBlank(preferredManufacturerId, "supply relationship preferredManufacturerId");
        productCategories = TradeValidation.copyNonEmptyDistinct(productCategories, "supply relationship productCategories");
        historicalStartYear = TradeValidation.requireYear(historicalStartYear, "supply relationship historicalStartYear");
        historicalEndYear = Objects.requireNonNull(historicalEndYear, "historicalEndYear");
        if (historicalEndYear.isPresent()) {
            int endYear = historicalEndYear.getAsInt();
            TradeValidation.requireYear(endYear, "supply relationship historicalEndYear");
            if (endYear < historicalStartYear) {
                throw new IllegalArgumentException("Supply relationship end year must not be before start year");
            }
        }
        relationshipStrength = Objects.requireNonNull(relationshipStrength, "relationshipStrength");
        territoryId = Objects.requireNonNull(territoryId, "territoryId");
        historicalNotes = TradeValidation.requireNonBlank(historicalNotes, "supply relationship historicalNotes");
    }
}
