package com.butchercraft.world.workforce.department;

public record DepartmentId(String value) implements Comparable<DepartmentId> {
    public DepartmentId {
        value = DepartmentValidation.requireId(value, "id");
    }

    @Override
    public int compareTo(DepartmentId other) {
        return value.compareTo(other.value);
    }
}
