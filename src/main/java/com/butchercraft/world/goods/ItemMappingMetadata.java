package com.butchercraft.world.goods;

import java.util.Objects;

/**
 * Informational reference to a possible physical item representation.
 *
 * <p>The goods domain does not resolve this reference or depend on an ItemStack.</p>
 */
public record ItemMappingMetadata(GoodId providerId, GoodId itemId) implements Comparable<ItemMappingMetadata> {
    public ItemMappingMetadata {
        providerId = Objects.requireNonNull(providerId, "providerId");
        itemId = Objects.requireNonNull(itemId, "itemId");
    }

    public static ItemMappingMetadata of(String providerId, String itemId) {
        return new ItemMappingMetadata(GoodId.of(providerId), GoodId.of(itemId));
    }

    @Override
    public int compareTo(ItemMappingMetadata other) {
        Objects.requireNonNull(other, "other");
        int providerComparison = providerId.compareTo(other.providerId);
        return providerComparison != 0 ? providerComparison : itemId.compareTo(other.itemId);
    }
}
