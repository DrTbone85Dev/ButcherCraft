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
        assertEquals(identity.commercialProperties(), restored.commercialProperties());
        assertEquals(identity.businesses(), restored.businesses());
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
        assertEquals("Legacy Phase 1 development region migrated to the version 4 world identity schema.",
                migrated.region().description());
        assertEquals("Legacy naming", migrated.region().culturalIdentity());
        assertEquals("legacy_phase_1", migrated.region().namingProfileId());
        assertEquals("Legacy County", migrated.counties().getFirst().displayName());
        assertEquals("Legacy Village", migrated.settlements().getFirst().displayName());
        assertEquals(4, migrated.commercialProperties().size());
        assertEquals(4, migrated.businesses().size());
        assertEquals("legacy_settlement", migrated.commercialProperties().getFirst().settlementId());
        assertEquals("legacy_settlement", migrated.businesses().getFirst().primarySettlementId());
    }

    @Test
    void legacyPhaseTwoWorldIdentityMigratesWithGeneratedCommercialProperties() {
        WorldIdentity phaseTwoIdentity = new WorldIdentityGenerator().generate(456L);
        CompoundTag phaseTwo = WorldIdentityNbtSerializer.save(phaseTwoIdentity);
        phaseTwo.putInt("schema_version", 2);
        phaseTwo.remove("commercial_properties");

        WorldIdentity migrated = WorldIdentityNbtSerializer.load(phaseTwo);

        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(phaseTwoIdentity.id(), migrated.id());
        assertEquals(phaseTwoIdentity.region(), migrated.region());
        assertEquals(phaseTwoIdentity.counties(), migrated.counties());
        assertEquals(phaseTwoIdentity.commercialProperties(), migrated.commercialProperties());
        assertEquals(phaseTwoIdentity.businesses(), migrated.businesses());
    }

    @Test
    void legacyPhaseThreeWorldIdentityMigratesWithGeneratedBusinesses() {
        WorldIdentity phaseThreeIdentity = new WorldIdentityGenerator().generate(789L);
        CompoundTag phaseThree = WorldIdentityNbtSerializer.save(phaseThreeIdentity);
        phaseThree.putInt("schema_version", 3);
        phaseThree.remove("businesses");

        WorldIdentity migrated = WorldIdentityNbtSerializer.load(phaseThree);

        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(phaseThreeIdentity.id(), migrated.id());
        assertEquals(phaseThreeIdentity.region(), migrated.region());
        assertEquals(phaseThreeIdentity.counties(), migrated.counties());
        assertEquals(phaseThreeIdentity.commercialProperties(), migrated.commercialProperties());
        assertEquals(phaseThreeIdentity.businesses(), migrated.businesses());
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
