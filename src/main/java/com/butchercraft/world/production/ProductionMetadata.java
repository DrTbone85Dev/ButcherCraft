package com.butchercraft.world.production;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProductionMetadata(
        Set<String> tags,
        Optional<String> description
) {
    private static final ProductionMetadata EMPTY = new ProductionMetadata(Set.of(), Optional.empty());

    public ProductionMetadata {
        tags = ProductionValidation.copyTags(tags);
        description = Objects.requireNonNull(description, "description")
                .map(value -> ProductionValidation.requireText(value, "Production description", 2_048));
    }

    public static ProductionMetadata empty() {
        return EMPTY;
    }
}
