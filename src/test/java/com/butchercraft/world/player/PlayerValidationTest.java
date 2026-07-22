package com.butchercraft.world.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerValidationTest {
    private final StartingScenario inheritedScenario = BuiltInStartingScenarioCatalog.scenarios().stream()
            .filter(scenario -> scenario.id().value().equals("inherited_family_business"))
            .findFirst()
            .orElseThrow();

    @Test
    void playerModelsRejectInvalidCoreFields() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerIdentityId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new StartingScenarioId("Bad Id"));
        assertThrows(IllegalArgumentException.class, () -> new StartingAssets(
                "business",
                "property",
                List.of(),
                List.of("supplier"),
                List.of("finance")
        ));
        assertThrows(IllegalArgumentException.class, () -> new StartingRelationship(
                StartingRelationshipType.FAMILY_TIE,
                " ",
                "Invalid blank reference."
        ));
        assertThrows(IllegalArgumentException.class, () -> new InheritanceRecord(
                "owner",
                "business",
                " ",
                LegacyAcquisitionType.INHERITED,
                "Invalid blank property reference."
        ));
        assertThrows(IllegalArgumentException.class, () -> new LegacyProgress(
                -1,
                0,
                List.of("milestone"),
                InitialReputation.UNKNOWN_NEWCOMER,
                "Invalid negative generation count."
        ));
        assertThrows(IllegalArgumentException.class, () -> new PlayerBackground(
                " ",
                "Background.",
                "Legacy."
        ));
        assertThrows(IllegalArgumentException.class, () -> new StartingScenario(
                new StartingScenarioId("invalid_inherited_scenario"),
                "Invalid Inherited Scenario",
                StartingScenarioType.INHERITED_FAMILY_BUSINESS,
                List.of(CareerProfile.FAMILY_SUCCESSOR),
                "settlement",
                InitialReputation.LOCAL_FAMILY_NAME,
                inheritedScenario.startingAssets(),
                Optional.empty(),
                inheritedScenario.startingRelationships(),
                inheritedScenario.initialLegacyProgress(),
                "Invalid inherited scenario.",
                "Invalid background."
        ));
        assertThrows(IllegalArgumentException.class, () -> new PlayerIdentity(
                new PlayerIdentityId("invalid_player"),
                "Future Character",
                CareerProfile.FAMILY_SUCCESSOR,
                inheritedScenario.id(),
                "settlement",
                "Legacy.",
                new PlayerBackground("Future Character", "Background.", "Legacy."),
                InitialReputation.LOCAL_FAMILY_NAME,
                List.of()
        ));
    }

    @Test
    void registryRejectsDuplicateScenarioIdsAndNames() {
        List<StartingScenario> duplicateIdScenarios = new ArrayList<>(BuiltInStartingScenarioCatalog.scenarios());
        duplicateIdScenarios.add(copyScenario(
                inheritedScenario.id(),
                "Duplicate Id Scenario",
                inheritedScenario.careerProfiles(),
                inheritedScenario.startingRelationships()
        ));
        List<StartingScenario> duplicateNameScenarios = new ArrayList<>(BuiltInStartingScenarioCatalog.scenarios());
        duplicateNameScenarios.add(copyScenario(
                new StartingScenarioId("duplicate_name_scenario"),
                inheritedScenario.displayName(),
                inheritedScenario.careerProfiles(),
                inheritedScenario.startingRelationships()
        ));

        assertThrows(IllegalArgumentException.class, () -> PlayerRegistry.of(duplicateIdScenarios));
        assertThrows(IllegalArgumentException.class, () -> PlayerRegistry.of(duplicateNameScenarios));
    }

    @Test
    void registryRejectsMissingCareerProfilesAndOrphanedPlaceholders() {
        List<StartingScenario> missingCareerProfile = BuiltInStartingScenarioCatalog.scenarios().stream()
                .map(scenario -> scenario.id().value().equals("county_contract")
                        ? copyScenario(
                                scenario.id(),
                                scenario.displayName(),
                                List.of(CareerProfile.FAMILY_SUCCESSOR),
                                scenario.startingRelationships()
                        )
                        : scenario)
                .toList();
        List<StartingScenario> orphanedPlaceholder = BuiltInStartingScenarioCatalog.scenarios().stream()
                .map(scenario -> scenario.id().value().equals("inherited_family_business")
                        ? copyScenario(
                                scenario.id(),
                                scenario.displayName(),
                                scenario.careerProfiles(),
                                List.of(new StartingRelationship(
                                        StartingRelationshipType.FAMILY_TIE,
                                        "missing_placeholder",
                                        "Invalid orphaned placeholder."
                                ))
                        )
                        : scenario)
                .toList();

        assertThrows(IllegalArgumentException.class, () -> PlayerRegistry.of(missingCareerProfile));
        assertThrows(IllegalArgumentException.class, () -> PlayerRegistry.of(orphanedPlaceholder));
    }

    private StartingScenario copyScenario(
            StartingScenarioId id,
            String displayName,
            List<CareerProfile> careerProfiles,
            List<StartingRelationship> relationships
    ) {
        return new StartingScenario(
                id,
                displayName,
                inheritedScenario.scenarioType(),
                careerProfiles,
                inheritedScenario.startingSettlementReference(),
                inheritedScenario.initialReputation(),
                inheritedScenario.startingAssets(),
                inheritedScenario.inheritanceRecord(),
                relationships,
                inheritedScenario.initialLegacyProgress(),
                inheritedScenario.scenarioSummary(),
                inheritedScenario.backgroundSummary()
        );
    }
}
