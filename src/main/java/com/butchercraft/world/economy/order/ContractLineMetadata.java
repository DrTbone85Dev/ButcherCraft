package com.butchercraft.world.economy.order;

import java.util.Optional;

public record ContractLineMetadata(Optional<String> externalReference, Optional<String> notes) {
    private static final ContractLineMetadata EMPTY = new ContractLineMetadata(Optional.empty(), Optional.empty());

    public ContractLineMetadata {
        externalReference = DomainValidation.optionalText(externalReference, "Contract line external reference", 256);
        notes = DomainValidation.optionalText(notes, "Contract line notes", 1_024);
    }

    public static ContractLineMetadata empty() {
        return EMPTY;
    }
}
