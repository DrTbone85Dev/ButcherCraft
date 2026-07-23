package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProductionLineMetadata(
        Set<String> tags,
        Optional<String> note
) {
    private static final ProductionLineMetadata EMPTY = new ProductionLineMetadata(Set.of(), Optional.empty());

    public ProductionLineMetadata {
        tags = ProductionValidation.copyTags(tags);
        note = Objects.requireNonNull(note, "note")
                .map(value -> ProductionValidation.requireText(value, "Production line note", 512));
    }

    public static ProductionLineMetadata empty() {
        return EMPTY;
    }
}
