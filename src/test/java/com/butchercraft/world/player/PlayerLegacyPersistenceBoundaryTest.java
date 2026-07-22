package com.butchercraft.world.player;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.persistence.WorldIdentityNbtSerializer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PlayerLegacyPersistenceBoundaryTest {
    @Test
    void playerLegacyDoesNotChangeWorldIdentitySaveSchemaYet() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(123L);

        CompoundTag saved = WorldIdentityNbtSerializer.save(identity);

        assertEquals(6, saved.getInt("schema_version"));
        assertFalse(saved.contains("player_identity"));
        assertFalse(saved.contains("player_legacy"));
        assertFalse(saved.contains("starting_scenario"));
        assertEquals(identity, WorldIdentityNbtSerializer.load(saved));
    }

    @Test
    void legacyWorldIdentityMigrationDoesNotRequirePlayerIdentityData() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(456L);
        CompoundTag phaseFive = WorldIdentityNbtSerializer.save(identity);
        phaseFive.putInt("schema_version", 5);
        phaseFive.remove("supply_network");

        WorldIdentity migrated = WorldIdentityNbtSerializer.load(phaseFive);

        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, migrated.schemaVersion());
        assertEquals(identity.supplyNetwork(), migrated.supplyNetwork());
    }
}
