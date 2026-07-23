package com.butchercraft.world.economy.order;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ContractMetadata(
        Optional<String> externalReference,
        Optional<String> notes,
        Optional<String> sourceModule,
        Optional<String> creationReason,
        Set<String> tags
) {
    private static final ContractMetadata EMPTY = new ContractMetadata(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Set.of()
    );

    public ContractMetadata {
        externalReference = DomainValidation.optionalText(externalReference, "Contract external reference", 256);
        notes = DomainValidation.optionalText(notes, "Contract notes", 2_048);
        sourceModule = DomainValidation.optionalText(sourceModule, "Contract source module", 256);
        creationReason = DomainValidation.optionalText(creationReason, "Contract creation reason", 512);
        Objects.requireNonNull(tags, "tags");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        tags.stream().map(tag -> DomainValidation.requireId(tag, "Contract tag")).sorted().forEach(normalized::add);
        tags = Collections.unmodifiableSet(normalized);
    }

    public static ContractMetadata empty() {
        return EMPTY;
    }
}
