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
}
