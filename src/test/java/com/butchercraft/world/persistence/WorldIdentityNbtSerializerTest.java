package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldIdentityNbtSerializerTest {
    @Test
    void worldIdentityRoundTripsThroughNbt() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(123L);

        CompoundTag saved = WorldIdentityNbtSerializer.save(identity);
        WorldIdentity restored = WorldIdentityNbtSerializer.load(saved);

        assertEquals(identity, restored);
        assertEquals(identity.region().description(), restored.region().description());
        assertEquals(identity.region().culturalIdentity(), restored.region().culturalIdentity());
        assertEquals(identity.region().namingProfileId(), restored.region().namingProfileId());
    }

    @Test
    void serializerRejectsUnsupportedSchemaVersion() {
        CompoundTag saved = WorldIdentityNbtSerializer.save(new WorldIdentityGenerator().generate(123L));
        saved.putInt("schema_version", WorldIdentity.CURRENT_SCHEMA_VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> WorldIdentityNbtSerializer.load(saved));
    }

    @Test
    void serializerRejectsMissingRequiredFields() {
        CompoundTag saved = WorldIdentityNbtSerializer.save(new WorldIdentityGenerator().generate(123L));
        saved.remove("region");

        assertThrows(IllegalArgumentException.class, () -> WorldIdentityNbtSerializer.load(saved));
    }

    @Test
    void legacyPhaseOneWorldIdentityMigratesToCurrentSchema() {
        CompoundTag legacy = legacyPhaseOneTag();

        WorldIdentity migrated = WorldIdentityNbtSerializer.load(legacy);

        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals("legacy_world", migrated.id());
        assertEquals(987L, migrated.worldSeed());
        assertEquals("legacy_region", migrated.region().id());
        assertEquals("Legacy Phase 1 development region migrated to the version 2 world identity schema.",
                migrated.region().description());
        assertEquals("Legacy naming", migrated.region().culturalIdentity());
        assertEquals("legacy_phase_1", migrated.region().namingProfileId());
        assertEquals("Legacy County", migrated.counties().getFirst().displayName());
        assertEquals("Legacy Village", migrated.settlements().getFirst().displayName());
    }

    private static CompoundTag legacyPhaseOneTag() {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 1);
        root.putString("id", "legacy_world");
        root.putLong("world_seed", 987L);

        CompoundTag region = new CompoundTag();
        region.putString("id", "legacy_region");
        region.putString("display_name", "Legacy Region");
        region.putString("agricultural_identity", "Legacy agriculture");
        region.putString("economic_identity", "Legacy economy");
        region.putString("naming_convention", "Legacy naming");
        root.put("region", region);

        CompoundTag settlement = new CompoundTag();
        settlement.putString("id", "legacy_settlement");
        settlement.putString("display_name", "Legacy Village");
        settlement.putString("county_id", "legacy_county");
        settlement.putString("type", "village");

        ListTag settlements = new ListTag();
        settlements.add(settlement);

        CompoundTag county = new CompoundTag();
        county.putString("id", "legacy_county");
        county.putString("display_name", "Legacy County");
        county.putString("region_id", "legacy_region");
        county.put("settlements", settlements);

        ListTag counties = new ListTag();
        counties.add(county);
        root.put("counties", counties);
        return root;
    }
}
