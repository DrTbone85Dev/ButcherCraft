package com.butchercraft.product.definition;

import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.packaging.definition.PackagingRegistry;
import com.butchercraft.product.datapack.ProductDatapackErrorCode;
import com.butchercraft.product.datapack.ProductDatapackValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic post-load validation for optional product packaging metadata.
 */
public final class ProductPackagingMetadataValidator {
    private ProductPackagingMetadataValidator() {
    }

    public static List<ProductDatapackValidationError> validate(
            ProductRegistry products,
            PackagingRegistry packaging
    ) {
        Objects.requireNonNull(products, "products");
        Objects.requireNonNull(packaging, "packaging");
        List<ProductDatapackValidationError> errors = new ArrayList<>();
        products.stream().forEach(product -> product.packagingMetadata().ifPresent(metadata ->
                validateProduct(products, packaging, product, metadata, errors)));
        return List.copyOf(errors);
    }

    private static void validateProduct(
            ProductRegistry products,
            PackagingRegistry packaging,
            ProductDefinition product,
            ProductPackagingMetadata metadata,
            List<ProductDatapackValidationError> errors
    ) {
        String source = "product:" + product.id().value();
        if (metadata.sourceProductId().equals(product.id())) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    product.id().value(),
                    ProductDatapackErrorCode.PACKAGING_SELF_REFERENCE,
                    "Packaged product cannot reference itself as its source product"
            ));
            return;
        }

        var packagingDefinition = packaging.find(metadata.packagingDefinitionId());
        if (packagingDefinition.isEmpty()) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    product.id().value(),
                    ProductDatapackErrorCode.UNKNOWN_PACKAGING_DEFINITION,
                    "Unknown packaging definition " + metadata.packagingDefinitionId().value()
            ));
            return;
        }

        var sourceProduct = products.find(metadata.sourceProductId());
        if (sourceProduct.isEmpty()) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    product.id().value(),
                    ProductDatapackErrorCode.UNKNOWN_PACKAGING_SOURCE_PRODUCT,
                    "Unknown packaging source product " + metadata.sourceProductId().value()
            ));
            return;
        }

        validateCompatibility(source, product, sourceProduct.orElseThrow(), packagingDefinition.orElseThrow(), errors);
    }

    private static void validateCompatibility(
            String source,
            ProductDefinition packagedProduct,
            ProductDefinition sourceProduct,
            PackagingDefinition packaging,
            List<ProductDatapackValidationError> errors
    ) {
        if (packagedProduct.defaultQuantityUnit() != sourceProduct.defaultQuantityUnit()) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    packagedProduct.id().value(),
                    ProductDatapackErrorCode.PACKAGING_INCOMPATIBLE_PRODUCT,
                    "Packaged product quantity unit must match the source product quantity unit"
            ));
            return;
        }
        if (!packagedProduct.category().equals(sourceProduct.category())) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    packagedProduct.id().value(),
                    ProductDatapackErrorCode.PACKAGING_INCOMPATIBLE_PRODUCT,
                    "Packaged product category must match the source product category"
            ));
            return;
        }
        if (!packaging.isCompatibleWith(sourceProduct)) {
            errors.add(ProductDatapackValidationError.of(
                    source,
                    packagedProduct.id().value(),
                    ProductDatapackErrorCode.PACKAGING_INCOMPATIBLE_PRODUCT,
                    "Packaging definition is not compatible with source product " + sourceProduct.id().value()
            ));
        }
    }
}
