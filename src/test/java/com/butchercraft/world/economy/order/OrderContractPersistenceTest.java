package com.butchercraft.world.economy.order;

import com.butchercraft.world.economy.order.persistence.ContractStorage;
import com.butchercraft.world.economy.order.persistence.OrderStorage;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void separateDocumentsRoundTripDeterministicallyWithCrossReferences() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicContractDefinition contract = OrderContractTestFixtures.contract("test:persisted_contract");
        context.contracts().register(contract);
        context.contracts().activate(contract.id(), 12);
        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:persisted_order", 10, contract.id());
        context.orders().submit(order);
        context.orders().accept(order.id(), 16);
        EconomicTransaction transaction = OrderContractTestFixtures.appliedTransaction(
                context, "test:persisted_tx", 4, 26
        );
        context.orders().recordFulfillment(List.of(new OrderFulfillmentRequest(
                order.id(), OrderContractTestFixtures.LINE_ID, transaction.id(), GoodQuantity.of(4), 26
        )));

        ContractStorage contractStorage = new ContractStorage(
                temporaryDirectory.resolve("contracts.json"), context.actors()
        );
        String contractJson = contractStorage.serialize(context.contracts());
        ContractManager loadedContracts = contractStorage.deserialize(contractJson);
        OrderStorage orderStorage = new OrderStorage(
                temporaryDirectory.resolve("orders.json"), context.actors(), context.inventories().registry(),
                context.transactions(), loadedContracts
        );
        String orderJson = orderStorage.serialize(context.orders());
        OrderManager loadedOrders = orderStorage.deserialize(orderJson);

        assertEquals(contractJson, contractStorage.serialize(loadedContracts));
        assertEquals(orderJson, orderStorage.serialize(loadedOrders));
        assertEquals(List.of(order.id()), loadedContracts.governedOrders(contract.id()));
        assertEquals(OrderStatus.PARTIALLY_FULFILLED,
                loadedOrders.runtimeFor(order.id()).orElseThrow().status());
        assertEquals(GoodQuantity.of(4), loadedOrders.fulfilledQuantity(order.id(), OrderContractTestFixtures.LINE_ID));
        assertTrue(orderJson.contains("\"fulfilled_quantity\": \"4\""));
        assertFalse(orderJson.contains("ItemStack"));
    }

    @Test
    void fileSaveUsesTemporaryReplacementAndMissingFilesLoadEmpty() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        ContractStorage contracts = new ContractStorage(
                temporaryDirectory.resolve("butchercraft/contracts.json"), context.actors()
        );
        ContractManager emptyContracts = contracts.load();
        OrderStorage orders = new OrderStorage(
                temporaryDirectory.resolve("butchercraft/orders.json"), context.actors(),
                context.inventories().registry(), context.transactions(), emptyContracts
        );
        OrderManager emptyOrders = orders.load();
        assertEquals(0, emptyContracts.definitions().size());
        assertEquals(0, emptyOrders.definitions().size());

        contracts.save(emptyContracts);
        orders.save(emptyOrders);
        assertTrue(Files.exists(contracts.filePath()));
        assertTrue(Files.exists(orders.filePath()));
        assertFalse(Files.exists(contracts.filePath().resolveSibling("contracts.json.tmp")));
        assertFalse(Files.exists(orders.filePath().resolveSibling("orders.json.tmp")));
    }

    @Test
    void malformedUnsupportedAndDuplicateDocumentsFailVisibly() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        ContractStorage contracts = new ContractStorage(temporaryDirectory.resolve("contracts.json"), context.actors());
        OrderStorage orders = new OrderStorage(
                temporaryDirectory.resolve("orders.json"), context.actors(), context.inventories().registry(),
                context.transactions(), context.contracts()
        );

        assertThrows(IllegalArgumentException.class, () -> contracts.deserialize("{broken"));
        assertThrows(IllegalArgumentException.class, () -> orders.deserialize("{broken"));
        assertThrows(IllegalArgumentException.class, () -> contracts.deserialize(
                "{\"schema_version\":2,\"contracts\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> orders.deserialize(
                "{\"schema_version\":2,\"orders\":[]}"));

        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:duplicate", 2);
        context.orders().submit(order);
        String json = orders.serialize(context.orders());
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray records = root.getAsJsonArray("orders");
        records.add(records.get(0).deepCopy());
        String duplicate = new GsonBuilder().serializeNulls().create().toJson(root);
        assertThrows(IllegalArgumentException.class, () -> orders.deserialize(duplicate));
    }

    @Test
    void invalidTransactionAndContractReferencesRejectTheLoadedSnapshot() {
        OrderContractTestFixtures.Context context = OrderContractTestFixtures.context();
        EconomicOrderDefinition order = OrderContractTestFixtures.order("test:allocation_ref", 2);
        context.orders().submit(order);
        context.orders().accept(order.id(), 16);
        EconomicTransaction transaction = OrderContractTestFixtures.appliedTransaction(context, "test:known_tx", 2, 26);
        context.orders().recordFulfillment(List.of(new OrderFulfillmentRequest(
                order.id(), OrderContractTestFixtures.LINE_ID, transaction.id(), GoodQuantity.of(2), 26
        )));
        OrderStorage orders = new OrderStorage(
                temporaryDirectory.resolve("orders.json"), context.actors(), context.inventories().registry(),
                context.transactions(), context.contracts()
        );
        String unknownTransaction = orders.serialize(context.orders()).replace("test:known_tx", "test:missing_tx");
        assertThrows(IllegalArgumentException.class, () -> orders.deserialize(unknownTransaction));

        EconomicContractDefinition contract = OrderContractTestFixtures.contract("test:orphan_contract");
        context.contracts().register(contract);
        context.contracts().activate(contract.id(), 12);
        EconomicOrderDefinition governed = OrderContractTestFixtures.order("test:orphan_order", 2, contract.id());
        context.orders().submit(governed);
        ContractStorage contracts = new ContractStorage(
                temporaryDirectory.resolve("contracts.json"), context.actors()
        );
        ContractManager loadedContracts = contracts.deserialize(contracts.serialize(context.contracts()));
        OrderStorage linkedOrders = new OrderStorage(
                temporaryDirectory.resolve("linked-orders.json"), context.actors(),
                context.inventories().registry(), context.transactions(), loadedContracts
        );
        String linkedJson = linkedOrders.serialize(context.orders())
                .replace("test:orphan_contract", "test:missing_contract");
        assertThrows(IllegalArgumentException.class, () -> linkedOrders.deserialize(linkedJson));
    }
}
