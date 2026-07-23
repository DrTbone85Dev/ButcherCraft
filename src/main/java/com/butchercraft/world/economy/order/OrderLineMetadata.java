package com.butchercraft.world.economy.order;

import java.util.Optional;

public record OrderLineMetadata(Optional<String> externalReference, Optional<String> notes) {
    private static final OrderLineMetadata EMPTY = new OrderLineMetadata(Optional.empty(), Optional.empty());

    public OrderLineMetadata {
        externalReference = DomainValidation.optionalText(externalReference, "Order line external reference", 256);
        notes = DomainValidation.optionalText(notes, "Order line notes", 1_024);
    }

    public static OrderLineMetadata empty() {
        return EMPTY;
    }
}
