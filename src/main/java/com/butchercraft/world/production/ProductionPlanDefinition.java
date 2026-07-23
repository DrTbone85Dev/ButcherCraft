package com.butchercraft.world.production;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.OrderId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public record ProductionPlanDefinition(
        ProductionPlanId id,
        ProductionProcessId processId,
        ActorId producerActorId,
        Optional<BusinessId> businessId,
        long batchCount,
        List<ProductionInventoryBinding> inventoryBindings,
        long createdSimulationTick,
        long earliestStartTick,
        OptionalLong latestCompletionTick,
        ProductionPriority priority,
        Optional<OrderId> requestingOrderId,
        Optional<ContractId> governingContractId,
        ProductionPlanMetadata metadata,
        int schemaVersion
) {
    public ProductionPlanDefinition {
        id = Objects.requireNonNull(id, "id");
        processId = Objects.requireNonNull(processId, "processId");
        producerActorId = Objects.requireNonNull(producerActorId, "producerActorId");
        businessId = Objects.requireNonNull(businessId, "businessId");
        if (batchCount <= 0L) throw new IllegalArgumentException("Production batch count must be positive");
        inventoryBindings = Objects.requireNonNull(inventoryBindings, "inventoryBindings").stream()
                .map(binding -> Objects.requireNonNull(binding, "inventoryBinding")).sorted().toList();
        if (inventoryBindings.size() > ProductionSchema.MAXIMUM_BINDINGS) {
            throw new IllegalArgumentException("Production plan has too many inventory bindings");
        }
        rejectDuplicateBindings(inventoryBindings);
        if (createdSimulationTick < 0L || earliestStartTick < createdSimulationTick) {
            throw new IllegalArgumentException("Production plan timing is invalid");
        }
        latestCompletionTick = Objects.requireNonNull(latestCompletionTick, "latestCompletionTick");
        long validatedEarliestStartTick = earliestStartTick;
        latestCompletionTick.ifPresent(tick -> {
            if (tick < validatedEarliestStartTick) {
                throw new IllegalArgumentException("Production latest completion precedes earliest start");
            }
        });
        priority = Objects.requireNonNull(priority, "priority");
        requestingOrderId = Objects.requireNonNull(requestingOrderId, "requestingOrderId");
        governingContractId = Objects.requireNonNull(governingContractId, "governingContractId");
        metadata = Objects.requireNonNull(metadata, "metadata");
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production plan");
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ProductionInventoryBinding> inputBindings() {
        return inventoryBindings.stream()
                .filter(binding -> binding.direction() == ProductionBindingDirection.INPUT)
                .toList();
    }

    public List<ProductionInventoryBinding> outputBindings() {
        return inventoryBindings.stream()
                .filter(binding -> binding.direction() == ProductionBindingDirection.OUTPUT)
                .toList();
    }

    private static void rejectDuplicateBindings(List<ProductionInventoryBinding> bindings) {
        Set<BindingKey> keys = new HashSet<>();
        for (ProductionInventoryBinding binding : bindings) {
            if (!keys.add(new BindingKey(binding.direction(), binding.lineId()))) {
                throw new IllegalArgumentException("Duplicate production inventory binding: " + binding.lineId());
            }
        }
    }

    private record BindingKey(ProductionBindingDirection direction, ProductionLineId lineId) {
    }

    public static final class Builder {
        private ProductionPlanId id;
        private ProductionProcessId processId;
        private ActorId producerActorId;
        private BusinessId businessId;
        private long batchCount;
        private final List<ProductionInventoryBinding> bindings = new ArrayList<>();
        private long createdSimulationTick;
        private long earliestStartTick;
        private OptionalLong latestCompletionTick = OptionalLong.empty();
        private ProductionPriority priority = ProductionPriority.NORMAL;
        private OrderId requestingOrderId;
        private ContractId governingContractId;
        private ProductionPlanMetadata metadata = ProductionPlanMetadata.empty();
        private int schemaVersion = ProductionSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(ProductionPlanId value) { id = value; return this; }
        public Builder id(String value) { return id(ProductionPlanId.of(value)); }
        public Builder processId(ProductionProcessId value) { processId = value; return this; }
        public Builder producerActorId(ActorId value) { producerActorId = value; return this; }
        public Builder businessId(BusinessId value) { businessId = value; return this; }
        public Builder batchCount(long value) { batchCount = value; return this; }
        public Builder inventoryBinding(ProductionInventoryBinding value) { bindings.add(value); return this; }
        public Builder createdSimulationTick(long value) { createdSimulationTick = value; return this; }
        public Builder earliestStartTick(long value) { earliestStartTick = value; return this; }
        public Builder latestCompletionTick(long value) { latestCompletionTick = OptionalLong.of(value); return this; }
        public Builder priority(ProductionPriority value) { priority = value; return this; }
        public Builder requestingOrderId(OrderId value) { requestingOrderId = value; return this; }
        public Builder governingContractId(ContractId value) { governingContractId = value; return this; }
        public Builder metadata(ProductionPlanMetadata value) { metadata = value; return this; }
        public Builder schemaVersion(int value) { schemaVersion = value; return this; }

        public ProductionPlanDefinition build() {
            return new ProductionPlanDefinition(
                    id, processId, producerActorId, Optional.ofNullable(businessId), batchCount, bindings,
                    createdSimulationTick, earliestStartTick, latestCompletionTick, priority,
                    Optional.ofNullable(requestingOrderId), Optional.ofNullable(governingContractId),
                    metadata, schemaVersion
            );
        }
    }
}
