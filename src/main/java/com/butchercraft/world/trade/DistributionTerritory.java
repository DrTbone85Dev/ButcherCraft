package com.butchercraft.world.trade;

import com.butchercraft.world.business.BusinessId;

import java.util.List;
import java.util.Objects;

public record DistributionTerritory(
        DistributionTerritoryId id,
        String displayName,
        TradeRegionId tradeRegionId,
        List<String> coveredSettlementIds,
        List<BusinessId> primaryBusinessIds,
        List<String> dominantManufacturerIds,
        int distributionImportance,
        int regionalInfluenceScore,
        String historicalNotes
) {
    public DistributionTerritory {
        id = Objects.requireNonNull(id, "id");
        displayName = TradeValidation.requireNonBlank(displayName, "distribution territory displayName");
        tradeRegionId = Objects.requireNonNull(tradeRegionId, "tradeRegionId");
        coveredSettlementIds = TradeValidation.copyNonEmptyDistinct(coveredSettlementIds, "distribution territory coveredSettlementIds");
        primaryBusinessIds = TradeValidation.copyNonEmptyDistinct(primaryBusinessIds, "distribution territory primaryBusinessIds");
        dominantManufacturerIds = TradeValidation.copyNonEmptyDistinct(dominantManufacturerIds, "distribution territory dominantManufacturerIds");
        distributionImportance = TradeValidation.requireScore(distributionImportance, "distribution territory distributionImportance");
        regionalInfluenceScore = TradeValidation.requireScore(regionalInfluenceScore, "distribution territory regionalInfluenceScore");
        historicalNotes = TradeValidation.requireNonBlank(historicalNotes, "distribution territory historicalNotes");
    }
}
