package com.butchercraft.world.goods;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Economic product definition. This is separate from processing content definitions.
 */
public final class ProductDefinition extends GoodDefinition {
    private final IndustryId sourceIndustryId;
    private final ProductStage transformationStage;

    public ProductDefinition(
            GoodId id,
            String displayName,
            IndustryId industryId,
            UnitOfMeasure unitOfMeasure,
            Stackability stackability,
            Set<EconomicFlag> economicFlags,
            StorageRequirement storageRequirement,
            TransportRequirement transportRequirement,
            Set<ItemMappingMetadata> itemMappings,
            int schemaVersion,
            IndustryId sourceIndustryId,
            ProductStage transformationStage
    ) {
        super(
                id,
                displayName,
                GoodCategory.PRODUCT,
                industryId,
                unitOfMeasure,
                stackability,
                economicFlags,
                storageRequirement,
                transportRequirement,
                itemMappings,
                schemaVersion
        );
        this.sourceIndustryId = Objects.requireNonNull(sourceIndustryId, "sourceIndustryId");
        this.transformationStage = Objects.requireNonNull(transformationStage, "transformationStage");
    }

    public IndustryId sourceIndustryId() {
        return sourceIndustryId;
    }

    public ProductStage transformationStage() {
        return transformationStage;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected boolean subtypeEquals(GoodDefinition other) {
        ProductDefinition that = (ProductDefinition) other;
        return sourceIndustryId.equals(that.sourceIndustryId)
                && transformationStage == that.transformationStage;
    }

    @Override
    protected int subtypeHashCode() {
        return Objects.hash(sourceIndustryId, transformationStage);
    }

    public static final class Builder {
        private GoodId id;
        private String displayName;
        private IndustryId industryId;
        private UnitOfMeasure unitOfMeasure;
        private Stackability stackability;
        private final Set<EconomicFlag> economicFlags = new LinkedHashSet<>();
        private StorageRequirement storageRequirement;
        private TransportRequirement transportRequirement;
        private final Set<ItemMappingMetadata> itemMappings = new LinkedHashSet<>();
        private int schemaVersion = GoodSchema.CURRENT_VERSION;
        private IndustryId sourceIndustryId;
        private ProductStage transformationStage;

        private Builder() {
        }

        public Builder id(GoodId id) {
            this.id = Objects.requireNonNull(id, "id");
            return this;
        }

        public Builder id(String id) {
            return id(GoodId.of(id));
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder industryId(IndustryId industryId) {
            this.industryId = Objects.requireNonNull(industryId, "industryId");
            return this;
        }

        public Builder unitOfMeasure(UnitOfMeasure unitOfMeasure) {
            this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            return this;
        }

        public Builder stackability(Stackability stackability) {
            this.stackability = Objects.requireNonNull(stackability, "stackability");
            return this;
        }

        public Builder economicFlag(EconomicFlag economicFlag) {
            economicFlags.add(Objects.requireNonNull(economicFlag, "economicFlag"));
            return this;
        }

        public Builder economicFlags(Set<EconomicFlag> economicFlags) {
            this.economicFlags.addAll(Objects.requireNonNull(economicFlags, "economicFlags"));
            return this;
        }

        public Builder storageRequirement(StorageRequirement storageRequirement) {
            this.storageRequirement = Objects.requireNonNull(storageRequirement, "storageRequirement");
            return this;
        }

        public Builder transportRequirement(TransportRequirement transportRequirement) {
            this.transportRequirement = Objects.requireNonNull(transportRequirement, "transportRequirement");
            return this;
        }

        public Builder itemMapping(ItemMappingMetadata itemMapping) {
            itemMappings.add(Objects.requireNonNull(itemMapping, "itemMapping"));
            return this;
        }

        public Builder itemMappings(Set<ItemMappingMetadata> itemMappings) {
            this.itemMappings.addAll(Objects.requireNonNull(itemMappings, "itemMappings"));
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder sourceIndustryId(IndustryId sourceIndustryId) {
            this.sourceIndustryId = Objects.requireNonNull(sourceIndustryId, "sourceIndustryId");
            return this;
        }

        public Builder transformationStage(ProductStage transformationStage) {
            this.transformationStage = Objects.requireNonNull(transformationStage, "transformationStage");
            return this;
        }

        public ProductDefinition build() {
            require(id != null, "Product id is required");
            require(displayName != null, "Product display name is required");
            require(industryId != null, "Product industry id is required");
            require(unitOfMeasure != null, "Product unit of measure is required");
            require(stackability != null, "Product stackability is required");
            require(storageRequirement != null, "Product storage requirement is required");
            require(transportRequirement != null, "Product transport requirement is required");
            require(sourceIndustryId != null, "Product source industry id is required");
            require(transformationStage != null, "Product transformation stage is required");
            return new ProductDefinition(
                    id,
                    displayName,
                    industryId,
                    unitOfMeasure,
                    stackability,
                    economicFlags,
                    storageRequirement,
                    transportRequirement,
                    itemMappings,
                    schemaVersion,
                    sourceIndustryId,
                    transformationStage
            );
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }
    }
}
