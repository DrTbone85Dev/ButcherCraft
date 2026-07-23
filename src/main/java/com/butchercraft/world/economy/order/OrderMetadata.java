package com.butchercraft.world.economy.order;

import java.util.Objects;
import java.util.Optional;

public record OrderMetadata(
        Optional<String> externalReference,
        Optional<String> notes,
        Optional<String> sourceModule,
        Optional<String> creationReason
) {
    private static final OrderMetadata EMPTY = new OrderMetadata(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
    );

    public OrderMetadata {
        externalReference = DomainValidation.optionalText(externalReference, "Order external reference", 256);
        notes = DomainValidation.optionalText(notes, "Order notes", 2_048);
        sourceModule = DomainValidation.optionalText(sourceModule, "Order source module", 256);
        creationReason = DomainValidation.optionalText(creationReason, "Order creation reason", 512);
    }

    public static OrderMetadata empty() {
        return EMPTY;
    }

    public static OrderMetadata of(String externalReference, String notes, String sourceModule, String creationReason) {
        return new OrderMetadata(
                Optional.ofNullable(externalReference),
                Optional.ofNullable(notes),
                Optional.ofNullable(sourceModule),
                Optional.ofNullable(creationReason)
        );
    }
}
