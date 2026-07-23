package com.butchercraft.world.goods;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.butchercraft.world.goods.GoodTestFixtures.registry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodStorageTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void storageRoundTripsAllDefinitionAndTransformationFields() {
        GoodRegistry registry = registry();
        GoodStorage storage = storage("goods.json");

        storage.save(registry);
        GoodRegistry loaded = storage.load();

        assertEquals(registry.definitions(), loaded.definitions());
        assertEquals(registry.transformations(), loaded.transformations());
        assertTrue(Files.exists(temporaryDirectory.resolve("goods.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("goods.json.tmp")));
        ProductDefinition groundBeef = (ProductDefinition) loaded.find(GoodId.of("test:ground_beef")).orElseThrow();
        assertEquals(ProductStage.FINISHED, groundBeef.transformationStage());
        assertEquals(1, groundBeef.itemMappings().size());
    }

    @Test
    void serializationIsDeterministicAndUsesStableFieldNames() {
        GoodStorage storage = storage("goods.json");
        String json = storage.serialize(registry());

        assertTrue(json.indexOf("test:beef_carcass") < json.indexOf("test:ground_beef"));
        assertTrue(json.indexOf("test:ground_beef") < json.indexOf("test:water"));
        assertTrue(json.contains("\"schema_version\": 1"));
        assertTrue(json.contains("\"goods\""));
        assertTrue(json.contains("\"transformations\""));
        assertTrue(json.contains("\"storage_requirement\""));
        assertTrue(json.contains("\"transport_requirement\""));
        assertTrue(json.contains("\"item_mappings\""));
        assertEquals(json, storage.serialize(storage.deserialize(json)));
    }

    @Test
    void missingFileLoadsAnEmptyRegistryWithTheIndustryCatalog() {
        GoodRegistry loaded = storage("missing.json").load();

        assertEquals(0, loaded.size());
        assertEquals(BuiltInIndustryCatalog.all(), loaded.knownIndustries());
    }

    @Test
    void storageRejectsMalformedJsonAndUnsupportedSchemas() {
        GoodStorage storage = storage("goods.json");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(99, "", "")));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                commodityObject(99, "test:water", "butchercraft:utilities"),
                ""
        )));
    }

    @Test
    void storageRejectsDuplicateIdsMissingFieldsAndUnknownIndustries() {
        GoodStorage storage = storage("goods.json");
        String commodity = commodityObject(1, "test:water", "butchercraft:utilities");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                commodity + "," + commodity,
                ""
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                commodity.replace("\"display_name\": \"Water\",", ""),
                ""
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                commodityObject(1, "test:water", "example:unknown"),
                ""
        )));
    }

    @Test
    void storageRejectsUnknownCategoryUnitStackabilityStorageTransportAndFlags() {
        GoodStorage storage = storage("goods.json");
        String valid = root(1, commodityObject(1, "test:water", "butchercraft:utilities"), "");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"category\": \"commodity\"", "\"category\": \"mystery\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"unit_of_measure\": \"liter\"", "\"unit_of_measure\": \"barrel\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"stackability\": \"stackable\"", "\"stackability\": \"sometimes\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"storage_requirement\": \"ambient\"", "\"storage_requirement\": \"unknown\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"transport_requirement\": \"liquid\"", "\"transport_requirement\": \"unknown\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                valid.replace("\"economic_flags\": [\"tradeable\"]", "\"economic_flags\": [\"unknown\"]")
        ));
    }

    @Test
    void storageRejectsUnknownCommodityTypeAndProductStage() {
        GoodStorage storage = storage("goods.json");
        String commodity = root(1, commodityObject(1, "test:water", "butchercraft:utilities"), "");
        String product = root(1, productObject(1, "test:ground_beef"), "");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                commodity.replace("\"commodity_type\": \"water\"", "\"commodity_type\": \"mystery\"")
        ));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(
                product.replace("\"product_stage\": \"finished\"", "\"product_stage\": \"mystery\"")
        ));
    }

    @Test
    void storageRejectsInvalidTransformationSchemaReferencesAndCycles() {
        GoodStorage storage = storage("goods.json");
        String goods = productObject(1, "test:input") + "," + productObject(1, "test:output");

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                goods,
                transformationObject(99, "test:input", "test:output")
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                goods,
                transformationObject(1, "test:missing", "test:output")
        )));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(root(
                1,
                goods,
                transformationObject(1, "test:input", "test:output") + ","
                        + transformationObject(1, "test:output", "test:input")
        )));
    }

    private GoodStorage storage(String fileName) {
        return new GoodStorage(temporaryDirectory.resolve(fileName), BuiltInIndustryCatalog.all());
    }

    private static String root(int schemaVersion, String goods, String transformations) {
        return """
                {
                  "schema_version": %d,
                  "goods": [%s],
                  "transformations": [%s]
                }
                """.formatted(schemaVersion, goods, transformations);
    }

    private static String commodityObject(int schemaVersion, String id, String industryId) {
        return """
                {
                  "schema_version": %d,
                  "id": "%s",
                  "display_name": "Water",
                  "category": "commodity",
                  "industry_id": "%s",
                  "unit_of_measure": "liter",
                  "stackability": "stackable",
                  "economic_flags": ["tradeable"],
                  "storage_requirement": "ambient",
                  "transport_requirement": "liquid",
                  "item_mappings": [],
                  "commodity_type": "water"
                }
                """.formatted(schemaVersion, id, industryId);
    }

    private static String productObject(int schemaVersion, String id) {
        return """
                {
                  "schema_version": %d,
                  "id": "%s",
                  "display_name": "Ground Beef",
                  "category": "product",
                  "industry_id": "butchercraft:meat_processing",
                  "source_industry_id": "butchercraft:meat_processing",
                  "unit_of_measure": "pound",
                  "stackability": "stackable",
                  "economic_flags": ["tradeable", "perishable"],
                  "storage_requirement": "refrigerated",
                  "transport_requirement": "refrigerated",
                  "item_mappings": [],
                  "product_stage": "finished"
                }
                """.formatted(schemaVersion, id);
    }

    private static String transformationObject(int schemaVersion, String input, String output) {
        return """
                {
                  "schema_version": %d,
                  "input_good_id": "%s",
                  "output_good_id": "%s",
                  "yield_numerator": 4,
                  "yield_denominator": 5,
                  "owning_industry_id": "butchercraft:meat_processing"
                }
                """.formatted(schemaVersion, input, output);
    }
}
