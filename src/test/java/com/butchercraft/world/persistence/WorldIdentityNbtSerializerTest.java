package com.butchercraft.world.persistence;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import net.minecraft.nbt.CompoundTag;
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
}
