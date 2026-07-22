package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;

public record PreferredSupplier(
        BusinessId customerBusinessId,
        BusinessId supplierBusinessId,
        SupplyRelationshipId relationshipId,
        List<ProductCategory> productCategories,
        RelationshipStrength relationshipStrength,
        String notes
) {
    public PreferredSupplier {
        customerBusinessId = Objects.requireNonNull(customerBusinessId, "customerBusinessId");
        supplierBusinessId = Objects.requireNonNull(supplierBusinessId, "supplierBusinessId");
        if (customerBusinessId.equals(supplierBusinessId)) {
            throw new IllegalArgumentException("Preferred supplier must not reference itself");
        }
        relationshipId = Objects.requireNonNull(relationshipId, "relationshipId");
        productCategories = TradeValidation.copyNonEmptyDistinct(productCategories, "preferred supplier productCategories");
        relationshipStrength = Objects.requireNonNull(relationshipStrength, "relationshipStrength");
        notes = TradeValidation.requireNonBlank(notes, "preferred supplier notes");
    }
}
