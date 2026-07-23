package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionDefinitionTest {
    @Test
    void definitionIsImmutableAndBuilderDefaultsToPendingSchemaOne() {
        TransactionMetadata metadata = TransactionMetadata.builder()
                .reason("Cycle count")
                .referenceId("COUNT-42")
                .user("operator")
                .externalSystem("warehouse")
                .comments("Verified adjustment")
                .build();
        EconomicTransaction transaction = EconomicTransaction.builder()
                .id(TransactionId.of("test:adjustment"))
                .type(TransactionType.INVENTORY_ADJUSTMENT)
                .destinationInventoryId(com.butchercraft.world.inventory.InventoryId.of("test:inventory"))
                .goodId(GoodId.of("test:good"))
                .quantity(5L)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .simulationTick(10L)
                .metadata(metadata)
                .build();

        assertEquals(TransactionStatus.PENDING, transaction.status());
        assertEquals(TransactionSchema.CURRENT_VERSION, transaction.schemaVersion());
        assertEquals(Optional.of("Cycle count"), transaction.metadata().reason());
        assertEquals(TransactionStatus.APPLIED, transaction.withStatus(TransactionStatus.APPLIED).status());
        assertEquals(TransactionStatus.PENDING, transaction.status());
        assertNotEquals(transaction, transaction.withStatus(TransactionStatus.APPLIED));
    }

    @Test
    void definitionsRejectInvalidIdsQuantitiesTicksSchemasAndMetadata() {
        assertThrows(IllegalArgumentException.class, () -> TransactionId.of("Invalid ID"));
        assertThrows(IllegalArgumentException.class, () -> TransactionMetadata.builder().reason(" ").build());
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().quantity(-1L).build());
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().simulationTick(-1L).build());
        assertThrows(IllegalArgumentException.class, () -> baseBuilder().schemaVersion(2).build());
        assertThrows(NullPointerException.class, () -> EconomicTransaction.builder()
                .id(TransactionId.of("test:incomplete"))
                .type(TransactionType.INVENTORY_ADD)
                .build());
    }

    @Test
    void serializedEnumsExposeEverySchemaOneValue() {
        for (TransactionType value : TransactionType.values()) {
            assertEquals(value, TransactionType.fromSerializedName(value.serializedName()));
        }
        for (TransactionStatus value : TransactionStatus.values()) {
            assertEquals(value, TransactionStatus.fromSerializedName(value.serializedName()));
        }
        assertEquals(12, TransactionType.values().length);
        assertEquals(5, TransactionStatus.values().length);
    }

    private static EconomicTransaction.Builder baseBuilder() {
        return EconomicTransaction.builder()
                .id(TransactionId.of("test:base"))
                .type(TransactionType.INVENTORY_ADD)
                .destinationInventoryId(com.butchercraft.world.inventory.InventoryId.of("test:inventory"))
                .goodId(GoodId.of("test:good"))
                .quantity(1L)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .simulationTick(0L);
    }
}
