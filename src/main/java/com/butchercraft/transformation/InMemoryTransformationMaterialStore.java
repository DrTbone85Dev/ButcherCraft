package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic in-memory material store for pure transformation transactions.
 */
public final class InMemoryTransformationMaterialStore implements TransformationMaterialStore {
    private final LinkedHashMap<EngineId, ProductQuantity> quantities = new LinkedHashMap<>();
    private final Map<EngineId, ProductQuantity> capacities;
    private final int materialSlotCapacity;

    private InMemoryTransformationMaterialStore(
            Map<EngineId, ProductQuantity> quantities,
            Map<EngineId, ProductQuantity> capacities,
            int materialSlotCapacity
    ) {
        if (materialSlotCapacity < 0) {
            throw new IllegalArgumentException("Material slot capacity cannot be negative");
        }
        this.capacities = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(capacities, "capacities")));
        this.materialSlotCapacity = materialSlotCapacity;
        this.quantities.putAll(copyPositiveQuantities(quantities));
        validateCurrentCapacity();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static InMemoryTransformationMaterialStore empty() {
        return builder().build();
    }

    public static InMemoryTransformationMaterialStore of(List<MaterialAmount> materials) {
        Builder builder = builder();
        for (MaterialAmount material : Objects.requireNonNull(materials, "materials")) {
            builder.material(material.materialId(), material.quantity());
        }
        return builder.build();
    }

    @Override
    public Optional<ProductQuantity> quantity(EngineId materialId) {
        return Optional.ofNullable(quantities.get(Objects.requireNonNull(materialId, "materialId")));
    }

    @Override
    public List<MaterialAmount> materials() {
        return quantities.entrySet().stream()
                .map(entry -> new MaterialAmount(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public boolean canExtract(MaterialAmount amount) {
        Objects.requireNonNull(amount, "amount");
        ProductQuantity available = quantities.get(amount.materialId());
        if (available == null || available.unit() != amount.quantity().unit()) {
            return false;
        }
        return available.amount() >= amount.quantity().amount();
    }

    @Override
    public boolean canInsert(MaterialAmount amount) {
        Objects.requireNonNull(amount, "amount");
        LinkedHashMap<EngineId, ProductQuantity> copy = new LinkedHashMap<>(quantities);
        return tryInsert(copy, amount);
    }

    @Override
    public boolean canExtractAll(List<MaterialAmount> amounts) {
        LinkedHashMap<EngineId, ProductQuantity> copy = new LinkedHashMap<>(quantities);
        for (MaterialAmount amount : Objects.requireNonNull(amounts, "amounts")) {
            if (!tryExtract(copy, amount)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canInsertAll(List<MaterialAmount> amounts) {
        LinkedHashMap<EngineId, ProductQuantity> copy = new LinkedHashMap<>(quantities);
        for (MaterialAmount amount : Objects.requireNonNull(amounts, "amounts")) {
            if (!tryInsert(copy, amount)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public TransformationMaterialStoreSnapshot snapshot() {
        return new TransformationMaterialStoreSnapshot(materials());
    }

    @Override
    public void restore(TransformationMaterialStoreSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        quantities.clear();
        quantities.putAll(copyPositiveMaterials(snapshot.materials()));
        validateCurrentCapacity();
    }

    @Override
    public void extract(MaterialAmount amount) {
        Objects.requireNonNull(amount, "amount");
        if (!tryExtract(quantities, amount)) {
            throw new IllegalStateException("Material store cannot extract " + amount.materialId().value());
        }
    }

    @Override
    public void insert(MaterialAmount amount) {
        Objects.requireNonNull(amount, "amount");
        if (!tryInsert(quantities, amount)) {
            throw new IllegalStateException("Material store cannot insert " + amount.materialId().value());
        }
    }

    private boolean tryExtract(LinkedHashMap<EngineId, ProductQuantity> source, MaterialAmount amount) {
        ProductQuantity available = source.get(amount.materialId());
        if (available == null || available.unit() != amount.quantity().unit()) {
            return false;
        }
        if (available.amount() < amount.quantity().amount()) {
            return false;
        }
        ProductQuantity remaining = available.subtract(amount.quantity());
        if (remaining.isZero()) {
            source.remove(amount.materialId());
        } else {
            source.put(amount.materialId(), remaining);
        }
        return true;
    }

    private boolean tryInsert(LinkedHashMap<EngineId, ProductQuantity> destination, MaterialAmount amount) {
        ProductQuantity existing = destination.get(amount.materialId());
        if (existing == null && destination.size() >= materialSlotCapacity) {
            return false;
        }
        if (existing != null && existing.unit() != amount.quantity().unit()) {
            return false;
        }
        ProductQuantity capacity = capacities.get(amount.materialId());
        if (capacity != null && capacity.unit() != amount.quantity().unit()) {
            return false;
        }

        ProductQuantity inserted;
        try {
            inserted = existing == null ? amount.quantity() : existing.add(amount.quantity());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return false;
        }
        if (capacity != null && inserted.amount() > capacity.amount()) {
            return false;
        }
        destination.put(amount.materialId(), inserted);
        return true;
    }

    private void validateCurrentCapacity() {
        if (quantities.size() > materialSlotCapacity) {
            throw new IllegalArgumentException("Material store contains more materials than its slot capacity");
        }
        for (var entry : quantities.entrySet()) {
            ProductQuantity capacity = capacities.get(entry.getKey());
            if (capacity == null) {
                continue;
            }
            if (capacity.unit() != entry.getValue().unit() || entry.getValue().amount() > capacity.amount()) {
                throw new IllegalArgumentException("Material store quantity exceeds capacity for " + entry.getKey().value());
            }
        }
    }

    private static LinkedHashMap<EngineId, ProductQuantity> copyPositiveQuantities(Map<EngineId, ProductQuantity> source) {
        LinkedHashMap<EngineId, ProductQuantity> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(source, "source").entrySet()) {
            EngineId id = Objects.requireNonNull(entry.getKey(), "material id");
            ProductQuantity quantity = Objects.requireNonNull(entry.getValue(), "quantity");
            if (quantity.isZero()) {
                continue;
            }
            copied.put(id, quantity);
        }
        return copied;
    }

    private static LinkedHashMap<EngineId, ProductQuantity> copyPositiveMaterials(List<MaterialAmount> materials) {
        LinkedHashMap<EngineId, ProductQuantity> copied = new LinkedHashMap<>();
        for (MaterialAmount material : Objects.requireNonNull(materials, "materials")) {
            copied.put(material.materialId(), material.quantity());
        }
        return copied;
    }

    public static final class Builder {
        private final LinkedHashMap<EngineId, ProductQuantity> quantities = new LinkedHashMap<>();
        private final LinkedHashMap<EngineId, ProductQuantity> capacities = new LinkedHashMap<>();
        private int materialSlotCapacity = Integer.MAX_VALUE;

        private Builder() {
        }

        public Builder material(String materialId, long grams) {
            return material(EngineId.of(materialId), ProductQuantity.grams(grams));
        }

        public Builder material(EngineId materialId, ProductQuantity quantity) {
            Objects.requireNonNull(materialId, "materialId");
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.isZero()) {
                return this;
            }
            ProductQuantity existing = quantities.get(materialId);
            quantities.put(materialId, existing == null ? quantity : existing.add(quantity));
            return this;
        }

        public Builder capacity(String materialId, long grams) {
            return capacity(EngineId.of(materialId), ProductQuantity.grams(grams));
        }

        public Builder capacity(EngineId materialId, ProductQuantity quantity) {
            capacities.put(Objects.requireNonNull(materialId, "materialId"), Objects.requireNonNull(quantity, "quantity"));
            return this;
        }

        public Builder materialSlotCapacity(int materialSlotCapacity) {
            this.materialSlotCapacity = materialSlotCapacity;
            return this;
        }

        public InMemoryTransformationMaterialStore build() {
            return new InMemoryTransformationMaterialStore(quantities, capacities, materialSlotCapacity);
        }
    }
}
