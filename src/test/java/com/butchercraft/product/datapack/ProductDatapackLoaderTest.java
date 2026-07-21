package com.butchercraft.product.datapack;

import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductDatapackLoaderTest {
    private static final List<String> EXPECTED_BUNDLED_PRODUCT_IDS = List.of(
            "butchercraft:beef_trim",
            "butchercraft:ground_beef",
            "butchercraft:retail_ground_beef",
            "butchercraft:pork_trim",
            "butchercraft:ground_pork",
            "butchercraft:bison_trim",
            "butchercraft:ground_bison",
            "butchercraft:beef_forequarter",
            "butchercraft:beef_chuck",
            "butchercraft:beef_rib",
            "butchercraft:beef_packer_brisket",
            "butchercraft:beef_plate",
            "butchercraft:beef_shank",
            "butchercraft:beef_fat",
            "butchercraft:beef_bone",
            "butchercraft:beef_hindquarter",
            "butchercraft:beef_round",
            "butchercraft:beef_sirloin",
            "butchercraft:beef_short_loin",
            "butchercraft:beef_flank",
            "butchercraft:t_bone_steak",
            "butchercraft:porterhouse_steak",
            "butchercraft:beef_strip_loin",
            "butchercraft:beef_tenderloin",
            "butchercraft:top_round",
            "butchercraft:bottom_round",
            "butchercraft:eye_of_round",
            "butchercraft:sirloin_tip",
            "butchercraft:top_sirloin",
            "butchercraft:sirloin_steak",
            "butchercraft:tri_tip"
    );

    @Test
    void validProductDatapackLoadsInResourceOrder() {
        ProductDatapackLoadResult result = loader().load(resources(
                Map.entry("second", product("butchercraft:second", "Second", "butchercraft:pork", "gram")),
                Map.entry("first", product("butchercraft:first", "First", "butchercraft:beef", "gram"))
        ));

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(List.of("butchercraft:second", "butchercraft:first"),
                result.registry().orElseThrow().stream()
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void packagingMetadataLoadsWhenPresentAndExistingProductsCanOmitIt() {
        ProductDatapackLoadResult result = loader().load(resources(
                Map.entry("ground", product("butchercraft:ground_beef", "Ground Beef", "butchercraft:beef", "gram")),
                Map.entry("retail", packagedProduct(
                        "butchercraft:retail_ground_beef",
                        "Retail Ground Beef",
                        "butchercraft:beef",
                        "gram",
                        "butchercraft:retail_package",
                        "butchercraft:ground_beef"
                ))
        ));

        assertTrue(result.succeeded(), result::describeErrors);
        var products = result.registry().orElseThrow();
        assertTrue(products.find(BuiltInProductRegistry.GROUND_BEEF).orElseThrow().packagingMetadata().isEmpty());
        var metadata = products.find(BuiltInProductRegistry.RETAIL_GROUND_BEEF)
                .orElseThrow()
                .packagingMetadata()
                .orElseThrow();
        assertEquals("butchercraft:retail_package", metadata.packagingDefinitionId().value());
        assertEquals("butchercraft:ground_beef", metadata.sourceProductId().value());
    }

    @Test
    void bundledProductDatapackResourcesLoadInStableOrder() {
        ProductDatapackLoadResult result = loader().load(ProductRegistryService.bundledResources());

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS.size(), result.registry().orElseThrow().size());
        assertEquals(EXPECTED_BUNDLED_PRODUCT_IDS, result.registry().orElseThrow().stream()
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void duplicateProductIdsAreRejectedBeforeRegistryBuild() {
        ProductDatapackLoadResult result = loader().load(resources(
                Map.entry("first", product("butchercraft:duplicate", "First", "butchercraft:beef", "gram")),
                Map.entry("second", product("butchercraft:duplicate", "Second", "butchercraft:beef", "gram"))
        ));

        assertFalse(result.succeeded());
        assertEquals(List.of(ProductDatapackErrorCode.DUPLICATE_ID, ProductDatapackErrorCode.DUPLICATE_ID),
                result.errors().stream().map(ProductDatapackValidationError::code).toList());
    }

    @Test
    void malformedJsonAndMissingRequiredIdentityFieldsAreRejected() {
        ProductDatapackLoadResult malformed = loader().load(resources(Map.entry(
                "malformed",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"tags\"", "\"tagz\"")
        )));
        ProductDatapackLoadResult missingId = loader().load(resources(Map.entry(
                "missing_id",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"id\"", "\"product_id\"")
        )));
        ProductDatapackLoadResult missingDisplayName = loader().load(resources(Map.entry(
                "missing_display",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"display_name\"", "\"name\"")
        )));

        assertEquals(ProductDatapackErrorCode.MALFORMED_TAGS, malformed.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.MISSING_ID, missingId.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.MISSING_DISPLAY_NAME, missingDisplayName.errors().getFirst().code());
    }

    @Test
    void unsupportedSchemaCategoryAndUnitValidationAreStructured() {
        ProductDatapackLoadResult unsupportedSchema = loader().load(resources(Map.entry(
                "schema",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"schema_version\": 1", "\"schema_version\": 99")
        )));
        ProductDatapackLoadResult unknownCategory = loader().load(resources(Map.entry(
                "category",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:venison", "gram")
        )));
        ProductDatapackLoadResult unknownUnit = loader().load(resources(Map.entry(
                "unit",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "kilogram")
        )));

        assertEquals(ProductDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION, unsupportedSchema.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.UNKNOWN_CATEGORY, unknownCategory.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.UNKNOWN_QUANTITY_UNIT, unknownUnit.errors().getFirst().code());
    }

    @Test
    void malformedTagsAndMetadataAreRejected() {
        ProductDatapackLoadResult malformedTags = loader().load(resources(Map.entry(
                "tags",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"butchercraft:trait/trim\"", "10")
        )));
        ProductDatapackLoadResult malformedMetadata = loader().load(resources(Map.entry(
                "metadata",
                product("butchercraft:beef_trim", "Beef Trim", "butchercraft:beef", "gram")
                        .replace("\"built_in\"", "true")
        )));

        assertEquals(ProductDatapackErrorCode.MALFORMED_TAGS, malformedTags.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.MALFORMED_METADATA, malformedMetadata.errors().getFirst().code());
    }

    @Test
    void malformedPackagingMetadataIsRejected() {
        ProductDatapackLoadResult malformedPackagingObject = loader().load(resources(Map.entry(
                "packaging",
                product("butchercraft:retail_ground_beef", "Retail Ground Beef", "butchercraft:beef", "gram")
                        .replace("\"metadata\"", "\"packaging\": true,\n  \"metadata\"")
        )));
        ProductDatapackLoadResult malformedPackagingId = loader().load(resources(Map.entry(
                "packaging_id",
                packagedProduct(
                        "butchercraft:retail_ground_beef",
                        "Retail Ground Beef",
                        "butchercraft:beef",
                        "gram",
                        "ButcherCraft:Retail Package",
                        "butchercraft:ground_beef"
                )
        )));

        assertEquals(ProductDatapackErrorCode.MALFORMED_PACKAGING_METADATA,
                malformedPackagingObject.errors().getFirst().code());
        assertEquals(ProductDatapackErrorCode.MALFORMED_PACKAGING_METADATA,
                malformedPackagingId.errors().getFirst().code());
    }

    public static ProductDatapackLoader loader() {
        return new ProductDatapackLoader(ContentSnapshotService.knownProductCategories());
    }

    @SafeVarargs
    public static Map<String, JsonElement> resources(Map.Entry<String, String>... entries) {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            resources.put(entry.getKey(), JsonParser.parseString(entry.getValue()));
        }
        return resources;
    }

    public static String product(String id, String displayName, String category, String unit) {
        return productWithTag(id, displayName, category, unit, "butchercraft:trait/trim");
    }

    public static String productWithTag(String id, String displayName, String category, String unit, String tag) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "category": "%s",
                  "default_quantity_unit": "%s",
                  "tags": [
                    "%s"
                  ],
                  "metadata": {
                    "butchercraft:schema/source": "built_in"
                  }
                }
                """.formatted(id, displayName, category, unit, tag);
    }

    public static String packagedProduct(
            String id,
            String displayName,
            String category,
            String unit,
            String packagingDefinition,
            String sourceProduct
    ) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "category": "%s",
                  "default_quantity_unit": "%s",
                  "tags": [
                    "butchercraft:trait/retail_packaged"
                  ],
                  "packaging": {
                    "definition": "%s",
                    "source_product": "%s"
                  },
                  "metadata": {
                    "butchercraft:schema/source": "built_in"
                  }
                }
                """.formatted(id, displayName, category, unit, packagingDefinition, sourceProduct);
    }
}
