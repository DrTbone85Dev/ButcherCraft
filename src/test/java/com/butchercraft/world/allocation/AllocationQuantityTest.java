package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationQuantityTest {
    @Test
    void exactDecimalQuantitiesNormalizeWithoutFloatingPoint() {
        AllocationQuantity quantity = AllocationQuantity.of("12.340000000", CapacityUnits.STORAGE_MASS);

        assertEquals(new BigDecimal("12.34"), quantity.amount());
        assertEquals("12.34", quantity.canonicalAmount());
        assertEquals("12.34 butchercraft:storage_mass", quantity.canonicalValue());
        assertEquals(
                quantity,
                new AllocationQuantity(new BigDecimal("12.340"), CapacityUnits.STORAGE_MASS)
        );
    }

    @Test
    void quantityArithmeticIsExactAndUnitSafe() {
        AllocationQuantity first = AllocationQuantity.of("0.1", CapacityUnits.ENERGY);
        AllocationQuantity second = AllocationQuantity.of("0.2", CapacityUnits.ENERGY);

        assertEquals("0.3", first.add(second).canonicalAmount());
        assertEquals(first, first.add(second).subtract(second));
        assertTrue(second.compareAmount(first) > 0);
        assertTrue(first.isCompatibleWith(second));
        assertFalse(first.isCompatibleWith(AllocationQuantity.of("0.1", CapacityUnits.MACHINE_TIME)));
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                () -> first.add(AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME))
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.QUANTITY_UNDERFLOW,
                () -> first.subtract(second)
        );
    }

    @Test
    void quantityValidationRejectsNegativeNonCanonicalAndOutOfBoundsValues() {
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NEGATIVE_QUANTITY,
                () -> AllocationQuantity.of("-1", CapacityUnits.ENERGY)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NONCANONICAL_INPUT,
                () -> AllocationQuantity.of("1e3", CapacityUnits.ENERGY)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                () -> AllocationQuantity.of("0.0000000001", CapacityUnits.ENERGY)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.ARITHMETIC_OVERFLOW,
                () -> AllocationQuantity.of("123456789012345678901234567890123456789", CapacityUnits.ENERGY)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.ZERO_QUANTITY,
                () -> AllocationQuantity.zero(CapacityUnits.ENERGY).requirePositive("energy")
        );
    }

    @Test
    void quantityNaturalOrderingIsDeterministicAcrossUnitsThenAmounts() {
        AllocationQuantity energy = AllocationQuantity.of("100", CapacityUnits.ENERGY);
        AllocationQuantity machine = AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME);
        assertEquals(
                Integer.signum(CapacityUnits.ENERGY.compareTo(CapacityUnits.MACHINE_TIME)),
                Integer.signum(energy.compareTo(machine))
        );
    }
}
