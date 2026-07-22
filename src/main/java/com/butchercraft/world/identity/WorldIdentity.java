package com.butchercraft.world.identity;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessRegistry;
import com.butchercraft.world.business.BuiltInBusinessCatalog;
import com.butchercraft.world.ownership.BuiltInOwnershipCatalog;
import com.butchercraft.world.ownership.Family;
import com.butchercraft.world.ownership.FamilyRegistry;
import com.butchercraft.world.ownership.OwnershipEntity;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.ownership.OwnershipHistory;
import com.butchercraft.world.ownership.OwnershipIdentitySnapshot;
import com.butchercraft.world.ownership.OwnershipRegistry;
import com.butchercraft.world.ownership.PersonIdentity;
import com.butchercraft.world.property.BuiltInCommercialPropertyCatalog;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;
import com.butchercraft.world.trade.BuiltInTradeNetworkCatalog;
import com.butchercraft.world.trade.DistributionRoute;
import com.butchercraft.world.trade.DistributionTerritory;
import com.butchercraft.world.trade.SupplyNetwork;
import com.butchercraft.world.trade.SupplyRelationship;
import com.butchercraft.world.trade.TradeNetworkRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorldIdentity(
        int schemaVersion,
        String id,
        long worldSeed,
        Region region,
        List<County> counties,
        List<CommercialProperty> commercialProperties,
        List<Business> businesses,
        List<Family> families,
        List<PersonIdentity> historicalPersons,
        List<OwnershipEntity> ownershipEntities,
        List<OwnershipHistory> ownershipHistories,
        SupplyNetwork supplyNetwork
) {
    public static final int CURRENT_SCHEMA_VERSION = 6;

    public WorldIdentity(int schemaVersion, String id, long worldSeed, Region region, List<County> counties) {
        this(schemaVersion, id, worldSeed, region, counties, generatedProperties(worldSeed, counties));
    }

    public WorldIdentity(
            int schemaVersion,
            String id,
            long worldSeed,
            Region region,
            List<County> counties,
            List<CommercialProperty> commercialProperties
    ) {
        this(
                schemaVersion,
                id,
                worldSeed,
                region,
                counties,
                commercialProperties,
                BuiltInBusinessCatalog.generate(worldSeed, region, settlementsFrom(counties), commercialProperties)
        );
    }

    public WorldIdentity(
            int schemaVersion,
            String id,
            long worldSeed,
            Region region,
            List<County> counties,
            List<CommercialProperty> commercialProperties,
            List<Business> businesses
    ) {
        this(
                schemaVersion,
                id,
                worldSeed,
                region,
                counties,
                commercialProperties,
                businesses,
                BuiltInOwnershipCatalog.generate(worldSeed, region, settlementsFrom(counties), businesses)
        );
    }

    private WorldIdentity(
            int schemaVersion,
            String id,
            long worldSeed,
            Region region,
            List<County> counties,
            List<CommercialProperty> commercialProperties,
            List<Business> businesses,
            OwnershipIdentitySnapshot ownership
    ) {
        this(
                schemaVersion,
                id,
                worldSeed,
                region,
                counties,
                commercialProperties,
                businesses,
                ownership.families(),
                ownership.historicalPersons(),
                ownership.ownershipEntities(),
                ownership.ownershipHistories()
        );
    }

    public WorldIdentity(
            int schemaVersion,
            String id,
            long worldSeed,
            Region region,
            List<County> counties,
            List<CommercialProperty> commercialProperties,
            List<Business> businesses,
            List<Family> families,
            List<PersonIdentity> historicalPersons,
            List<OwnershipEntity> ownershipEntities,
            List<OwnershipHistory> ownershipHistories
    ) {
        this(
                schemaVersion,
                id,
                worldSeed,
                region,
                counties,
                commercialProperties,
                businesses,
                families,
                historicalPersons,
                ownershipEntities,
                ownershipHistories,
                generatedSupplyNetwork(worldSeed, region, counties, businesses, ownershipHistories)
        );
    }

    public WorldIdentity {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world identity schema version: " + schemaVersion);
        }
        id = requireNonBlank(id, "id");
        region = Objects.requireNonNull(region, "region");
        counties = List.copyOf(Objects.requireNonNull(counties, "counties"));
        if (counties.isEmpty()) {
            throw new IllegalArgumentException("World identity counties must not be empty");
        }
        Set<String> countyIds = new HashSet<>();
        Set<String> settlementIds = new HashSet<>();
        for (County county : counties) {
            Objects.requireNonNull(county, "county");
            if (!region.id().equals(county.regionId())) {
                throw new IllegalArgumentException("County " + county.id() + " belongs to " + county.regionId()
                        + " instead of region " + region.id());
            }
            if (!countyIds.add(county.id())) {
                throw new IllegalArgumentException("Duplicate county id: " + county.id());
            }
            for (Settlement settlement : county.settlements()) {
                if (!settlementIds.add(settlement.id())) {
                    throw new IllegalArgumentException("Duplicate settlement id: " + settlement.id());
                }
            }
        }
        commercialProperties = List.copyOf(Objects.requireNonNull(commercialProperties, "commercialProperties"));
        validateCommercialProperties(commercialProperties, settlementIds);
        businesses = List.copyOf(Objects.requireNonNull(businesses, "businesses"));
        validateBusinesses(businesses, region, settlementsFrom(counties), commercialProperties);
        families = List.copyOf(Objects.requireNonNull(families, "families"));
        historicalPersons = List.copyOf(Objects.requireNonNull(historicalPersons, "historicalPersons"));
        ownershipEntities = List.copyOf(Objects.requireNonNull(ownershipEntities, "ownershipEntities"));
        ownershipHistories = List.copyOf(Objects.requireNonNull(ownershipHistories, "ownershipHistories"));
        validateOwnership(families, historicalPersons, ownershipEntities, ownershipHistories, settlementsFrom(counties), businesses);
        supplyNetwork = Objects.requireNonNull(supplyNetwork, "supplyNetwork");
        validateSupplyNetwork(supplyNetwork, region, settlementsFrom(counties), businesses, ownershipHistories);
    }

    public List<Settlement> settlements() {
        return counties.stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    public List<CommercialProperty> commercialPropertiesForSettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return commercialProperties.stream()
                .filter(property -> property.settlementId().equals(settlementId))
                .toList();
    }

    public List<Business> businessesForSettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return businesses.stream()
                .filter(business -> business.primarySettlementId().equals(settlementId))
                .toList();
    }

    public List<Business> businessesForProperty(CommercialPropertyId propertyId) {
        Objects.requireNonNull(propertyId, "propertyId");
        return businesses.stream()
                .filter(business -> business.occupancyHistory().stream()
                        .anyMatch(occupancy -> occupancy.propertyId().equals(propertyId)))
                .toList();
    }

    public List<OwnershipHistory> ownershipHistoriesForBusiness(String businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return ownershipHistories.stream()
                .filter(history -> history.businessId().value().equals(businessId))
                .toList();
    }

    public List<OwnershipHistory> ownershipHistoriesForEntity(OwnershipEntityId entityId) {
        Objects.requireNonNull(entityId, "entityId");
        return ownershipHistories.stream()
                .filter(history -> history.ownershipRecords().stream()
                        .anyMatch(record -> record.ownershipEntityId().equals(entityId)))
                .toList();
    }

    public List<SupplyRelationship> supplyRelationshipsForBusiness(String businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.supplierBusinessId().value().equals(businessId)
                        || relationship.customerBusinessId().value().equals(businessId))
                .toList();
    }

    public List<DistributionTerritory> tradeTerritoriesForSettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return supplyNetwork.distributionTerritories().stream()
                .filter(territory -> territory.coveredSettlementIds().contains(settlementId))
                .toList();
    }

    public List<DistributionRoute> distributionRoutesForSettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        return supplyNetwork.distributionRoutes().stream()
                .filter(route -> route.originSettlementId().equals(settlementId)
                        || route.destinationSettlementId().equals(settlementId))
                .toList();
    }

    private static List<CommercialProperty> generatedProperties(long worldSeed, List<County> counties) {
        return BuiltInCommercialPropertyCatalog.generate(worldSeed, settlementsFrom(counties));
    }

    private static SupplyNetwork generatedSupplyNetwork(
            long worldSeed,
            Region region,
            List<County> counties,
            List<Business> businesses,
            List<OwnershipHistory> ownershipHistories
    ) {
        return BuiltInTradeNetworkCatalog.generate(worldSeed, region, settlementsFrom(counties), businesses, ownershipHistories);
    }

    private static List<Settlement> settlementsFrom(List<County> counties) {
        return Objects.requireNonNull(counties, "counties").stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    private static void validateCommercialProperties(List<CommercialProperty> properties, Set<String> settlementIds) {
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("World identity commercial properties must not be empty");
        }
        Set<CommercialPropertyId> propertyIds = new HashSet<>();
        Map<String, Set<String>> namesBySettlement = new HashMap<>();
        for (CommercialProperty property : properties) {
            Objects.requireNonNull(property, "commercialProperty");
            if (!settlementIds.contains(property.settlementId())) {
                throw new IllegalArgumentException("Commercial property " + property.id().value()
                        + " references unknown settlement: " + property.settlementId());
            }
            if (!propertyIds.add(property.id())) {
                throw new IllegalArgumentException("Duplicate commercial property id: " + property.id().value());
            }
            Set<String> names = namesBySettlement.computeIfAbsent(property.settlementId(), ignored -> new HashSet<>());
            if (!names.add(property.displayName())) {
                throw new IllegalArgumentException("Duplicate commercial property name in settlement "
                        + property.settlementId() + ": " + property.displayName());
            }
        }
        for (String settlementId : settlementIds) {
            if (!namesBySettlement.containsKey(settlementId)) {
                throw new IllegalArgumentException("Settlement has no commercial properties: " + settlementId);
            }
        }
    }

    private static void validateBusinesses(
            List<Business> businesses,
            Region region,
            List<Settlement> settlements,
            List<CommercialProperty> commercialProperties
    ) {
        BusinessRegistry.of(businesses, region, settlements, commercialProperties);
    }

    private static void validateOwnership(
            List<Family> families,
            List<PersonIdentity> historicalPersons,
            List<OwnershipEntity> ownershipEntities,
            List<OwnershipHistory> ownershipHistories,
            List<Settlement> settlements,
            List<Business> businesses
    ) {
        FamilyRegistry.of(families, historicalPersons, settlements);
        OwnershipRegistry.of(ownershipEntities, ownershipHistories, families, historicalPersons, businesses);
    }

    private static void validateSupplyNetwork(
            SupplyNetwork supplyNetwork,
            Region region,
            List<Settlement> settlements,
            List<Business> businesses,
            List<OwnershipHistory> ownershipHistories
    ) {
        TradeNetworkRegistry.of(supplyNetwork, region, settlements, businesses, ownershipHistories);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("World identity " + fieldName + " must not be blank");
        }
        return value;
    }
}
