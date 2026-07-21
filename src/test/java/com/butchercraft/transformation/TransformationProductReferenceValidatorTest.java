package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.product.definition.ProductDefinition;
import com.butchercraft.product.definition.ProductRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationProductReferenceValidatorTest {
    private static final EngineId GRINDING = EngineId.of("butchercraft:grinding");
    private static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    private static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");

    @Test
    void builtInTransformationsResolveThroughBuiltInProductRegistry() {
        TransformationProductReferenceReport report = TransformationProductReferenceValidator.validate(
                BuiltInTransformationRegistry.builtInRegistry(),
                BuiltInProductRegistry.builtInRegistry()
        );

        assertTrue(report.succeeded());
        assertTrue(report.issues().isEmpty());
    }

    @Test
    void transformationConstructionDoesNotRequireProductRegistry() {
        TransformationDefinition definition = transformation(
                "butchercraft:missing_reference",
                EngineId.of("butchercraft:missing_input"),
                GROUND_BEEF
        );

        TransformationProductReferenceReport report = TransformationProductReferenceValidator.validate(
                definition,
                ProductRegistry.builder()
                        .register(product(GROUND_BEEF, QuantityUnit.GRAM))
                        .build()
        );

        assertTrue(report.hasErrors());
        assertEquals(List.of("missing_input_product"), report.issues().stream()
                .map(TransformationProductReferenceIssue::code)
                .toList());
        assertEquals(EngineId.of("butchercraft:missing_input"), report.issues().getFirst().productId());
    }

    @Test
    void validatorReportsMissingProductsInDefinitionOrder() {
        TransformationDefinition definition = transformation(
                "butchercraft:missing_products",
                EngineId.of("butchercraft:missing_input"),
                EngineId.of("butchercraft:missing_output")
        );

        TransformationProductReferenceReport report = TransformationProductReferenceValidator.validate(
                definition,
                ProductRegistry.builder().build()
        );

        assertEquals(List.of("missing_input_product", "missing_output_product"), report.issues().stream()
                .map(TransformationProductReferenceIssue::code)
                .toList());
        assertEquals(List.of(
                        TransformationProductReferenceRole.INPUT,
                        TransformationProductReferenceRole.OUTPUT
                ),
                report.issues().stream()
                        .map(TransformationProductReferenceIssue::role)
                        .toList());
    }

    @Test
    void validatorReportsQuantityUnitMismatches() {
        TransformationDefinition definition = transformation(
                "butchercraft:unit_mismatch",
                BEEF_TRIM,
                GROUND_BEEF
        );
        ProductRegistry products = ProductRegistry.builder()
                .register(product(BEEF_TRIM, QuantityUnit.PIECE))
                .register(product(GROUND_BEEF, QuantityUnit.PIECE))
                .build();

        TransformationProductReferenceReport report = TransformationProductReferenceValidator.validate(definition, products);

        assertEquals(List.of("input_product_unit_mismatch", "output_product_unit_mismatch"), report.issues().stream()
                .map(TransformationProductReferenceIssue::code)
                .toList());
    }

    @Test
    void registryValidationCombinesReportsInTransformationOrder() {
        TransformationRegistry transformations = TransformationRegistry.builder()
                .register(transformation(
                        "butchercraft:first_missing",
                        EngineId.of("butchercraft:first_missing_input"),
                        GROUND_BEEF
                ))
                .register(transformation(
                        "butchercraft:second_missing",
                        BEEF_TRIM,
                        EngineId.of("butchercraft:second_missing_output")
                ))
                .build();
        ProductRegistry products = ProductRegistry.builder()
                .register(product(BEEF_TRIM, QuantityUnit.GRAM))
                .register(product(GROUND_BEEF, QuantityUnit.GRAM))
                .build();

        TransformationProductReferenceReport report = TransformationProductReferenceValidator.validate(transformations, products);

        assertEquals(List.of("butchercraft:first_missing", "butchercraft:second_missing"), report.issues().stream()
                .map(issue -> issue.transformationId().value())
                .toList());
    }

    @Test
    void validatorRejectsNullInputs() {
        ProductRegistry products = ProductRegistry.builder().build();
        TransformationDefinition definition = transformation("butchercraft:test", BEEF_TRIM, GROUND_BEEF);

        assertThrows(NullPointerException.class, () -> TransformationProductReferenceValidator.validate((TransformationDefinition) null, products));
        assertThrows(NullPointerException.class, () -> TransformationProductReferenceValidator.validate(definition, null));
        assertThrows(NullPointerException.class, () -> TransformationProductReferenceValidator.validate((TransformationRegistry) null, products));
    }

    private static TransformationDefinition transformation(String id, EngineId inputProduct, EngineId outputProduct) {
        return TransformationDefinition.builder()
                .id(id)
                .displayName(id)
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(GRINDING)
                .input(inputProduct, ProductQuantity.grams(100))
                .output(outputProduct, ProductQuantity.grams(90), TransformationOutputClassification.PRIMARY)
                .duration(ProcessingDuration.milliseconds(3_000))
                .yield(new YieldRatio(9, 10))
                .metadata("butchercraft:schema/source", "test")
                .build();
    }

    private static ProductDefinition product(EngineId id, QuantityUnit unit) {
        return ProductDefinition.builder()
                .id(id)
                .displayName(id.value())
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(EngineId.of("butchercraft:test_category")))
                .defaultQuantityUnit(unit)
                .metadata(BuiltInProductRegistry.METADATA_SOURCE, "test")
                .build();
    }
}
