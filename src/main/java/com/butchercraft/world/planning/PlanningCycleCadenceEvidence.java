package com.butchercraft.world.planning;

import java.util.List;
import java.util.Objects;

record PlanningCycleCadenceEvidence(
        PlanningCycleId cycleId,
        long simulationTick,
        String cadenceConfigurationIdentity,
        List<String> eligibilityReasons,
        List<PlanningTriggerRecord> consumedTriggers,
        String frozenInputIdentity,
        long captureBoundaryTick,
        long nextEligibilityTick,
        int schemaVersion
) {
    PlanningCycleCadenceEvidence {
        Objects.requireNonNull(cycleId, "cycleId");
        simulationTick = PlanningValidation.tick(simulationTick);
        if (!cycleId.equals(PlanningCycleId.forTick(simulationTick))) {
            throw new IllegalArgumentException("Planning cadence evidence cycle identity is inconsistent");
        }
        cadenceConfigurationIdentity = PlanningValidation.id(
                cadenceConfigurationIdentity,
                "Planning cadence configuration identity"
        );
        eligibilityReasons = List.copyOf(Objects.requireNonNull(eligibilityReasons, "eligibilityReasons")
                .stream().map(value -> PlanningValidation.id(value, "Planning eligibility reason")).sorted().toList());
        if (eligibilityReasons.isEmpty()) {
            throw new IllegalArgumentException("Planning cadence evidence requires an eligibility reason");
        }
        consumedTriggers = Objects.requireNonNull(consumedTriggers, "consumedTriggers")
                .stream().sorted().toList();
        frozenInputIdentity = PlanningValidation.id(frozenInputIdentity, "Planning frozen input identity");
        captureBoundaryTick = PlanningValidation.tick(captureBoundaryTick);
        nextEligibilityTick = PlanningValidation.tick(nextEligibilityTick);
        if (nextEligibilityTick <= simulationTick) {
            throw new IllegalArgumentException("Planning next eligibility must follow the completed cycle tick");
        }
        schemaVersion = PlanningValidation.schema(schemaVersion);
    }

    String evidenceIdentity() {
        return PlanningValidation.derivedId(
                "planning_cycle_cadence_evidence",
                cycleId.value(),
                cadenceConfigurationIdentity,
                PlanningValidation.canonicalStrings(eligibilityReasons),
                PlanningValidation.canonicalStrings(consumedTriggers.stream()
                        .map(PlanningTriggerRecord::triggerIdentity).toList()),
                frozenInputIdentity,
                Long.toString(captureBoundaryTick),
                Long.toString(nextEligibilityTick)
        );
    }
}
