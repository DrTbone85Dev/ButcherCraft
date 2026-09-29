package com.butchercraft.workstation.condition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Frozen calculation content, not a mutable balance configuration or condition authority. */
public record MachineConditionPolicy(
        int schemaVersion,
        String machineType,
        String configurationIdentity,
        long maximumLoss,
        long processingLoss,
        long processingServiceDebt,
        long serviceSoon,
        long serviceDue,
        long criticalLoss,
        Optional<Long> faultLoss,
        List<ConditionExposurePolicy> exposures,
        String identity
) {
    public MachineConditionPolicy {
        if (schemaVersion != 1) throw new UnsupportedConditionSchemaException("condition policy", schemaVersion);
        machineType = ConditionDigest.text(machineType, "machineType");
        configurationIdentity = ConditionDigest.text(configurationIdentity, "configurationIdentity");
        faultLoss = Objects.requireNonNull(faultLoss, "faultLoss");
        if (maximumLoss <= 0 || processingLoss < 0 || processingServiceDebt < 0
                || serviceSoon < 0 || serviceDue < serviceSoon || criticalLoss < 0 || criticalLoss > maximumLoss
                || faultLoss.filter(value -> value <= 0 || value > maximumLoss).isPresent()) {
            throw new IllegalArgumentException("Invalid condition policy bounds");
        }
        exposures = Objects.requireNonNull(exposures, "exposures").stream().sorted().toList();
        if (exposures.stream().map(ConditionExposurePolicy::type).distinct().count() != exposures.size()) {
            throw new IllegalArgumentException("Duplicate typed exposure policy");
        }
        String expected = calculateIdentity(schemaVersion, machineType, configurationIdentity, maximumLoss,
                processingLoss, processingServiceDebt, serviceSoon, serviceDue, criticalLoss, faultLoss, exposures);
        if (!expected.equals(identity)) throw new IllegalArgumentException("Condition policy identity mismatch");
    }

    public static MachineConditionPolicy create(
            String machineType, String configurationIdentity, long maximumLoss, long processingLoss,
            long processingServiceDebt, long serviceSoon, long serviceDue, long criticalLoss,
            Optional<Long> faultLoss, List<ConditionExposurePolicy> exposures
    ) {
        List<ConditionExposurePolicy> ordered = exposures.stream().sorted().toList();
        return new MachineConditionPolicy(1, machineType, configurationIdentity, maximumLoss, processingLoss,
                processingServiceDebt, serviceSoon, serviceDue, criticalLoss, faultLoss, ordered,
                calculateIdentity(1, machineType, configurationIdentity, maximumLoss, processingLoss,
                        processingServiceDebt, serviceSoon, serviceDue, criticalLoss, faultLoss, ordered));
    }

    public static MachineConditionPolicy inert(String machineType) {
        return create(machineType, "butchercraft:condition_configuration/v1/inert_foundation", 10_000,
                0, 0, Long.MAX_VALUE, Long.MAX_VALUE, 10_000, Optional.empty(), List.of());
    }

    public boolean inert() {
        return processingLoss == 0 && processingServiceDebt == 0 && faultLoss.isEmpty()
                && exposures.stream().noneMatch(ConditionExposurePolicy::consequential);
    }

    public Optional<ConditionExposurePolicy> exposure(ConditionExposureType type) {
        return exposures.stream().filter(value -> value.type() == type).findFirst();
    }

    private static String calculateIdentity(
            int schema, String machineType, String configuration, long maximum, long processing,
            long debt, long soon, long due, long critical, Optional<Long> fault,
            List<ConditionExposurePolicy> exposures
    ) {
        ConditionDigest digest = new ConditionDigest("butchercraft:condition_policy/v1")
                .add(schema).add(machineType).add(configuration).add(maximum).add(processing)
                .add(debt).add(soon).add(due).add(critical).add(fault.isPresent());
        fault.ifPresent(digest::add);
        digest.add(exposures.size());
        exposures.forEach(exposure -> exposure.digestInto(digest));
        return "butchercraft:condition_policy/v1/" + digest.finish().substring("sha256:".length());
    }
}
