package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.County;
import com.butchercraft.world.identity.Region;
import com.butchercraft.world.identity.Settlement;
import com.butchercraft.world.identity.SettlementType;
import com.butchercraft.world.identity.WorldIdentity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public final class WorldIdentityNbtSerializer {
    private static final String SCHEMA_VERSION = "schema_version";
    private static final String ID = "id";
    private static final String WORLD_SEED = "world_seed";
    private static final String REGION = "region";
    private static final String COUNTIES = "counties";
    private static final String DISPLAY_NAME = "display_name";
    private static final String AGRICULTURAL_IDENTITY = "agricultural_identity";
    private static final String ECONOMIC_IDENTITY = "economic_identity";
    private static final String NAMING_CONVENTION = "naming_convention";
    private static final String REGION_ID = "region_id";
    private static final String SETTLEMENTS = "settlements";
    private static final String COUNTY_ID = "county_id";
    private static final String TYPE = "type";

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
        return tag;
    }

    public static WorldIdentity load(CompoundTag tag) {
        require(tag, SCHEMA_VERSION, Tag.TAG_INT);
        int schemaVersion = tag.getInt(SCHEMA_VERSION);
        if (schemaVersion != WorldIdentity.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported world identity schema version: " + schemaVersion);
        }
        require(tag, ID, Tag.TAG_STRING);
        require(tag, WORLD_SEED, Tag.TAG_LONG);
        require(tag, REGION, Tag.TAG_COMPOUND);
        require(tag, COUNTIES, Tag.TAG_LIST);
        return new WorldIdentity(
                schemaVersion,
                tag.getString(ID),
                tag.getLong(WORLD_SEED),
                loadRegion(tag.getCompound(REGION)),
                loadCounties(tag.getList(COUNTIES, Tag.TAG_COMPOUND))
        );
    }

    private static CompoundTag saveRegion(Region region) {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID, region.id());
        tag.putString(DISPLAY_NAME, region.displayName());
        tag.putString(AGRICULTURAL_IDENTITY, region.agriculturalIdentity());
        tag.putString(ECONOMIC_IDENTITY, region.economicIdentity());
        tag.putString(NAMING_CONVENTION, region.namingConvention());
        return tag;
    }

    private static Region loadRegion(CompoundTag tag) {
        require(tag, ID, Tag.TAG_STRING);
        require(tag, DISPLAY_NAME, Tag.TAG_STRING);
        require(tag, AGRICULTURAL_IDENTITY, Tag.TAG_STRING);
        require(tag, ECONOMIC_IDENTITY, Tag.TAG_STRING);
        require(tag, NAMING_CONVENTION, Tag.TAG_STRING);
        return new Region(
                tag.getString(ID),
                tag.getString(DISPLAY_NAME),
                tag.getString(AGRICULTURAL_IDENTITY),
                tag.getString(ECONOMIC_IDENTITY),
                tag.getString(NAMING_CONVENTION)
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

    private static void require(CompoundTag tag, String key, int type) {
        if (!tag.contains(key, type)) {
            throw new IllegalArgumentException("Missing or malformed world identity field: " + key);
        }
    }
}
