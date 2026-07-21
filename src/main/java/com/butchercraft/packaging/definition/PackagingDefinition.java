package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical immutable definition of a retail packaging format and product compatibility rules.
 */
public record PackagingDefinition(
        EngineId id,
        String displayName,
        int schemaVersion,
        PackagingFormat format,
        QuantityUnit defaultQuantityUnit,
        Set<ProductCategory> compatibleCategories,
        Set<EngineId> compatibleTags,
        Map<EngineId, String> metadata
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PackagingDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("Packaging display name cannot be blank");
        }
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Packaging schema version must be positive");
        }
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(defaultQuantityUnit, "defaultQuantityUnit");
        compatibleCategories = copyCategories(compatibleCategories);
        compatibleTags = copyTags(compatibleTags);
        if (compatibleCategories.isEmpty() && compatibleTags.isEmpty()) {
            throw new IllegalArgumentException("Packaging definition must declare at least one compatibility rule");
        }
        metadata = copyMetadata(metadata);
    }

    public static PackagingRegistryBuilder registryBuilder() {
        return new PackagingRegistryBuilder();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isCompatibleWith(ProductDefinition product) {
        Objects.requireNonNull(product, "product");
        if (product.defaultQuantityUnit() != defaultQuantityUnit) {
            return false;
        }
        if (!compatibleCategories.isEmpty() && !compatibleCategories.contains(product.category())) {
            return false;
        }
        if (compatibleTags.isEmpty()) {
            return true;
        }
        return compatibleTags.stream().anyMatch(product::hasTag);
    }

    private static Set<ProductCategory> copyCategories(Set<ProductCategory> source) {
        LinkedHashSet<ProductCategory> copied = new LinkedHashSet<>();
        for (ProductCategory category : Objects.requireNonNull(source, "compatibleCategories")) {
            copied.add(Objects.requireNonNull(category, "compatible category"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Set<EngineId> copyTags(Set<EngineId> source) {
        LinkedHashSet<EngineId> copied = new LinkedHashSet<>();
        for (EngineId tag : Objects.requireNonNull(source, "compatibleTags")) {
            copied.add(Objects.requireNonNull(tag, "compatible tag"));
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Map<EngineId, String> copyMetadata(Map<EngineId, String> source) {
        LinkedHashMap<EngineId, String> copied = new LinkedHashMap<>();
        for (var entry : Objects.requireNonNull(source, "metadata").entrySet()) {
            EngineId key = Objects.requireNonNull(entry.getKey(), "metadata key");
            String value = Objects.requireNonNull(entry.getValue(), "metadata value").strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Packaging metadata values cannot be blank");
            }
            copied.put(key, value);
        }
        return Collections.unmodifiableMap(copied);
    }

    public static final class Builder {
        private EngineId id;
        private String displayName;
        private Integer schemaVersion;
        private PackagingFormat format;
        private QuantityUnit defaultQuantityUnit;
        private final Set<ProductCategory> compatibleCategories = new LinkedHashSet<>();
        private final Set<EngineId> compatibleTags = new LinkedHashSet<>();
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

        public Builder format(PackagingFormat format) {
            this.format = Objects.requireNonNull(format, "format");
            return this;
        }

        public Builder format(String format) {
            return format(PackagingFormat.fromId(format));
        }

        public Builder defaultQuantityUnit(QuantityUnit defaultQuantityUnit) {
            this.defaultQuantityUnit = Objects.requireNonNull(defaultQuantityUnit, "defaultQuantityUnit");
            return this;
        }

        public Builder compatibleCategory(ProductCategory category) {
            compatibleCategories.add(Objects.requireNonNull(category, "category"));
            return this;
        }

        public Builder compatibleCategory(EngineId categoryId) {
            return compatibleCategory(ProductCategory.fromId(categoryId));
        }

        public Builder compatibleCategory(String categoryId) {
            return compatibleCategory(EngineId.of(categoryId));
        }

        public Builder compatibleTag(EngineId tag) {
            compatibleTags.add(Objects.requireNonNull(tag, "tag"));
            return this;
        }

        public Builder compatibleTag(String tag) {
            return compatibleTag(EngineId.of(tag));
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

        public PackagingDefinition build() {
            require(id != null, "Packaging id is required");
            require(displayName != null, "Packaging display name is required");
            require(schemaVersion != null, "Packaging schema version is required");
            require(format != null, "Packaging format is required");
            require(defaultQuantityUnit != null, "Packaging default quantity unit is required");
            return new PackagingDefinition(
                    id,
                    displayName,
                    schemaVersion,
                    format,
                    defaultQuantityUnit,
                    compatibleCategories,
                    compatibleTags,
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
