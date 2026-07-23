package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProductionPlanMetadata(
        Set<String> tags,
        Optional<String> correlationId,
        Optional<String> note
) {
    private static final ProductionPlanMetadata EMPTY =
            new ProductionPlanMetadata(Set.of(), Optional.empty(), Optional.empty());

    public ProductionPlanMetadata {
        tags = ProductionValidation.copyTags(tags);
        correlationId = Objects.requireNonNull(correlationId, "correlationId")
                .map(value -> ProductionValidation.requireId(value, "Production correlation id"));
        note = Objects.requireNonNull(note, "note")
                .map(value -> ProductionValidation.requireText(value, "Production plan note", 1_024));
    }

    public static ProductionPlanMetadata empty() {
        return EMPTY;
    }
}
