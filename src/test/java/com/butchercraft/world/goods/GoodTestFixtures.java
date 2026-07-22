package com.butchercraft.world.goods;

import java.util.List;

final class GoodTestFixtures {
    private GoodTestFixtures() {
    }

    static CommodityDefinition commodity(String id) {
        return CommodityDefinition.builder()
                .id("test:" + id)
                .displayName(displayName(id))
                .industryId(BuiltInIndustryCatalog.UTILITIES)
                .unitOfMeasure(UnitOfMeasure.LITER)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.LIQUID)
                .commodityType(CommodityType.WATER)
                .build();
    }

    static ProductDefinition product(String id) {
        return ProductDefinition.builder()
                .id("test:" + id)
                .displayName(displayName(id))
                .industryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                .sourceIndustryId(BuiltInIndustryCatalog.AGRICULTURE)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .economicFlag(EconomicFlag.PERISHABLE)
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .transportRequirement(TransportRequirement.REFRIGERATED)
                .transformationStage(ProductStage.INTERMEDIATE)
                .build();
    }

    static GoodTransformation transformation(String input, String output) {
        return new GoodTransformation(
                GoodId.of("test:" + input),
                GoodId.of("test:" + output),
                new GoodYieldRatio(4, 5),
                BuiltInIndustryCatalog.MEAT_PROCESSING
        );
    }

    static GoodRegistry registry() {
        List<GoodDefinition> definitions = List.of(
                commodity("water"),
                product("beef_carcass"),
                ProductDefinition.builder()
                        .id("test:ground_beef")
                        .displayName("Ground Beef")
                        .industryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                        .sourceIndustryId(BuiltInIndustryCatalog.MEAT_PROCESSING)
                        .unitOfMeasure(UnitOfMeasure.POUND)
                        .stackability(Stackability.STACKABLE)
                        .economicFlag(EconomicFlag.TRADEABLE)
                        .economicFlag(EconomicFlag.CONSUMABLE)
                        .economicFlag(EconomicFlag.PERISHABLE)
                        .storageRequirement(StorageRequirement.REFRIGERATED)
                        .transportRequirement(TransportRequirement.REFRIGERATED)
                        .itemMapping(ItemMappingMetadata.of("minecraft", "butchercraft:ground_beef_test_product"))
                        .transformationStage(ProductStage.FINISHED)
                        .build()
        );
        return GoodRegistry.of(
                definitions,
                List.of(transformation("beef_carcass", "ground_beef")),
                BuiltInIndustryCatalog.all()
        );
    }

    private static String displayName(String id) {
        String[] words = id.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
