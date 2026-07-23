package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.OptionalLong;

public record AllocationOrderingContext(
        int horizonPrecedence,
        int priority,
        OptionalLong requiredBySimulationTick,
        long needCreationSimulationTick,
        ExternalReference planningCycleReference,
        ExternalReference sourceApprovedPlanReference,
        long requestCreationSimulationTick,
        long stableRequestSequence
) implements Comparable<AllocationOrderingContext> {
    private static final Comparator<AllocationOrderingContext> ORDER = Comparator
            .comparingInt(AllocationOrderingContext::horizonPrecedence)
            .thenComparing(Comparator.comparingInt(AllocationOrderingContext::priority).reversed())
            .thenComparingLong(context -> context.requiredBySimulationTick().orElse(Long.MAX_VALUE))
            .thenComparingLong(AllocationOrderingContext::needCreationSimulationTick)
            .thenComparingLong(AllocationOrderingContext::stableRequestSequence)
            .thenComparing(AllocationOrderingContext::planningCycleReference)
            .thenComparing(AllocationOrderingContext::sourceApprovedPlanReference)
            .thenComparingLong(AllocationOrderingContext::requestCreationSimulationTick);

    public AllocationOrderingContext {
        horizonPrecedence = AllocationValidation.precedence(horizonPrecedence, "horizonPrecedence");
        priority = AllocationValidation.precedence(priority, "priority");
        requiredBySimulationTick = AllocationValidation.optionalTick(
                requiredBySimulationTick,
                "requiredBySimulationTick"
        );
        needCreationSimulationTick = AllocationValidation.tick(
                needCreationSimulationTick,
                "needCreationSimulationTick"
        );
        planningCycleReference = AllocationValidation.required(
                planningCycleReference,
                "planningCycleReference"
        );
        sourceApprovedPlanReference = AllocationValidation.required(
                sourceApprovedPlanReference,
                "sourceApprovedPlanReference"
        );
        requestCreationSimulationTick = AllocationValidation.tick(
                requestCreationSimulationTick,
                "requestCreationSimulationTick"
        );
        stableRequestSequence = AllocationValidation.sequence(stableRequestSequence);
        if (requestCreationSimulationTick < needCreationSimulationTick) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INVALID_ORDERING_CONTEXT,
                    "requestCreationSimulationTick",
                    "Request creation tick cannot precede Need creation tick"
            );
        }
    }

    public long starvationAge(long currentSimulationTick) {
        long current = AllocationValidation.tick(currentSimulationTick, "currentSimulationTick");
        if (current < needCreationSimulationTick) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    "currentSimulationTick",
                    "Current simulation tick cannot precede Need creation tick"
            );
        }
        try {
            return Math.subtractExact(current, needCreationSimulationTick);
        } catch (ArithmeticException exception) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                    "currentSimulationTick",
                    "Starvation age overflowed"
            );
        }
    }

    public String canonicalKey() {
        return horizonPrecedence + "|" + priority + "|"
                + (requiredBySimulationTick.isPresent()
                ? Long.toString(requiredBySimulationTick.getAsLong())
                : "")
                + "|" + needCreationSimulationTick
                + "|" + planningCycleReference.canonicalKey()
                + "|" + sourceApprovedPlanReference.canonicalKey()
                + "|" + requestCreationSimulationTick
                + "|" + stableRequestSequence;
    }

    @Override
    public int compareTo(AllocationOrderingContext other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
