package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;

public record BusinessSpecializationProfile(
        BusinessId businessId,
        List<BusinessSpecialization> specializations,
        String notes
) {
    public BusinessSpecializationProfile {
        businessId = Objects.requireNonNull(businessId, "businessId");
        specializations = TradeValidation.copyNonEmptyDistinct(specializations, "business specialization profile specializations");
        notes = TradeValidation.requireNonBlank(notes, "business specialization profile notes");
    }
}
