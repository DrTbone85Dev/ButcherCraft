package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical immutable descriptive schema for a product id.
 */
public record ProductDefinition(
        EngineId id,
        String displayName,
        int schemaVersion,
        ProductCategory category,
        QuantityUnit defaultQuantityUnit,
        Set<EngineId> tags,
        Optional<ProductPackagingMetadata> packagingMetadata,
        Map<EngineId, String> metadata
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProductDefinition(
            EngineId id,
            String displayName,
            int schemaVersion,
            ProductCategory category,
            QuantityUnit defaultQuantityUnit,
            Set<EngineId> tags,
            Map<EngineId, String> metadata
    ) {
        this(id, displayName, schemaVersion, category, defaultQuantityUnit, tags, Optional.empty(), metadata);
    }

    public ProductDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Product display name cannot be blank");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Product schema version must be positive");
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(defaultQuantityUnit, "defaultQuantityUnit");
        tags = copyTags(tags);
        packagingMetadata = Objects.requireNonNull(packagingMetadata, "packagingMetadata");
        metadata = copyMetadata(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasTag(EngineId tag) {
        return tags.contains(Objects.requireNonNull(tag, "tag"));
    }

    private static Set<EngineId> copyTags(Set<EngineId> source) {
        LinkedHashSet<EngineId> copied = new LinkedHashSet<>();
        for (EngineId tag : Objects.requireNonNull(source, "tags")) {
            copied.add(Objects.requireNonNull(tag, "tag"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Map<EngineId, String> copyMetadata(Map<EngineId, String> source) {
        LinkedHashMap<EngineId, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(source, "metadata").entrySet()) {
            EngineId key = Objects.requireNonNull(entry.getKey(), "metadata key");
            String value = Objects.requireNonNull(entry.getValue(), "metadata value").strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Product metadata values cannot be blank");
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }

    public static final class Builder {
        private EngineId id;
        private String displayName;
        private Integer schemaVersion;
        private ProductCategory category;
        private QuantityUnit defaultQuantityUnit;
        private final Set<EngineId> tags = new LinkedHashSet<>();
        private Optional<ProductPackagingMetadata> packagingMetadata = Optional.empty();
        private final Map<EngineId, String> metadata = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder id(EngineId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder id(String id) {
            return id(EngineId.of(id));
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder category(ProductCategory category) {
            this.category = Objects.requireNonNull(category, "category");
            return this;
        }

        public Builder category(EngineId categoryId) {
            return category(ProductCategory.fromId(categoryId));
        }

        public Builder category(String categoryId) {
            return category(EngineId.of(categoryId));
        }

        public Builder defaultQuantityUnit(QuantityUnit defaultQuantityUnit) {
            this.defaultQuantityUnit = Objects.requireNonNull(defaultQuantityUnit, "defaultQuantityUnit");
            return this;
        }

        public Builder tag(EngineId tag) {
            tags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public Builder tag(String tag) {
            return tag(EngineId.of(tag));
        }

        public Builder tags(Set<EngineId> tags) {
            this.tags.addAll(Objects.requireNonNull(tags, "tags"));
            return this;
        }

        public Builder packagingMetadata(ProductPackagingMetadata packagingMetadata) {
            this.packagingMetadata = Optional.of(Objects.requireNonNull(packagingMetadata, "packagingMetadata"));
            return this;
        }

        public Builder packagingMetadata(Optional<ProductPackagingMetadata> packagingMetadata) {
            this.packagingMetadata = Objects.requireNonNull(packagingMetadata, "packagingMetadata");
            return this;
        }

        public Builder noPackagingMetadata() {
            this.packagingMetadata = Optional.empty();
            return this;
        }

        public Builder metadata(EngineId key, String value) {
            metadata.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        public Builder metadata(String key, String value) {
            return metadata(EngineId.of(key), value);
        }

        public Builder metadata(Map<EngineId, String> metadata) {
            this.metadata.putAll(Objects.requireNonNull(metadata, "metadata"));
            return this;
        }

        public ProductDefinition build() {
            require(id != null, "Product id is required");
            require(displayName != null, "Product display name is required");
            require(schemaVersion != null, "Product schema version is required");
            require(category != null, "Product category is required");
            require(defaultQuantityUnit != null, "Product default quantity unit is required");
            return new ProductDefinition(
                    id,
                    displayName,
                    schemaVersion,
                    category,
                    defaultQuantityUnit,
                    tags,
                    packagingMetadata,
                    metadata
            );
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }
    }
}
