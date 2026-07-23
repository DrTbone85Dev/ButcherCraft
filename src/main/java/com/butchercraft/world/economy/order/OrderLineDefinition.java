package com.butchercraft.world.economy.order;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;

import java.util.Objects;
import java.util.Optional;

public record OrderLineDefinition(
        OrderLineId id,
        GoodId goodId,
        GoodQuantity requestedQuantity,
        UnitOfMeasure unitOfMeasure,
        OrderLineRole role,
        Optional<InventoryId> preferredSourceInventoryId,
        Optional<InventoryId> preferredDestinationInventoryId,
        Optional<SubstitutionPolicy> substitutionPolicy,
        OrderLineMetadata metadata
) {
    public OrderLineDefinition {
        id = Objects.requireNonNull(id, "id");
        goodId = Objects.requireNonNull(goodId, "goodId");
        requestedQuantity = Objects.requireNonNull(requestedQuantity, "requestedQuantity")
                .requirePositive("Order line requested quantity");
        unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        role = Objects.requireNonNull(role, "role");
        preferredSourceInventoryId = Objects.requireNonNull(
                preferredSourceInventoryId,
                "preferredSourceInventoryId"
        );
        preferredDestinationInventoryId = Objects.requireNonNull(
                preferredDestinationInventoryId,
                "preferredDestinationInventoryId"
        );
        substitutionPolicy = Objects.requireNonNull(substitutionPolicy, "substitutionPolicy");
        metadata = Objects.requireNonNull(metadata, "metadata");
        if (preferredSourceInventoryId.isPresent()
                && preferredSourceInventoryId.equals(preferredDestinationInventoryId)) {
            throw new IllegalArgumentException("Order line preferred inventories must be distinct");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private OrderLineId id;
        private GoodId goodId;
        private GoodQuantity requestedQuantity;
        private UnitOfMeasure unitOfMeasure;
        private OrderLineRole role = OrderLineRole.REQUESTED;
        private InventoryId preferredSourceInventoryId;
        private InventoryId preferredDestinationInventoryId;
        private SubstitutionPolicy substitutionPolicy;
        private OrderLineMetadata metadata = OrderLineMetadata.empty();

        private Builder() {
        }

        public Builder id(OrderLineId value) { id = value; return this; }
        public Builder goodId(GoodId value) { goodId = value; return this; }
        public Builder requestedQuantity(GoodQuantity value) { requestedQuantity = value; return this; }
        public Builder unitOfMeasure(UnitOfMeasure value) { unitOfMeasure = value; return this; }
        public Builder role(OrderLineRole value) { role = value; return this; }
        public Builder preferredSourceInventoryId(InventoryId value) { preferredSourceInventoryId = value; return this; }
        public Builder preferredDestinationInventoryId(InventoryId value) { preferredDestinationInventoryId = value; return this; }
        public Builder substitutionPolicy(SubstitutionPolicy value) { substitutionPolicy = value; return this; }
        public Builder metadata(OrderLineMetadata value) { metadata = value; return this; }

        public OrderLineDefinition build() {
            return new OrderLineDefinition(
                    id,
                    goodId,
                    requestedQuantity,
                    unitOfMeasure,
                    role,
                    Optional.ofNullable(preferredSourceInventoryId),
                    Optional.ofNullable(preferredDestinationInventoryId),
                    Optional.ofNullable(substitutionPolicy),
                    metadata
            );
        }
    }
}
