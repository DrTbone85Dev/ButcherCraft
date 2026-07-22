package com.butchercraft.world.trade;

import java.util.Objects;
import java.util.OptionalInt;

public record SupplyContract(
        SupplyContractId id,
        SupplyRelationshipId relationshipId,
        int startYear,
        OptionalInt endYear,
        RelationshipStrength recordedStrength,
        String termsSummary
) {
    public SupplyContract {
        id = Objects.requireNonNull(id, "id");
        relationshipId = Objects.requireNonNull(relationshipId, "relationshipId");
        startYear = TradeValidation.requireYear(startYear, "supply contract startYear");
        endYear = Objects.requireNonNull(endYear, "endYear");
        if (endYear.isPresent()) {
            int value = endYear.getAsInt();
            TradeValidation.requireYear(value, "supply contract endYear");
            if (value < startYear) {
                throw new IllegalArgumentException("Supply contract end year must not be before start year");
            }
        }
        recordedStrength = Objects.requireNonNull(recordedStrength, "recordedStrength");
        termsSummary = TradeValidation.requireNonBlank(termsSummary, "supply contract termsSummary");
    }
}
