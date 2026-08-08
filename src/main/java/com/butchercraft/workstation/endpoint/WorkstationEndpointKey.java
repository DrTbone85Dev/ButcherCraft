package com.butchercraft.workstation.endpoint;

public record WorkstationEndpointKey(
        String workstationTypeIdentity,
        String dimensionIdentity,
        int x,
        int y,
        int z
) implements Comparable<WorkstationEndpointKey> {
    public WorkstationEndpointKey {
        workstationTypeIdentity = WorkstationEndpointValidation.id(
                workstationTypeIdentity,
                "workstation type identity"
        );
        dimensionIdentity = WorkstationEndpointValidation.id(dimensionIdentity, "dimension identity");
    }

    @Override
    public int compareTo(WorkstationEndpointKey other) {
        int typeComparison = workstationTypeIdentity.compareTo(other.workstationTypeIdentity);
        if (typeComparison != 0) return typeComparison;
        int dimensionComparison = dimensionIdentity.compareTo(other.dimensionIdentity);
        if (dimensionComparison != 0) return dimensionComparison;
        int xComparison = Integer.compare(x, other.x);
        if (xComparison != 0) return xComparison;
        int yComparison = Integer.compare(y, other.y);
        if (yComparison != 0) return yComparison;
        return Integer.compare(z, other.z);
    }

    public String canonicalValue() {
        return workstationTypeIdentity + "|" + dimensionIdentity + "|" + x + "|" + y + "|" + z;
    }
}
