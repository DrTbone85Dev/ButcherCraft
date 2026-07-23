package com.butchercraft.world.economy.order;

import java.util.Objects;

public record ContractId(String value) implements Comparable<ContractId> {
    public ContractId {
        value = DomainValidation.requireId(value, "Contract id");
    }

    public static ContractId of(String value) {
        return new ContractId(value);
    }

    @Override
    public int compareTo(ContractId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
