package com.butchercraft.packaging.datapack;

import com.butchercraft.content.ContentSnapshotService;
import com.butchercraft.packaging.definition.BuiltInPackagingRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PackagingDatapackLoaderTest {
    @Test
    void validPackagingDatapackLoadsInResourceOrder() {
        PackagingDatapackLoadResult result = loader().load(resources(
                Map.entry("second", packaging("butchercraft:second", "Second", "retail", "gram")),
                Map.entry("first", packaging("butchercraft:first", "First", "retail", "gram"))
        ));

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(List.of("butchercraft:second", "butchercraft:first"),
                result.registry().orElseThrow().stream()
                        .map(definition -> definition.id().value())
                        .toList());
    }

    @Test
    void bundledPackagingDatapackResourcesLoadInStableOrder() {
        PackagingDatapackLoadResult result = loader().load(ContentSnapshotService.bundledPackagingResources());

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(BuiltInPackagingRegistry.BUILT_IN_RESOURCE_PATHS.size(), result.registry().orElseThrow().size());
        assertEquals(List.of("butchercraft:retail_package"), result.registry().orElseThrow().stream()
                .map(definition -> definition.id().value())
                .toList());
    }

    @Test
    void duplicatePackagingIdsAreRejectedBeforeRegistryBuild() {
        PackagingDatapackLoadResult result = loader().load(resources(
                Map.entry("first", packaging("butchercraft:duplicate", "First", "retail", "gram")),
                Map.entry("second", packaging("butchercraft:duplicate", "Second", "retail", "gram"))
        ));

        assertFalse(result.succeeded());
        assertEquals(List.of(PackagingDatapackErrorCode.DUPLICATE_ID, PackagingDatapackErrorCode.DUPLICATE_ID),
                result.errors().stream().map(PackagingDatapackValidationError::code).toList());
    }

    @Test
    void malformedJsonAndMissingRequiredIdentityFieldsAreRejected() {
        PackagingDatapackLoadResult malformedRoot = loader().load(Map.of("root", JsonParser.parseString("10")));
        PackagingDatapackLoadResult malformed = loader().load(resources(Map.entry(
                "malformed",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"compatible_tags\"", "\"compatible_tagz\"")
        )));
        PackagingDatapackLoadResult missingId = loader().load(resources(Map.entry(
                "missing_id",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"id\"", "\"packaging_id\"")
        )));
        PackagingDatapackLoadResult missingDisplayName = loader().load(resources(Map.entry(
                "missing_display",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"display_name\"", "\"name\"")
        )));

        assertEquals(PackagingDatapackErrorCode.MALFORMED_JSON, malformedRoot.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.MALFORMED_TAGS, malformed.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.MISSING_ID, missingId.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.MISSING_DISPLAY_NAME, missingDisplayName.errors().getFirst().code());
    }

    @Test
    void unsupportedSchemaFormatCategoryAndUnitValidationAreStructured() {
        PackagingDatapackLoadResult unsupportedSchema = loader().load(resources(Map.entry(
                "schema",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"schema_version\": 1", "\"schema_version\": 99")
        )));
        PackagingDatapackLoadResult unknownFormat = loader().load(resources(Map.entry(
                "format",
                packaging("butchercraft:retail_package", "Retail Package", "tray", "gram")
        )));
        PackagingDatapackLoadResult unknownCategory = loader().load(resources(Map.entry(
                "category",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"butchercraft:beef\"", "\"butchercraft:venison\"")
        )));
        PackagingDatapackLoadResult unknownUnit = loader().load(resources(Map.entry(
                "unit",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "kilogram")
        )));

        assertEquals(PackagingDatapackErrorCode.UNSUPPORTED_SCHEMA_VERSION, unsupportedSchema.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.UNKNOWN_FORMAT, unknownFormat.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.UNKNOWN_CATEGORY, unknownCategory.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.UNKNOWN_QUANTITY_UNIT, unknownUnit.errors().getFirst().code());
    }

    @Test
    void malformedCategoriesTagsAndMetadataAreRejected() {
        PackagingDatapackLoadResult malformedCategories = loader().load(resources(Map.entry(
                "categories",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"butchercraft:beef\"", "10")
        )));
        PackagingDatapackLoadResult malformedTags = loader().load(resources(Map.entry(
                "tags",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"butchercraft:trait/ground\"", "10")
        )));
        PackagingDatapackLoadResult malformedMetadata = loader().load(resources(Map.entry(
                "metadata",
                packaging("butchercraft:retail_package", "Retail Package", "retail", "gram")
                        .replace("\"built_in\"", "true")
        )));

        assertEquals(PackagingDatapackErrorCode.MALFORMED_CATEGORIES, malformedCategories.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.MALFORMED_TAGS, malformedTags.errors().getFirst().code());
        assertEquals(PackagingDatapackErrorCode.MALFORMED_METADATA, malformedMetadata.errors().getFirst().code());
    }

    @Test
    void definitionWithoutCompatibilityRulesIsRejected() {
        PackagingDatapackLoadResult result = loader().load(resources(Map.entry(
                "no_rules",
                packagingWithoutRules("butchercraft:retail_package", "Retail Package", "retail", "gram")
        )));

        assertFalse(result.succeeded());
        assertEquals(PackagingDatapackErrorCode.MALFORMED_DEFINITION, result.errors().getFirst().code());
    }

    public static PackagingDatapackLoader loader() {
        return new PackagingDatapackLoader(ContentSnapshotService.knownProductCategories());
    }

    @SafeVarargs
    public static Map<String, JsonElement> resources(Map.Entry<String, String>... entries) {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            resources.put(entry.getKey(), JsonParser.parseString(entry.getValue()));
        }
        return resources;
    }

    public static String packaging(String id, String displayName, String format, String unit) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "format": "%s",
                  "default_quantity_unit": "%s",
                  "compatible_categories": [
                    "butchercraft:beef"
                  ],
                  "compatible_tags": [
                    "butchercraft:trait/ground"
                  ],
                  "metadata": {
                    "butchercraft:schema/source": "built_in"
                  }
                }
                """.formatted(id, displayName, format, unit);
    }

    private static String packagingWithoutRules(String id, String displayName, String format, String unit) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "format": "%s",
                  "default_quantity_unit": "%s",
                  "compatible_categories": [],
                  "compatible_tags": [],
                  "metadata": {
                    "butchercraft:schema/source": "built_in"
                  }
                }
                """.formatted(id, displayName, format, unit);
    }
}
