package com.butchercraft.world.player.runtime;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.ownership.FamilyId;
import com.butchercraft.world.ownership.OwnershipEntityId;
import com.butchercraft.world.player.CareerProfile;
import com.butchercraft.world.player.PlayerIdentityId;
import com.butchercraft.world.player.PlayerRegistry;
import com.butchercraft.world.player.StartingScenario;
import com.butchercraft.world.player.StartingScenarioId;
import com.butchercraft.world.property.CommercialPropertyId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerIdentityRegistryValidationTest {
    private final WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(9001L);
    private final PlayerRegistry startingScenarioRegistry = PlayerRegistry.builtIn();
    private final PlayerIdentityFactory factory = new PlayerIdentityFactory(startingScenarioRegistry);
    private final PlayerIdentityRecord valid = factory.createFirstTimeIdentity(
            UUID.nameUUIDFromBytes("valid-runtime-player".getBytes(StandardCharsets.UTF_8)),
            worldIdentity
    );

    @Test
    void duplicateMinecraftUuidIsRejected() {
        PlayerIdentityRecord duplicate = copy(valid, valid.minecraftUuid(), new PlayerIdentityId(valid.identityId().value() + "_other"));

        assertThrows(IllegalArgumentException.class, () -> PlayerIdentityRegistry.of(List.of(valid, duplicate)));
    }

    @Test
    void duplicatePlayerIdentityIdIsRejected() {
        PlayerIdentityRecord duplicate = copy(
                valid,
                UUID.nameUUIDFromBytes("duplicate-identity-id".getBytes(StandardCharsets.UTF_8)),
                valid.identityId()
        );

        assertThrows(IllegalArgumentException.class, () -> PlayerIdentityRegistry.of(List.of(valid, duplicate)));
    }

    @Test
    void validRegistryPreservesDeterministicUuidOrder() {
        PlayerIdentityRecord second = factory.createFirstTimeIdentity(
                UUID.nameUUIDFromBytes("second-valid-runtime-player".getBytes(StandardCharsets.UTF_8)),
                worldIdentity
        );

        PlayerIdentityRegistry registry = PlayerIdentityRegistry.of(List.of(second, valid));

        assertEquals(registry.identities().stream()
                .map(identity -> identity.minecraftUuid().toString())
                .sorted()
                .toList(), registry.identities().stream().map(identity -> identity.minecraftUuid().toString()).toList());
    }

    @Test
    void missingSettlementIsRejected() {
        PlayerIdentityRecord invalid = copy(valid, "missing_settlement");

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void missingCommercialPropertyIsRejected() {
        PlayerIdentityRecord invalid = copyWithReferences(
                valid,
                Optional.of(new CommercialPropertyId("missing_property")),
                valid.businessId(),
                valid.ownershipEntityId(),
                valid.familyId()
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void missingBusinessIsRejected() {
        PlayerIdentityRecord invalid = copyWithReferences(
                valid,
                valid.commercialPropertyId(),
                Optional.of(new BusinessId("missing_business")),
                valid.ownershipEntityId(),
                valid.familyId()
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void missingFamilyIsRejected() {
        PlayerIdentityRecord invalid = copyWithReferences(
                valid,
                valid.commercialPropertyId(),
                valid.businessId(),
                valid.ownershipEntityId(),
                Optional.of(new FamilyId("missing_family"))
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void missingOwnershipEntityIsRejected() {
        PlayerIdentityRecord invalid = copyWithReferences(
                valid,
                valid.commercialPropertyId(),
                valid.businessId(),
                Optional.of(new OwnershipEntityId("missing_ownership_entity")),
                valid.familyId()
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void invalidStartingScenarioIsRejected() {
        PlayerIdentityRecord invalid = new PlayerIdentityRecord(
                valid.minecraftUuid(),
                valid.identityId(),
                new StartingScenarioId("missing_starting_scenario"),
                valid.careerProfile(),
                valid.settlementId(),
                valid.commercialPropertyId(),
                valid.businessId(),
                valid.ownershipEntityId(),
                valid.familyId(),
                valid.creationTimestamp(),
                valid.schemaVersion()
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    @Test
    void invalidCareerProfileForScenarioIsRejected() {
        StartingScenario scenario = startingScenarioRegistry.find(valid.startingScenarioId()).orElseThrow();
        CareerProfile unsupported = Arrays.stream(CareerProfile.values())
                .filter(profile -> !scenario.careerProfiles().contains(profile))
                .findFirst()
                .orElseThrow();
        PlayerIdentityRecord invalid = new PlayerIdentityRecord(
                valid.minecraftUuid(),
                valid.identityId(),
                valid.startingScenarioId(),
                unsupported,
                valid.settlementId(),
                valid.commercialPropertyId(),
                valid.businessId(),
                valid.ownershipEntityId(),
                valid.familyId(),
                valid.creationTimestamp(),
                valid.schemaVersion()
        );

        assertThrows(IllegalArgumentException.class,
                () -> PlayerIdentityRegistry.of(List.of(invalid)).validateReferences(worldIdentity, startingScenarioRegistry));
    }

    private static PlayerIdentityRecord copy(PlayerIdentityRecord source, UUID uuid, PlayerIdentityId identityId) {
        return new PlayerIdentityRecord(
                uuid,
                identityId,
                source.startingScenarioId(),
                source.careerProfile(),
                source.settlementId(),
                source.commercialPropertyId(),
                source.businessId(),
                source.ownershipEntityId(),
                source.familyId(),
                source.creationTimestamp(),
                source.schemaVersion()
        );
    }

    private static PlayerIdentityRecord copy(PlayerIdentityRecord source, String settlementId) {
        return new PlayerIdentityRecord(
                source.minecraftUuid(),
                source.identityId(),
                source.startingScenarioId(),
                source.careerProfile(),
                settlementId,
                source.commercialPropertyId(),
                source.businessId(),
                source.ownershipEntityId(),
                source.familyId(),
                source.creationTimestamp(),
                source.schemaVersion()
        );
    }

    private static PlayerIdentityRecord copyWithReferences(
            PlayerIdentityRecord source,
            Optional<CommercialPropertyId> propertyId,
            Optional<BusinessId> businessId,
            Optional<OwnershipEntityId> ownershipEntityId,
            Optional<FamilyId> familyId
    ) {
        return new PlayerIdentityRecord(
                source.minecraftUuid(),
                source.identityId(),
                source.startingScenarioId(),
                source.careerProfile(),
                source.settlementId(),
                propertyId,
                businessId,
                ownershipEntityId,
                familyId,
                source.creationTimestamp(),
                source.schemaVersion()
        );
    }
}
