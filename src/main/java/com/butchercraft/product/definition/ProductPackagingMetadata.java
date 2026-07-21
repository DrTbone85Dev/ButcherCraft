package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Optional immutable retail packaging relationship for a product definition.
 *
 * <p>This identifies the packaging definition used by a retail product and the stable source
 * product that is packaged. It does not define recipes, execution, labels, weight, freshness, or
 * supply consumption.</p>
 */
public record ProductPackagingMetadata(
        EngineId packagingDefinitionId,
        EngineId sourceProductId
) {
    public ProductPackagingMetadata {
        Objects.requireNonNull(packagingDefinitionId, "packagingDefinitionId");
        Objects.requireNonNull(sourceProductId, "sourceProductId");
    }
}
