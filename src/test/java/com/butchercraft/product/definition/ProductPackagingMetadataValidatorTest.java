package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.packaging.definition.PackagingDefinition;
import com.butchercraft.packaging.definition.PackagingFormat;
import com.butchercraft.packaging.definition.PackagingRegistry;
import com.butchercraft.product.datapack.ProductDatapackErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPackagingMetadataValidatorTest {
    private static final ProductCategory BEEF = ProductCategory.fromId(EngineId.of("butchercraft:beef"));
    private static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    private static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");

    @Test
    void validMetadataReferencesExistingCompatibleDefinitions() {
        ProductRegistry products = ProductRegistry.builder()
                .register(product("butchercraft:ground_beef", BEEF, TAG_GROUND, null))
                .register(product("butchercraft:retail_ground_beef", BEEF, BuiltInProductRegistry.TAG_RETAIL_PACKAGED,
                        new ProductPackagingMetadata(
                                EngineId.of("butchercraft:retail_package"),
                                EngineId.of("butchercraft:ground_beef")
                        )))
                .build();

        assertTrue(ProductPackagingMetadataValidator.validate(products, packagingRegistry(TAG_GROUND)).isEmpty());
    }

    @Test
    void unknownPackagingDefinitionIsReported() {
        ProductRegistry products = ProductRegistry.builder()
                .register(product("butchercraft:ground_beef", BEEF, TAG_GROUND, null))
                .register(product("butchercraft:retail_ground_beef", BEEF, BuiltInProductRegistry.TAG_RETAIL_PACKAGED,
                        new ProductPackagingMetadata(
                                EngineId.of("butchercraft:missing_package"),
                                EngineId.of("butchercraft:ground_beef")
                        )))
                .build();

        var errors = ProductPackagingMetadataValidator.validate(products, packagingRegistry(TAG_GROUND));

        assertEquals(ProductDatapackErrorCode.UNKNOWN_PACKAGING_DEFINITION, errors.getFirst().code());
    }

    @Test
    void unknownSourceProductIsReported() {
        ProductRegistry products = ProductRegistry.builder()
                .register(product("butchercraft:retail_ground_beef", BEEF, BuiltInProductRegistry.TAG_RETAIL_PACKAGED,
                        new ProductPackagingMetadata(
                                EngineId.of("butchercraft:retail_package"),
                                EngineId.of("butchercraft:missing_source")
                        )))
                .build();

        var errors = ProductPackagingMetadataValidator.validate(products, packagingRegistry(TAG_GROUND));

        assertEquals(ProductDatapackErrorCode.UNKNOWN_PACKAGING_SOURCE_PRODUCT, errors.getFirst().code());
    }

    @Test
    void selfReferenceIsReported() {
        ProductRegistry products = ProductRegistry.builder()
                .register(product("butchercraft:retail_ground_beef", BEEF, BuiltInProductRegistry.TAG_RETAIL_PACKAGED,
                        new ProductPackagingMetadata(
                                EngineId.of("butchercraft:retail_package"),
                                EngineId.of("butchercraft:retail_ground_beef")
                        )))
                .build();

        var errors = ProductPackagingMetadataValidator.validate(products, packagingRegistry(TAG_GROUND));

        assertEquals(ProductDatapackErrorCode.PACKAGING_SELF_REFERENCE, errors.getFirst().code());
    }

    @Test
    void incompatibleSourceProductIsReported() {
        ProductRegistry products = ProductRegistry.builder()
                .register(product("butchercraft:beef_trim", BEEF, TAG_TRIM, null))
                .register(product("butchercraft:retail_ground_beef", BEEF, BuiltInProductRegistry.TAG_RETAIL_PACKAGED,
                        new ProductPackagingMetadata(
                                EngineId.of("butchercraft:retail_package"),
                                EngineId.of("butchercraft:beef_trim")
                        )))
                .build();

        var errors = ProductPackagingMetadataValidator.validate(products, packagingRegistry(TAG_GROUND));

        assertEquals(ProductDatapackErrorCode.PACKAGING_INCOMPATIBLE_PRODUCT, errors.getFirst().code());
    }

    private static ProductDefinition product(
            String id,
            ProductCategory category,
            EngineId tag,
            ProductPackagingMetadata packagingMetadata
    ) {
        ProductDefinition.Builder builder = ProductDefinition.builder()
                .id(id)
                .displayName(id)
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(category)
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag(tag)
                .metadata("butchercraft:schema/source", "test");
        if (packagingMetadata != null) {
            builder.packagingMetadata(packagingMetadata);
        }
        return builder.build();
    }

    private static PackagingRegistry packagingRegistry(EngineId compatibleTag) {
        return PackagingRegistry.builder()
                .register(PackagingDefinition.builder()
                        .id("butchercraft:retail_package")
                        .displayName("Retail Package")
                        .schemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION)
                        .format(PackagingFormat.RETAIL)
                        .defaultQuantityUnit(QuantityUnit.GRAM)
                        .compatibleCategory(BEEF)
                        .compatibleTag(compatibleTag)
                        .metadata(Map.of(EngineId.of("butchercraft:schema/source"), "test"))
                        .build())
                .build();
    }
}
