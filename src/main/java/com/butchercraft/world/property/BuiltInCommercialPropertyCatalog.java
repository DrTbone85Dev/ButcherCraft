package com.butchercraft.world.property;

import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.SettlementType;
import com.butchercraft.world.identity.WorldIdentityDeterminism;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

public final class BuiltInCommercialPropertyCatalog {
    public static final int PROPERTIES_PER_SETTLEMENT = 4;

    private static final long YEAR_SALT = 0x52a91f7c3d4e80b1L;
    private static final long STATUS_SALT = 0x72e4079f31a5c6d2L;
    private static final long CONDITION_SALT = 0x19f5c2b84e37d064L;
    private static final long SIZE_SALT = 0x0d46a2397be1f8a5L;
    private static final long OWNER_SALT = 0x4c89b52e710fc6a3L;
    private static final long SUMMARY_SALT = 0x73b2a6c1904e5d88L;

    private static final List<PropertySlot> COMMON_SLOTS = List.of(
            new PropertySlot("family_butcher_shop", CommercialPropertyType.FAMILY_BUTCHER_SHOP),
            new PropertySlot("market_storefront", CommercialPropertyType.VACANT_STOREFRONT)
    );

    private BuiltInCommercialPropertyCatalog() {
    }

    public static List<CommercialProperty> generate(long worldSeed, List<Settlement> settlements) {
        List<CommercialProperty> properties = new ArrayList<>();
        settlements.stream()
                .sorted(Comparator.comparing(Settlement::id))
                .forEach(settlement -> properties.addAll(generateForSettlement(worldSeed, settlement)));
        return properties.stream()
                .sorted(Comparator.comparing(property -> property.id().value()))
                .toList();
    }

    private static List<CommercialProperty> generateForSettlement(long worldSeed, Settlement settlement) {
        List<CommercialProperty> properties = new ArrayList<>();
        for (PropertySlot slot : slotsFor(settlement.type())) {
            properties.add(generateProperty(worldSeed, settlement, slot));
        }
        return properties;
    }

    private static CommercialProperty generateProperty(long worldSeed, Settlement settlement, PropertySlot slot) {
        String propertyId = settlement.id() + "_" + slot.id();
        int constructionYear = constructionYear(worldSeed, settlement, slot);
        LotSize lotSize = lotSize(worldSeed, settlement, slot);
        BuildingSize buildingSize = buildingSize(worldSeed, settlement, slot, lotSize);
        UtilityProfile utilities = utilityProfile(slot.type());
        PropertyCondition condition = condition(worldSeed, propertyId, slot.type());
        PropertyStatus status = status(worldSeed, propertyId, slot.type());
        ExpansionCapacity expansionCapacity = new ExpansionCapacity(Math.max(0,
                lotSize.squareMeters() - buildingSize.squareMeters() - serviceYardAllowance(slot.type())));

        return new CommercialProperty(
                new CommercialPropertyId(propertyId),
                displayName(settlement, slot),
                settlement.id(),
                slot.type(),
                constructionYear,
                condition,
                status,
                lotSize,
                buildingSize,
                utilities,
                expansionCapacity,
                new PropertyHistory(
                        historicalSummary(worldSeed, settlement, slot, constructionYear, status, condition),
                        ownershipHistory(worldSeed, propertyId, constructionYear, slot.type(), status)
                )
        );
    }

    private static List<PropertySlot> slotsFor(SettlementType settlementType) {
        return switch (settlementType) {
            case HAMLET -> List.of(
                    COMMON_SLOTS.get(0),
                    COMMON_SLOTS.get(1),
                    new PropertySlot("commercial_lot", CommercialPropertyType.EMPTY_COMMERCIAL_LOT),
                    new PropertySlot("locker_plant", CommercialPropertyType.LOCKER_PLANT)
            );
            case VILLAGE -> List.of(
                    COMMON_SLOTS.get(0),
                    COMMON_SLOTS.get(1),
                    new PropertySlot("locker_plant", CommercialPropertyType.LOCKER_PLANT),
                    new PropertySlot("supply_warehouse", CommercialPropertyType.WAREHOUSE)
            );
            case TOWN -> List.of(
                    COMMON_SLOTS.get(0),
                    COMMON_SLOTS.get(1),
                    new PropertySlot("industrial_building", CommercialPropertyType.INDUSTRIAL_BUILDING),
                    new PropertySlot("cold_storage", CommercialPropertyType.COLD_STORAGE_FACILITY)
            );
            case REGIONAL_CITY -> List.of(
                    COMMON_SLOTS.get(0),
                    COMMON_SLOTS.get(1),
                    new PropertySlot("distribution_center", CommercialPropertyType.DISTRIBUTION_CENTER),
                    new PropertySlot("cold_storage", CommercialPropertyType.COLD_STORAGE_FACILITY)
            );
        };
    }

    private static String displayName(Settlement settlement, PropertySlot slot) {
        return switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> settlement.displayName() + " Family Butcher Shop";
            case VACANT_STOREFRONT -> settlement.displayName() + " Market Storefront";
            case LOCKER_PLANT -> settlement.displayName() + " Locker Plant";
            case WAREHOUSE -> settlement.displayName() + " Supply Warehouse";
            case INDUSTRIAL_BUILDING -> settlement.displayName() + " Industrial Building";
            case EMPTY_COMMERCIAL_LOT -> settlement.displayName() + " Commercial Lot";
            case DISTRIBUTION_CENTER -> settlement.displayName() + " Distribution Center";
            case COLD_STORAGE_FACILITY -> settlement.displayName() + " Cold Storage Facility";
        };
    }

    private static int constructionYear(long worldSeed, Settlement settlement, PropertySlot slot) {
        int minYear = switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> 1910;
            case VACANT_STOREFRONT -> 1900;
            case LOCKER_PLANT -> 1920;
            case WAREHOUSE -> 1930;
            case INDUSTRIAL_BUILDING -> 1940;
            case EMPTY_COMMERCIAL_LOT -> 1950;
            case DISTRIBUTION_CENTER -> 1970;
            case COLD_STORAGE_FACILITY -> 1950;
        };
        int maxYear = switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> 1985;
            case VACANT_STOREFRONT -> 1995;
            case LOCKER_PLANT -> 1975;
            case WAREHOUSE -> 1990;
            case INDUSTRIAL_BUILDING -> 1995;
            case EMPTY_COMMERCIAL_LOT -> 2005;
            case DISTRIBUTION_CENTER -> 2008;
            case COLD_STORAGE_FACILITY -> 2000;
        };
        return minYear + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                YEAR_SALT,
                maxYear - minYear + 1,
                settlement.id(),
                slot.id()
        );
    }

    private static PropertyStatus status(long worldSeed, String propertyId, CommercialPropertyType type) {
        List<PropertyStatus> statuses = switch (type) {
            case FAMILY_BUTCHER_SHOP -> List.of(PropertyStatus.OPERATING, PropertyStatus.VACANT, PropertyStatus.RESERVED, PropertyStatus.UNDER_RENOVATION);
            case VACANT_STOREFRONT -> List.of(PropertyStatus.VACANT, PropertyStatus.RESERVED, PropertyStatus.UNDER_RENOVATION);
            case LOCKER_PLANT -> List.of(PropertyStatus.OPERATING, PropertyStatus.VACANT, PropertyStatus.UNDER_RENOVATION, PropertyStatus.ABANDONED);
            case WAREHOUSE -> List.of(PropertyStatus.OPERATING, PropertyStatus.VACANT, PropertyStatus.RESERVED);
            case INDUSTRIAL_BUILDING -> List.of(PropertyStatus.OPERATING, PropertyStatus.VACANT, PropertyStatus.UNDER_RENOVATION, PropertyStatus.ABANDONED, PropertyStatus.CONDEMNED);
            case EMPTY_COMMERCIAL_LOT -> List.of(PropertyStatus.VACANT, PropertyStatus.RESERVED, PropertyStatus.ABANDONED);
            case DISTRIBUTION_CENTER -> List.of(PropertyStatus.OPERATING, PropertyStatus.RESERVED, PropertyStatus.VACANT);
            case COLD_STORAGE_FACILITY -> List.of(PropertyStatus.OPERATING, PropertyStatus.VACANT, PropertyStatus.UNDER_RENOVATION, PropertyStatus.CONDEMNED);
        };
        return statuses.get(WorldIdentityDeterminism.stableIndex(worldSeed, STATUS_SALT, statuses.size(), propertyId, type.serializedName()));
    }

    private static PropertyCondition condition(long worldSeed, String propertyId, CommercialPropertyType type) {
        List<PropertyCondition> conditions = switch (type) {
            case FAMILY_BUTCHER_SHOP -> List.of(PropertyCondition.EXCELLENT, PropertyCondition.GOOD, PropertyCondition.FAIR);
            case VACANT_STOREFRONT -> List.of(PropertyCondition.GOOD, PropertyCondition.FAIR, PropertyCondition.POOR);
            case LOCKER_PLANT -> List.of(PropertyCondition.GOOD, PropertyCondition.FAIR, PropertyCondition.POOR);
            case WAREHOUSE -> List.of(PropertyCondition.EXCELLENT, PropertyCondition.GOOD, PropertyCondition.FAIR);
            case INDUSTRIAL_BUILDING -> List.of(PropertyCondition.EXCELLENT, PropertyCondition.GOOD, PropertyCondition.FAIR, PropertyCondition.POOR, PropertyCondition.DERELICT);
            case EMPTY_COMMERCIAL_LOT -> List.of(PropertyCondition.FAIR, PropertyCondition.POOR, PropertyCondition.DERELICT);
            case DISTRIBUTION_CENTER -> List.of(PropertyCondition.EXCELLENT, PropertyCondition.GOOD, PropertyCondition.FAIR);
            case COLD_STORAGE_FACILITY -> List.of(PropertyCondition.EXCELLENT, PropertyCondition.GOOD, PropertyCondition.FAIR, PropertyCondition.POOR);
        };
        return conditions.get(WorldIdentityDeterminism.stableIndex(worldSeed, CONDITION_SALT, conditions.size(), propertyId, type.serializedName()));
    }

    private static LotSize lotSize(long worldSeed, Settlement settlement, PropertySlot slot) {
        int base = switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> 900;
            case VACANT_STOREFRONT -> 420;
            case LOCKER_PLANT -> 1_800;
            case WAREHOUSE -> 3_000;
            case INDUSTRIAL_BUILDING -> 4_800;
            case EMPTY_COMMERCIAL_LOT -> 1_600;
            case DISTRIBUTION_CENTER -> 7_500;
            case COLD_STORAGE_FACILITY -> 3_600;
        };
        int variance = WorldIdentityDeterminism.stableIndex(worldSeed, SIZE_SALT, base / 3, settlement.id(), slot.id(), "lot");
        return new LotSize(base + variance);
    }

    private static BuildingSize buildingSize(long worldSeed, Settlement settlement, PropertySlot slot, LotSize lotSize) {
        if (slot.type() == CommercialPropertyType.EMPTY_COMMERCIAL_LOT) {
            return new BuildingSize(0);
        }
        int base = switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> 320;
            case VACANT_STOREFRONT -> 180;
            case LOCKER_PLANT -> 760;
            case WAREHOUSE -> 1_350;
            case INDUSTRIAL_BUILDING -> 2_400;
            case EMPTY_COMMERCIAL_LOT -> 0;
            case DISTRIBUTION_CENTER -> 3_800;
            case COLD_STORAGE_FACILITY -> 1_600;
        };
        int variance = WorldIdentityDeterminism.stableIndex(worldSeed, SIZE_SALT, Math.max(1, base / 4), settlement.id(), slot.id(), "building");
        return new BuildingSize(Math.min(lotSize.squareMeters(), base + variance));
    }

    private static UtilityProfile utilityProfile(CommercialPropertyType type) {
        return switch (type) {
            case FAMILY_BUTCHER_SHOP -> new UtilityProfile(ElectricalService.STANDARD_COMMERCIAL, true, true, true, false, false, true, RefrigerationCapacity.SMALL_RETAIL);
            case VACANT_STOREFRONT -> new UtilityProfile(ElectricalService.LIGHT_COMMERCIAL, true, true, true, false, false, true, RefrigerationCapacity.NONE);
            case LOCKER_PLANT -> new UtilityProfile(ElectricalService.HEAVY_COMMERCIAL, true, true, true, true, false, true, RefrigerationCapacity.LOCKER_ROOM);
            case WAREHOUSE -> new UtilityProfile(ElectricalService.HEAVY_COMMERCIAL, true, true, false, true, false, true, RefrigerationCapacity.NONE);
            case INDUSTRIAL_BUILDING -> new UtilityProfile(ElectricalService.INDUSTRIAL_THREE_PHASE, true, true, true, true, true, true, RefrigerationCapacity.SMALL_RETAIL);
            case EMPTY_COMMERCIAL_LOT -> new UtilityProfile(ElectricalService.LIGHT_COMMERCIAL, false, false, false, false, false, true, RefrigerationCapacity.NONE);
            case DISTRIBUTION_CENTER -> new UtilityProfile(ElectricalService.INDUSTRIAL_THREE_PHASE, true, true, true, true, true, true, RefrigerationCapacity.WAREHOUSE);
            case COLD_STORAGE_FACILITY -> new UtilityProfile(ElectricalService.INDUSTRIAL_THREE_PHASE, true, true, true, true, false, true, RefrigerationCapacity.INDUSTRIAL);
        };
    }

    private static int serviceYardAllowance(CommercialPropertyType type) {
        return switch (type) {
            case FAMILY_BUTCHER_SHOP, VACANT_STOREFRONT -> 120;
            case EMPTY_COMMERCIAL_LOT -> 0;
            case LOCKER_PLANT, WAREHOUSE -> 420;
            case INDUSTRIAL_BUILDING, COLD_STORAGE_FACILITY -> 700;
            case DISTRIBUTION_CENTER -> 1_200;
        };
    }

    private static String historicalSummary(
            long worldSeed,
            Settlement settlement,
            PropertySlot slot,
            int constructionYear,
            PropertyStatus status,
            PropertyCondition condition
    ) {
        String origin = switch (slot.type()) {
            case FAMILY_BUTCHER_SHOP -> "a neighborhood butcher shop";
            case VACANT_STOREFRONT -> "a main-street retail storefront";
            case LOCKER_PLANT -> "a community locker plant";
            case WAREHOUSE -> "a livestock and food-supply warehouse";
            case INDUSTRIAL_BUILDING -> "a small industrial processing building";
            case EMPTY_COMMERCIAL_LOT -> "a platted commercial lot";
            case DISTRIBUTION_CENTER -> "a regional distribution depot";
            case COLD_STORAGE_FACILITY -> "a commercial cold-storage facility";
        };
        List<String> arcs = List.of(
                "Local records show multiple owners adapting it to changing regional trade patterns",
                "County notes describe it as a practical property that stayed useful as nearby businesses changed",
                "Later renovations were modest, preserving the property's original commercial role",
                "Its ownership history reflects steady local use rather than a single permanent business identity"
        );
        String arc = arcs.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                SUMMARY_SALT,
                arcs.size(),
                settlement.id(),
                slot.id()
        ));
        return "Built in " + constructionYear + " as " + origin + " serving " + settlement.displayName() + ". "
                + arc + "; it is currently " + status.serializedName().replace('_', ' ')
                + " and rated " + condition.serializedName() + ".";
    }

    private static List<OwnershipRecord> ownershipHistory(
            long worldSeed,
            String propertyId,
            int constructionYear,
            CommercialPropertyType type,
            PropertyStatus status
    ) {
        int firstEnd = Math.min(
                constructionYear + 10 + WorldIdentityDeterminism.stableIndex(worldSeed, OWNER_SALT, 16, propertyId, "first_end"),
                2013
        );
        int secondStart = firstEnd + 1;
        int secondEnd = Math.min(secondStart + 8 + WorldIdentityDeterminism.stableIndex(worldSeed, OWNER_SALT, 18, propertyId, "second_end"), 2020);
        if (secondEnd <= secondStart) {
            secondEnd = Math.min(secondStart + 5, 2020);
        }
        return List.of(
                new OwnershipRecord(
                        ownerName(worldSeed, propertyId, "founder", type),
                        constructionYear,
                        OptionalInt.of(firstEnd),
                        PropertyAcquisitionMethod.ORIGINAL_CONSTRUCTION,
                        "Established the property footprint and first commercial use."
                ),
                new OwnershipRecord(
                        ownerName(worldSeed, propertyId, "middle", type),
                        secondStart,
                        OptionalInt.of(secondEnd),
                        PropertyAcquisitionMethod.PRIVATE_SALE,
                        "Adapted the location as regional trade and settlement needs changed."
                ),
                new OwnershipRecord(
                        currentOwnerName(worldSeed, propertyId, status),
                        secondEnd + 1,
                        OptionalInt.empty(),
                        currentAcquisitionMethod(status),
                        "Current record holder; this is property ownership history, not a business entity."
                )
        );
    }

    private static String ownerName(long worldSeed, String propertyId, String phase, CommercialPropertyType type) {
        List<String> familyOwners = List.of("Alder Family", "Benton Family", "Carver Family", "Dawson Family", "Ellis Family", "Harlan Family", "Mercer Family", "Whitcomb Family");
        List<String> institutionalOwners = List.of("County Locker Association", "Market Street Cooperative", "Regional Supply Company", "Heritage Property Trust", "Founders Real Estate Office", "Prairie Commercial Holdings");
        List<String> pool = type == CommercialPropertyType.FAMILY_BUTCHER_SHOP ? familyOwners : institutionalOwners;
        return pool.get(WorldIdentityDeterminism.stableIndex(worldSeed, OWNER_SALT, pool.size(), propertyId, phase));
    }

    private static String currentOwnerName(long worldSeed, String propertyId, PropertyStatus status) {
        List<String> vacantOwners = List.of("County Property Trust", "First Market Bank", "Regional Holding Office", "Estate Management Company");
        List<String> activeOwners = List.of("Local Commercial Holdings", "Settlement Property Group", "Regional Facility Trust", "Market District Partners");
        List<String> pool = switch (status) {
            case VACANT, ABANDONED, CONDEMNED, RESERVED -> vacantOwners;
            case OPERATING, UNDER_RENOVATION -> activeOwners;
        };
        return pool.get(WorldIdentityDeterminism.stableIndex(worldSeed, OWNER_SALT, pool.size(), propertyId, "current"));
    }

    private static PropertyAcquisitionMethod currentAcquisitionMethod(PropertyStatus status) {
        return switch (status) {
            case VACANT, ABANDONED, CONDEMNED -> PropertyAcquisitionMethod.BANK_REPOSSESSION;
            case RESERVED -> PropertyAcquisitionMethod.DEVELOPMENT_HOLD;
            case UNDER_RENOVATION -> PropertyAcquisitionMethod.ESTATE_TRANSFER;
            case OPERATING -> PropertyAcquisitionMethod.REGIONAL_COMPANY_PURCHASE;
        };
    }

    private record PropertySlot(String id, CommercialPropertyType type) {
    }
}
