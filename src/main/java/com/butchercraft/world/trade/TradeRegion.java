package com.butchercraft.world.trade;

import java.util.List;
import java.util.Objects;

public record TradeRegion(
        TradeRegionId id,
        String displayName,
        String regionId,
        List<String> settlementIds,
        int regionalInfluenceScore,
        String historicalNotes
) {
    public TradeRegion {
        id = Objects.requireNonNull(id, "id");
        displayName = TradeValidation.requireNonBlank(displayName, "trade region displayName");
        regionId = TradeValidation.requireNonBlank(regionId, "trade region regionId");
        settlementIds = TradeValidation.copyNonEmptyDistinct(settlementIds, "trade region settlementIds");
        regionalInfluenceScore = TradeValidation.requireScore(regionalInfluenceScore, "trade region regionalInfluenceScore");
        historicalNotes = TradeValidation.requireNonBlank(historicalNotes, "trade region historicalNotes");
    }
}
