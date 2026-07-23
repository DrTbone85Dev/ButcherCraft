package com.butchercraft.world.allocation;

public record ResourceCategory(String value) implements Comparable<ResourceCategory> {
    public ResourceCategory {
        value = AllocationValidation.id(value, "resourceCategory");
    }

    public static ResourceCategory of(String value) {
        return new ResourceCategory(value);
    }

    @Override
    public int compareTo(ResourceCategory other) {
        return value.compareTo(other.value);
    }
}
