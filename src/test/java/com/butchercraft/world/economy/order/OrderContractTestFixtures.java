package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionType;

import java.util.List;
import java.util.Set;

final class OrderContractTestFixtures {
    static final GoodId BEEF = InventoryTestFixtures.BEEF;
    static final ActorId REQUESTER = InventoryTestFixtures.WAREHOUSE_ACTOR;
    static final ActorId COUNTERPARTY = InventoryTestFixtures.FARM_ACTOR;
    static final OrderId ORDER_ID = OrderId.of("test:order");
    static final OrderLineId LINE_ID = OrderLineId.of("test:line");
    static final ContractId CONTRACT_ID = ContractId.of("test:contract");

    private OrderContractTestFixtures() { }

    static Context context() {
        InventoryManager inventories = InventoryTestFixtures.manager();
        EconomicActorRegistry actors = inventories.registry().actorRegistry();
        TransactionManager transactions = new TransactionManager(inventories);
        ContractManager contracts = new ContractManager(actors);
        OrderManager orders = new OrderManager(actors, inventories.registry(), transactions, contracts);
        return new Context(actors, inventories, transactions, contracts, orders);
    }

    static EconomicOrderDefinition order(String id, long quantity) {
        return order(id, quantity, null);
    }

    static EconomicOrderDefinition order(String id, long quantity, ContractId contractId) {
        EconomicOrderDefinition.Builder builder = EconomicOrderDefinition.builder()
                .id(OrderId.of(id)).displayName("Replenishment Order").type(OrderType.REPLENISHMENT)
                .requesterActorId(REQUESTER).counterpartyActorId(COUNTERPARTY)
                .lines(List.of(line("test:line", quantity, OrderLineRole.REQUESTED)))
                .createdSimulationTick(15L).requestedFulfillmentTick(30L)
                .latestAcceptableFulfillmentTick(40L).priority(OrderPriority.HIGH)
                .tags(Set.of(OrderTag.of("test:replenishment")))
                .metadata(OrderMetadata.of("EXT-1", "test order", "tests", "coverage"));
        if (contractId != null) builder.governingContractId(contractId);
        return builder.build();
    }

    static OrderLineDefinition line(String id, long quantity, OrderLineRole role) {
        return OrderLineDefinition.builder().id(OrderLineId.of(id)).goodId(BEEF)
                .requestedQuantity(GoodQuantity.of(quantity)).unitOfMeasure(UnitOfMeasure.POUND)
                .role(role).substitutionPolicy(SubstitutionPolicy.EXACT_ONLY)
                .metadata(new OrderLineMetadata(java.util.Optional.of("LINE-1"), java.util.Optional.empty()))
                .build();
    }

    static EconomicContractDefinition contract(String id) {
        return EconomicContractDefinition.builder().id(ContractId.of(id)).displayName("Supply Contract")
                .type(ContractType.SUPPLY).principalActorId(REQUESTER).counterpartyActorId(COUNTERPARTY)
                .supportedIndustries(Set.of(BuiltInIndustryCatalog.AGRICULTURE))
                .lines(List.of(ContractLineDefinition.builder().id(ContractLineId.of("test:contract_line"))
                        .goodId(BEEF).committedQuantity(GoodQuantity.of(100))
                        .unitOfMeasure(UnitOfMeasure.POUND).commitmentPeriod(CommitmentPeriod.WEEKLY)
                        .minimumQuantity(GoodQuantity.of(10)).maximumQuantity(GoodQuantity.of(120))
                        .allowedVariance(GoodQuantity.of(5)).build()))
                .effectiveSimulationTick(10L).expirationSimulationTick(100L)
                .schedule(ContractSchedule.interval(20L)).terms(ContractTerms.standard())
                .metadata(new ContractMetadata(
                        java.util.Optional.of("CONTRACT-1"), java.util.Optional.empty(),
                        java.util.Optional.of("tests"), java.util.Optional.empty(), Set.of("test:supply")
                )).build();
    }

    static EconomicTransaction appliedTransaction(Context context, String id, long quantity, long tick) {
        EconomicTransaction transaction = EconomicTransaction.builder().id(TransactionId.of(id))
                .type(TransactionType.INVENTORY_ADD).destinationActorId(REQUESTER)
                .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY).goodId(BEEF)
                .quantity(quantity).unitOfMeasure(UnitOfMeasure.POUND).simulationTick(tick).build();
        if (!context.transactions().submit(transaction).success()) {
            throw new IllegalStateException("Fixture transaction was rejected");
        }
        return context.transactions().find(transaction.id()).orElseThrow();
    }

    record Context(
            EconomicActorRegistry actors,
            InventoryManager inventories,
            TransactionManager transactions,
            ContractManager contracts,
            OrderManager orders
    ) { }
}
