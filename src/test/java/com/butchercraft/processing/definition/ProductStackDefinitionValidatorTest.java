package com.butchercraft.processing.definition;

import com.butchercraft.product.component.ProductStackData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStackDefinitionValidatorTest {
    @Test
    void validStackDataMatchesLoadedDefinition() {
        ProductStackData data = new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        );

        DefinitionValidationReport report = ProductStackDefinitionValidator.validate(data, DefinitionTestFixtures.builtIns());

        assertTrue(report.issues().isEmpty());
    }

    @Test
    void unknownProductIsReported() {
        ProductStackData data = new ProductStackData(
                "butchercraft:unknown_product",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        );

        DefinitionValidationReport report = ProductStackDefinitionValidator.validate(data, DefinitionTestFixtures.builtIns());

        assertTrue(report.hasErrors());
        assertEquals("unknown_product", report.issues().getFirst().reasonCode());
    }

    @Test
    void wrongSpeciesIsReported() {
        ProductStackData data = new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:pork",
                "butchercraft:trim",
                1_000,
                "gram",
                700
        );

        DefinitionValidationReport report = ProductStackDefinitionValidator.validate(data, DefinitionTestFixtures.builtIns());

        assertTrue(report.hasErrors());
        assertEquals("wrong_species", report.issues().getFirst().reasonCode());
    }

    @Test
    void wrongProcessingStateIsReported() {
        ProductStackData data = new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:ground",
                1_000,
                "gram",
                700
        );

        DefinitionValidationReport report = ProductStackDefinitionValidator.validate(data, DefinitionTestFixtures.builtIns());

        assertTrue(report.hasErrors());
        assertEquals("wrong_processing_state", report.issues().getFirst().reasonCode());
    }

    @Test
    void unsupportedQuantityUnitIsReported() {
        ProductStackData data = new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1,
                "piece",
                700
        );

        DefinitionValidationReport report = ProductStackDefinitionValidator.validate(data, DefinitionTestFixtures.builtIns());

        assertTrue(report.hasErrors());
        assertEquals("unsupported_quantity_unit", report.issues().getFirst().reasonCode());
    }

    @Test
    void componentRulesRejectOutOfRangeQualityBeforeDefinitionValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ProductStackData(
                "butchercraft:beef_trim",
                "butchercraft:beef",
                "butchercraft:trim",
                1_000,
                "gram",
                1_001
        ));
    }
}
