package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.StorageRequirement;

import java.util.Objects;
import java.util.Optional;

public record StorageNode(
        StorageNodeId id,
        String displayName,
        StorageRequirement storageRequirement,
        StorageCapacity capacity,
        Optional<StorageNodeId> parentNodeId,
        int schemaVersion
) {
    public StorageNode {
        id = Objects.requireNonNull(id, "id");
        displayName = requireDisplayName(displayName);
        storageRequirement = Objects.requireNonNull(storageRequirement, "storageRequirement");
        capacity = Objects.requireNonNull(capacity, "capacity");
        parentNodeId = Objects.requireNonNull(parentNodeId, "parentNodeId");
        if (schemaVersion != InventorySchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported storage node schema version: " + schemaVersion);
        }
        if (parentNodeId.isPresent() && parentNodeId.orElseThrow().equals(id)) {
            throw new IllegalArgumentException("Storage node must not be its own parent: " + id.value());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String requireDisplayName(String displayName) {
        String normalized = Objects.requireNonNull(displayName, "displayName").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Storage node display name cannot be blank");
        }
        return normalized;
    }

    public static final class Builder {
        private StorageNodeId id;
        private String displayName;
        private StorageRequirement storageRequirement;
        private StorageCapacity capacity = StorageCapacity.unlimited();
        private Optional<StorageNodeId> parentNodeId = Optional.empty();
        private int schemaVersion = InventorySchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(String id) {
            return id(StorageNodeId.of(id));
        }

        public Builder id(StorageNodeId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder storageRequirement(StorageRequirement storageRequirement) {
            this.storageRequirement = Objects.requireNonNull(storageRequirement, "storageRequirement");
            return this;
        }

        public Builder capacity(StorageCapacity capacity) {
            this.capacity = Objects.requireNonNull(capacity, "capacity");
            return this;
        }

        public Builder parentNodeId(StorageNodeId parentNodeId) {
            this.parentNodeId = Optional.of(Objects.requireNonNull(parentNodeId, "parentNodeId"));
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public StorageNode build() {
            return new StorageNode(
                    id,
                    displayName,
                    storageRequirement,
                    capacity,
                    parentNodeId,
                    schemaVersion
            );
        }
    }
}
