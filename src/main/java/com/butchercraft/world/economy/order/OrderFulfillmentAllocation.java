package com.butchercraft.world.economy.order;

import com.butchercraft.world.transaction.TransactionId;

import java.util.Objects;

public record OrderFulfillmentAllocation(
        TransactionId transactionId,
        GoodQuantity quantity,
        long simulationTick
) {
    public OrderFulfillmentAllocation {
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        quantity = Objects.requireNonNull(quantity, "quantity").requirePositive("Allocation quantity");
        simulationTick = DomainValidation.requireTick(simulationTick, "Allocation simulation tick");
    }
}
