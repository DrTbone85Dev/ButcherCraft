package com.butchercraft.content;

import com.butchercraft.engine.EngineId;
import com.butchercraft.packaging.datapack.PackagingDatapackErrorCode;
import com.butchercraft.packaging.datapack.PackagingDatapackLoaderTest;
import com.butchercraft.product.datapack.ProductDatapackErrorCode;
import com.butchercraft.product.datapack.ProductDatapackLoaderTest;
import com.butchercraft.product.definition.BuiltInProductRegistry;
import com.butchercraft.transformation.BuiltInTransformationRegistry;
import com.butchercraft.transformation.TransformationId;
import com.butchercraft.transformation.TransformationOutput;
import com.butchercraft.transformation.datapack.TransformationDatapackErrorCode;
import com.butchercraft.transformation.datapack.TransformationRegistryService;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSnapshotServiceTest {
    @AfterEach
    void resetSnapshot() {
        ContentSnapshotService.resetToBundledSnapshot();
    }

    @Test
    void successfulProductAndTransformationSnapshotActivationIsAtomic() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ContentSnapshotService.bundledProductResources(),
                ContentSnapshotService.bundledPackagingResources(),
                ContentSnapshotService.bundledTransformationResources()
        );

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(31, ContentSnapshotService.currentProductRegistry().size());
        assertEquals(4, ContentSnapshotService.currentPackagingRegistry().size());
        assertEquals(8, ContentSnapshotService.currentTransformationRegistry().size());
        assertEquals(previous.products().stream().toList(), ContentSnapshotService.currentProductRegistry().stream().toList());
        assertEquals(previous.packaging().stream().toList(), ContentSnapshotService.currentPackagingRegistry().stream().toList());
        assertEquals(previous.transformations().stream().toList(),
                ContentSnapshotService.currentTransformationRegistry().stream().toList());
    }

    @Test
    void transformationReferencesCandidateProductAddedInSameReload() {
        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ProductDatapackLoaderTest.resources(
                        Map.entry("test_input", ProductDatapackLoaderTest.product(
                                "butchercraft:test_input",
                                "Test Input",
                                "butchercraft:beef",
                                "gram"
                        )),
                        Map.entry("test_output", ProductDatapackLoaderTest.product(
                                "butchercraft:test_output",
                                "Test Output",
                                "butchercraft:beef",
                                "gram"
                        ))
                ),
                resources(Map.entry("test_transformation", transformation(
                        "butchercraft:test_transform",
                        "butchercraft:test_input",
                        "butchercraft:test_output"
                )))
        );

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(2, ContentSnapshotService.currentProductRegistry().size());
        assertEquals(4, ContentSnapshotService.currentPackagingRegistry().size());
        assertEquals(1, ContentSnapshotService.currentTransformationRegistry().size());
        assertTrue(ContentSnapshotService.currentTransformationRegistry()
                .contains(TransformationId.of("butchercraft:test_transform")));
    }

    @Test
    void productPackagingMetadataCanReferenceCandidatePackagingAndProductFromSameReload() {
        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ProductDatapackLoaderTest.resources(
                        Map.entry("ground", ProductDatapackLoaderTest.productWithTag(
                                "butchercraft:ground_beef",
                                "Ground Beef",
                                "butchercraft:beef",
                                "gram",
                                "butchercraft:trait/ground"
                        )),
                        Map.entry("retail", ProductDatapackLoaderTest.packagedProduct(
                                "butchercraft:retail_ground_beef",
                                "Retail Ground Beef",
                                "butchercraft:beef",
                                "gram",
                                "butchercraft:retail_package",
                                "butchercraft:ground_beef"
                        ))
                ),
                PackagingDatapackLoaderTest.resources(Map.entry(
                        "retail_package",
                        PackagingDatapackLoaderTest.packaging(
                                "butchercraft:retail_package",
                                "Retail Package",
                                "retail",
                                "gram"
                        )
                )),
                Map.of()
        );

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(2, ContentSnapshotService.currentProductRegistry().size());
        assertEquals(1, ContentSnapshotService.currentPackagingRegistry().size());
        assertEquals(0, ContentSnapshotService.currentTransformationRegistry().size());
        assertEquals("butchercraft:ground_beef", ContentSnapshotService.currentProductRegistry()
                .find(EngineId.of("butchercraft:retail_ground_beef"))
                .orElseThrow()
                .packagingMetadata()
                .orElseThrow()
                .sourceProductId()
                .value());
    }

    @Test
    void invalidProductLeavesBothActiveRegistriesUnchanged() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ProductDatapackLoaderTest.resources(Map.entry(
                        "bad_product",
                        ProductDatapackLoaderTest.product(
                                "butchercraft:test_input",
                                "Test Input",
                                "butchercraft:venison",
                                "gram"
                        )
                )),
                resources(Map.entry("test_transformation", transformation(
                        "butchercraft:test_transform",
                        "butchercraft:test_input",
                        "butchercraft:test_output"
                )))
        );

        assertFalse(result.succeeded());
        assertEquals(ProductDatapackErrorCode.UNKNOWN_CATEGORY, result.productErrors().getFirst().code());
        assertTrue(result.packagingErrors().isEmpty());
        assertTrue(result.transformationErrors().isEmpty());
        assertSame(previous, ContentSnapshotService.currentSnapshot());
    }

    @Test
    void invalidPackagingLeavesAllActiveRegistriesUnchanged() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ContentSnapshotService.bundledProductResources(),
                PackagingDatapackLoaderTest.resources(Map.entry(
                        "bad_package",
                        PackagingDatapackLoaderTest.packaging(
                                "butchercraft:retail_package",
                                "Retail Package",
                                "retail",
                                "kilogram"
                        )
                )),
                ContentSnapshotService.bundledTransformationResources()
        );

        assertFalse(result.succeeded());
        assertTrue(result.productErrors().isEmpty());
        assertEquals(PackagingDatapackErrorCode.UNKNOWN_QUANTITY_UNIT, result.packagingErrors().getFirst().code());
        assertTrue(result.transformationErrors().isEmpty());
        assertSame(previous, ContentSnapshotService.currentSnapshot());
    }

    @Test
    void invalidProductPackagingMetadataLeavesAllActiveRegistriesUnchanged() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ProductDatapackLoaderTest.resources(
                        Map.entry("ground", ProductDatapackLoaderTest.product(
                                "butchercraft:ground_beef",
                                "Ground Beef",
                                "butchercraft:beef",
                                "gram"
                        )),
                        Map.entry("retail", ProductDatapackLoaderTest.packagedProduct(
                                "butchercraft:retail_ground_beef",
                                "Retail Ground Beef",
                                "butchercraft:beef",
                                "gram",
                                "butchercraft:missing_package",
                                "butchercraft:ground_beef"
                        ))
                ),
                Map.of(),
                Map.of()
        );

        assertFalse(result.succeeded());
        assertEquals(ProductDatapackErrorCode.UNKNOWN_PACKAGING_DEFINITION,
                result.productErrors().getFirst().code());
        assertTrue(result.packagingErrors().isEmpty());
        assertTrue(result.transformationErrors().isEmpty());
        assertSame(previous, ContentSnapshotService.currentSnapshot());
    }

    @Test
    void invalidTransformationLeavesBothActiveRegistriesUnchanged() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ProductDatapackLoaderTest.resources(
                        Map.entry("test_input", ProductDatapackLoaderTest.product(
                                "butchercraft:test_input",
                                "Test Input",
                                "butchercraft:beef",
                                "gram"
                        )),
                        Map.entry("test_output", ProductDatapackLoaderTest.product(
                                "butchercraft:test_output",
                                "Test Output",
                                "butchercraft:beef",
                                "gram"
                        ))
                ),
                resources(Map.entry("bad_transformation", transformation(
                        "butchercraft:test_transform",
                        "butchercraft:test_input",
                        "butchercraft:missing_output"
                )))
        );

        assertFalse(result.succeeded());
        assertTrue(result.productErrors().isEmpty());
        assertTrue(result.packagingErrors().isEmpty());
        assertEquals(TransformationDatapackErrorCode.UNKNOWN_PRODUCT, result.transformationErrors().getFirst().code());
        assertSame(previous, ContentSnapshotService.currentSnapshot());
    }

    @Test
    void removedProductReferencedByTransformationRejectsEntireReload() {
        ContentSnapshot previous = ContentSnapshotService.currentSnapshot();
        LinkedHashMap<String, JsonElement> productsWithoutGroundBeef = new LinkedHashMap<>(ContentSnapshotService.bundledProductResources());
        productsWithoutGroundBeef.remove("data/butchercraft/butchercraft/content/product/ground_beef.json");
        productsWithoutGroundBeef.remove("data/butchercraft/butchercraft/content/product/retail_ground_beef.json");

        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                productsWithoutGroundBeef,
                ContentSnapshotService.bundledTransformationResources()
        );

        assertFalse(result.succeeded());
        assertTrue(result.productErrors().isEmpty());
        assertTrue(result.packagingErrors().isEmpty());
        assertEquals(TransformationDatapackErrorCode.UNKNOWN_PRODUCT, result.transformationErrors().getFirst().code());
        assertSame(previous, ContentSnapshotService.currentSnapshot());
    }

    @Test
    void bundledSnapshotPreservesGrinderAndBandsawTransformationDefinitions() {
        ContentSnapshot snapshot = ContentSnapshotService.loadBundledSnapshot();

        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:grind_beef")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:grind_pork")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:grind_bison")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:break_beef_forequarter")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:break_beef_hindquarter")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:cut_beef_short_loin")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:cut_beef_round")));
        assertTrue(snapshot.transformations().contains(TransformationId.of("butchercraft:cut_beef_sirloin")));
        assertTrue(snapshot.packaging().contains(EngineId.of("butchercraft:retail_package")));
        assertTrue(snapshot.packaging().contains(EngineId.of("butchercraft:vacuum_package")));
        assertTrue(snapshot.packaging().contains(EngineId.of("butchercraft:butcher_paper_package")));
        assertTrue(snapshot.packaging().contains(EngineId.of("butchercraft:freezer_paper_package")));
        assertEquals(8, snapshot.transformations()
                .find(TransformationId.of("butchercraft:break_beef_forequarter"))
                .orElseThrow()
                .outputs()
                .stream()
                .map(TransformationOutput::producedAmount)
                .map(amount -> amount.materialId().value())
                .distinct()
                .count());
        assertTrue(snapshot.products().contains(EngineId.of("butchercraft:beef_trim")));
        assertTrue(snapshot.products().contains(EngineId.of("butchercraft:beef_forequarter")));
        assertTrue(snapshot.products().contains(EngineId.of("butchercraft:beef_hindquarter")));
        assertTrue(snapshot.products().contains(EngineId.of("butchercraft:tri_tip")));
        assertTrue(snapshot.products().contains(EngineId.of("butchercraft:retail_ground_beef")));
    }

    @Test
    void bundledSnapshotLoadsEveryBeefFabricationProduct() {
        ContentSnapshot snapshot = ContentSnapshotService.loadBundledSnapshot();

        for (String resourcePath : BuiltInProductRegistry.BUILT_IN_RESOURCE_PATHS) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1, resourcePath.length() - ".json".length());
            assertTrue(
                    snapshot.products().contains(EngineId.of("butchercraft:" + fileName)),
                    "Missing bundled product from content snapshot: " + fileName
            );
        }
        assertEquals(31, snapshot.products().size());
    }

    @Test
    void legacyTransformationRegistryFacadeReadsTheActiveSnapshot() {
        ContentSnapshotLoadResult result = ContentSnapshotService.replaceFromDatapack(
                ContentSnapshotService.bundledProductResources(),
                resources(Map.entry("grind_beef", transformation(
                        "butchercraft:grind_beef",
                        "butchercraft:beef_trim",
                        "butchercraft:ground_beef"
                )))
        );

        assertTrue(result.succeeded(), result::describeErrors);
        assertEquals(1, TransformationRegistryService.currentRegistry().size());
        assertTrue(TransformationRegistryService.currentRegistry().contains(TransformationId.of("butchercraft:grind_beef")));
    }

    @SafeVarargs
    private static Map<String, JsonElement> resources(Map.Entry<String, String>... entries) {
        LinkedHashMap<String, JsonElement> resources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            resources.put(entry.getKey(), JsonParser.parseString(entry.getValue()));
        }
        return resources;
    }

    private static String transformation(String id, String inputProduct, String outputProduct) {
        return """
                {
                  "schema_version": 1,
                  "id": "%s",
                  "display_name": "%s",
                  "required_capability": "%s",
                  "inputs": [
                    {
                      "product_id": "%s",
                      "quantity": 100,
                      "unit": "gram"
                    }
                  ],
                  "outputs": [
                    {
                      "product_id": "%s",
                      "quantity": 90,
                      "unit": "gram",
                      "classification": "primary"
                    }
                  ],
                  "duration": {
                    "milliseconds": 3000
                  },
                  "yield": {
                    "numerator": 9,
                    "denominator": 10
                  },
                  "metadata": {
                    "butchercraft:schema/source": "test"
                  }
                }
                """.formatted(id, id, BuiltInTransformationRegistry.WORKSTATION_CAPABILITY_GRINDING.value(),
                inputProduct, outputProduct);
    }
}
