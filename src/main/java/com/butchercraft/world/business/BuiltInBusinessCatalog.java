package com.butchercraft.world.business;

import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.WorldIdentityDeterminism;
import com.butchercraft.world.manufacturer.BuiltInManufacturerCatalog;
import com.butchercraft.world.manufacturer.Manufacturer;
import com.butchercraft.world.manufacturer.ManufacturerCategory;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;
import com.butchercraft.world.property.CommercialPropertyType;
import com.butchercraft.world.property.PropertyCondition;
import com.butchercraft.world.property.PropertyStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class BuiltInBusinessCatalog {
    private static final long TYPE_SALT = 0x64ac59f031a2b7d4L;
    private static final long STATUS_SALT = 0x08e6c2b9a74d513fL;
    private static final long REPUTATION_SALT = 0x119c3de54fb872a0L;
    private static final long NAME_SALT = 0x7612bb9834eca015L;
    private static final long YEAR_SALT = 0x4fa938c127e0a6d5L;
    private static final long MANUFACTURER_SALT = 0x6dd1a928f4057c33L;

    private BuiltInBusinessCatalog() {
    }

    public static List<Business> generate(
            long worldSeed,
            Region region,
            List<Settlement> settlements,
            List<CommercialProperty> properties
    ) {
        Map<String, Settlement> settlementsById = settlements.stream()
                .collect(Collectors.toUnmodifiableMap(Settlement::id, settlement -> settlement));
        return properties.stream()
                .sorted(Comparator.comparing(property -> property.id().value()))
                .filter(property -> property.propertyType() != CommercialPropertyType.EMPTY_COMMERCIAL_LOT)
                .map(property -> generateBusiness(worldSeed, region, settlementsById.get(property.settlementId()), property))
                .sorted(Comparator.comparing(business -> business.id().value()))
                .toList();
    }

    private static Business generateBusiness(
            long worldSeed,
            Region region,
            Settlement settlement,
            CommercialProperty property
    ) {
        if (settlement == null) {
            throw new IllegalArgumentException("Commercial property references unknown settlement: " + property.settlementId());
        }
        BusinessType type = businessType(worldSeed, property);
        BusinessStatus status = businessStatus(worldSeed, property, type);
        BusinessReputation reputation = reputation(worldSeed, property, status, type);
        int foundingYear = foundingYear(worldSeed, property, type);
        BusinessOccupancy occupancy = occupancy(worldSeed, property.id(), foundingYear, status);
        String displayName = displayName(worldSeed, settlement, property, type);

        return new Business(
                new BusinessId(property.id().value() + "_business"),
                displayName,
                type,
                foundingYear,
                status,
                reputation,
                new BusinessHistory(
                        historicalSummary(region, settlement, displayName, type, foundingYear, status, reputation),
                        List.of(occupancy)
                ),
                List.of(property.id()),
                property.id(),
                settlement.id(),
                region.id(),
                List.of(),
                corporateHeadquarters(type, property.id()),
                ownershipModel(worldSeed, property.id().value(), foundingYear, status, type),
                preferredManufacturerIds(worldSeed, property.id().value(), type),
                List.of()
        );
    }

    private static BusinessType businessType(long worldSeed, CommercialProperty property) {
        return switch (property.propertyType()) {
            case FAMILY_BUTCHER_SHOP -> stableChoice(worldSeed, property.id().value(), List.of(
                    BusinessType.FAMILY_BUTCHER_SHOP,
                    BusinessType.RETAIL_MEAT_MARKET,
                    BusinessType.CUSTOM_PROCESSOR
            ));
            case VACANT_STOREFRONT -> stableChoice(worldSeed, property.id().value(), List.of(
                    BusinessType.RETAIL_MEAT_MARKET,
                    BusinessType.WHOLESALE_SUPPLIER
            ));
            case LOCKER_PLANT -> stableChoice(worldSeed, property.id().value(), List.of(
                    BusinessType.LOCKER_PLANT,
                    BusinessType.CUSTOM_PROCESSOR
            ));
            case WAREHOUSE -> stableChoice(worldSeed, property.id().value(), List.of(
                    BusinessType.WHOLESALE_SUPPLIER,
                    BusinessType.FOOD_DISTRIBUTION_COMPANY
            ));
            case INDUSTRIAL_BUILDING -> stableChoice(worldSeed, property.id().value(), List.of(
                    BusinessType.REGIONAL_PROCESSING_COMPANY,
                    BusinessType.CUSTOM_PROCESSOR
            ));
            case DISTRIBUTION_CENTER -> BusinessType.FOOD_DISTRIBUTION_COMPANY;
            case COLD_STORAGE_FACILITY -> BusinessType.COLD_STORAGE_COMPANY;
            case EMPTY_COMMERCIAL_LOT -> throw new IllegalArgumentException("Empty commercial lots do not generate business records");
        };
    }

    private static BusinessStatus businessStatus(long worldSeed, CommercialProperty property, BusinessType type) {
        List<BusinessStatus> statuses = switch (property.status()) {
            case OPERATING -> List.of(BusinessStatus.OPERATING, BusinessStatus.SEASONAL, BusinessStatus.RELOCATED);
            case VACANT -> List.of(BusinessStatus.CLOSED, BusinessStatus.VACANT_RECORD, BusinessStatus.BANKRUPT, BusinessStatus.RELOCATED);
            case RESERVED -> List.of(BusinessStatus.RELOCATED, BusinessStatus.MERGED, BusinessStatus.VACANT_RECORD);
            case UNDER_RENOVATION -> List.of(BusinessStatus.OPERATING, BusinessStatus.RELOCATED, BusinessStatus.CLOSED);
            case ABANDONED, CONDEMNED -> List.of(BusinessStatus.CLOSED, BusinessStatus.BANKRUPT, BusinessStatus.VACANT_RECORD);
        };
        return statuses.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                STATUS_SALT,
                statuses.size(),
                property.id().value(),
                type.serializedName()
        ));
    }

    private static BusinessReputation reputation(
            long worldSeed,
            CommercialProperty property,
            BusinessStatus status,
            BusinessType type
    ) {
        List<BusinessReputation> reputations = switch (status) {
            case OPERATING, SEASONAL -> List.of(BusinessReputation.EXCELLENT, BusinessReputation.GOOD, BusinessReputation.AVERAGE);
            case RELOCATED, MERGED -> List.of(BusinessReputation.LEGENDARY, BusinessReputation.EXCELLENT, BusinessReputation.GOOD, BusinessReputation.AVERAGE);
            case CLOSED -> List.of(BusinessReputation.GOOD, BusinessReputation.AVERAGE, BusinessReputation.DECLINING);
            case BANKRUPT, VACANT_RECORD -> List.of(BusinessReputation.AVERAGE, BusinessReputation.DECLINING, BusinessReputation.POOR);
        };
        if (property.condition() == PropertyCondition.EXCELLENT && status.hasActiveOccupancy()) {
            reputations = List.of(BusinessReputation.LEGENDARY, BusinessReputation.EXCELLENT, BusinessReputation.GOOD);
        }
        return reputations.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                REPUTATION_SALT,
                reputations.size(),
                property.id().value(),
                type.serializedName(),
                status.serializedName()
        ));
    }

    private static int foundingYear(long worldSeed, CommercialProperty property, BusinessType type) {
        int minYear = property.constructionYear();
        int maxYear = Math.min(2018, minYear + switch (type) {
            case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET -> 18;
            case CUSTOM_PROCESSOR, LOCKER_PLANT -> 24;
            case REGIONAL_PROCESSING_COMPANY, COLD_STORAGE_COMPANY -> 28;
            case FOOD_DISTRIBUTION_COMPANY, WHOLESALE_SUPPLIER -> 30;
        });
        return minYear + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                YEAR_SALT,
                maxYear - minYear + 1,
                property.id().value(),
                type.serializedName()
        );
    }

    private static BusinessOccupancy occupancy(
            long worldSeed,
            CommercialPropertyId propertyId,
            int foundingYear,
            BusinessStatus status
    ) {
        OptionalInt endYear = status.hasActiveOccupancy()
                ? OptionalInt.empty()
                : OptionalInt.of(endYear(worldSeed, propertyId.value(), foundingYear, status));
        return new BusinessOccupancy(
                propertyId,
                foundingYear,
                endYear,
                occupancyReason(status),
                occupancyNotes(status)
        );
    }

    private static int endYear(long worldSeed, String propertyId, int foundingYear, BusinessStatus status) {
        int latest = 2025;
        int range = Math.max(1, latest - foundingYear + 1);
        return foundingYear + WorldIdentityDeterminism.stableIndex(
                worldSeed,
                YEAR_SALT,
                range,
                propertyId,
                status.serializedName(),
                "end"
        );
    }

    private static BusinessOccupancyReason occupancyReason(BusinessStatus status) {
        return switch (status) {
            case OPERATING, SEASONAL -> BusinessOccupancyReason.FOUNDED;
            case RELOCATED -> BusinessOccupancyReason.RELOCATED_TO;
            case MERGED -> BusinessOccupancyReason.MERGER_RECORD;
            case BANKRUPT, CLOSED -> BusinessOccupancyReason.CLOSURE_RECORD;
            case VACANT_RECORD -> BusinessOccupancyReason.VACANCY_RECORD;
        };
    }

    private static String occupancyNotes(BusinessStatus status) {
        return switch (status) {
            case OPERATING -> "Current operating location recorded for future business systems.";
            case SEASONAL -> "Current seasonal operating location recorded without gameplay effects.";
            case RELOCATED -> "Recorded as a relocation event while preserving the business identity.";
            case MERGED -> "Historical merger record preserved for future lineage systems.";
            case BANKRUPT -> "Closed after financial distress; no economy simulation is attached.";
            case CLOSED -> "Historical closure record preserved for future archives.";
            case VACANT_RECORD -> "Last known business record associated with a currently vacant commercial site.";
        };
    }

    private static String displayName(
            long worldSeed,
            Settlement settlement,
            CommercialProperty property,
            BusinessType type
    ) {
        String familyName = familyNames().get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                NAME_SALT,
                familyNames().size(),
                property.id().value(),
                type.serializedName()
        ));
        return switch (type) {
            case FAMILY_BUTCHER_SHOP -> familyName + " Family Butcher of " + settlement.displayName();
            case RETAIL_MEAT_MARKET -> retailName(settlement, property, familyName);
            case CUSTOM_PROCESSOR -> customProcessorName(settlement, property, familyName);
            case REGIONAL_PROCESSING_COMPANY -> settlement.displayName() + " Regional Processing";
            case LOCKER_PLANT -> settlement.displayName() + " Community Locker";
            case COLD_STORAGE_COMPANY -> settlement.displayName() + " Cold Storage Company";
            case FOOD_DISTRIBUTION_COMPANY -> settlement.displayName() + " Food Distribution";
            case WHOLESALE_SUPPLIER -> wholesaleName(settlement, property, familyName);
        };
    }

    private static String retailName(Settlement settlement, CommercialProperty property, String familyName) {
        return switch (property.propertyType()) {
            case FAMILY_BUTCHER_SHOP -> familyName + " Family Market of " + settlement.displayName();
            case VACANT_STOREFRONT -> settlement.displayName() + " Main Street Meat Market";
            case LOCKER_PLANT, WAREHOUSE, INDUSTRIAL_BUILDING, EMPTY_COMMERCIAL_LOT, DISTRIBUTION_CENTER,
                 COLD_STORAGE_FACILITY -> familyName + " Meat Market of " + settlement.displayName();
        };
    }

    private static String customProcessorName(Settlement settlement, CommercialProperty property, String familyName) {
        return switch (property.propertyType()) {
            case FAMILY_BUTCHER_SHOP -> familyName + " Shop Custom Processing";
            case LOCKER_PLANT -> familyName + " Locker Custom Processing";
            case INDUSTRIAL_BUILDING -> settlement.displayName() + " Industrial Custom Processing";
            case VACANT_STOREFRONT, WAREHOUSE, EMPTY_COMMERCIAL_LOT, DISTRIBUTION_CENTER, COLD_STORAGE_FACILITY ->
                    familyName + " Custom Processing";
        };
    }

    private static String wholesaleName(Settlement settlement, CommercialProperty property, String familyName) {
        return switch (property.propertyType()) {
            case VACANT_STOREFRONT -> familyName + " Market Wholesale of " + settlement.displayName();
            case WAREHOUSE -> familyName + " Warehouse Wholesale of " + settlement.displayName();
            case FAMILY_BUTCHER_SHOP, LOCKER_PLANT, INDUSTRIAL_BUILDING, EMPTY_COMMERCIAL_LOT, DISTRIBUTION_CENTER,
                 COLD_STORAGE_FACILITY -> familyName + " Wholesale Supply";
        };
    }

    private static String historicalSummary(
            Region region,
            Settlement settlement,
            String displayName,
            BusinessType type,
            int foundingYear,
            BusinessStatus status,
            BusinessReputation reputation
    ) {
        String purpose = switch (type) {
            case FAMILY_BUTCHER_SHOP -> "as a family-owned butcher shop serving local households and ranch families";
            case RETAIL_MEAT_MARKET -> "as a retail meat market built around counter service and prepared cuts";
            case CUSTOM_PROCESSOR -> "as a custom processor serving nearby farms, ranches, and independent shops";
            case REGIONAL_PROCESSING_COMPANY -> "as a regional processing company designed for larger commercial accounts";
            case LOCKER_PLANT -> "as a locker plant supporting community cold storage and custom meat service";
            case COLD_STORAGE_COMPANY -> "as a cold storage company built around reliable refrigerated capacity";
            case FOOD_DISTRIBUTION_COMPANY -> "as a distribution company linking processors, retailers, and regional routes";
            case WHOLESALE_SUPPLIER -> "as a wholesale supplier supporting commercial shops across the county";
        };
        String outcome = switch (status) {
            case OPERATING -> "It remains part of the active commercial record with a " + reputation.serializedName() + " reputation";
            case SEASONAL -> "It is recorded as seasonal, preserving its role in local commercial history";
            case RELOCATED -> "It has relocation history without changing the identity of the original organization";
            case MERGED -> "It later became part of a larger commercial lineage while preserving its archived identity";
            case BANKRUPT -> "It is preserved as a bankruptcy-era record for future historical systems";
            case CLOSED -> "It is preserved as a closed business record rather than being erased from local history";
            case VACANT_RECORD -> "It represents the last known commercial record attached to a vacant site";
        };
        return "Founded in " + foundingYear + " " + purpose + " in " + settlement.displayName()
                + " within the " + region.displayName() + ". " + outcome + ".";
    }

    private static Optional<CommercialPropertyId> corporateHeadquarters(BusinessType type, CommercialPropertyId propertyId) {
        return switch (type) {
            case REGIONAL_PROCESSING_COMPANY, FOOD_DISTRIBUTION_COMPANY, COLD_STORAGE_COMPANY -> Optional.of(propertyId);
            case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET, CUSTOM_PROCESSOR, LOCKER_PLANT, WHOLESALE_SUPPLIER -> Optional.empty();
        };
    }

    private static BusinessOwnershipModel ownershipModel(
            long worldSeed,
            String propertyId,
            int foundingYear,
            BusinessStatus status,
            BusinessType type
    ) {
        BusinessOwnershipType ownershipType = ownershipType(worldSeed, propertyId, status, type);
        String ownerName = ownerName(worldSeed, propertyId, ownershipType);
        return new BusinessOwnershipModel(
                ownerName,
                ownershipType,
                foundingYear,
                "Ownership metadata is architectural world identity only; no player ownership or economy behavior is attached."
        );
    }

    private static BusinessOwnershipType ownershipType(
            long worldSeed,
            String propertyId,
            BusinessStatus status,
            BusinessType type
    ) {
        if (status == BusinessStatus.BANKRUPT || status == BusinessStatus.VACANT_RECORD) {
            return BusinessOwnershipType.BANK_MANAGED;
        }
        List<BusinessOwnershipType> ownershipTypes = switch (type) {
            case FAMILY_BUTCHER_SHOP, RETAIL_MEAT_MARKET, CUSTOM_PROCESSOR -> List.of(
                    BusinessOwnershipType.FAMILY,
                    BusinessOwnershipType.INDEPENDENT_OPERATOR
            );
            case LOCKER_PLANT -> List.of(BusinessOwnershipType.COOPERATIVE, BusinessOwnershipType.FAMILY, BusinessOwnershipType.REGIONAL_COMPANY);
            case REGIONAL_PROCESSING_COMPANY, COLD_STORAGE_COMPANY, FOOD_DISTRIBUTION_COMPANY -> List.of(
                    BusinessOwnershipType.REGIONAL_COMPANY,
                    BusinessOwnershipType.COOPERATIVE
            );
            case WHOLESALE_SUPPLIER -> List.of(
                    BusinessOwnershipType.INDEPENDENT_OPERATOR,
                    BusinessOwnershipType.REGIONAL_COMPANY,
                    BusinessOwnershipType.COOPERATIVE
            );
        };
        return ownershipTypes.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                NAME_SALT,
                ownershipTypes.size(),
                propertyId,
                type.serializedName(),
                "ownership"
        ));
    }

    private static String ownerName(long worldSeed, String propertyId, BusinessOwnershipType ownershipType) {
        List<String> pool = switch (ownershipType) {
            case FAMILY -> familyNames().stream().map(name -> name + " Family").toList();
            case INDEPENDENT_OPERATOR -> List.of("Independent Local Operators", "Market District Partners", "County Shopkeepers Office");
            case COOPERATIVE -> List.of("Regional Producers Cooperative", "County Locker Cooperative", "Market Families Cooperative");
            case REGIONAL_COMPANY -> List.of("Regional Commercial Holdings", "Commonwealth Food Group", "Settlement Processing Group");
            case ESTATE -> List.of("Family Estate Office", "Local Estate Trustees", "Heritage Estate Company");
            case BANK_MANAGED -> List.of("First Market Bank", "County Property Trust", "Regional Receivership Office");
        };
        return pool.get(WorldIdentityDeterminism.stableIndex(worldSeed, NAME_SALT, pool.size(), propertyId, ownershipType.serializedName()));
    }

    private static List<String> preferredManufacturerIds(long worldSeed, String propertyId, BusinessType type) {
        ManufacturerCategory category = manufacturerCategory(type);
        List<Manufacturer> manufacturers = BuiltInManufacturerCatalog.manufacturers().stream()
                .filter(manufacturer -> manufacturer.servesCategory(category))
                .sorted(Comparator.comparing(Manufacturer::id))
                .toList();
        if (manufacturers.isEmpty()) {
            return List.of();
        }
        Manufacturer selected = manufacturers.get(WorldIdentityDeterminism.stableIndex(
                worldSeed,
                MANUFACTURER_SALT,
                manufacturers.size(),
                propertyId,
                type.serializedName(),
                category.serializedName()
        ));
        return List.of(selected.id());
    }

    private static ManufacturerCategory manufacturerCategory(BusinessType type) {
        return switch (type) {
            case FAMILY_BUTCHER_SHOP, CUSTOM_PROCESSOR, REGIONAL_PROCESSING_COMPANY, LOCKER_PLANT ->
                    ManufacturerCategory.PROCESSING_EQUIPMENT;
            case COLD_STORAGE_COMPANY -> ManufacturerCategory.REFRIGERATION;
            case FOOD_DISTRIBUTION_COMPANY -> ManufacturerCategory.MATERIAL_HANDLING;
            case RETAIL_MEAT_MARKET, WHOLESALE_SUPPLIER -> ManufacturerCategory.PACKAGING;
        };
    }

    private static <T> T stableChoice(long worldSeed, String propertyId, List<T> values) {
        return values.get(WorldIdentityDeterminism.stableIndex(worldSeed, TYPE_SALT, values.size(), propertyId));
    }

    private static List<String> familyNames() {
        return List.of("Alder", "Benton", "Carver", "Dawson", "Ellis", "Harlan", "Mercer", "Whitcomb");
    }
}
