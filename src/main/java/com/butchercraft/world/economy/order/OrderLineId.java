package com.butchercraft.world.economy.order;

import java.util.Objects;

public record OrderLineId(String value) implements Comparable<OrderLineId> {
    public OrderLineId {
        value = DomainValidation.requireId(value, "Order line id");
    }

    public static OrderLineId of(String value) {
        return new OrderLineId(value);
    }

    @Override
    public int compareTo(OrderLineId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
