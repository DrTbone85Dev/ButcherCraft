package com.butchercraft.world.player;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerScenarioCatalogTest {
    @Test
    void builtInScenarioCatalogIsDeterministicAndComplete() {
        var first = BuiltInStartingScenarioCatalog.scenarios();
        var second = BuiltInStartingScenarioCatalog.scenarios();

        assertEquals(first, second);
        assertEquals(BuiltInStartingScenarioCatalog.STARTING_SCENARIO_COUNT, first.size());
        assertEquals(StartingScenarioType.values().length, first.stream()
                .map(StartingScenario::scenarioType)
                .collect(Collectors.toSet())
                .size());
        assertEquals(CareerProfile.values().length, first.stream()
                .flatMap(scenario -> scenario.careerProfiles().stream())
                .collect(Collectors.toSet())
                .size());
        assertTrue(first.stream().allMatch(scenario -> !scenario.scenarioSummary().isBlank()));
        assertTrue(first.stream().allMatch(scenario -> !scenario.backgroundSummary().isBlank()));
        assertTrue(first.stream().allMatch(scenario -> !scenario.startingRelationships().isEmpty()));
    }

    @Test
    void builtInRegistryLoadsInStableScenarioIdOrder() {
        PlayerRegistry registry = BuiltInStartingScenarioCatalog.createRegistry();

        assertEquals(BuiltInStartingScenarioCatalog.STARTING_SCENARIO_COUNT, registry.size());
        assertEquals(
                BuiltInStartingScenarioCatalog.scenarios().stream()
                        .sorted(Comparator.comparing(scenario -> scenario.id().value()))
                        .toList(),
                registry.startingScenarios()
        );
        assertFalse(registry.findByCareerProfile(CareerProfile.FAMILY_SUCCESSOR).isEmpty());
        assertFalse(registry.findByScenarioType(StartingScenarioType.INHERITED_FAMILY_BUSINESS).isEmpty());
    }

    @Test
    void scenarioRegistryDoesNotDependOnCatalogConstructionOrder() {
        var builtIn = BuiltInStartingScenarioCatalog.scenarios();
        PlayerRegistry normal = PlayerRegistry.of(builtIn);
        PlayerRegistry reversed = PlayerRegistry.of(builtIn.reversed());

        assertEquals(normal.startingScenarios(), reversed.startingScenarios());
    }
}
