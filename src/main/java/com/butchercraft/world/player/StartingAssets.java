package com.butchercraft.world.player;

import java.util.List;

public record StartingAssets(
        String businessReference,
        String commercialPropertyReference,
        List<String> equipmentPlaceholders,
        List<String> supplierRelationshipPlaceholders,
        List<String> financialPlaceholders
) {
    public StartingAssets {
        businessReference = PlayerValidation.requireNonBlank(businessReference, "starting assets businessReference");
        commercialPropertyReference = PlayerValidation.requireNonBlank(commercialPropertyReference, "starting assets commercialPropertyReference");
        equipmentPlaceholders = PlayerValidation.copyNonEmptyDistinct(equipmentPlaceholders, "starting assets equipmentPlaceholders");
        supplierRelationshipPlaceholders = PlayerValidation.copyNonEmptyDistinct(supplierRelationshipPlaceholders, "starting assets supplierRelationshipPlaceholders");
        financialPlaceholders = PlayerValidation.copyNonEmptyDistinct(financialPlaceholders, "starting assets financialPlaceholders");
    }
}
