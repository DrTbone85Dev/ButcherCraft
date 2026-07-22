package com.butchercraft.world.player.runtime;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.player.PlayerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityFactoryTest {
    private final PlayerIdentityFactory factory = new PlayerIdentityFactory();

    @Test
    void firstTimeIdentityIsDeterministicPerWorldSeedAndMinecraftUuid() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(9090L);
        UUID minecraftUuid = UUID.nameUUIDFromBytes("phase-nine-player".getBytes());

        PlayerIdentityRecord first = factory.createFirstTimeIdentity(minecraftUuid, worldIdentity);
        PlayerIdentityRecord second = factory.createFirstTimeIdentity(minecraftUuid, worldIdentity);

        assertEquals(first, second);
        assertEquals(PlayerIdentityRuntimeSchema.CURRENT_VERSION, first.schemaVersion());
        assertEquals(minecraftUuid, first.minecraftUuid());
        PlayerIdentityRegistry.of(List.of(first)).validateReferences(worldIdentity, PlayerRegistry.builtIn());
    }

    @Test
    void identityChangesWhenWorldSeedChanges() {
        UUID minecraftUuid = UUID.nameUUIDFromBytes("same-player-different-world".getBytes());

        PlayerIdentityRecord first = factory.createFirstTimeIdentity(minecraftUuid, new WorldIdentityGenerator().generate(100L));
        PlayerIdentityRecord second = factory.createFirstTimeIdentity(minecraftUuid, new WorldIdentityGenerator().generate(200L));

        assertNotEquals(first.identityId(), second.identityId());
    }

    @Test
    void factoryCreatesReferenceOnlyRuntimeIdentity() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(5151L);
        PlayerIdentityRecord identity = factory.createFirstTimeIdentity(
                UUID.nameUUIDFromBytes("reference-only-player".getBytes()),
                worldIdentity
        );

        assertTrue(PlayerRegistry.builtIn().contains(identity.startingScenarioId()));
        assertTrue(worldIdentity.settlements().stream().anyMatch(settlement -> settlement.id().equals(identity.settlementId())));
        identity.commercialPropertyId().ifPresent(propertyId ->
                assertTrue(worldIdentity.commercialProperties().stream().anyMatch(property -> property.id().equals(propertyId))));
        identity.businessId().ifPresent(businessId ->
                assertTrue(worldIdentity.businesses().stream().anyMatch(business -> business.id().equals(businessId))));
        identity.familyId().ifPresent(familyId ->
                assertTrue(worldIdentity.families().stream().anyMatch(family -> family.id().equals(familyId))));
        identity.ownershipEntityId().ifPresent(entityId ->
                assertTrue(worldIdentity.ownershipEntities().stream().anyMatch(entity -> entity.id().equals(entityId))));
    }
}
