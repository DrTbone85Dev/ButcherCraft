package com.butchercraft.processing.definition;

import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.product.component.ProductStackData;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public final class ProductStackDefinitionValidator {
    private ProductStackDefinitionValidator() {
    }

    public static DefinitionValidationReport validate(
            ProductStackData data,
            DefinitionRegistryView definitions
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(definitions, "definitions");

        DefinitionValidationReport report = DefinitionValidationReport.EMPTY;
        ResourceLocation productId = ResourceLocation.tryParse(data.productTypeId());
        if (productId == null) {
            return DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "invalid_product_id",
                    "Product stack data contains an invalid product id"
            ));
        }

        ProductDefinition product = definitions.products().get(productId);
        if (product == null) {
            return DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "unknown_product",
                    productId,
                    "Product stack data references an unknown product definition"
            ));
        }

        if (!product.species().toString().equals(data.sourceCategoryId())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "wrong_species",
                    productId,
                    "Product stack source/species does not match the loaded product definition"
            ));
        }
        if (!product.processingState().toString().equals(data.processingStateId())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "wrong_processing_state",
                    productId,
                    "Product stack processing state does not match the loaded product definition"
            ));
        }
        if (!product.quantityUnit().equals(data.quantityUnitId())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "unsupported_quantity_unit",
                    productId,
                    "Product stack quantity unit is not permitted by the loaded product definition"
            ));
        }
        if (data.qualityScore() < ProductQuality.MIN_SCORE || data.qualityScore() > ProductQuality.MAX_SCORE) {
            report = report.plus(DefinitionValidationIssue.error(
                    "quality_out_of_range",
                    productId,
                    "Product stack quality score is outside engine bounds"
            ));
        }
        return report;
    }
}
