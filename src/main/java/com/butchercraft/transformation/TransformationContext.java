package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable runtime facts needed to evaluate a transformation request.
 */
public record TransformationContext(
        List<MaterialAmount> availableMaterials,
        Optional<EngineId> workstationCapability
) {
    public TransformationContext {
        availableMaterials = List.copyOf(Objects.requireNonNull(availableMaterials, "availableMaterials"));
        workstationCapability = Objects.requireNonNull(workstationCapability, "workstationCapability");
        workstationCapability.ifPresent(capability -> Objects.requireNonNull(capability, "workstationCapability value"));
    }

    Optional<ProductQuantity> availableQuantity(EngineId materialId) {
        Objects.requireNonNull(materialId, "materialId");
        ProductQuantity total = null;
        for (MaterialAmount availableMaterial : availableMaterials) {
            if (!materialId.equals(availableMaterial.materialId())) {
                continue;
            }
            if (total == null) {
                total = availableMaterial.quantity();
            } else {
                total = total.add(availableMaterial.quantity());
            }
        }
        return Optional.ofNullable(total);
    }
}
