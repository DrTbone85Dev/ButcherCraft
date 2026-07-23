package com.butchercraft.world.economy.order;

import java.util.Objects;

public record ContractLineId(String value) implements Comparable<ContractLineId> {
    public ContractLineId {
        value = DomainValidation.requireId(value, "Contract line id");
    }

    public static ContractLineId of(String value) {
        return new ContractLineId(value);
    }

    @Override
    public int compareTo(ContractLineId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
