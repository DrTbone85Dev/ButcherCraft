package com.butchercraft.world.economy.order;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OrderOperationResult(boolean success, Optional<OrderFailureCode> failureCode, List<String> messages) {
    public OrderOperationResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (success == failureCode.isPresent()) {
            throw new IllegalArgumentException("Order operation success and failure code are inconsistent");
        }
    }

    public static OrderOperationResult accepted() {
        return new OrderOperationResult(true, Optional.empty(), List.of());
    }

    public static OrderOperationResult rejected(OrderFailureCode code, String message) {
        return new OrderOperationResult(false, Optional.of(code), List.of(message));
    }
}
