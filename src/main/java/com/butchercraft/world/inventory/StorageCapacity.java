package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

public record StorageCapacity(
        Optional<CapacityLimit> maximumWeight,
        Optional<CapacityLimit> maximumVolume,
        OptionalLong maximumUnits,
        OptionalInt maximumDistinctGoods
) {
    public StorageCapacity {
        maximumWeight = Objects.requireNonNull(maximumWeight, "maximumWeight");
        maximumVolume = Objects.requireNonNull(maximumVolume, "maximumVolume");
        maximumUnits = Objects.requireNonNull(maximumUnits, "maximumUnits");
        maximumDistinctGoods = Objects.requireNonNull(maximumDistinctGoods, "maximumDistinctGoods");
        maximumWeight.ifPresent(limit -> requireDimension(limit, UnitDimension.WEIGHT, "weight"));
        maximumVolume.ifPresent(limit -> requireDimension(limit, UnitDimension.VOLUME, "volume"));
        maximumUnits.ifPresent(value -> requireNonNegative(value, "maximum units"));
        maximumDistinctGoods.ifPresent(value -> requireNonNegative(value, "maximum distinct goods"));
    }

    public static StorageCapacity unlimited() {
        return new StorageCapacity(
                Optional.empty(),
                Optional.empty(),
                OptionalLong.empty(),
                OptionalInt.empty()
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validateEntries(Collection<InventoryEntry> entries, String capacityOwner) {
        Objects.requireNonNull(entries, "entries");
        String owner = Objects.requireNonNull(capacityOwner, "capacityOwner");
        maximumWeight.ifPresent(limit -> validateMeasuredLimit(entries, limit, UnitDimension.WEIGHT, owner));
        maximumVolume.ifPresent(limit -> validateMeasuredLimit(entries, limit, UnitDimension.VOLUME, owner));
        maximumUnits.ifPresent(limit -> validateUnitLimit(entries, limit, owner));
        maximumDistinctGoods.ifPresent(limit -> validateDistinctGoodLimit(entries, limit, owner));
    }

    public void validateWithin(StorageCapacity parent, String capacityOwner) {
        Objects.requireNonNull(parent, "parent");
        String owner = Objects.requireNonNull(capacityOwner, "capacityOwner");
        validateNestedLimit(maximumWeight, parent.maximumWeight, UnitDimension.WEIGHT, owner);
        validateNestedLimit(maximumVolume, parent.maximumVolume, UnitDimension.VOLUME, owner);
        if (maximumUnits.isPresent() && parent.maximumUnits.isPresent()
                && maximumUnits.getAsLong() > parent.maximumUnits.getAsLong()) {
            throw new IllegalArgumentException("Storage capacity exceeds parent maximum units: " + owner);
        }
        if (maximumDistinctGoods.isPresent() && parent.maximumDistinctGoods.isPresent()
                && maximumDistinctGoods.getAsInt() > parent.maximumDistinctGoods.getAsInt()) {
            throw new IllegalArgumentException("Storage capacity exceeds parent maximum distinct goods: " + owner);
        }
    }

    private static void validateMeasuredLimit(
            Collection<InventoryEntry> entries,
            CapacityLimit limit,
            UnitDimension dimension,
            String owner
    ) {
        BigDecimal used = entries.stream()
                .filter(entry -> dimensionOf(entry.unitOfMeasure()) == dimension)
                .map(entry -> normalized(entry.quantity(), entry.unitOfMeasure(), dimension))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allowed = normalized(limit.quantity(), limit.unitOfMeasure(), dimension);
        if (used.compareTo(allowed) > 0) {
            throw new IllegalArgumentException("Storage capacity exceeds maximum "
                    + dimension.serializedName + ": " + owner);
        }
    }

    private static void validateUnitLimit(Collection<InventoryEntry> entries, long limit, String owner) {
        BigInteger used = entries.stream()
                .filter(entry -> dimensionOf(entry.unitOfMeasure()) == UnitDimension.DISCRETE)
                .map(entry -> BigInteger.valueOf(entry.quantity()))
                .reduce(BigInteger.ZERO, BigInteger::add);
        if (used.compareTo(BigInteger.valueOf(limit)) > 0) {
            throw new IllegalArgumentException("Storage capacity exceeds maximum units: " + owner);
        }
    }

    private static void validateDistinctGoodLimit(Collection<InventoryEntry> entries, int limit, String owner) {
        Set<GoodId> distinctGoods = new HashSet<>();
        entries.stream()
                .filter(entry -> entry.quantity() > 0L)
                .map(InventoryEntry::goodId)
                .forEach(distinctGoods::add);
        if (distinctGoods.size() > limit) {
            throw new IllegalArgumentException("Storage capacity exceeds maximum distinct goods: " + owner);
        }
    }

    private static void validateNestedLimit(
            Optional<CapacityLimit> child,
            Optional<CapacityLimit> parent,
            UnitDimension dimension,
            String owner
    ) {
        if (child.isEmpty() || parent.isEmpty()) {
            return;
        }
        BigDecimal childValue = normalized(child.get().quantity(), child.get().unitOfMeasure(), dimension);
        BigDecimal parentValue = normalized(parent.get().quantity(), parent.get().unitOfMeasure(), dimension);
        if (childValue.compareTo(parentValue) > 0) {
            throw new IllegalArgumentException("Storage capacity exceeds parent maximum "
                    + dimension.serializedName + ": " + owner);
        }
    }

    private static BigDecimal normalized(long quantity, UnitOfMeasure unit, UnitDimension dimension) {
        BigDecimal factor = switch (unit) {
            case KILOGRAM -> BigDecimal.ONE;
            case POUND -> new BigDecimal("0.45359237");
            case TON -> new BigDecimal("907.18474");
            case LITER -> BigDecimal.ONE;
            case GALLON -> new BigDecimal("3.785411784");
            case BUSHEL -> new BigDecimal("35.23907016688");
            default -> throw new IllegalArgumentException("Unit is not valid for "
                    + dimension.serializedName + " capacity: " + unit.serializedName());
        };
        return BigDecimal.valueOf(quantity).multiply(factor);
    }

    private static void requireDimension(CapacityLimit limit, UnitDimension dimension, String label) {
        Objects.requireNonNull(limit, label + "Limit");
        if (dimensionOf(limit.unitOfMeasure()) != dimension) {
            throw new IllegalArgumentException("Maximum " + label + " uses an invalid unit: "
                    + limit.unitOfMeasure().serializedName());
        }
    }

    private static UnitDimension dimensionOf(UnitOfMeasure unit) {
        return switch (Objects.requireNonNull(unit, "unit")) {
            case POUND, KILOGRAM, TON -> UnitDimension.WEIGHT;
            case LITER, GALLON, BUSHEL -> UnitDimension.VOLUME;
            case EACH, HEAD, PALLET, CRATE, BOX -> UnitDimension.DISCRETE;
            case KILOWATT_HOUR -> UnitDimension.OTHER;
        };
    }

    private static long requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException("Storage " + label + " must not be negative: " + value);
        }
        return value;
    }

    public record CapacityLimit(long quantity, UnitOfMeasure unitOfMeasure) {
        public CapacityLimit {
            requireNonNegative(quantity, "capacity limit");
            unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        }
    }

    public static final class Builder {
        private CapacityLimit maximumWeight;
        private CapacityLimit maximumVolume;
        private OptionalLong maximumUnits = OptionalLong.empty();
        private OptionalInt maximumDistinctGoods = OptionalInt.empty();

        private Builder() {
        }

        public Builder maximumWeight(long quantity, UnitOfMeasure unit) {
            maximumWeight = new CapacityLimit(quantity, unit);
            return this;
        }

        public Builder maximumVolume(long quantity, UnitOfMeasure unit) {
            maximumVolume = new CapacityLimit(quantity, unit);
            return this;
        }

        public Builder maximumUnits(long quantity) {
            maximumUnits = OptionalLong.of(requireNonNegative(quantity, "maximum units"));
            return this;
        }

        public Builder maximumDistinctGoods(int quantity) {
            requireNonNegative(quantity, "maximum distinct goods");
            maximumDistinctGoods = OptionalInt.of(quantity);
            return this;
        }

        public StorageCapacity build() {
            return new StorageCapacity(
                    Optional.ofNullable(maximumWeight),
                    Optional.ofNullable(maximumVolume),
                    maximumUnits,
                    maximumDistinctGoods
            );
        }
    }

    private enum UnitDimension {
        WEIGHT("weight"),
        VOLUME("volume"),
        DISCRETE("units"),
        OTHER("other");

        private final String serializedName;

        UnitDimension(String serializedName) {
            this.serializedName = serializedName;
        }
    }
}
