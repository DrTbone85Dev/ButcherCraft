package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;

public record PreferredManufacturer(
        BusinessId businessId,
        String manufacturerId,
        List<ProductCategory> productCategories,
        String notes
) {
    public PreferredManufacturer {
        businessId = Objects.requireNonNull(businessId, "businessId");
        manufacturerId = TradeValidation.requireNonBlank(manufacturerId, "preferred manufacturer manufacturerId");
        productCategories = TradeValidation.copyNonEmptyDistinct(productCategories, "preferred manufacturer productCategories");
        notes = TradeValidation.requireNonBlank(notes, "preferred manufacturer notes");
    }
}
