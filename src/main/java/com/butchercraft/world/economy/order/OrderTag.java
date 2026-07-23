package com.butchercraft.world.economy.order;

import java.util.Objects;

public record OrderTag(String value) implements Comparable<OrderTag> {
    public OrderTag {
        value = DomainValidation.requireId(value, "Order tag");
    }

    public static OrderTag of(String value) {
        return new OrderTag(value);
    }

    @Override
    public int compareTo(OrderTag other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
