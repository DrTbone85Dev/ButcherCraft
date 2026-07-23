package com.butchercraft.world.transaction;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionValidatorTest {
    @Test
    void validatorAcceptsSupportedInventoryOperations() {
        TransactionValidator validator = new TransactionValidator(TransactionTestFixtures.manager());

        assertTrue(validator.validateForSubmission(TransactionTestFixtures.beefTransaction(
                "test:add", TransactionType.INVENTORY_ADD, 5L, 26L
        )).accepted());
        assertTrue(validator.validateForSubmission(TransactionTestFixtures.beefTransaction(
                "test:remove", TransactionType.INVENTORY_REMOVE, 5L, 26L
        )).accepted());
        assertTrue(validator.validateForSubmission(TransactionTestFixtures.beefTransaction(
                "test:transfer", TransactionType.INVENTORY_TRANSFER, 5L, 26L
        )).accepted());
        assertTrue(validator.validateForSubmission(TransactionTestFixtures.beefTransaction(
                "test:adjust", TransactionType.INVENTORY_ADJUSTMENT, 5L, 26L
        )).accepted());
    }

    @Test
    void validatorRejectsUnknownReferencesInvalidUnitsAndEndpointShapes() {
        TransactionValidator validator = new TransactionValidator(TransactionTestFixtures.manager());

        assertFailure(validator, base("test:unknown_good").goodId(GoodId.of("test:missing")).build(),
                TransactionFailureCode.UNKNOWN_GOOD);
        assertFailure(validator, base("test:unknown_actor").destinationActorId(ActorId.of("test:missing")).build(),
                TransactionFailureCode.UNKNOWN_ACTOR);
        assertFailure(validator, base("test:unknown_inventory")
                        .destinationInventoryId(InventoryId.of("test:missing")).build(),
                TransactionFailureCode.UNKNOWN_INVENTORY);
        assertFailure(validator, base("test:invalid_unit").unitOfMeasure(UnitOfMeasure.EACH).build(),
                TransactionFailureCode.VALIDATION_FAILED);
        assertFailure(validator, EconomicTransaction.builder()
                        .id(TransactionId.of("test:invalid_endpoints"))
                        .type(TransactionType.INVENTORY_TRANSFER)
                        .sourceInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                        .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                        .goodId(InventoryTestFixtures.BEEF)
                        .quantity(1L)
                        .unitOfMeasure(UnitOfMeasure.POUND)
                        .simulationTick(26L)
                        .build(),
                TransactionFailureCode.VALIDATION_FAILED);
    }

    @Test
    void validatorRejectsZeroUnderflowCapacityStatusAndFutureExecutionTypes() {
        TransactionValidator validator = new TransactionValidator(TransactionTestFixtures.manager());

        assertFailure(validator, base("test:zero").quantity(0L).build(),
                TransactionFailureCode.NEGATIVE_QUANTITY);
        assertFailure(validator, TransactionTestFixtures.beefTransaction(
                "test:underflow", TransactionType.INVENTORY_REMOVE, 21L, 26L
        ), TransactionFailureCode.INSUFFICIENT_INVENTORY);
        assertFailure(validator, TransactionTestFixtures.beefTransaction(
                "test:capacity", TransactionType.INVENTORY_ADD, 81L, 26L
        ), TransactionFailureCode.CAPACITY_EXCEEDED);
        assertFailure(validator, TransactionTestFixtures.beefTransaction(
                "test:status", TransactionType.INVENTORY_ADD, 1L, 26L
        ).withStatus(TransactionStatus.VALIDATED), TransactionFailureCode.INVALID_STATUS);
        assertFailure(validator, EconomicTransaction.builder()
                        .id(TransactionId.of("test:future_sale"))
                        .type(TransactionType.SALE)
                        .goodId(InventoryTestFixtures.BEEF)
                        .quantity(1L)
                        .unitOfMeasure(UnitOfMeasure.POUND)
                        .simulationTick(26L)
                        .build(),
                TransactionFailureCode.VALIDATION_FAILED);
    }

    private static EconomicTransaction.Builder base(String id) {
        return EconomicTransaction.builder()
                .id(TransactionId.of(id))
                .type(TransactionType.INVENTORY_ADD)
                .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(1L)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(26L);
    }

    private static void assertFailure(
            TransactionValidator validator,
            EconomicTransaction transaction,
            TransactionFailureCode expected
    ) {
        TransactionValidation validation = validator.validateForSubmission(transaction);
        assertFalse(validation.accepted());
        assertEquals(expected, validation.failureCode().orElseThrow());
    }
}
