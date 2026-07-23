package com.butchercraft.world.economy.order;

import java.util.Objects;

public record OrderId(String value) implements Comparable<OrderId> {
    public OrderId {
        value = DomainValidation.requireId(value, "Order id");
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }

    @Override
    public int compareTo(OrderId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
