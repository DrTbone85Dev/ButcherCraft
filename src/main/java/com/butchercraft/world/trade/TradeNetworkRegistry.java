package com.butchercraft.world.trade;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.manufacturer.ManufacturerRegistry;
import com.butchercraft.world.ownership.OwnershipHistory;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TradeNetworkRegistry {
    private final SupplyNetwork supplyNetwork;
    private final Map<SupplyRelationshipId, SupplyRelationship> relationshipsById;
    private final Map<DistributionTerritoryId, DistributionTerritory> territoriesById;
    private final Map<DistributionRouteId, DistributionRoute> routesById;
    private final Map<TradeRegionId, TradeRegion> tradeRegionsById;
    private final Map<SupplyContractId, SupplyContract> contractsById;
    private final Map<BusinessId, Business> businessesById;
    private final Map<String, Settlement> settlementsById;

    private TradeNetworkRegistry(
            SupplyNetwork supplyNetwork,
            Map<SupplyRelationshipId, SupplyRelationship> relationshipsById,
            Map<DistributionTerritoryId, DistributionTerritory> territoriesById,
            Map<DistributionRouteId, DistributionRoute> routesById,
            Map<TradeRegionId, TradeRegion> tradeRegionsById,
            Map<SupplyContractId, SupplyContract> contractsById,
            Map<BusinessId, Business> businessesById,
            Map<String, Settlement> settlementsById
    ) {
        this.supplyNetwork = supplyNetwork;
        this.relationshipsById = relationshipsById;
        this.territoriesById = territoriesById;
        this.routesById = routesById;
        this.tradeRegionsById = tradeRegionsById;
        this.contractsById = contractsById;
        this.businessesById = businessesById;
        this.settlementsById = settlementsById;
    }

    public static TradeNetworkRegistry of(
            SupplyNetwork supplyNetwork,
            Region region,
            Collection<Settlement> settlements,
            Collection<Business> businesses,
            Collection<OwnershipHistory> ownershipHistories
    ) {
        return of(supplyNetwork, region, settlements, businesses, ownershipHistories, ManufacturerRegistry.builtIn());
    }

    public static TradeNetworkRegistry of(
            SupplyNetwork supplyNetwork,
            Region region,
            Collection<Settlement> settlements,
            Collection<Business> businesses,
            Collection<OwnershipHistory> ownershipHistories,
            ManufacturerRegistry manufacturers
    ) {
        Objects.requireNonNull(supplyNetwork, "supplyNetwork");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(settlements, "settlements");
        Objects.requireNonNull(businesses, "businesses");
        Objects.requireNonNull(ownershipHistories, "ownershipHistories");
        Objects.requireNonNull(manufacturers, "manufacturers");
        if (!supplyNetwork.regionId().equals(region.id())) {
            throw new IllegalArgumentException("Supply network references unknown region: " + supplyNetwork.regionId());
        }
        Map<String, Settlement> settlementsById = settlements.stream()
                .collect(Collectors.toUnmodifiableMap(Settlement::id, Function.identity()));
        if (settlementsById.isEmpty()) {
            throw new IllegalArgumentException("Trade network registry requires at least one settlement");
        }
        Map<BusinessId, Business> businessesById = businesses.stream()
                .collect(Collectors.toUnmodifiableMap(Business::id, Function.identity()));
        if (businessesById.isEmpty()) {
            throw new IllegalArgumentException("Trade network registry requires at least one business");
        }
        Set<BusinessId> ownedBusinessIds = ownershipHistories.stream()
                .map(OwnershipHistory::businessId)
                .collect(Collectors.toUnmodifiableSet());
        if (!ownedBusinessIds.containsAll(businessesById.keySet())) {
            throw new IllegalArgumentException("Trade network requires ownership histories for all businesses");
        }

        List<TradeRegion> tradeRegions = supplyNetwork.tradeRegions().stream()
                .map(tradeRegion -> validateTradeRegion(tradeRegion, region, settlementsById.keySet()))
                .sorted(Comparator.comparing(tradeRegion -> tradeRegion.id().value()))
                .toList();
        rejectDuplicates(tradeRegions, TradeRegion::id, "trade region id");
        Map<TradeRegionId, TradeRegion> tradeRegionsById = tradeRegions.stream()
                .collect(Collectors.toUnmodifiableMap(TradeRegion::id, Function.identity()));

        List<DistributionTerritory> territories = supplyNetwork.distributionTerritories().stream()
                .map(territory -> validateTerritory(territory, tradeRegionsById.keySet(), settlementsById.keySet(), businessesById, manufacturers))
                .sorted(Comparator.comparing(territory -> territory.id().value()))
                .toList();
        rejectDuplicates(territories, DistributionTerritory::id, "distribution territory id");
        Map<DistributionTerritoryId, DistributionTerritory> territoriesById = territories.stream()
                .collect(Collectors.toUnmodifiableMap(DistributionTerritory::id, Function.identity()));

        List<DistributionRoute> routes = supplyNetwork.distributionRoutes().stream()
                .map(route -> validateRoute(route, settlementsById.keySet(), businessesById))
                .sorted(Comparator.comparing(route -> route.id().value()))
                .toList();
        rejectDuplicates(routes, DistributionRoute::id, "distribution route id");
        Map<DistributionRouteId, DistributionRoute> routesById = routes.stream()
                .collect(Collectors.toUnmodifiableMap(DistributionRoute::id, Function.identity()));

        List<SupplyRelationship> relationships = supplyNetwork.supplyRelationships().stream()
                .map(relationship -> validateRelationship(relationship, businessesById, territoriesById.keySet(), manufacturers))
                .sorted(Comparator.comparing(relationship -> relationship.id().value()))
                .toList();
        rejectDuplicates(relationships, SupplyRelationship::id, "supply relationship id");
        rejectDuplicateBusinessRelationships(relationships);
        Map<SupplyRelationshipId, SupplyRelationship> relationshipsById = relationships.stream()
                .collect(Collectors.toUnmodifiableMap(SupplyRelationship::id, Function.identity()));

        List<SupplyContract> contracts = supplyNetwork.supplyContracts().stream()
                .map(contract -> validateContract(contract, relationshipsById))
                .sorted(Comparator.comparing(contract -> contract.id().value()))
                .toList();
        rejectDuplicates(contracts, SupplyContract::id, "supply contract id");
        Map<SupplyContractId, SupplyContract> contractsById = contracts.stream()
                .collect(Collectors.toUnmodifiableMap(SupplyContract::id, Function.identity()));

        validatePreferredSuppliers(supplyNetwork.preferredSuppliers(), relationshipsById, businessesById);
        validatePreferredManufacturers(supplyNetwork.preferredManufacturers(), businessesById.keySet(), manufacturers);
        validateBusinessSpecializations(supplyNetwork.businessSpecializations(), businessesById.keySet());
        rejectOrphanedRecords(supplyNetwork, relationships, contracts, territories, tradeRegions, businessesById.keySet());

        SupplyNetwork deterministicNetwork = new SupplyNetwork(
                supplyNetwork.id(),
                supplyNetwork.displayName(),
                supplyNetwork.regionId(),
                tradeRegions,
                territories,
                routes,
                relationships,
                contracts,
                sortedPreferredSuppliers(supplyNetwork.preferredSuppliers()),
                sortedPreferredManufacturers(supplyNetwork.preferredManufacturers()),
                sortedBusinessSpecializations(supplyNetwork.businessSpecializations())
        );
        return new TradeNetworkRegistry(
                deterministicNetwork,
                relationshipsById,
                territoriesById,
                routesById,
                tradeRegionsById,
                contractsById,
                businessesById,
                settlementsById
        );
    }

    public SupplyNetwork supplyNetwork() {
        return supplyNetwork;
    }

    public boolean contains(SupplyRelationshipId id) {
        return relationshipsById.containsKey(id);
    }

    public Optional<SupplyRelationship> find(SupplyRelationshipId id) {
        return Optional.ofNullable(relationshipsById.get(id));
    }

    public Optional<SupplyRelationship> find(String id) {
        return find(new SupplyRelationshipId(id));
    }

    public Optional<DistributionTerritory> findTerritory(DistributionTerritoryId id) {
        return Optional.ofNullable(territoriesById.get(id));
    }

    public Optional<DistributionRoute> findRoute(DistributionRouteId id) {
        return Optional.ofNullable(routesById.get(id));
    }

    public Optional<TradeRegion> findTradeRegion(TradeRegionId id) {
        return Optional.ofNullable(tradeRegionsById.get(id));
    }

    public Optional<SupplyContract> findContract(SupplyContractId id) {
        return Optional.ofNullable(contractsById.get(id));
    }

    public int relationshipCount() {
        return supplyNetwork.supplyRelationships().size();
    }

    public int territoryCount() {
        return supplyNetwork.distributionTerritories().size();
    }

    public int routeCount() {
        return supplyNetwork.distributionRoutes().size();
    }

    public Stream<SupplyRelationship> stream() {
        return supplyNetwork.supplyRelationships().stream();
    }

    public List<SupplyRelationship> findByBusiness(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.supplierBusinessId().equals(businessId)
                        || relationship.customerBusinessId().equals(businessId))
                .toList();
    }

    public List<SupplyRelationship> findByManufacturer(String manufacturerId) {
        Objects.requireNonNull(manufacturerId, "manufacturerId");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.preferredManufacturerId().equals(manufacturerId))
                .toList();
    }

    public List<SupplyRelationship> findBySettlement(String settlementId) {
        Objects.requireNonNull(settlementId, "settlementId");
        if (!settlementsById.containsKey(settlementId)) {
            return List.of();
        }
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> settlementId.equals(businessesById.get(relationship.supplierBusinessId()).primarySettlementId())
                        || settlementId.equals(businessesById.get(relationship.customerBusinessId()).primarySettlementId()))
                .toList();
    }

    public List<SupplyRelationship> findByProductCategory(ProductCategory productCategory) {
        Objects.requireNonNull(productCategory, "productCategory");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.productCategories().contains(productCategory))
                .toList();
    }

    public List<SupplyRelationship> findByTerritory(DistributionTerritoryId territoryId) {
        Objects.requireNonNull(territoryId, "territoryId");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.territoryId().equals(territoryId))
                .toList();
    }

    public List<SupplyRelationship> findByRelationshipType(SupplyRelationshipType relationshipType) {
        Objects.requireNonNull(relationshipType, "relationshipType");
        return supplyNetwork.supplyRelationships().stream()
                .filter(relationship -> relationship.relationshipType() == relationshipType)
                .toList();
    }

    public List<PreferredSupplier> findPreferredSuppliers(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return supplyNetwork.preferredSuppliers().stream()
                .filter(preferredSupplier -> preferredSupplier.customerBusinessId().equals(businessId))
                .toList();
    }

    public List<PreferredManufacturer> findPreferredManufacturers(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return supplyNetwork.preferredManufacturers().stream()
                .filter(preferredManufacturer -> preferredManufacturer.businessId().equals(businessId))
                .toList();
    }

    public List<BusinessSpecialization> findBusinessSpecializations(BusinessId businessId) {
        Objects.requireNonNull(businessId, "businessId");
        return supplyNetwork.businessSpecializations().stream()
                .filter(profile -> profile.businessId().equals(businessId))
                .findFirst()
                .map(BusinessSpecializationProfile::specializations)
                .orElse(List.of());
    }

    private static TradeRegion validateTradeRegion(
            TradeRegion tradeRegion,
            Region region,
            Set<String> settlementIds
    ) {
        Objects.requireNonNull(tradeRegion, "tradeRegion");
        if (!tradeRegion.regionId().equals(region.id())) {
            throw new IllegalArgumentException("Trade region " + tradeRegion.id().value()
                    + " references unknown region: " + tradeRegion.regionId());
        }
        for (String settlementId : tradeRegion.settlementIds()) {
            if (!settlementIds.contains(settlementId)) {
                throw new IllegalArgumentException("Trade region " + tradeRegion.id().value()
                        + " references unknown settlement: " + settlementId);
            }
        }
        return tradeRegion;
    }

    private static DistributionTerritory validateTerritory(
            DistributionTerritory territory,
            Set<TradeRegionId> tradeRegionIds,
            Set<String> settlementIds,
            Map<BusinessId, Business> businessesById,
            ManufacturerRegistry manufacturers
    ) {
        Objects.requireNonNull(territory, "territory");
        if (!tradeRegionIds.contains(territory.tradeRegionId())) {
            throw new IllegalArgumentException("Distribution territory " + territory.id().value()
                    + " references unknown trade region: " + territory.tradeRegionId().value());
        }
        for (String settlementId : territory.coveredSettlementIds()) {
            if (!settlementIds.contains(settlementId)) {
                throw new IllegalArgumentException("Distribution territory " + territory.id().value()
                        + " references unknown settlement: " + settlementId);
            }
        }
        for (BusinessId businessId : territory.primaryBusinessIds()) {
            Business business = businessesById.get(businessId);
            if (business == null) {
                throw new IllegalArgumentException("Distribution territory " + territory.id().value()
                        + " references unknown primary business: " + businessId.value());
            }
            if (!territory.coveredSettlementIds().contains(business.primarySettlementId())) {
                throw new IllegalArgumentException("Distribution territory " + territory.id().value()
                        + " primary business is outside the covered settlements: " + businessId.value());
            }
        }
        for (String manufacturerId : territory.dominantManufacturerIds()) {
            if (!manufacturers.contains(manufacturerId)) {
                throw new IllegalArgumentException("Distribution territory " + territory.id().value()
                        + " references unknown dominant manufacturer: " + manufacturerId);
            }
        }
        return territory;
    }

    private static DistributionRoute validateRoute(
            DistributionRoute route,
            Set<String> settlementIds,
            Map<BusinessId, Business> businessesById
    ) {
        Objects.requireNonNull(route, "route");
        if (!settlementIds.contains(route.originSettlementId())) {
            throw new IllegalArgumentException("Distribution route " + route.id().value()
                    + " references unknown origin settlement: " + route.originSettlementId());
        }
        if (!settlementIds.contains(route.destinationSettlementId())) {
            throw new IllegalArgumentException("Distribution route " + route.id().value()
                    + " references unknown destination settlement: " + route.destinationSettlementId());
        }
        for (BusinessId businessId : route.primaryBusinessIds()) {
            Business business = businessesById.get(businessId);
            if (business == null) {
                throw new IllegalArgumentException("Distribution route " + route.id().value()
                        + " references unknown primary business: " + businessId.value());
            }
            if (!business.primarySettlementId().equals(route.originSettlementId())
                    && !business.primarySettlementId().equals(route.destinationSettlementId())) {
                throw new IllegalArgumentException("Distribution route " + route.id().value()
                        + " primary business is outside the route endpoints: " + businessId.value());
            }
        }
        return route;
    }

    private static SupplyRelationship validateRelationship(
            SupplyRelationship relationship,
            Map<BusinessId, Business> businessesById,
            Set<DistributionTerritoryId> territoryIds,
            ManufacturerRegistry manufacturers
    ) {
        Objects.requireNonNull(relationship, "relationship");
        Business supplier = businessesById.get(relationship.supplierBusinessId());
        if (supplier == null) {
            throw new IllegalArgumentException("Supply relationship " + relationship.id().value()
                    + " references unknown supplier business: " + relationship.supplierBusinessId().value());
        }
        Business customer = businessesById.get(relationship.customerBusinessId());
        if (customer == null) {
            throw new IllegalArgumentException("Supply relationship " + relationship.id().value()
                    + " references unknown customer business: " + relationship.customerBusinessId().value());
        }
        if (!manufacturers.contains(relationship.preferredManufacturerId())) {
            throw new IllegalArgumentException("Supply relationship " + relationship.id().value()
                    + " references unknown preferred manufacturer: " + relationship.preferredManufacturerId());
        }
        if (!territoryIds.contains(relationship.territoryId())) {
            throw new IllegalArgumentException("Supply relationship " + relationship.id().value()
                    + " references unknown distribution territory: " + relationship.territoryId().value());
        }
        int earliestStart = Math.max(supplier.foundingYear(), customer.foundingYear());
        if (relationship.historicalStartYear() < earliestStart) {
            throw new IllegalArgumentException("Supply relationship " + relationship.id().value()
                    + " starts before both businesses existed");
        }
        return relationship;
    }

    private static SupplyContract validateContract(
            SupplyContract contract,
            Map<SupplyRelationshipId, SupplyRelationship> relationshipsById
    ) {
        Objects.requireNonNull(contract, "contract");
        SupplyRelationship relationship = relationshipsById.get(contract.relationshipId());
        if (relationship == null) {
            throw new IllegalArgumentException("Supply contract " + contract.id().value()
                    + " references unknown supply relationship: " + contract.relationshipId().value());
        }
        if (contract.startYear() < relationship.historicalStartYear()) {
            throw new IllegalArgumentException("Supply contract " + contract.id().value()
                    + " starts before its supply relationship");
        }
        OptionalInt relationshipEndYear = relationship.historicalEndYear();
        if (relationshipEndYear.isPresent()
                && (contract.endYear().isEmpty() || contract.endYear().getAsInt() > relationshipEndYear.getAsInt())) {
            throw new IllegalArgumentException("Supply contract " + contract.id().value()
                    + " extends beyond its supply relationship");
        }
        return contract;
    }

    private static void validatePreferredSuppliers(
            List<PreferredSupplier> preferredSuppliers,
            Map<SupplyRelationshipId, SupplyRelationship> relationshipsById,
            Map<BusinessId, Business> businessesById
    ) {
        rejectDuplicates(preferredSuppliers, PreferredSupplier::relationshipId, "preferred supplier relationship id");
        for (PreferredSupplier preferredSupplier : preferredSuppliers) {
            if (!businessesById.containsKey(preferredSupplier.customerBusinessId())) {
                throw new IllegalArgumentException("Preferred supplier references unknown customer business: "
                        + preferredSupplier.customerBusinessId().value());
            }
            if (!businessesById.containsKey(preferredSupplier.supplierBusinessId())) {
                throw new IllegalArgumentException("Preferred supplier references unknown supplier business: "
                        + preferredSupplier.supplierBusinessId().value());
            }
            SupplyRelationship relationship = relationshipsById.get(preferredSupplier.relationshipId());
            if (relationship == null) {
                throw new IllegalArgumentException("Preferred supplier references unknown relationship: "
                        + preferredSupplier.relationshipId().value());
            }
            if (!relationship.customerBusinessId().equals(preferredSupplier.customerBusinessId())
                    || !relationship.supplierBusinessId().equals(preferredSupplier.supplierBusinessId())) {
                throw new IllegalArgumentException("Preferred supplier does not match its supply relationship: "
                        + preferredSupplier.relationshipId().value());
            }
            if (!relationship.productCategories().containsAll(preferredSupplier.productCategories())) {
                throw new IllegalArgumentException("Preferred supplier product categories are not covered by relationship: "
                        + preferredSupplier.relationshipId().value());
            }
        }
    }

    private static void validatePreferredManufacturers(
            List<PreferredManufacturer> preferredManufacturers,
            Set<BusinessId> businessIds,
            ManufacturerRegistry manufacturers
    ) {
        rejectDuplicates(preferredManufacturers, preferredManufacturer ->
                preferredManufacturer.businessId().value() + ":" + preferredManufacturer.manufacturerId(), "preferred manufacturer");
        for (PreferredManufacturer preferredManufacturer : preferredManufacturers) {
            if (!businessIds.contains(preferredManufacturer.businessId())) {
                throw new IllegalArgumentException("Preferred manufacturer references unknown business: "
                        + preferredManufacturer.businessId().value());
            }
            if (!manufacturers.contains(preferredManufacturer.manufacturerId())) {
                throw new IllegalArgumentException("Preferred manufacturer references unknown manufacturer: "
                        + preferredManufacturer.manufacturerId());
            }
        }
    }

    private static void validateBusinessSpecializations(
            List<BusinessSpecializationProfile> profiles,
            Set<BusinessId> businessIds
    ) {
        rejectDuplicates(profiles, BusinessSpecializationProfile::businessId, "business specialization business id");
        for (BusinessSpecializationProfile profile : profiles) {
            if (!businessIds.contains(profile.businessId())) {
                throw new IllegalArgumentException("Business specialization references unknown business: "
                        + profile.businessId().value());
            }
        }
    }

    private static void rejectOrphanedRecords(
            SupplyNetwork supplyNetwork,
            List<SupplyRelationship> relationships,
            List<SupplyContract> contracts,
            List<DistributionTerritory> territories,
            List<TradeRegion> tradeRegions,
            Set<BusinessId> businessIds
    ) {
        Set<SupplyRelationshipId> contractedRelationshipIds = contracts.stream()
                .map(SupplyContract::relationshipId)
                .collect(Collectors.toUnmodifiableSet());
        for (SupplyRelationship relationship : relationships) {
            if (!contractedRelationshipIds.contains(relationship.id())) {
                throw new IllegalArgumentException("Supply relationship has no archival contract: " + relationship.id().value());
            }
        }
        Set<SupplyRelationshipId> preferredSupplierRelationshipIds = supplyNetwork.preferredSuppliers().stream()
                .map(PreferredSupplier::relationshipId)
                .collect(Collectors.toUnmodifiableSet());
        for (SupplyRelationship relationship : relationships) {
            if (!preferredSupplierRelationshipIds.contains(relationship.id())) {
                throw new IllegalArgumentException("Supply relationship has no preferred supplier record: "
                        + relationship.id().value());
            }
        }

        Set<BusinessId> customerIds = relationships.stream()
                .map(SupplyRelationship::customerBusinessId)
                .collect(Collectors.toUnmodifiableSet());
        Set<BusinessId> businessesWithoutSupplier = businessIds.stream()
                .filter(id -> !customerIds.contains(id))
                .collect(Collectors.toSet());
        if (!businessesWithoutSupplier.isEmpty()) {
            throw new IllegalArgumentException("Businesses without supplier relationships: " + businessesWithoutSupplier);
        }

        Set<DistributionTerritoryId> usedTerritoryIds = relationships.stream()
                .map(SupplyRelationship::territoryId)
                .collect(Collectors.toUnmodifiableSet());
        Set<DistributionTerritoryId> orphanedTerritories = territories.stream()
                .map(DistributionTerritory::id)
                .filter(id -> !usedTerritoryIds.contains(id))
                .collect(Collectors.toSet());
        if (!orphanedTerritories.isEmpty()) {
            throw new IllegalArgumentException("Orphaned distribution territories: " + orphanedTerritories);
        }

        Set<TradeRegionId> usedTradeRegions = territories.stream()
                .map(DistributionTerritory::tradeRegionId)
                .collect(Collectors.toUnmodifiableSet());
        Set<TradeRegionId> orphanedTradeRegions = tradeRegions.stream()
                .map(TradeRegion::id)
                .filter(id -> !usedTradeRegions.contains(id))
                .collect(Collectors.toSet());
        if (!orphanedTradeRegions.isEmpty()) {
            throw new IllegalArgumentException("Orphaned trade regions: " + orphanedTradeRegions);
        }

        Set<BusinessId> specializedBusinessIds = supplyNetwork.businessSpecializations().stream()
                .map(BusinessSpecializationProfile::businessId)
                .collect(Collectors.toUnmodifiableSet());
        Set<BusinessId> businessesWithoutSpecialization = businessIds.stream()
                .filter(id -> !specializedBusinessIds.contains(id))
                .collect(Collectors.toSet());
        if (!businessesWithoutSpecialization.isEmpty()) {
            throw new IllegalArgumentException("Businesses without trade specializations: " + businessesWithoutSpecialization);
        }
    }

    private static void rejectDuplicateBusinessRelationships(List<SupplyRelationship> relationships) {
        Map<String, Long> counts = new HashMap<>();
        for (SupplyRelationship relationship : relationships) {
            String key = relationship.supplierBusinessId().value()
                    + "->" + relationship.customerBusinessId().value()
                    + ":" + relationship.relationshipType().serializedName();
            counts.merge(key, 1L, Long::sum);
        }
        Set<String> duplicates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1L)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate supply relationships: " + duplicates);
        }
    }

    private static List<PreferredSupplier> sortedPreferredSuppliers(List<PreferredSupplier> preferredSuppliers) {
        return preferredSuppliers.stream()
                .sorted(Comparator.comparing((PreferredSupplier supplier) -> supplier.customerBusinessId().value())
                        .thenComparing(supplier -> supplier.supplierBusinessId().value())
                        .thenComparing(supplier -> supplier.relationshipId().value()))
                .toList();
    }

    private static List<PreferredManufacturer> sortedPreferredManufacturers(List<PreferredManufacturer> preferredManufacturers) {
        return preferredManufacturers.stream()
                .sorted(Comparator.comparing((PreferredManufacturer manufacturer) -> manufacturer.businessId().value())
                        .thenComparing(PreferredManufacturer::manufacturerId))
                .toList();
    }

    private static List<BusinessSpecializationProfile> sortedBusinessSpecializations(List<BusinessSpecializationProfile> profiles) {
        return profiles.stream()
                .sorted(Comparator.comparing(profile -> profile.businessId().value()))
                .toList();
    }

    private static <T, K> void rejectDuplicates(List<T> values, Function<T, K> keyFunction, String label) {
        Set<K> duplicates = values.stream()
                .map(keyFunction)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1L)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate " + label + ": " + duplicates);
        }
    }
}
