package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.definition.ProductDefinition;
import com.butchercraft.product.definition.ProductRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Separate deterministic validation for transformation product references.
 */
public final class TransformationProductReferenceValidator {
    private TransformationProductReferenceValidator() {
    }

    public static TransformationProductReferenceReport validate(
            TransformationDefinition definition,
            ProductRegistry products
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(products, "products");

        List<TransformationProductReferenceIssue> issues = new ArrayList<>();
        for (TransformationInput input : definition.inputs()) {
            validateReference(
                    definition.id(),
                    input.requiredAmount().materialId(),
                    input.requiredAmount().quantity().unit(),
                    TransformationProductReferenceRole.INPUT,
                    products,
                    issues
            );
        }
        for (TransformationOutput output : definition.outputs()) {
            validateReference(
                    definition.id(),
                    output.producedAmount().materialId(),
                    output.producedAmount().quantity().unit(),
                    TransformationProductReferenceRole.OUTPUT,
                    products,
                    issues
            );
        }
        return issues.isEmpty()
                ? TransformationProductReferenceReport.EMPTY
                : new TransformationProductReferenceReport(issues);
    }

    public static TransformationProductReferenceReport validate(
            TransformationRegistry transformations,
            ProductRegistry products
    ) {
        Objects.requireNonNull(transformations, "transformations");
        Objects.requireNonNull(products, "products");
        return TransformationProductReferenceReport.combine(transformations.stream()
                .map(definition -> validate(definition, products))
                .toList());
    }

    private static void validateReference(
            TransformationId transformationId,
            EngineId productId,
            QuantityUnit quantityUnit,
            TransformationProductReferenceRole role,
            ProductRegistry products,
            List<TransformationProductReferenceIssue> issues
    ) {
        ProductDefinition product = products.find(productId).orElse(null);
        if (product == null) {
            issues.add(issue(
                    transformationId,
                    productId,
                    role,
                    missingCode(role),
                    "Transformation references missing " + roleName(role) + " product " + productId.value()
            ));
            return;
        }
        if (product.defaultQuantityUnit() != quantityUnit) {
            issues.add(issue(
                    transformationId,
                    productId,
                    role,
                    unitCode(role),
                    "Transformation " + roleName(role) + " product " + productId.value()
                            + " uses " + quantityUnit.id()
                            + " but product definition defaults to " + product.defaultQuantityUnit().id()
            ));
        }
    }

    private static TransformationProductReferenceIssue issue(
            TransformationId transformationId,
            EngineId productId,
            TransformationProductReferenceRole role,
            String code,
            String message
    ) {
        return new TransformationProductReferenceIssue(transformationId, productId, role, code, message);
    }

    private static String missingCode(TransformationProductReferenceRole role) {
        return switch (role) {
            case INPUT -> "missing_input_product";
            case OUTPUT -> "missing_output_product";
        };
    }

    private static String unitCode(TransformationProductReferenceRole role) {
        return switch (role) {
            case INPUT -> "input_product_unit_mismatch";
            case OUTPUT -> "output_product_unit_mismatch";
        };
    }

    private static String roleName(TransformationProductReferenceRole role) {
        return switch (role) {
            case INPUT -> "input";
            case OUTPUT -> "output";
        };
    }
}
