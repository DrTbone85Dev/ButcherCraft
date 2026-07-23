package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record WorkReference(String referenceType, String referenceId) implements Comparable<WorkReference> {
    public WorkReference {
        referenceType = SchedulerValidation.requireId(referenceType, "Work reference type");
        referenceId = SchedulerValidation.requireId(referenceId, "Work reference id");
    }
    @Override public int compareTo(WorkReference other) {
        int type = referenceType.compareTo(Objects.requireNonNull(other, "other").referenceType);
        return type != 0 ? type : referenceId.compareTo(other.referenceId);
    }
}
