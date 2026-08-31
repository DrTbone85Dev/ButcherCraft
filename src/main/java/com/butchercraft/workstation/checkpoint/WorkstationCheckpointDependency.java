package com.butchercraft.workstation.checkpoint;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Objects;

public record WorkstationCheckpointDependency(
        WorkstationInstanceId instanceId,
        WorkstationCheckpointDependencyCategory category,
        String evidenceIdentity
) implements Comparable<WorkstationCheckpointDependency> {
    public WorkstationCheckpointDependency {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        category = Objects.requireNonNull(category, "category");
        evidenceIdentity = requireText(evidenceIdentity, "evidenceIdentity");
    }

    @Override
    public int compareTo(WorkstationCheckpointDependency other) {
        int identity = instanceId.compareTo(other.instanceId);
        if (identity != 0) return identity;
        int categoryOrder = category.compareTo(other.category);
        return categoryOrder != 0 ? categoryOrder : evidenceIdentity.compareTo(other.evidenceIdentity);
    }

    private static String requireText(String value, String field) {
        String required = Objects.requireNonNull(value, field).trim();
        if (required.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return required;
    }
}
