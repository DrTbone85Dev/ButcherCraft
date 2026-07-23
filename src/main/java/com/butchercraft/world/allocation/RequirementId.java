package com.butchercraft.world.allocation;

public record RequirementId(String value) implements Comparable<RequirementId> {
    public RequirementId {
        value = AllocationValidation.id(value, "requirementId");
    }

    public static RequirementId of(String value) {
        return new RequirementId(value);
    }

    @Override
    public int compareTo(RequirementId other) {
        return value.compareTo(other.value);
    }
}
