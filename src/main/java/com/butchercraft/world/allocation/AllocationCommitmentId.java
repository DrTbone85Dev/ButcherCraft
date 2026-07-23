package com.butchercraft.world.allocation;

public record AllocationCommitmentId(String value) implements Comparable<AllocationCommitmentId> {
    public AllocationCommitmentId {
        value = AllocationValidation.id(value, "allocationCommitmentId");
    }

    public static AllocationCommitmentId of(String value) {
        return new AllocationCommitmentId(value);
    }

    @Override
    public int compareTo(AllocationCommitmentId other) {
        return value.compareTo(other.value);
    }
}
