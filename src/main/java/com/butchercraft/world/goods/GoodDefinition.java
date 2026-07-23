package com.butchercraft.world.goods;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public abstract sealed class GoodDefinition permits CommodityDefinition, ProductDefinition {
    private final GoodId id;
    private final String displayName;
    private final GoodCategory category;
    private final IndustryId industryId;
    private final UnitOfMeasure unitOfMeasure;
    private final Stackability stackability;
    private final Set<EconomicFlag> economicFlags;
    private final StorageRequirement storageRequirement;
    private final TransportRequirement transportRequirement;
    private final Set<ItemMappingMetadata> itemMappings;
    private final int schemaVersion;

    protected GoodDefinition(
            GoodId id,
            String displayName,
            GoodCategory category,
            IndustryId industryId,
            UnitOfMeasure unitOfMeasure,
            Stackability stackability,
            Set<EconomicFlag> economicFlags,
            StorageRequirement storageRequirement,
            TransportRequirement transportRequirement,
            Set<ItemMappingMetadata> itemMappings,
            int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = requireDisplayName(displayName);
        this.category = Objects.requireNonNull(category, "category");
        this.industryId = Objects.requireNonNull(industryId, "industryId");
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        this.stackability = Objects.requireNonNull(stackability, "stackability");
        this.economicFlags = copyEconomicFlags(economicFlags);
        this.storageRequirement = Objects.requireNonNull(storageRequirement, "storageRequirement");
        this.transportRequirement = Objects.requireNonNull(transportRequirement, "transportRequirement");
        this.itemMappings = copyItemMappings(itemMappings);
        if (schemaVersion != GoodSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported good definition schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
    }

    public final GoodId id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final GoodCategory category() {
        return category;
    }

    public final IndustryId industryId() {
        return industryId;
    }

    public final UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure;
    }

    public final Stackability stackability() {
        return stackability;
    }

    public final Set<EconomicFlag> economicFlags() {
        return economicFlags;
    }

    public final StorageRequirement storageRequirement() {
        return storageRequirement;
    }

    public final TransportRequirement transportRequirement() {
        return transportRequirement;
    }

    public final Set<ItemMappingMetadata> itemMappings() {
        return itemMappings;
    }

    public final int schemaVersion() {
        return schemaVersion;
    }

    public final boolean hasEconomicFlag(EconomicFlag flag) {
        return economicFlags.contains(Objects.requireNonNull(flag, "flag"));
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        GoodDefinition that = (GoodDefinition) other;
        return schemaVersion == that.schemaVersion
                && id.equals(that.id)
                && displayName.equals(that.displayName)
                && category == that.category
                && industryId.equals(that.industryId)
                && unitOfMeasure == that.unitOfMeasure
                && stackability == that.stackability
                && economicFlags.equals(that.economicFlags)
                && storageRequirement == that.storageRequirement
                && transportRequirement == that.transportRequirement
                && itemMappings.equals(that.itemMappings)
                && subtypeEquals(that);
    }

    @Override
    public final int hashCode() {
        return 31 * Objects.hash(
                id,
                displayName,
                category,
                industryId,
                unitOfMeasure,
                stackability,
                economicFlags,
                storageRequirement,
                transportRequirement,
                itemMappings,
                schemaVersion
        ) + subtypeHashCode();
    }

    protected abstract boolean subtypeEquals(GoodDefinition other);

    protected abstract int subtypeHashCode();

    private static String requireDisplayName(String displayName) {
        String normalized = Objects.requireNonNull(displayName, "displayName").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Good display name cannot be blank");
        }
        return normalized;
    }

    private static Set<EconomicFlag> copyEconomicFlags(Set<EconomicFlag> source) {
        Objects.requireNonNull(source, "economicFlags");
        if (source.isEmpty()) {
            return Set.of();
        }
        EnumSet<EconomicFlag> copied = EnumSet.noneOf(EconomicFlag.class);
        for (EconomicFlag flag : source) {
            copied.add(Objects.requireNonNull(flag, "economic flag"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Set<ItemMappingMetadata> copyItemMappings(Set<ItemMappingMetadata> source) {
        Objects.requireNonNull(source, "itemMappings");
        TreeSet<ItemMappingMetadata> sorted = new TreeSet<>();
        for (ItemMappingMetadata mapping : source) {
            sorted.add(Objects.requireNonNull(mapping, "item mapping"));
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }
}
