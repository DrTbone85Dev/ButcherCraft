package com.butchercraft.world.economy.order;

import com.butchercraft.world.transaction.TransactionId;

import java.util.Objects;

public record OrderFulfillmentRequest(
        OrderId orderId,
        OrderLineId orderLineId,
        TransactionId transactionId,
        GoodQuantity quantity,
        long simulationTick
) {
    public OrderFulfillmentRequest {
        orderId = Objects.requireNonNull(orderId, "orderId");
        orderLineId = Objects.requireNonNull(orderLineId, "orderLineId");
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        quantity = Objects.requireNonNull(quantity, "quantity").requirePositive("Fulfillment quantity");
        simulationTick = DomainValidation.requireTick(simulationTick, "Fulfillment simulation tick");
    }
}
