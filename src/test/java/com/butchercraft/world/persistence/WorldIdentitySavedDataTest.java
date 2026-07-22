package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldIdentitySavedDataTest {
    @Test
    void generatedSavedDataIsDirtySoNewIdentityPersistsWithWorld() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(222L);

        WorldIdentitySavedData savedData = WorldIdentitySavedData.generated(identity);

        assertTrue(savedData.isDirty());
        assertEquals(identity, savedData.identity());
    }

    @Test
    void savedDataSavesAndLoadsWorldIdentity() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(333L);
        WorldIdentitySavedData savedData = new WorldIdentitySavedData(identity);

        CompoundTag tag = savedData.save(new CompoundTag(), RegistryAccess.EMPTY);
        WorldIdentitySavedData restored = WorldIdentitySavedData.load(tag, RegistryAccess.EMPTY);

        assertEquals(identity, restored.identity());
    }

    @Test
    void savedDataMarksLegacyPhaseOneIdentityDirtyAfterMigration() {
        WorldIdentity identity = new WorldIdentityGenerator().generate(444L);
        CompoundTag legacy = WorldIdentityNbtSerializer.save(identity);
        legacy.putInt("schema_version", 1);
        CompoundTag legacyRegion = legacy.getCompound("region");
        legacyRegion.putString("naming_convention", identity.region().culturalIdentity());
        legacyRegion.remove("description");
        legacyRegion.remove("cultural_identity");
        legacyRegion.remove("naming_profile_id");

        WorldIdentitySavedData restored = WorldIdentitySavedData.load(legacy, RegistryAccess.EMPTY);

        assertTrue(restored.isDirty());
        assertEquals(WorldIdentity.CURRENT_SCHEMA_VERSION, restored.identity().schemaVersion());
        assertEquals(identity.commercialProperties(), restored.identity().commercialProperties());
        assertEquals(identity.businesses(), restored.identity().businesses());
        assertEquals(identity.families(), restored.identity().families());
        assertEquals(identity.historicalPersons(), restored.identity().historicalPersons());
        assertEquals(identity.ownershipEntities(), restored.identity().ownershipEntities());
        assertEquals(identity.ownershipHistories(), restored.identity().ownershipHistories());
    }
}
