package com.butchercraft.world.allocation;

public record ResourceId(String value) implements Comparable<ResourceId> {
    public ResourceId {
        value = AllocationValidation.id(value, "resourceId");
    }

    public static ResourceId of(String value) {
        return new ResourceId(value);
    }

    @Override
    public int compareTo(ResourceId other) {
        return value.compareTo(other.value);
    }
}
