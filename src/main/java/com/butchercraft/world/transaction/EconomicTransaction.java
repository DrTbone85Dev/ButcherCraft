package com.butchercraft.world.transaction;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class EconomicTransaction {
    private final TransactionId id;
    private final TransactionType type;
    private final Optional<ActorId> sourceActorId;
    private final Optional<ActorId> destinationActorId;
    private final Optional<InventoryId> sourceInventoryId;
    private final Optional<InventoryId> destinationInventoryId;
    private final GoodId goodId;
    private final long quantity;
    private final UnitOfMeasure unitOfMeasure;
    private final long simulationTick;
    private final TransactionStatus status;
    private final TransactionMetadata metadata;
    private final List<InventoryChange> inventoryChangePlan;
    private final int schemaVersion;

    public EconomicTransaction(
            TransactionId id,
            TransactionType type,
            Optional<ActorId> sourceActorId,
            Optional<ActorId> destinationActorId,
            Optional<InventoryId> sourceInventoryId,
            Optional<InventoryId> destinationInventoryId,
            GoodId goodId,
            long quantity,
            UnitOfMeasure unitOfMeasure,
            long simulationTick,
            TransactionStatus status,
            TransactionMetadata metadata,
            int schemaVersion
    ) {
        this(
                id, type, sourceActorId, destinationActorId, sourceInventoryId, destinationInventoryId,
                goodId, quantity, unitOfMeasure, simulationTick, status, metadata, List.of(), schemaVersion
        );
    }

    public EconomicTransaction(
            TransactionId id,
            TransactionType type,
            Optional<ActorId> sourceActorId,
            Optional<ActorId> destinationActorId,
            Optional<InventoryId> sourceInventoryId,
            Optional<InventoryId> destinationInventoryId,
            GoodId goodId,
            long quantity,
            UnitOfMeasure unitOfMeasure,
            long simulationTick,
            TransactionStatus status,
            TransactionMetadata metadata,
            List<InventoryChange> inventoryChangePlan,
            int schemaVersion
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.sourceActorId = Objects.requireNonNull(sourceActorId, "sourceActorId");
        this.destinationActorId = Objects.requireNonNull(destinationActorId, "destinationActorId");
        this.sourceInventoryId = Objects.requireNonNull(sourceInventoryId, "sourceInventoryId");
        this.destinationInventoryId = Objects.requireNonNull(destinationInventoryId, "destinationInventoryId");
        this.goodId = Objects.requireNonNull(goodId, "goodId");
        if (quantity < 0L) {
            throw new IllegalArgumentException("Transaction quantity must not be negative: " + quantity);
        }
        this.quantity = quantity;
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Transaction simulation tick must not be negative: " + simulationTick);
        }
        this.simulationTick = simulationTick;
        this.status = Objects.requireNonNull(status, "status");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.inventoryChangePlan = Objects.requireNonNull(inventoryChangePlan, "inventoryChangePlan").stream()
                .map(change -> Objects.requireNonNull(change, "inventoryChange"))
                .toList();
        if (schemaVersion != TransactionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported transaction schema version: " + schemaVersion);
        }
        this.schemaVersion = schemaVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public TransactionId id() {
        return id;
    }

    public TransactionType type() {
        return type;
    }

    public Optional<ActorId> sourceActorId() {
        return sourceActorId;
    }

    public Optional<ActorId> destinationActorId() {
        return destinationActorId;
    }

    public Optional<InventoryId> sourceInventoryId() {
        return sourceInventoryId;
    }

    public Optional<InventoryId> destinationInventoryId() {
        return destinationInventoryId;
    }

    public GoodId goodId() {
        return goodId;
    }

    public long quantity() {
        return quantity;
    }

    public UnitOfMeasure unitOfMeasure() {
        return unitOfMeasure;
    }

    public long simulationTick() {
        return simulationTick;
    }

    public TransactionStatus status() {
        return status;
    }

    public TransactionMetadata metadata() {
        return metadata;
    }

    public List<InventoryChange> inventoryChangePlan() {
        return inventoryChangePlan;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public EconomicTransaction withStatus(TransactionStatus nextStatus) {
        return new EconomicTransaction(
                id,
                type,
                sourceActorId,
                destinationActorId,
                sourceInventoryId,
                destinationInventoryId,
                goodId,
                quantity,
                unitOfMeasure,
                simulationTick,
                nextStatus,
                metadata,
                inventoryChangePlan,
                schemaVersion
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EconomicTransaction other)) {
            return false;
        }
        return quantity == other.quantity
                && simulationTick == other.simulationTick
                && schemaVersion == other.schemaVersion
                && id.equals(other.id)
                && type == other.type
                && sourceActorId.equals(other.sourceActorId)
                && destinationActorId.equals(other.destinationActorId)
                && sourceInventoryId.equals(other.sourceInventoryId)
                && destinationInventoryId.equals(other.destinationInventoryId)
                && goodId.equals(other.goodId)
                && unitOfMeasure == other.unitOfMeasure
                && status == other.status
                && metadata.equals(other.metadata)
                && inventoryChangePlan.equals(other.inventoryChangePlan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                type,
                sourceActorId,
                destinationActorId,
                sourceInventoryId,
                destinationInventoryId,
                goodId,
                quantity,
                unitOfMeasure,
                simulationTick,
                status,
                metadata,
                inventoryChangePlan,
                schemaVersion
        );
    }

    @Override
    public String toString() {
        return "EconomicTransaction[id=" + id + ", type=" + type + ", status=" + status + "]";
    }

    public static final class Builder {
        private TransactionId id;
        private TransactionType type;
        private ActorId sourceActorId;
        private ActorId destinationActorId;
        private InventoryId sourceInventoryId;
        private InventoryId destinationInventoryId;
        private GoodId goodId;
        private long quantity;
        private UnitOfMeasure unitOfMeasure;
        private long simulationTick;
        private TransactionStatus status = TransactionStatus.PENDING;
        private TransactionMetadata metadata = TransactionMetadata.empty();
        private final List<InventoryChange> inventoryChangePlan = new ArrayList<>();
        private int schemaVersion = TransactionSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(TransactionId value) {
            id = value;
            return this;
        }

        public Builder type(TransactionType value) {
            type = value;
            return this;
        }

        public Builder sourceActorId(ActorId value) {
            sourceActorId = value;
            return this;
        }

        public Builder destinationActorId(ActorId value) {
            destinationActorId = value;
            return this;
        }

        public Builder sourceInventoryId(InventoryId value) {
            sourceInventoryId = value;
            return this;
        }

        public Builder destinationInventoryId(InventoryId value) {
            destinationInventoryId = value;
            return this;
        }

        public Builder goodId(GoodId value) {
            goodId = value;
            return this;
        }

        public Builder quantity(long value) {
            quantity = value;
            return this;
        }

        public Builder unitOfMeasure(UnitOfMeasure value) {
            unitOfMeasure = value;
            return this;
        }

        public Builder simulationTick(long value) {
            simulationTick = value;
            return this;
        }

        public Builder status(TransactionStatus value) {
            status = value;
            return this;
        }

        public Builder metadata(TransactionMetadata value) {
            metadata = value;
            return this;
        }

        public Builder inventoryChange(InventoryChange value) {
            inventoryChangePlan.add(Objects.requireNonNull(value, "inventoryChange"));
            return this;
        }

        public Builder inventoryChangePlan(List<InventoryChange> value) {
            inventoryChangePlan.clear();
            inventoryChangePlan.addAll(Objects.requireNonNull(value, "inventoryChangePlan"));
            return this;
        }

        public Builder schemaVersion(int value) {
            schemaVersion = value;
            return this;
        }

        public EconomicTransaction build() {
            return new EconomicTransaction(
                    id,
                    type,
                    Optional.ofNullable(sourceActorId),
                    Optional.ofNullable(destinationActorId),
                    Optional.ofNullable(sourceInventoryId),
                    Optional.ofNullable(destinationInventoryId),
                    goodId,
                    quantity,
                    unitOfMeasure,
                    simulationTick,
                    status,
                    metadata,
                    inventoryChangePlan,
                    schemaVersion
            );
        }
    }
}
