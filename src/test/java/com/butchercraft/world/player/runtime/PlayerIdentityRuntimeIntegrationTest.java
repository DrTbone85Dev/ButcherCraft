package com.butchercraft.world.player.runtime;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.persistence.WorldIdentityNbtSerializer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityRuntimeIntegrationTest {
    @Test
    void modRegistersPlayerJoinInitializer() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/ButcherCraft.java"));

        assertTrue(source.contains("PlayerJoinInitializer.INSTANCE::initialize"));
    }

    @Test
    void playerJoinInitializerUsesServerSidePlayerLogin() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/player/runtime/PlayerJoinInitializer.java"
        ));

        assertTrue(source.contains("PlayerEvent.PlayerLoggedInEvent"));
        assertTrue(source.contains("ServerPlayer"));
        assertTrue(source.contains("WorldIdentityService"));
        assertTrue(source.contains("PlayerIdentityRuntimeSchema.FILE_NAME"));
    }

    @Test
    void worldIdentitySchemaRemainsUnchangedAndDoesNotStorePlayerIdentities() {
        CompoundTag savedWorldIdentity = WorldIdentityNbtSerializer.save(
                new com.butchercraft.world.identity.WorldIdentityGenerator().generate(9100L)
        );

        assertEquals(6, WorldIdentity.CURRENT_SCHEMA_VERSION);
        assertFalse(savedWorldIdentity.contains("player_identities"));
        assertFalse(savedWorldIdentity.contains("player_identity"));
    }
}
