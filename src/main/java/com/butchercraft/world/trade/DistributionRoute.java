package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;

public record DistributionRoute(
        DistributionRouteId id,
        String displayName,
        String originSettlementId,
        String destinationSettlementId,
        List<BusinessId> primaryBusinessIds,
        List<ProductCategory> productCategories,
        int routeImportance,
        String historicalNotes
) {
    public DistributionRoute {
        id = Objects.requireNonNull(id, "id");
        displayName = TradeValidation.requireNonBlank(displayName, "distribution route displayName");
        originSettlementId = TradeValidation.requireNonBlank(originSettlementId, "distribution route originSettlementId");
        destinationSettlementId = TradeValidation.requireNonBlank(destinationSettlementId, "distribution route destinationSettlementId");
        if (originSettlementId.equals(destinationSettlementId)) {
            throw new IllegalArgumentException("Distribution route must connect different settlements");
        }
        primaryBusinessIds = TradeValidation.copyNonEmptyDistinct(primaryBusinessIds, "distribution route primaryBusinessIds");
        productCategories = TradeValidation.copyNonEmptyDistinct(productCategories, "distribution route productCategories");
        routeImportance = TradeValidation.requireScore(routeImportance, "distribution route routeImportance");
        historicalNotes = TradeValidation.requireNonBlank(historicalNotes, "distribution route historicalNotes");
    }
}
