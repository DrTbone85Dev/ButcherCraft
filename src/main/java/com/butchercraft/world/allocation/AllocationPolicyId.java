package com.butchercraft.world.allocation;

public record AllocationPolicyId(String value) implements Comparable<AllocationPolicyId> {
    public AllocationPolicyId {
        value = AllocationValidation.id(value, "allocationPolicyId");
    }

    public static AllocationPolicyId of(String value) {
        return new AllocationPolicyId(value);
    }

    @Override
    public int compareTo(AllocationPolicyId other) {
        return value.compareTo(other.value);
    }
}
