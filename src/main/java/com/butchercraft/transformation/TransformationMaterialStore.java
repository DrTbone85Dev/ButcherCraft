package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure Java material store used by transformation transactions.
 */
public interface TransformationMaterialStore {
    Optional<ProductQuantity> quantity(EngineId materialId);

    List<MaterialAmount> materials();

    boolean canExtract(MaterialAmount amount);

    boolean canInsert(MaterialAmount amount);

    boolean canExtractAll(List<MaterialAmount> amounts);

    boolean canInsertAll(List<MaterialAmount> amounts);

    TransformationMaterialStoreSnapshot snapshot();

    void restore(TransformationMaterialStoreSnapshot snapshot);

    void extract(MaterialAmount amount);

    void insert(MaterialAmount amount);

    default TransformationContext toContext(Optional<WorkstationCapability> workstationCapability) {
        return new TransformationContext(materials(), Objects.requireNonNull(workstationCapability, "workstationCapability"));
    }
}
