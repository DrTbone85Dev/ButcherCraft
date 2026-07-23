package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public record EconomicOrderDefinition(
        OrderId id,
        String displayName,
        OrderType type,
        ActorId requesterActorId,
        Optional<ActorId> counterpartyActorId,
        Optional<ContractId> governingContractId,
        List<OrderLineDefinition> lines,
        long createdSimulationTick,
        OptionalLong requestedFulfillmentTick,
        OptionalLong latestAcceptableFulfillmentTick,
        OrderPriority priority,
        Set<OrderTag> tags,
        OrderMetadata metadata,
        int schemaVersion
) {
    public EconomicOrderDefinition {
        id = Objects.requireNonNull(id, "id");
        displayName = DomainValidation.requireText(displayName, "Order display name", 256);
        type = Objects.requireNonNull(type, "type");
        requesterActorId = Objects.requireNonNull(requesterActorId, "requesterActorId");
        counterpartyActorId = Objects.requireNonNull(counterpartyActorId, "counterpartyActorId");
        governingContractId = Objects.requireNonNull(governingContractId, "governingContractId");
        lines = copyLines(lines);
        createdSimulationTick = DomainValidation.requireTick(createdSimulationTick, "Order created tick");
        requestedFulfillmentTick = Objects.requireNonNull(requestedFulfillmentTick, "requestedFulfillmentTick");
        latestAcceptableFulfillmentTick = Objects.requireNonNull(
                latestAcceptableFulfillmentTick,
                "latestAcceptableFulfillmentTick"
        );
        if (requestedFulfillmentTick.isPresent()) {
            requireNotBefore(requestedFulfillmentTick.orElseThrow(), createdSimulationTick, "requested");
        }
        if (latestAcceptableFulfillmentTick.isPresent()) {
            requireNotBefore(
                    latestAcceptableFulfillmentTick.orElseThrow(),
                    requestedFulfillmentTick.orElse(createdSimulationTick),
                    "latest acceptable"
            );
        }
        priority = Objects.requireNonNull(priority, "priority");
        tags = copyTags(tags);
        metadata = Objects.requireNonNull(metadata, "metadata");
        schemaVersion = DomainValidation.requireSchema(schemaVersion, "order definition");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<OrderLineDefinition> findLine(OrderLineId lineId) {
        return lines.stream().filter(line -> line.id().equals(Objects.requireNonNull(lineId, "lineId"))).findFirst();
    }

    private static List<OrderLineDefinition> copyLines(List<OrderLineDefinition> source) {
        List<OrderLineDefinition> copied = Objects.requireNonNull(source, "lines").stream()
                .map(line -> Objects.requireNonNull(line, "line"))
                .sorted(Comparator.comparing(OrderLineDefinition::id))
                .toList();
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("Order definition requires at least one line");
        }
        Set<OrderLineId> ids = new HashSet<>();
        Set<SemanticLine> semantics = new HashSet<>();
        for (OrderLineDefinition line : copied) {
            if (!ids.add(line.id())) {
                throw new IllegalArgumentException("Duplicate order line id: " + line.id().value());
            }
            SemanticLine semantic = new SemanticLine(
                    line.goodId(),
                    line.unitOfMeasure(),
                    line.role(),
                    line.preferredSourceInventoryId(),
                    line.preferredDestinationInventoryId()
            );
            if (!semantics.add(semantic)) {
                throw new IllegalArgumentException("Duplicate semantic order line: " + line.goodId().value());
            }
        }
        return List.copyOf(copied);
    }

    private static Set<OrderTag> copyTags(Set<OrderTag> source) {
        LinkedHashSet<OrderTag> copied = new LinkedHashSet<>();
        Objects.requireNonNull(source, "tags").stream()
                .map(tag -> Objects.requireNonNull(tag, "tag"))
                .sorted()
                .forEach(copied::add);
        return Collections.unmodifiableSet(copied);
    }

    private static void requireNotBefore(long value, long minimum, String label) {
        DomainValidation.requireTick(value, "Order " + label + " fulfillment tick");
        if (value < minimum) {
            throw new IllegalArgumentException("Order " + label + " fulfillment tick is before its minimum");
        }
    }

    private record SemanticLine(
            com.butchercraft.world.goods.GoodId goodId,
            com.butchercraft.world.goods.UnitOfMeasure unit,
            OrderLineRole role,
            Optional<com.butchercraft.world.inventory.InventoryId> source,
            Optional<com.butchercraft.world.inventory.InventoryId> destination
    ) {
    }

    public static final class Builder {
        private OrderId id;
        private String displayName;
        private OrderType type;
        private ActorId requesterActorId;
        private ActorId counterpartyActorId;
        private ContractId governingContractId;
        private List<OrderLineDefinition> lines = List.of();
        private long createdSimulationTick;
        private OptionalLong requestedFulfillmentTick = OptionalLong.empty();
        private OptionalLong latestAcceptableFulfillmentTick = OptionalLong.empty();
        private OrderPriority priority = OrderPriority.NORMAL;
        private Set<OrderTag> tags = Set.of();
        private OrderMetadata metadata = OrderMetadata.empty();
        private int schemaVersion = OrderContractSchema.CURRENT_VERSION;

        private Builder() {
        }

        public Builder id(OrderId value) { id = value; return this; }
        public Builder displayName(String value) { displayName = value; return this; }
        public Builder type(OrderType value) { type = value; return this; }
        public Builder requesterActorId(ActorId value) { requesterActorId = value; return this; }
        public Builder counterpartyActorId(ActorId value) { counterpartyActorId = value; return this; }
        public Builder governingContractId(ContractId value) { governingContractId = value; return this; }
        public Builder lines(List<OrderLineDefinition> value) { lines = value; return this; }
        public Builder createdSimulationTick(long value) { createdSimulationTick = value; return this; }
        public Builder requestedFulfillmentTick(long value) { requestedFulfillmentTick = OptionalLong.of(value); return this; }
        public Builder latestAcceptableFulfillmentTick(long value) { latestAcceptableFulfillmentTick = OptionalLong.of(value); return this; }
        public Builder priority(OrderPriority value) { priority = value; return this; }
        public Builder tags(Set<OrderTag> value) { tags = value; return this; }
        public Builder metadata(OrderMetadata value) { metadata = value; return this; }
        public Builder schemaVersion(int value) { schemaVersion = value; return this; }

        public EconomicOrderDefinition build() {
            return new EconomicOrderDefinition(
                    id, displayName, type, requesterActorId, Optional.ofNullable(counterpartyActorId),
                    Optional.ofNullable(governingContractId), lines, createdSimulationTick,
                    requestedFulfillmentTick, latestAcceptableFulfillmentTick, priority, tags, metadata, schemaVersion
            );
        }
    }
}
