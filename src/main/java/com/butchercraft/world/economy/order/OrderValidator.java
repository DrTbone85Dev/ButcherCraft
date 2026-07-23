package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodDefinition;
import com.butchercraft.world.inventory.InventoryRegistry;

import java.util.Objects;

public final class OrderValidator {
    private final EconomicActorRegistry actorRegistry;
    private final InventoryRegistry inventoryRegistry;

    public OrderValidator(EconomicActorRegistry actorRegistry, InventoryRegistry inventoryRegistry) {
        this.actorRegistry = Objects.requireNonNull(actorRegistry, "actorRegistry");
        this.inventoryRegistry = Objects.requireNonNull(inventoryRegistry, "inventoryRegistry");
    }

    public OrderOperationResult validate(EconomicOrderDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        EconomicActorDefinition requester = actorRegistry.find(definition.requesterActorId()).orElse(null);
        if (requester == null) {
            return OrderOperationResult.rejected(OrderFailureCode.UNKNOWN_ACTOR,
                    "Unknown requester actor: " + definition.requesterActorId().value());
        }
        EconomicActorDefinition counterparty = definition.counterpartyActorId()
                .flatMap(actorRegistry::find).orElse(null);
        if (definition.counterpartyActorId().isPresent() && counterparty == null) {
            return OrderOperationResult.rejected(OrderFailureCode.UNKNOWN_ACTOR,
                    "Unknown counterparty actor: " + definition.counterpartyActorId().orElseThrow().value());
        }
        if (counterparty != null && requester.id().equals(counterparty.id())
                && definition.type() != OrderType.INTERNAL && definition.type() != OrderType.SYSTEM) {
            return OrderOperationResult.rejected(OrderFailureCode.INVALID_PARTIES,
                    "Only internal or system orders may use the same party on both sides");
        }
        OrderOperationResult capabilityResult = validateCapabilities(definition, requester, counterparty);
        if (!capabilityResult.success()) {
            return capabilityResult;
        }
        for (OrderLineDefinition line : definition.lines()) {
            GoodDefinition good = actorRegistry.goodRegistry().find(line.goodId()).orElse(null);
            if (good == null) {
                return OrderOperationResult.rejected(OrderFailureCode.UNKNOWN_GOOD,
                        "Unknown Good: " + line.goodId().value());
            }
            if (good.unitOfMeasure() != line.unitOfMeasure()) {
                return OrderOperationResult.rejected(OrderFailureCode.UNIT_MISMATCH,
                        "Order line unit does not match Good: " + line.id().value());
            }
            if (line.preferredSourceInventoryId().isPresent()
                    && !inventoryRegistry.contains(line.preferredSourceInventoryId().orElseThrow())) {
                return OrderOperationResult.rejected(OrderFailureCode.VALIDATION_FAILED,
                        "Unknown preferred source inventory: "
                                + line.preferredSourceInventoryId().orElseThrow().value());
            }
            if (line.preferredDestinationInventoryId().isPresent()
                    && !inventoryRegistry.contains(line.preferredDestinationInventoryId().orElseThrow())) {
                return OrderOperationResult.rejected(OrderFailureCode.VALIDATION_FAILED,
                        "Unknown preferred destination inventory: "
                                + line.preferredDestinationInventoryId().orElseThrow().value());
            }
        }
        return OrderOperationResult.accepted();
    }

    private static OrderOperationResult validateCapabilities(
            EconomicOrderDefinition definition,
            EconomicActorDefinition requester,
            EconomicActorDefinition counterparty
    ) {
        return switch (definition.type()) {
            case PURCHASE -> requireCapabilities(requester, ActorCapability.BUY, counterparty, ActorCapability.SELL);
            case SALE, SUPPLY -> requireCapabilities(
                    requester, ActorCapability.SELL, counterparty, ActorCapability.BUY
            );
            default -> OrderOperationResult.accepted();
        };
    }

    private static OrderOperationResult requireCapabilities(
            EconomicActorDefinition requester,
            ActorCapability requesterCapability,
            EconomicActorDefinition counterparty,
            ActorCapability counterpartyCapability
    ) {
        if (!requester.hasCapability(requesterCapability)) {
            return OrderOperationResult.rejected(OrderFailureCode.INVALID_PARTIES,
                    "Requester lacks required capability: " + requesterCapability.serializedName());
        }
        if (counterparty != null && !counterparty.hasCapability(counterpartyCapability)) {
            return OrderOperationResult.rejected(OrderFailureCode.INVALID_PARTIES,
                    "Counterparty lacks required capability: " + counterpartyCapability.serializedName());
        }
        return OrderOperationResult.accepted();
    }
}
