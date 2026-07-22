package com.butchercraft.world.player;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRegistryTest {
    private final PlayerRegistry registry = PlayerRegistry.builtIn();

    @Test
    void registryFindsScenarioByIdTypeAndCareerProfile() {
        StartingScenario scenario = registry.find("inherited_family_business").orElseThrow();

        assertTrue(registry.contains(scenario.id()));
        assertEquals(scenario, registry.find(scenario.id()).orElseThrow());
        assertTrue(registry.findByScenarioType(scenario.scenarioType()).contains(scenario));
        assertTrue(registry.findByCareerProfile(CareerProfile.FAMILY_SUCCESSOR).contains(scenario));
        assertFalse(registry.findByCareerProfile(CareerProfile.COUNTY_PROCESSOR).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.startingScenarios().clear());
    }

    @Test
    void registryValidatesAPlayerIdentityAgainstWorldSettlementsAndScenarioCompatibility() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(42L);
        StartingScenario scenario = registry.find("inherited_family_business").orElseThrow();
        PlayerIdentity playerIdentity = playerIdentity(
                scenario,
                CareerProfile.FAMILY_SUCCESSOR,
                worldIdentity.settlements().getFirst().id()
        );

        registry.validateIdentity(playerIdentity, worldIdentity.settlements());
    }

    @Test
    void registryRejectsUnknownIdentityScenarioCareerAndSettlementReferences() {
        WorldIdentity worldIdentity = new WorldIdentityGenerator().generate(42L);
        StartingScenario scenario = registry.find("inherited_family_business").orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> registry.validateIdentity(
                playerIdentityWithScenario(new StartingScenarioId("missing_scenario"), scenario, worldIdentity.settlements().getFirst().id()),
                worldIdentity.settlements()
        ));
        assertThrows(IllegalArgumentException.class, () -> registry.validateIdentity(
                playerIdentity(scenario, CareerProfile.CORPORATE_MANAGER, worldIdentity.settlements().getFirst().id()),
                worldIdentity.settlements()
        ));
        assertThrows(IllegalArgumentException.class, () -> registry.validateIdentity(
                playerIdentity(scenario, CareerProfile.FAMILY_SUCCESSOR, "missing_settlement"),
                worldIdentity.settlements()
        ));
    }

    private static PlayerIdentity playerIdentity(
            StartingScenario scenario,
            CareerProfile careerProfile,
            String settlementId
    ) {
        return playerIdentityWithScenario(scenario.id(), scenario, settlementId, careerProfile);
    }

    private static PlayerIdentity playerIdentityWithScenario(
            StartingScenarioId scenarioId,
            StartingScenario scenario,
            String settlementId
    ) {
        return playerIdentityWithScenario(scenarioId, scenario, settlementId, CareerProfile.FAMILY_SUCCESSOR);
    }

    private static PlayerIdentity playerIdentityWithScenario(
            StartingScenarioId scenarioId,
            StartingScenario scenario,
            String settlementId,
            CareerProfile careerProfile
    ) {
        return new PlayerIdentity(
                new PlayerIdentityId("test_player_identity"),
                scenario.displayName() + " Character Name",
                careerProfile,
                scenarioId,
                settlementId,
                scenario.initialLegacyProgress().legacySummary(),
                new PlayerBackground(
                        scenario.displayName() + " Character Name",
                        scenario.backgroundSummary(),
                        scenario.initialLegacyProgress().legacySummary()
                ),
                scenario.initialReputation(),
                scenario.startingRelationships()
        );
    }
}
