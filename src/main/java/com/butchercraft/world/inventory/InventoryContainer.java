package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;

import java.util.Objects;

public record InventoryContainer(
        InventoryId id,
        String displayName,
        ActorId ownerActorId,
        StorageNodeId storageNodeId,
        InventoryType inventoryType,
        StorageCapacity capacity,
        int schemaVersion
) {
    public InventoryContainer {
        id = Objects.requireNonNull(id, "id");
        displayName = requireDisplayName(displayName);
        ownerActorId = Objects.requireNonNull(ownerActorId, "ownerActorId");
        storageNodeId = Objects.requireNonNull(storageNodeId, "storageNodeId");
        inventoryType = Objects.requireNonNull(inventoryType, "inventoryType");
        capacity = Objects.requireNonNull(capacity, "capacity");
        if (schemaVersion != InventorySchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported inventory container schema version: " + schemaVersion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireDisplayName(String displayName) {
        String normalized = Objects.requireNonNull(displayName, "displayName").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Inventory display name cannot be blank");
        }
        return normalized;
    }

    public static final class Builder {
        private InventoryId id;
        private String displayName;
        private ActorId ownerActorId;
        private StorageNodeId storageNodeId;
        private InventoryType inventoryType;
        private StorageCapacity capacity = StorageCapacity.unlimited();
        private int schemaVersion = InventorySchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(String id) {
            return id(InventoryId.of(id));
        }

        public Builder id(InventoryId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder ownerActorId(ActorId ownerActorId) {
            this.ownerActorId = Objects.requireNonNull(ownerActorId, "ownerActorId");
            return this;
        }

        public Builder storageNodeId(StorageNodeId storageNodeId) {
            this.storageNodeId = Objects.requireNonNull(storageNodeId, "storageNodeId");
            return this;
        }

        public Builder inventoryType(InventoryType inventoryType) {
            this.inventoryType = Objects.requireNonNull(inventoryType, "inventoryType");
            return this;
        }

        public Builder capacity(StorageCapacity capacity) {
            this.capacity = Objects.requireNonNull(capacity, "capacity");
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public InventoryContainer build() {
            return new InventoryContainer(
                    id,
                    displayName,
                    ownerActorId,
                    storageNodeId,
                    inventoryType,
                    capacity,
                    schemaVersion
            );
        }
    }
}
