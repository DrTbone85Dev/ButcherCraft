package com.butchercraft.world.trade;

import com.butchercraft.world.business.Business;
import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.BusinessReputation;
import com.butchercraft.world.business.BusinessType;
import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.SettlementType;
import com.butchercraft.world.identity.WorldIdentityDeterminism;
import com.butchercraft.world.manufacturer.Manufacturer;
import com.butchercraft.world.manufacturer.ManufacturerCategory;
import com.butchercraft.world.manufacturer.ManufacturerRegistry;
import com.butchercraft.world.ownership.OwnershipHistory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

public final class BuiltInTradeNetworkCatalog {
    private static final long RELATIONSHIP_TYPE_SALT = 0x7a919c08d2be4f33L;
    private static final long SUPPLIER_SALT = 0x423fe884d70b16d5L;
    private static final long STRENGTH_SALT = 0x185d972c4e087a1bL;
    private static final long YEAR_SALT = 0x50b2f0e27944519dL;
    private static final long IMPORTANCE_SALT = 0x31f149f4aa49b7c9L;
    private static final long MANUFACTURER_SALT = 0x69c49b72de3c8a15L;

    private BuiltInTradeNetworkCatalog() {
    }

    public static SupplyNetwork generate(
            long worldSeed,
            Region region,
            List<Settlement> settlements,
            List<Business> businesses,
            List<OwnershipHistory> ownershipHistories
    ) {
        return generate(worldSeed, region, settlements, businesses, ownershipHistories, ManufacturerRegistry.builtIn());
    }

    public static SupplyNetwork generate(
            long worldSeed,
            Region region,
            List<Settlement> settlements,
            List<Business> businesses,
            List<OwnershipHistory> ownershipHistories,
            ManufacturerRegistry manufacturers
    ) {
        List<Settlement> sortedSettlements = settlements.stream()
                .sorted(Comparator.comparing(Settlement::id))
                .toList();
        List<Business> sortedBusinesses = businesses.stream()
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList();
        TradeRegionId tradeRegionId = new TradeRegionId(region.id() + "_trade_region");
        TradeRegion tradeRegion = new TradeRegion(
                tradeRegionId,
                region.displayName() + " Trade Region",
                region.id(),
                sortedSettlements.stream().map(Settlement::id).toList(),
                influenceScore(worldSeed, region.id(), "trade_region"),
                "Historical trade region record for future supplier, purchasing, transportation, and market systems."
        );
        Map<String, List<Business>> businessesBySettlement = sortedBusinesses.stream()
                .collect(Collectors.groupingBy(
                        Business::primarySettlementId,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(business -> business.id().value()))
                                .toList())
                ));
        List<DistributionTerritory> territories = sortedSettlements.stream()
                .map(settlement -> territory(worldSeed, tradeRegionId, settlement, businessesBySettlement, manufacturers))
                .toList();
        List<DistributionRoute> routes = routes(worldSeed, sortedSettlements, businessesBySettlement);
        List<SupplyRelationship> relationships = sortedBusinesses.stream()
                .map(business -> relationship(worldSeed, business, sortedBusinesses, manufacturers))
                .sorted(Comparator.comparing(relationship -> relationship.id().value()))
                .toList();
        List<SupplyContract> contracts = relationships.stream()
                .map(BuiltInTradeNetworkCatalog::contract)
                .toList();
        List<PreferredSupplier> preferredSuppliers = relationships.stream()
                .map(BuiltInTradeNetworkCatalog::preferredSupplier)
                .toList();
        List<PreferredManufacturer> preferredManufacturers = sortedBusinesses.stream()
                .map(business -> preferredManufacturer(worldSeed, business, manufacturers))
                .toList();
        List<BusinessSpecializationProfile> specializations = sortedBusinesses.stream()
                .map(BuiltInTradeNetworkCatalog::specializationProfile)
                .toList();

        SupplyNetwork supplyNetwork = new SupplyNetwork(
                new SupplyNetworkId(region.id() + "_supply_network"),
                region.displayName() + " Supply Network",
                region.id(),
                List.of(tradeRegion),
                territories,
                routes,
                relationships,
                contracts,
                preferredSuppliers,
                preferredManufacturers,
                specializations
        );
        return TradeNetworkRegistry.of(supplyNetwork, region, settlements, businesses, ownershipHistories, manufacturers).supplyNetwork();
    }

    private static DistributionTerritory territory(
            long worldSeed,
            TradeRegionId tradeRegionId,
            Settlement settlement,
            Map<String, List<Business>> businessesBySettlement,
            ManufacturerRegistry manufacturers
    ) {
        List<Business> localBusinesses = businessesBySettlement.getOrDefault(settlement.id(), List.of());
        List<BusinessId> primaryBusinesses = localBusinesses.stream()
                .map(Business::id)
                .toList();
        List<String> dominantManufacturers = dominantManufacturers(worldSeed, settlement, localBusinesses, manufacturers);
        return new DistributionTerritory(
                new DistributionTerritoryId(settlement.id() + "_trade_territory"),
                settlement.displayName() + " Trade Territory",
                tradeRegionId,
                List.of(settlement.id()),
                primaryBusinesses,
                dominantManufacturers,
                distributionImportance(worldSeed, settlement),
                influenceScore(worldSeed, settlement.id(), "territory"),
                "Historical operating territory for businesses in " + settlement.displayName()
                        + "; no route timing, pricing, or inventory behavior is attached."
        );
    }

    private static List<DistributionRoute> routes(
            long worldSeed,
            List<Settlement> settlements,
            Map<String, List<Business>> businessesBySettlement
    ) {
        Settlement hub = settlements.stream()
                .filter(settlement -> settlement.type() == SettlementType.REGIONAL_CITY)
                .findFirst()
                .orElseGet(() -> settlements.getLast());
        List<DistributionRoute> routes = new ArrayList<>();
        for (Settlement settlement : settlements) {
            if (settlement.id().equals(hub.id())) {
                continue;
            }
            List<BusinessId> primaryBusinesses = primaryBusinessesForRoute(
                    businessesBySettlement.getOrDefault(settlement.id(), List.of()),
                    businessesBySettlement.getOrDefault(hub.id(), List.of())
            );
            routes.add(new DistributionRoute(
                    new DistributionRouteId(settlement.id() + "_to_" + hub.id() + "_route"),
                    settlement.displayName() + " to " + hub.displayName() + " Commercial Route",
                    settlement.id(),
                    hub.id(),
                    primaryBusinesses,
                    productCategoriesForRoute(settlement, primaryBusinesses),
                    routeImportance(worldSeed, settlement, hub),
                    "Historical commercial route connecting " + settlement.displayName() + " with "
                            + hub.displayName() + "; no transportation simulation is attached."
            ));
        }
        return routes.stream()
                .sorted(Comparator.comparing(route -> route.id().value()))
                .toList();
    }

    private static SupplyRelationship relationship(
            long worldSeed,
            Business customer,
            List<Business> businesses,
            ManufacturerRegistry manufacturers
    ) {
        SupplyRelationshipType relationshipType = relationshipType(worldSeed, customer);
        Business supplier = supplier(worldSeed, customer, relationshipType, businesses);
        List<ProductCategory> productCategories = productCategories(customer, relationshipType);
        RelationshipStrength strength = relationshipStrength(worldSeed, customer, supplier, relationshipType);
        int startYear = startYear(worldSeed, customer, supplier, relationshipType);
        OptionalInt endYear = endYear(worldSeed, customer, supplier, relationshipType, strength, startYear);
        String manufacturerId = manufacturerId(worldSeed, customer, productCategories, relationshipType, manufacturers);

        return new SupplyRelationship(
                new SupplyRelationshipId(customer.id().value() + "_supply_relationship"),
                supplier.id(),
                customer.id(),
                relationshipType,
                manufacturerId,
                productCategories,
                startYear,
                endYear,
                strength,
                new DistributionTerritoryId(customer.primarySettlementId() + "_trade_territory"),
                relationshipNotes(customer, supplier, relationshipType, strength)
        );
    }

    private static SupplyContract contract(SupplyRelationship relationship) {
        return new SupplyContract(
                new SupplyContractId(relationship.id().value() + "_contract"),
                relationship.id(),
                relationship.historicalStartYear(),
                relationship.historicalEndYear(),
                relationship.relationshipStrength(),
                "Archival contract identity for " + relationship.relationshipType().serializedName().replace('_', ' ')
                        + "; no pricing, purchasing, delivery, or inventory terms are simulated."
        );
    }

    private static PreferredSupplier preferredSupplier(SupplyRelationship relationship) {
        return new PreferredSupplier(
                relationship.customerBusinessId(),
                relationship.supplierBusinessId(),
                relationship.id(),
                relationship.productCategories(),
                relationship.relationshipStrength(),
                "Preferred supplier record derived from the historical supply relationship."
        );
    }

    private static PreferredManufacturer preferredManufacturer(
            long worldSeed,
            Business business,
            ManufacturerRegistry manufacturers
    ) {
        List<ProductCategory> productCategories = productCategoriesForBusinessType(business.businessType());
        return new PreferredManufacturer(
                business.id(),
                manufacturerId(worldSeed, business, productCategories, SupplyRelationshipType.PRIMARY_SUPPLIER, manufacturers),
                productCategories,
                "Preferred manufacturer identity preserved for future equipment, packaging, sourcing, and service systems."
        );
    }

    private static BusinessSpecializationProfile specializationProfile(Business business) {
        return new BusinessSpecializationProfile(
                business.id(),
                specializations(business.businessType()),
                "Trade specialization identity for future demand, supplier, inspection, and market systems."
        );
    }

    private static SupplyRelationshipType relationshipType(long worldSeed, Business customer) {
        List<SupplyRelationshipType> options = switch (customer.businessType()) {
            case FAMILY_BUTCHER_SHOP -> List.of(
                    SupplyRelationshipType.PRIMARY_SUPPLIER,
                    SupplyRelationshipType.COOPERATIVE_PARTNER,
                    SupplyRelationshipType.PACKAGING_SUPPLIER
            );
            case RETAIL_MEAT_MARKET -> List.of(
                    SupplyRelationshipType.PRIMARY_SUPPLIER,
                    SupplyRelationshipType.SECONDARY_SUPPLIER,
                    SupplyRelationshipType.PACKAGING_SUPPLIER,
                    SupplyRelationshipType.INGREDIENT_SUPPLIER
            );
            case CUSTOM_PROCESSOR -> List.of(
                    SupplyRelationshipType.COOPERATIVE_PARTNER,
                    SupplyRelationshipType.EQUIPMENT_SUPPLIER,
                    SupplyRelationshipType.SECONDARY_SUPPLIER
            );
            case REGIONAL_PROCESSING_COMPANY -> List.of(
                    SupplyRelationshipType.WHOLESALE_NETWORK,
                    SupplyRelationshipType.EQUIPMENT_SUPPLIER,
                    SupplyRelationshipType.INGREDIENT_SUPPLIER
            );
            case LOCKER_PLANT -> List.of(
                    SupplyRelationshipType.COOPERATIVE_PARTNER,
                    SupplyRelationshipType.SECONDARY_SUPPLIER,
                    SupplyRelationshipType.EQUIPMENT_SUPPLIER
            );
            case COLD_STORAGE_COMPANY -> List.of(
                    SupplyRelationshipType.REGIONAL_DISTRIBUTOR,
                    SupplyRelationshipType.EQUIPMENT_SUPPLIER,
                    SupplyRelationshipType.WHOLESALE_NETWORK
            );
            case FOOD_DISTRIBUTION_COMPANY -> List.of(
                    SupplyRelationshipType.REGIONAL_DISTRIBUTOR,
                    SupplyRelationshipType.WHOLESALE_NETWORK,
                    SupplyRelationshipType.PRIMARY_SUPPLIER
            );
            case WHOLESALE_SUPPLIER -> List.of(
                    SupplyRelationshipType.WHOLESALE_NETWORK,
                    SupplyRelationshipType.PRIMARY_SUPPLIER,
                    SupplyRelationshipType.SECONDARY_SUPPLIER
            );
        };
        return options.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                RELATIONSHIP_TYPE_SALT,
                options.size(),
                customer.id().value(),
                customer.businessType().serializedName()
        ));
    }

    private static Business supplier(
            long worldSeed,
            Business customer,
            SupplyRelationshipType relationshipType,
            List<Business> businesses
    ) {
        List<Business> candidates = businesses.stream()
                .filter(business -> !business.id().equals(customer.id()))
                .filter(business -> supplierTypes(relationshipType, customer.businessType()).contains(business.businessType()))
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList();
        if (candidates.isEmpty()) {
            candidates = businesses.stream()
                    .filter(business -> !business.id().equals(customer.id()))
                    .sorted(Comparator.comparing(business -> business.id().value()))
                    .toList();
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Supply relationship generation requires at least two businesses");
        }
        int index = WorldIdentityDeterminism.stableIndex(
                worldSeed,
                SUPPLIER_SALT,
                candidates.size(),
                customer.id().value(),
                relationshipType.serializedName()
        );
        return candidates.get(index);
    }

    private static Set<BusinessType> supplierTypes(SupplyRelationshipType relationshipType, BusinessType customerType) {
        return switch (relationshipType) {
            case PRIMARY_SUPPLIER, SECONDARY_SUPPLIER -> switch (customerType) {
                case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET, CUSTOM_PROCESSOR, LOCKER_PLANT ->
                        Set.of(BusinessType.WHOLESALE_SUPPLIER, BusinessType.FOOD_DISTRIBUTION_COMPANY, BusinessType.REGIONAL_PROCESSING_COMPANY);
                case REGIONAL_PROCESSING_COMPANY, FOOD_DISTRIBUTION_COMPANY, WHOLESALE_SUPPLIER, COLD_STORAGE_COMPANY ->
                        Set.of(BusinessType.REGIONAL_PROCESSING_COMPANY, BusinessType.FOOD_DISTRIBUTION_COMPANY, BusinessType.WHOLESALE_SUPPLIER);
            };
            case REGIONAL_DISTRIBUTOR -> Set.of(BusinessType.FOOD_DISTRIBUTION_COMPANY, BusinessType.COLD_STORAGE_COMPANY, BusinessType.WHOLESALE_SUPPLIER);
            case EQUIPMENT_SUPPLIER -> Set.of(BusinessType.WHOLESALE_SUPPLIER, BusinessType.FOOD_DISTRIBUTION_COMPANY, BusinessType.REGIONAL_PROCESSING_COMPANY);
            case INGREDIENT_SUPPLIER -> Set.of(BusinessType.WHOLESALE_SUPPLIER, BusinessType.CUSTOM_PROCESSOR, BusinessType.REGIONAL_PROCESSING_COMPANY);
            case PACKAGING_SUPPLIER -> Set.of(BusinessType.WHOLESALE_SUPPLIER, BusinessType.FOOD_DISTRIBUTION_COMPANY);
            case COOPERATIVE_PARTNER -> Set.of(BusinessType.LOCKER_PLANT, BusinessType.CUSTOM_PROCESSOR, BusinessType.FAMILY_BUTCHER_SHOP, BusinessType.RETAIL_MEAT_MARKET);
            case WHOLESALE_NETWORK -> Set.of(BusinessType.WHOLESALE_SUPPLIER, BusinessType.FOOD_DISTRIBUTION_COMPANY, BusinessType.REGIONAL_PROCESSING_COMPANY);
        };
    }

    private static List<ProductCategory> productCategories(Business customer, SupplyRelationshipType relationshipType) {
        LinkedHashSet<ProductCategory> categories = new LinkedHashSet<>(productCategoriesForBusinessType(customer.businessType()));
        categories.addAll(productCategoriesForRelationship(relationshipType));
        return List.copyOf(categories);
    }

    private static List<ProductCategory> productCategoriesForBusinessType(BusinessType businessType) {
        return switch (businessType) {
            case FAMILY_BUTCHER_SHOP -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.FRESH_PORK, ProductCategory.SAUSAGE);
            case RETAIL_MEAT_MARKET -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.FRESH_PORK, ProductCategory.SMOKED_PRODUCTS, ProductCategory.PACKAGING_SUPPLIES);
            case CUSTOM_PROCESSOR -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.WILD_GAME, ProductCategory.LAMB);
            case REGIONAL_PROCESSING_COMPANY -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.FRESH_PORK, ProductCategory.FROZEN_FOODS);
            case LOCKER_PLANT -> List.of(ProductCategory.FROZEN_FOODS, ProductCategory.WILD_GAME, ProductCategory.FRESH_BEEF);
            case COLD_STORAGE_COMPANY -> List.of(ProductCategory.FROZEN_FOODS, ProductCategory.FRESH_BEEF, ProductCategory.FRESH_PORK);
            case FOOD_DISTRIBUTION_COMPANY -> List.of(ProductCategory.FROZEN_FOODS, ProductCategory.POULTRY, ProductCategory.PACKAGING_SUPPLIES);
            case WHOLESALE_SUPPLIER -> List.of(ProductCategory.PACKAGING_SUPPLIES, ProductCategory.SEASONINGS, ProductCategory.EQUIPMENT);
        };
    }

    private static List<ProductCategory> productCategoriesForRelationship(SupplyRelationshipType relationshipType) {
        return switch (relationshipType) {
            case PRIMARY_SUPPLIER -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.FRESH_PORK);
            case SECONDARY_SUPPLIER -> List.of(ProductCategory.LAMB, ProductCategory.POULTRY);
            case REGIONAL_DISTRIBUTOR -> List.of(ProductCategory.FROZEN_FOODS, ProductCategory.JERKY);
            case EQUIPMENT_SUPPLIER -> List.of(ProductCategory.EQUIPMENT);
            case INGREDIENT_SUPPLIER -> List.of(ProductCategory.SEASONINGS, ProductCategory.SAUSAGE);
            case PACKAGING_SUPPLIER -> List.of(ProductCategory.PACKAGING_SUPPLIES);
            case COOPERATIVE_PARTNER -> List.of(ProductCategory.WILD_GAME, ProductCategory.FRESH_BEEF);
            case WHOLESALE_NETWORK -> List.of(ProductCategory.FRESH_BEEF, ProductCategory.FROZEN_FOODS);
        };
    }

    private static RelationshipStrength relationshipStrength(
            long worldSeed,
            Business customer,
            Business supplier,
            SupplyRelationshipType relationshipType
    ) {
        List<RelationshipStrength> options = switch (customer.reputation()) {
            case LEGENDARY, EXCELLENT -> List.of(RelationshipStrength.EXCLUSIVE, RelationshipStrength.PREFERRED, RelationshipStrength.ESTABLISHED);
            case GOOD -> List.of(RelationshipStrength.PREFERRED, RelationshipStrength.ESTABLISHED, RelationshipStrength.OCCASIONAL);
            case AVERAGE -> List.of(RelationshipStrength.ESTABLISHED, RelationshipStrength.OCCASIONAL, RelationshipStrength.HISTORICAL);
            case DECLINING, POOR -> List.of(RelationshipStrength.OCCASIONAL, RelationshipStrength.HISTORICAL, RelationshipStrength.FORMER);
        };
        if (!customer.status().hasActiveOccupancy() || !supplier.status().hasActiveOccupancy()) {
            options = List.of(RelationshipStrength.HISTORICAL, RelationshipStrength.FORMER, RelationshipStrength.OCCASIONAL);
        }
        return options.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                STRENGTH_SALT,
                options.size(),
                customer.id().value(),
                supplier.id().value(),
                relationshipType.serializedName()
        ));
    }

    private static int startYear(
            long worldSeed,
            Business customer,
            Business supplier,
            SupplyRelationshipType relationshipType
    ) {
        int earliest = Math.max(customer.foundingYear(), supplier.foundingYear());
        int span = Math.max(1, Math.min(2026, earliest + 18) - earliest + 1);
        return earliest + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                YEAR_SALT,
                span,
                customer.id().value(),
                supplier.id().value(),
                relationshipType.serializedName()
        );
    }

    private static OptionalInt endYear(
            long worldSeed,
            Business customer,
            Business supplier,
            SupplyRelationshipType relationshipType,
            RelationshipStrength strength,
            int startYear
    ) {
        if (customer.status().hasActiveOccupancy()
                && supplier.status().hasActiveOccupancy()
                && strength != RelationshipStrength.FORMER
                && strength != RelationshipStrength.HISTORICAL) {
            return OptionalInt.empty();
        }
        int span = Math.max(1, 2025 - startYear + 1);
        return OptionalInt.of(startYear + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                YEAR_SALT,
                span,
                customer.id().value(),
                supplier.id().value(),
                relationshipType.serializedName(),
                "end"
        ));
    }

    private static String manufacturerId(
            long worldSeed,
            Business business,
            List<ProductCategory> productCategories,
            SupplyRelationshipType relationshipType,
            ManufacturerRegistry manufacturers
    ) {
        if (!business.preferredManufacturerIds().isEmpty()) {
            return business.preferredManufacturerIds().getFirst();
        }
        ManufacturerCategory category = manufacturerCategory(productCategories.getFirst());
        List<Manufacturer> candidates = manufacturers.findByCategory(category).stream()
                .sorted(Comparator.comparing(Manufacturer::id))
                .toList();
        if (candidates.isEmpty()) {
            candidates = manufacturers.stream()
                    .sorted(Comparator.comparing(Manufacturer::id))
                    .toList();
        }
        return candidates.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                MANUFACTURER_SALT,
                candidates.size(),
                business.id().value(),
                category.serializedName(),
                relationshipType.serializedName()
        )).id();
    }

    private static ManufacturerCategory manufacturerCategory(ProductCategory productCategory) {
        return switch (productCategory) {
            case PACKAGING_SUPPLIES -> ManufacturerCategory.PACKAGING;
            case FROZEN_FOODS -> ManufacturerCategory.REFRIGERATION;
            case EQUIPMENT -> ManufacturerCategory.PROCESSING_EQUIPMENT;
            case SEASONINGS -> ManufacturerCategory.SMALLWARES;
            case FRESH_BEEF, FRESH_PORK, POULTRY, LAMB, WILD_GAME, SMOKED_PRODUCTS, SAUSAGE, JERKY ->
                    ManufacturerCategory.PROCESSING_EQUIPMENT;
        };
    }

    private static List<BusinessSpecialization> specializations(BusinessType businessType) {
        return switch (businessType) {
            case FAMILY_BUTCHER_SHOP -> List.of(BusinessSpecialization.RETAIL, BusinessSpecialization.CUSTOM_PROCESSING, BusinessSpecialization.DRY_AGING);
            case RETAIL_MEAT_MARKET -> List.of(BusinessSpecialization.RETAIL, BusinessSpecialization.SAUSAGE_PRODUCTION, BusinessSpecialization.SMOKED_PRODUCTS);
            case CUSTOM_PROCESSOR -> List.of(BusinessSpecialization.CUSTOM_PROCESSING, BusinessSpecialization.LIVESTOCK_PROCESSING, BusinessSpecialization.WILD_GAME_PROCESSING);
            case REGIONAL_PROCESSING_COMPANY -> List.of(BusinessSpecialization.WHOLESALE, BusinessSpecialization.LIVESTOCK_PROCESSING, BusinessSpecialization.SAUSAGE_PRODUCTION);
            case LOCKER_PLANT -> List.of(BusinessSpecialization.COLD_STORAGE, BusinessSpecialization.CUSTOM_PROCESSING, BusinessSpecialization.WILD_GAME_PROCESSING);
            case COLD_STORAGE_COMPANY -> List.of(BusinessSpecialization.COLD_STORAGE, BusinessSpecialization.DISTRIBUTION, BusinessSpecialization.WHOLESALE);
            case FOOD_DISTRIBUTION_COMPANY -> List.of(BusinessSpecialization.DISTRIBUTION, BusinessSpecialization.WHOLESALE);
            case WHOLESALE_SUPPLIER -> List.of(BusinessSpecialization.WHOLESALE, BusinessSpecialization.DISTRIBUTION, BusinessSpecialization.RETAIL);
        };
    }

    private static List<String> dominantManufacturers(
            long worldSeed,
            Settlement settlement,
            List<Business> localBusinesses,
            ManufacturerRegistry manufacturers
    ) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        localBusinesses.stream()
                .flatMap(business -> business.preferredManufacturerIds().stream())
                .forEach(ids::add);
        if (ids.isEmpty()) {
            List<Manufacturer> sortedManufacturers = manufacturers.stream()
                    .sorted(Comparator.comparing(Manufacturer::id))
                    .toList();
            ids.add(sortedManufacturers.get(WorldIdentityDeterminism.stableIndex(
                    worldSeed,
                    MANUFACTURER_SALT,
                    sortedManufacturers.size(),
                    settlement.id()
            )).id());
        }
        return ids.stream().limit(3).toList();
    }

    private static List<BusinessId> primaryBusinessesForRoute(List<Business> originBusinesses, List<Business> hubBusinesses) {
        LinkedHashSet<BusinessId> ids = new LinkedHashSet<>();
        originBusinesses.stream().limit(2).map(Business::id).forEach(ids::add);
        hubBusinesses.stream().limit(2).map(Business::id).forEach(ids::add);
        return List.copyOf(ids);
    }

    private static List<ProductCategory> productCategoriesForRoute(Settlement settlement, List<BusinessId> primaryBusinesses) {
        LinkedHashSet<ProductCategory> categories = new LinkedHashSet<>();
        categories.add(switch (settlement.type()) {
            case HAMLET -> ProductCategory.FRESH_BEEF;
            case VILLAGE -> ProductCategory.FRESH_PORK;
            case TOWN -> ProductCategory.FROZEN_FOODS;
            case REGIONAL_CITY -> ProductCategory.PACKAGING_SUPPLIES;
        });
        categories.add(primaryBusinesses.size() > 2 ? ProductCategory.EQUIPMENT : ProductCategory.SEASONINGS);
        return List.copyOf(categories);
    }

    private static int distributionImportance(long worldSeed, Settlement settlement) {
        int base = switch (settlement.type()) {
            case HAMLET -> 35;
            case VILLAGE -> 50;
            case TOWN -> 68;
            case REGIONAL_CITY -> 84;
        };
        return Math.min(100, base + WorldIdentityDeterminism.stableIndex(worldSeed, IMPORTANCE_SALT, 12, settlement.id()));
    }

    private static int routeImportance(long worldSeed, Settlement origin, Settlement destination) {
        int base = switch (origin.type()) {
            case HAMLET -> 35;
            case VILLAGE -> 48;
            case TOWN -> 62;
            case REGIONAL_CITY -> 80;
        };
        if (destination.type() == SettlementType.REGIONAL_CITY) {
            base += 8;
        }
        return Math.min(100, base + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                IMPORTANCE_SALT,
                12,
                origin.id(),
                destination.id()
        ));
    }

    private static int influenceScore(long worldSeed, String id, String role) {
        return 55 + WorldIdentityDeterminism.stableIndex(worldSeed, IMPORTANCE_SALT, 41, id, role);
    }

    private static String relationshipNotes(
            Business customer,
            Business supplier,
            SupplyRelationshipType relationshipType,
            RelationshipStrength strength
    ) {
        return "Historical " + relationshipType.serializedName().replace('_', ' ')
                + " relationship from " + supplier.displayName() + " to " + customer.displayName()
                + " recorded as " + strength.serializedName().replace('_', ' ')
                + "; no purchasing, pricing, inventory, or delivery behavior is attached.";
    }
}
