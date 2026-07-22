package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.County;
import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.SettlementType;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.property.BuiltInCommercialPropertyCatalog;
import com.butchercraft.world.property.BuildingSize;
import com.butchercraft.world.property.CommercialProperty;
import com.butchercraft.world.property.CommercialPropertyId;
import com.butchercraft.world.property.CommercialPropertyType;
import com.butchercraft.world.property.ElectricalService;
import com.butchercraft.world.property.ExpansionCapacity;
import com.butchercraft.world.property.LotSize;
import com.butchercraft.world.property.OwnershipRecord;
import com.butchercraft.world.property.PropertyAcquisitionMethod;
import com.butchercraft.world.property.PropertyCondition;
import com.butchercraft.world.property.PropertyHistory;
import com.butchercraft.world.property.PropertyStatus;
import com.butchercraft.world.property.RefrigerationCapacity;
import com.butchercraft.world.property.UtilityProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public final class WorldIdentityNbtSerializer {
    private static final String SCHEMA_VERSION = "schema_version";
    private static final String ID = "id";
    private static final String WORLD_SEED = "world_seed";
    private static final String REGION = "region";
    private static final String COUNTIES = "counties";
    private static final String DISPLAY_NAME = "display_name";
    private static final String DESCRIPTION = "description";
    private static final String AGRICULTURAL_IDENTITY = "agricultural_identity";
    private static final String ECONOMIC_IDENTITY = "economic_identity";
    private static final String CULTURAL_IDENTITY = "cultural_identity";
    private static final String NAMING_PROFILE_ID = "naming_profile_id";
    private static final String LEGACY_NAMING_CONVENTION = "naming_convention";
    private static final String LEGACY_PHASE_ONE_NAMING_PROFILE = "legacy_phase_1";
    private static final String REGION_ID = "region_id";
    private static final String SETTLEMENTS = "settlements";
    private static final String COUNTY_ID = "county_id";
    private static final String TYPE = "type";
    private static final String COMMERCIAL_PROPERTIES = "commercial_properties";
    private static final String SETTLEMENT_ID = "settlement_id";
    private static final String PROPERTY_TYPE = "property_type";
    private static final String CONSTRUCTION_YEAR = "construction_year";
    private static final String CONDITION = "condition";
    private static final String STATUS = "status";
    private static final String LOT_SIZE = "lot_size_square_meters";
    private static final String BUILDING_SIZE = "building_size_square_meters";
    private static final String UTILITY_PROFILE = "utility_profile";
    private static final String EXPANSION_CAPACITY = "expansion_capacity_square_meters";
    private static final String HISTORY = "history";
    private static final String HISTORICAL_SUMMARY = "historical_summary";
    private static final String OWNERSHIP_HISTORY = "ownership_history";
    private static final String OWNER_NAME = "owner_name";
    private static final String START_YEAR = "start_year";
    private static final String END_YEAR = "end_year";
    private static final String ACQUISITION_METHOD = "acquisition_method";
    private static final String HISTORICAL_NOTES = "historical_notes";
    private static final String ELECTRICAL_SERVICE = "electrical_service";
    private static final String WATER_SERVICE = "water_service";
    private static final String SEWER_SERVICE = "sewer_service";
    private static final String NATURAL_GAS = "natural_gas";
    private static final String LOADING_DOCK = "loading_dock";
    private static final String RAIL_ACCESS = "rail_access";
    private static final String HIGHWAY_ACCESS = "highway_access";
    private static final String REFRIGERATION_CAPACITY = "refrigeration_capacity";

    private WorldIdentityNbtSerializer() {
    }

    public static CompoundTag save(WorldIdentity identity) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(SCHEMA_VERSION, identity.schemaVersion());
        tag.putString(ID, identity.id());
        tag.putLong(WORLD_SEED, identity.worldSeed());
        tag.put(REGION, saveRegion(identity.region()));
        ListTag counties = new ListTag();
        for (County county : identity.counties()) {
            counties.add(saveCounty(county));
        }
        tag.put(COUNTIES, counties);
        ListTag commercialProperties = new ListTag();
        for (CommercialProperty property : identity.commercialProperties()) {
            commercialProperties.add(saveCommercialProperty(property));
        }
        tag.put(COMMERCIAL_PROPERTIES, commercialProperties);
        return tag;
    }

    public static WorldIdentity load(CompoundTag tag) {
        require(tag, SCHEMA_VERSION, Tag.TAG_INT);
        int schemaVersion = tag.getInt(SCHEMA_VERSION);
        if (schemaVersion == WorldIdentity.CURRENT_SCHEMA_VERSION) {
            return loadCurrent(tag, schemaVersion);
        }
        if (schemaVersion == 2) {
            return loadLegacyPhaseTwo(tag);
        }
        if (schemaVersion == 1) {
            return loadLegacyPhaseOne(tag);
        }
        throw new IllegalArgumentException("Unsupported world identity schema version: " + schemaVersion);
    }

    public static boolean requiresMigration(CompoundTag tag) {
        require(tag, SCHEMA_VERSION, Tag.TAG_INT);
        return tag.getInt(SCHEMA_VERSION) != WorldIdentity.CURRENT_SCHEMA_VERSION;
    }

    private static WorldIdentity loadCurrent(CompoundTag tag, int schemaVersion) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, WORLD_SEED, Tag.TAG_LONG);
        require(tag, REGION, Tag.TAG_COMPOUND);
        require(tag, COUNTIES, Tag.TAG_LIST);
        require(tag, COMMERCIAL_PROPERTIES, Tag.TAG_LIST);
        return new WorldIdentity(
                schemaVersion,
                tag.getString(ID),
                tag.getLong(WORLD_SEED),
                loadRegion(tag.getCompound(REGION)),
                loadCounties(tag.getList(COUNTIES, Tag.TAG_COMPOUND)),
                loadCommercialProperties(tag.getList(COMMERCIAL_PROPERTIES, Tag.TAG_COMPOUND))
        );
    }

    private static WorldIdentity loadLegacyPhaseTwo(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, WORLD_SEED, Tag.TAG_LONG);
        require(tag, REGION, Tag.TAG_COMPOUND);
        require(tag, COUNTIES, Tag.TAG_LIST);
        long worldSeed = tag.getLong(WORLD_SEED);
        List<County> counties = loadCounties(tag.getList(COUNTIES, Tag.TAG_COMPOUND));
        return new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                tag.getString(ID),
                worldSeed,
                loadRegion(tag.getCompound(REGION)),
                counties,
                BuiltInCommercialPropertyCatalog.generate(worldSeed, settlementsFrom(counties))
        );
    }

    private static WorldIdentity loadLegacyPhaseOne(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, WORLD_SEED, Tag.TAG_LONG);
        require(tag, REGION, Tag.TAG_COMPOUND);
        require(tag, COUNTIES, Tag.TAG_LIST);
        long worldSeed = tag.getLong(WORLD_SEED);
        List<County> counties = loadCounties(tag.getList(COUNTIES, Tag.TAG_COMPOUND));
        return new WorldIdentity(
                WorldIdentity.CURRENT_SCHEMA_VERSION,
                tag.getString(ID),
                worldSeed,
                loadLegacyPhaseOneRegion(tag.getCompound(REGION)),
                counties,
                BuiltInCommercialPropertyCatalog.generate(worldSeed, settlementsFrom(counties))
        );
    }

    private static CompoundTag saveRegion(Region region) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, region.id());
        tag.putString(DISPLAY_NAME, region.displayName());
        tag.putString(DESCRIPTION, region.description());
        tag.putString(AGRICULTURAL_IDENTITY, region.agriculturalIdentity());
        tag.putString(ECONOMIC_IDENTITY, region.economicIdentity());
        tag.putString(CULTURAL_IDENTITY, region.culturalIdentity());
        tag.putString(NAMING_PROFILE_ID, region.namingProfileId());
        return tag;
    }

    private static Region loadRegion(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, DESCRIPTION, Tag.TAG_STRING);
        require(tag, AGRICULTURAL_IDENTITY, Tag.TAG_STRING);
        require(tag, ECONOMIC_IDENTITY, Tag.TAG_STRING);
        require(tag, CULTURAL_IDENTITY, Tag.TAG_STRING);
        require(tag, NAMING_PROFILE_ID, Tag.TAG_STRING);
        return new Region(
                tag.getString(ID),
                tag.getString(DISPLAY_NAME),
                tag.getString(DESCRIPTION),
                tag.getString(AGRICULTURAL_IDENTITY),
                tag.getString(ECONOMIC_IDENTITY),
                tag.getString(CULTURAL_IDENTITY),
                tag.getString(NAMING_PROFILE_ID)
        );
    }

    private static Region loadLegacyPhaseOneRegion(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, AGRICULTURAL_IDENTITY, Tag.TAG_STRING);
        require(tag, ECONOMIC_IDENTITY, Tag.TAG_STRING);
        require(tag, LEGACY_NAMING_CONVENTION, Tag.TAG_STRING);
        return new Region(
                tag.getString(ID),
                tag.getString(DISPLAY_NAME),
                "Legacy Phase 1 development region migrated to the version 3 world identity schema.",
                tag.getString(AGRICULTURAL_IDENTITY),
                tag.getString(ECONOMIC_IDENTITY),
                tag.getString(LEGACY_NAMING_CONVENTION),
                LEGACY_PHASE_ONE_NAMING_PROFILE
        );
    }

    private static CompoundTag saveCounty(County county) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, county.id());
        tag.putString(DISPLAY_NAME, county.displayName());
        tag.putString(REGION_ID, county.regionId());
        ListTag settlements = new ListTag();
        for (Settlement settlement : county.settlements()) {
            settlements.add(saveSettlement(settlement));
        }
        tag.put(SETTLEMENTS, settlements);
        return tag;
    }

    private static County loadCounty(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, REGION_ID, Tag.TAG_STRING);
        require(tag, SETTLEMENTS, Tag.TAG_LIST);
        return new County(
                tag.getString(ID),
                tag.getString(DISPLAY_NAME),
                tag.getString(REGION_ID),
                loadSettlements(tag.getList(SETTLEMENTS, Tag.TAG_COMPOUND))
        );
    }

    private static CompoundTag saveSettlement(Settlement settlement) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, settlement.id());
        tag.putString(DISPLAY_NAME, settlement.displayName());
        tag.putString(COUNTY_ID, settlement.countyId());
        tag.putString(TYPE, settlement.type().serializedName());
        return tag;
    }

    private static Settlement loadSettlement(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, COUNTY_ID, Tag.TAG_STRING);
        require(tag, TYPE, Tag.TAG_STRING);
        return new Settlement(
                tag.getString(ID),
                tag.getString(DISPLAY_NAME),
                tag.getString(COUNTY_ID),
                SettlementType.fromSerializedName(tag.getString(TYPE))
        );
    }

    private static List<County> loadCounties(ListTag tags) {
        List<County> counties = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            counties.add(loadCounty(tags.getCompound(index)));
        }
        return counties;
    }

    private static List<Settlement> loadSettlements(ListTag tags) {
        List<Settlement> settlements = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            settlements.add(loadSettlement(tags.getCompound(index)));
        }
        return settlements;
    }

    private static CompoundTag saveCommercialProperty(CommercialProperty property) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, property.id().value());
        tag.putString(DISPLAY_NAME, property.displayName());
        tag.putString(SETTLEMENT_ID, property.settlementId());
        tag.putString(PROPERTY_TYPE, property.propertyType().serializedName());
        tag.putInt(CONSTRUCTION_YEAR, property.constructionYear());
        tag.putString(CONDITION, property.condition().serializedName());
        tag.putString(STATUS, property.status().serializedName());
        tag.putInt(LOT_SIZE, property.lotSize().squareMeters());
        tag.putInt(BUILDING_SIZE, property.buildingSize().squareMeters());
        tag.put(UTILITY_PROFILE, saveUtilityProfile(property.utilityProfile()));
        tag.putInt(EXPANSION_CAPACITY, property.expansionCapacity().squareMeters());
        tag.put(HISTORY, savePropertyHistory(property.history()));
        return tag;
    }

    private static CommercialProperty loadCommercialProperty(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, SETTLEMENT_ID, Tag.TAG_STRING);
        require(tag, PROPERTY_TYPE, Tag.TAG_STRING);
        require(tag, CONSTRUCTION_YEAR, Tag.TAG_INT);
        require(tag, CONDITION, Tag.TAG_STRING);
        require(tag, STATUS, Tag.TAG_STRING);
        require(tag, LOT_SIZE, Tag.TAG_INT);
        require(tag, BUILDING_SIZE, Tag.TAG_INT);
        require(tag, UTILITY_PROFILE, Tag.TAG_COMPOUND);
        require(tag, EXPANSION_CAPACITY, Tag.TAG_INT);
        require(tag, HISTORY, Tag.TAG_COMPOUND);
        return new CommercialProperty(
                new CommercialPropertyId(tag.getString(ID)),
                tag.getString(DISPLAY_NAME),
                tag.getString(SETTLEMENT_ID),
                CommercialPropertyType.fromSerializedName(tag.getString(PROPERTY_TYPE)),
                tag.getInt(CONSTRUCTION_YEAR),
                PropertyCondition.fromSerializedName(tag.getString(CONDITION)),
                PropertyStatus.fromSerializedName(tag.getString(STATUS)),
                new LotSize(tag.getInt(LOT_SIZE)),
                new BuildingSize(tag.getInt(BUILDING_SIZE)),
                loadUtilityProfile(tag.getCompound(UTILITY_PROFILE)),
                new ExpansionCapacity(tag.getInt(EXPANSION_CAPACITY)),
                loadPropertyHistory(tag.getCompound(HISTORY))
        );
    }

    private static CompoundTag saveUtilityProfile(UtilityProfile utilityProfile) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ELECTRICAL_SERVICE, utilityProfile.electricalService().serializedName());
        tag.putBoolean(WATER_SERVICE, utilityProfile.waterService());
        tag.putBoolean(SEWER_SERVICE, utilityProfile.sewerService());
        tag.putBoolean(NATURAL_GAS, utilityProfile.naturalGas());
        tag.putBoolean(LOADING_DOCK, utilityProfile.loadingDock());
        tag.putBoolean(RAIL_ACCESS, utilityProfile.railAccess());
        tag.putBoolean(HIGHWAY_ACCESS, utilityProfile.highwayAccess());
        tag.putString(REFRIGERATION_CAPACITY, utilityProfile.refrigerationCapacity().serializedName());
        return tag;
    }

    private static UtilityProfile loadUtilityProfile(CompoundTag tag) {
        require(tag, ELECTRICAL_SERVICE, Tag.TAG_STRING);
        require(tag, WATER_SERVICE, Tag.TAG_BYTE);
        require(tag, SEWER_SERVICE, Tag.TAG_BYTE);
        require(tag, NATURAL_GAS, Tag.TAG_BYTE);
        require(tag, LOADING_DOCK, Tag.TAG_BYTE);
        require(tag, RAIL_ACCESS, Tag.TAG_BYTE);
        require(tag, HIGHWAY_ACCESS, Tag.TAG_BYTE);
        require(tag, REFRIGERATION_CAPACITY, Tag.TAG_STRING);
        return new UtilityProfile(
                ElectricalService.fromSerializedName(tag.getString(ELECTRICAL_SERVICE)),
                tag.getBoolean(WATER_SERVICE),
                tag.getBoolean(SEWER_SERVICE),
                tag.getBoolean(NATURAL_GAS),
                tag.getBoolean(LOADING_DOCK),
                tag.getBoolean(RAIL_ACCESS),
                tag.getBoolean(HIGHWAY_ACCESS),
                RefrigerationCapacity.fromSerializedName(tag.getString(REFRIGERATION_CAPACITY))
        );
    }

    private static CompoundTag savePropertyHistory(PropertyHistory history) {
        CompoundTag tag = new CompoundTag();
        tag.putString(HISTORICAL_SUMMARY, history.historicalSummary());
        ListTag ownershipHistory = new ListTag();
        for (OwnershipRecord record : history.ownershipHistory()) {
            ownershipHistory.add(saveOwnershipRecord(record));
        }
        tag.put(OWNERSHIP_HISTORY, ownershipHistory);
        return tag;
    }

    private static PropertyHistory loadPropertyHistory(CompoundTag tag) {
        require(tag, HISTORICAL_SUMMARY, Tag.TAG_STRING);
        require(tag, OWNERSHIP_HISTORY, Tag.TAG_LIST);
        return new PropertyHistory(
                tag.getString(HISTORICAL_SUMMARY),
                loadOwnershipHistory(tag.getList(OWNERSHIP_HISTORY, Tag.TAG_COMPOUND))
        );
    }

    private static CompoundTag saveOwnershipRecord(OwnershipRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putString(OWNER_NAME, record.ownerName());
        tag.putInt(START_YEAR, record.startYear());
        record.endYear().ifPresent(endYear -> tag.putInt(END_YEAR, endYear));
        tag.putString(ACQUISITION_METHOD, record.acquisitionMethod().serializedName());
        tag.putString(HISTORICAL_NOTES, record.historicalNotes());
        return tag;
    }

    private static OwnershipRecord loadOwnershipRecord(CompoundTag tag) {
        require(tag, OWNER_NAME, Tag.TAG_STRING);
        require(tag, START_YEAR, Tag.TAG_INT);
        require(tag, ACQUISITION_METHOD, Tag.TAG_STRING);
        require(tag, HISTORICAL_NOTES, Tag.TAG_STRING);
        return new OwnershipRecord(
                tag.getString(OWNER_NAME),
                tag.getInt(START_YEAR),
                tag.contains(END_YEAR, Tag.TAG_INT) ? OptionalInt.of(tag.getInt(END_YEAR)) : OptionalInt.empty(),
                PropertyAcquisitionMethod.fromSerializedName(tag.getString(ACQUISITION_METHOD)),
                tag.getString(HISTORICAL_NOTES)
        );
    }

    private static List<CommercialProperty> loadCommercialProperties(ListTag tags) {
        List<CommercialProperty> properties = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            properties.add(loadCommercialProperty(tags.getCompound(index)));
        }
        return properties;
    }

    private static List<OwnershipRecord> loadOwnershipHistory(ListTag tags) {
        List<OwnershipRecord> records = new ArrayList<>();
        for (int index = 0; index < tags.size(); index++) {
            records.add(loadOwnershipRecord(tags.getCompound(index)));
        }
        return records;
    }

    private static List<Settlement> settlementsFrom(List<County> counties) {
        return counties.stream()
                .flatMap(county -> county.settlements().stream())
                .toList();
    }

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("Missing or malformed world identity field: " + key);
        }
    }
}
