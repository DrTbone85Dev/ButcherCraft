package com.butchercraft.world.trade;

import java.util.List;
import java.util.Objects;

public record SupplyNetwork(
        SupplyNetworkId id,
        String displayName,
        String regionId,
        List<TradeRegion> tradeRegions,
        List<DistributionTerritory> distributionTerritories,
        List<DistributionRoute> distributionRoutes,
        List<SupplyRelationship> supplyRelationships,
        List<SupplyContract> supplyContracts,
        List<PreferredSupplier> preferredSuppliers,
        List<PreferredManufacturer> preferredManufacturers,
        List<BusinessSpecializationProfile> businessSpecializations
) {
    public SupplyNetwork {
        id = Objects.requireNonNull(id, "id");
        displayName = TradeValidation.requireNonBlank(displayName, "supply network displayName");
        regionId = TradeValidation.requireNonBlank(regionId, "supply network regionId");
        tradeRegions = TradeValidation.copyNonEmptyDistinct(tradeRegions, "supply network tradeRegions");
        distributionTerritories = TradeValidation.copyNonEmptyDistinct(distributionTerritories, "supply network distributionTerritories");
        distributionRoutes = TradeValidation.copyDistinct(distributionRoutes, "supply network distributionRoutes");
        supplyRelationships = TradeValidation.copyNonEmptyDistinct(supplyRelationships, "supply network supplyRelationships");
        supplyContracts = TradeValidation.copyNonEmptyDistinct(supplyContracts, "supply network supplyContracts");
        preferredSuppliers = TradeValidation.copyNonEmptyDistinct(preferredSuppliers, "supply network preferredSuppliers");
        preferredManufacturers = TradeValidation.copyNonEmptyDistinct(preferredManufacturers, "supply network preferredManufacturers");
        businessSpecializations = TradeValidation.copyNonEmptyDistinct(businessSpecializations, "supply network businessSpecializations");
    }
}
