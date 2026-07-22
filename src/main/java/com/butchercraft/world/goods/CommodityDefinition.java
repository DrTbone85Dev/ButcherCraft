package com.butchercraft.world.goods;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class CommodityDefinition extends GoodDefinition {
    private final CommodityType commodityType;

    public CommodityDefinition(
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
            CommodityType commodityType
    ) {
        super(
                id,
                displayName,
                GoodCategory.COMMODITY,
                industryId,
                unitOfMeasure,
                stackability,
                economicFlags,
                storageRequirement,
                transportRequirement,
                itemMappings,
                schemaVersion
        );
        this.commodityType = Objects.requireNonNull(commodityType, "commodityType");
    }

    public CommodityType commodityType() {
        return commodityType;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected boolean subtypeEquals(GoodDefinition other) {
        return commodityType == ((CommodityDefinition) other).commodityType;
    }

    @Override
    protected int subtypeHashCode() {
        return commodityType.hashCode();
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
        private CommodityType commodityType;

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

        public Builder commodityType(CommodityType commodityType) {
            this.commodityType = Objects.requireNonNull(commodityType, "commodityType");
            return this;
        }

        public CommodityDefinition build() {
            require(id != null, "Commodity id is required");
            require(displayName != null, "Commodity display name is required");
            require(industryId != null, "Commodity industry id is required");
            require(unitOfMeasure != null, "Commodity unit of measure is required");
            require(stackability != null, "Commodity stackability is required");
            require(storageRequirement != null, "Commodity storage requirement is required");
            require(transportRequirement != null, "Commodity transport requirement is required");
            require(commodityType != null, "Commodity type is required");
            return new CommodityDefinition(
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
                    commodityType
            );
        }

        private static void require(boolean condition, String message) {
            if (!condition) {
                throw new IllegalStateException(message);
            }
        }
    }
}
