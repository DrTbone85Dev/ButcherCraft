package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;

/**
 * Built-in product definitions used before product-definition datapack loading exists.
 */
public final class BuiltInProductRegistry {
    public static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    public static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    public static final EngineId PORK_TRIM = EngineId.of("butchercraft:pork_trim");
    public static final EngineId GROUND_PORK = EngineId.of("butchercraft:ground_pork");
    public static final EngineId BISON_TRIM = EngineId.of("butchercraft:bison_trim");
    public static final EngineId GROUND_BISON = EngineId.of("butchercraft:ground_bison");

    public static final EngineId CATEGORY_BEEF = EngineId.of("butchercraft:beef");
    public static final EngineId CATEGORY_PORK = EngineId.of("butchercraft:pork");
    public static final EngineId CATEGORY_BISON = EngineId.of("butchercraft:bison");

    public static final EngineId TAG_TRIM = EngineId.of("butchercraft:trait/trim");
    public static final EngineId TAG_GROUND = EngineId.of("butchercraft:trait/ground");
    public static final EngineId METADATA_SOURCE = EngineId.of("butchercraft:schema/source");

    private BuiltInProductRegistry() {
    }

    public static ProductRegistry builtInRegistry() {
        return ProductRegistry.builder()
                .register(beefTrim())
                .register(groundBeef())
                .register(porkTrim())
                .register(groundPork())
                .register(bisonTrim())
                .register(groundBison())
                .build();
    }

    public static ProductDefinition beefTrim() {
        return product(BEEF_TRIM, "Beef Trim", CATEGORY_BEEF, TAG_TRIM);
    }

    public static ProductDefinition groundBeef() {
        return product(GROUND_BEEF, "Ground Beef", CATEGORY_BEEF, TAG_GROUND);
    }

    public static ProductDefinition porkTrim() {
        return product(PORK_TRIM, "Pork Trim", CATEGORY_PORK, TAG_TRIM);
    }

    public static ProductDefinition groundPork() {
        return product(GROUND_PORK, "Ground Pork", CATEGORY_PORK, TAG_GROUND);
    }

    public static ProductDefinition bisonTrim() {
        return product(BISON_TRIM, "Bison Trim", CATEGORY_BISON, TAG_TRIM);
    }

    public static ProductDefinition groundBison() {
        return product(GROUND_BISON, "Ground Bison", CATEGORY_BISON, TAG_GROUND);
    }

    private static ProductDefinition product(
            EngineId id,
            String displayName,
            EngineId category,
            EngineId tag
    ) {
        return ProductDefinition.builder()
                .id(id)
                .displayName(displayName)
                .schemaVersion(ProductDefinition.CURRENT_SCHEMA_VERSION)
                .category(ProductCategory.fromId(category))
                .defaultQuantityUnit(QuantityUnit.GRAM)
                .tag(tag)
                .metadata(METADATA_SOURCE, "built_in")
                .build();
    }
}
