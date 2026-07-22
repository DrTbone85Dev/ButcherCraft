package com.butchercraft.world.player;

import com.butchercraft.world.identity.Settlement;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PlayerRegistry {
    private final List<StartingScenario> startingScenarios;
    private final Map<StartingScenarioId, StartingScenario> scenariosById;

    private PlayerRegistry(List<StartingScenario> startingScenarios, Map<StartingScenarioId, StartingScenario> scenariosById) {
        this.startingScenarios = startingScenarios;
        this.scenariosById = scenariosById;
    }

    public static PlayerRegistry builtIn() {
        return BuiltInStartingScenarioCatalog.createRegistry();
    }

    public static PlayerRegistry of(Collection<StartingScenario> startingScenarios) {
        Objects.requireNonNull(startingScenarios, "startingScenarios");
        if (startingScenarios.isEmpty()) {
            throw new IllegalArgumentException("Player registry must contain at least one starting scenario");
        }
        List<StartingScenario> deterministicScenarios = startingScenarios.stream()
                .map(PlayerRegistry::validateScenario)
                .sorted(Comparator.comparing(scenario -> scenario.id().value()))
                .toList();
        rejectDuplicates(deterministicScenarios, StartingScenario::id, "starting scenario id");
        rejectDuplicates(deterministicScenarios, StartingScenario::displayName, "starting scenario display name");
        rejectMissingScenarioTypes(deterministicScenarios);
        rejectMissingCareerProfiles(deterministicScenarios);

        Map<StartingScenarioId, StartingScenario> byId = deterministicScenarios.stream()
                .collect(Collectors.toUnmodifiableMap(StartingScenario::id, Function.identity()));
        return new PlayerRegistry(List.copyOf(deterministicScenarios), byId);
    }

    public boolean contains(StartingScenarioId id) {
        return scenariosById.containsKey(id);
    }

    public Optional<StartingScenario> find(StartingScenarioId id) {
        return Optional.ofNullable(scenariosById.get(id));
    }

    public Optional<StartingScenario> find(String id) {
        return find(new StartingScenarioId(id));
    }

    public int size() {
        return startingScenarios.size();
    }

    public List<StartingScenario> startingScenarios() {
        return startingScenarios;
    }

    public Stream<StartingScenario> stream() {
        return startingScenarios.stream();
    }

    public List<StartingScenario> findByScenarioType(StartingScenarioType scenarioType) {
        Objects.requireNonNull(scenarioType, "scenarioType");
        return startingScenarios.stream()
                .filter(scenario -> scenario.scenarioType() == scenarioType)
                .toList();
    }

    public List<StartingScenario> findByCareerProfile(CareerProfile careerProfile) {
        Objects.requireNonNull(careerProfile, "careerProfile");
        return startingScenarios.stream()
                .filter(scenario -> scenario.careerProfiles().contains(careerProfile))
                .toList();
    }

    public void validateIdentity(PlayerIdentity identity, Collection<Settlement> settlements) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(settlements, "settlements");
        StartingScenario scenario = find(identity.startingScenarioId())
                .orElseThrow(() -> new IllegalArgumentException("Player identity references unknown starting scenario: "
                        + identity.startingScenarioId().value()));
        if (!scenario.careerProfiles().contains(identity.careerProfile())) {
            throw new IllegalArgumentException("Player identity career profile is not supported by scenario: "
                    + identity.careerProfile().serializedName());
        }
        Set<String> settlementIds = settlements.stream()
                .map(Settlement::id)
                .collect(Collectors.toUnmodifiableSet());
        if (!settlementIds.contains(identity.startingSettlementId())) {
            throw new IllegalArgumentException("Player identity references unknown starting settlement: "
                    + identity.startingSettlementId());
        }
        Set<StartingRelationshipType> scenarioRelationshipTypes = scenario.startingRelationships().stream()
                .map(StartingRelationship::relationshipType)
                .collect(Collectors.toUnmodifiableSet());
        Set<StartingRelationshipType> identityRelationshipTypes = identity.initialRelationships().stream()
                .map(StartingRelationship::relationshipType)
                .collect(Collectors.toUnmodifiableSet());
        if (!identityRelationshipTypes.containsAll(scenarioRelationshipTypes)) {
            throw new IllegalArgumentException("Player identity is missing starting relationship types from scenario: "
                    + identity.startingScenarioId().value());
        }
    }

    private static StartingScenario validateScenario(StartingScenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        Set<String> validReferences = new HashSet<>();
        validReferences.add(scenario.startingAssets().businessReference());
        validReferences.add(scenario.startingAssets().commercialPropertyReference());
        validReferences.addAll(scenario.startingAssets().equipmentPlaceholders());
        validReferences.addAll(scenario.startingAssets().supplierRelationshipPlaceholders());
        validReferences.addAll(scenario.startingAssets().financialPlaceholders());
        scenario.inheritanceRecord().ifPresent(inheritance -> {
            validReferences.add(inheritance.previousOwnerReference());
            validReferences.add(inheritance.businessReference());
            validReferences.add(inheritance.propertyReference());
        });
        for (StartingRelationship relationship : scenario.startingRelationships()) {
            if (!validReferences.contains(relationship.reference())) {
                throw new IllegalArgumentException("Starting scenario " + scenario.id().value()
                        + " contains orphaned relationship placeholder: " + relationship.reference());
            }
        }
        return scenario;
    }

    private static void rejectMissingScenarioTypes(List<StartingScenario> scenarios) {
        Set<StartingScenarioType> scenarioTypes = scenarios.stream()
                .map(StartingScenario::scenarioType)
                .collect(Collectors.toUnmodifiableSet());
        Set<StartingScenarioType> missing = Stream.of(StartingScenarioType.values())
                .filter(type -> !scenarioTypes.contains(type))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Player registry is missing starting scenario types: " + missing);
        }
    }

    private static void rejectMissingCareerProfiles(List<StartingScenario> scenarios) {
        Set<CareerProfile> careerProfiles = scenarios.stream()
                .flatMap(scenario -> scenario.careerProfiles().stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<CareerProfile> missing = Stream.of(CareerProfile.values())
                .filter(profile -> !careerProfiles.contains(profile))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Player registry is missing career profiles: " + missing);
        }
    }

    private static <T, K> void rejectDuplicates(List<T> values, Function<T, K> keyFunction, String label) {
        Set<K> duplicates = values.stream()
                .map(keyFunction)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1L)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Duplicate " + label + ": " + duplicates);
        }
    }
}
