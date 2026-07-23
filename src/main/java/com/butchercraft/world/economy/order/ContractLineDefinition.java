package com.butchercraft.world.economy.order;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.Objects;
import java.util.Optional;

public record ContractLineDefinition(
        ContractLineId id,
        GoodId goodId,
        GoodQuantity committedQuantity,
        UnitOfMeasure unitOfMeasure,
        CommitmentPeriod commitmentPeriod,
        Optional<GoodQuantity> minimumQuantity,
        Optional<GoodQuantity> maximumQuantity,
        Optional<GoodQuantity> allowedVariance,
        ContractLineMetadata metadata
) {
    public ContractLineDefinition {
        id = Objects.requireNonNull(id, "id");
        goodId = Objects.requireNonNull(goodId, "goodId");
        committedQuantity = Objects.requireNonNull(committedQuantity, "committedQuantity")
                .requirePositive("Contract line committed quantity");
        unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        commitmentPeriod = Objects.requireNonNull(commitmentPeriod, "commitmentPeriod");
        minimumQuantity = positiveOptional(minimumQuantity, "minimumQuantity");
        maximumQuantity = positiveOptional(maximumQuantity, "maximumQuantity");
        allowedVariance = Objects.requireNonNull(allowedVariance, "allowedVariance");
        metadata = Objects.requireNonNull(metadata, "metadata");
        if (minimumQuantity.isPresent() && minimumQuantity.orElseThrow().compareTo(committedQuantity) > 0) {
            throw new IllegalArgumentException("Contract line minimum exceeds committed quantity");
        }
        if (maximumQuantity.isPresent() && maximumQuantity.orElseThrow().compareTo(committedQuantity) < 0) {
            throw new IllegalArgumentException("Contract line maximum is below committed quantity");
        }
        if (minimumQuantity.isPresent() && maximumQuantity.isPresent()
                && minimumQuantity.orElseThrow().compareTo(maximumQuantity.orElseThrow()) > 0) {
            throw new IllegalArgumentException("Contract line minimum exceeds maximum quantity");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Optional<GoodQuantity> positiveOptional(Optional<GoodQuantity> value, String label) {
        return Objects.requireNonNull(value, label).map(quantity -> quantity.requirePositive("Contract line " + label));
    }

    public static final class Builder {
        private ContractLineId id;
        private GoodId goodId;
        private GoodQuantity committedQuantity;
        private UnitOfMeasure unitOfMeasure;
        private CommitmentPeriod commitmentPeriod = CommitmentPeriod.PER_ORDER;
        private GoodQuantity minimumQuantity;
        private GoodQuantity maximumQuantity;
        private GoodQuantity allowedVariance;
        private ContractLineMetadata metadata = ContractLineMetadata.empty();

        private Builder() {
        }

        public Builder id(ContractLineId value) { id = value; return this; }
        public Builder goodId(GoodId value) { goodId = value; return this; }
        public Builder committedQuantity(GoodQuantity value) { committedQuantity = value; return this; }
        public Builder unitOfMeasure(UnitOfMeasure value) { unitOfMeasure = value; return this; }
        public Builder commitmentPeriod(CommitmentPeriod value) { commitmentPeriod = value; return this; }
        public Builder minimumQuantity(GoodQuantity value) { minimumQuantity = value; return this; }
        public Builder maximumQuantity(GoodQuantity value) { maximumQuantity = value; return this; }
        public Builder allowedVariance(GoodQuantity value) { allowedVariance = value; return this; }
        public Builder metadata(ContractLineMetadata value) { metadata = value; return this; }

        public ContractLineDefinition build() {
            return new ContractLineDefinition(
                    id, goodId, committedQuantity, unitOfMeasure, commitmentPeriod,
                    Optional.ofNullable(minimumQuantity), Optional.ofNullable(maximumQuantity),
                    Optional.ofNullable(allowedVariance), metadata
            );
        }
    }
}
