package com.butchercraft.world.goods;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.butchercraft.world.goods.GoodTestFixtures.commodity;
import static com.butchercraft.world.goods.GoodTestFixtures.product;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodDefinitionTest {
    @Test
    void commodityContainsCanonicalBaseFieldsAndType() {
        CommodityDefinition definition = CommodityDefinition.builder()
                .id("butchercraft:water")
                .displayName("  Water  ")
                .industryId(BuiltInIndustryCatalog.UTILITIES)
                .unitOfMeasure(UnitOfMeasure.LITER)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.LIQUID)
                .itemMapping(ItemMappingMetadata.of("minecraft", "minecraft:water_bucket"))
                .commodityType(CommodityType.WATER)
                .build();

        assertEquals(GoodId.of("butchercraft:water"), definition.id());
        assertEquals("Water", definition.displayName());
        assertEquals(GoodCategory.COMMODITY, definition.category());
        assertEquals(BuiltInIndustryCatalog.UTILITIES, definition.industryId());
        assertEquals(UnitOfMeasure.LITER, definition.unitOfMeasure());
        assertEquals(Stackability.STACKABLE, definition.stackability());
        assertTrue(definition.hasEconomicFlag(EconomicFlag.TRADEABLE));
        assertEquals(StorageRequirement.AMBIENT, definition.storageRequirement());
        assertEquals(TransportRequirement.LIQUID, definition.transportRequirement());
        assertEquals(CommodityType.WATER, definition.commodityType());
        assertEquals(GoodSchema.CURRENT_VERSION, definition.schemaVersion());
        assertEquals(1, definition.itemMappings().size());
    }

    @Test
    void productContainsSourceIndustryAndTransformationStage() {
        ProductDefinition definition = ProductDefinition.builder()
                .id("butchercraft:steel_beam")
                .displayName("Steel Beam")
                .industryId(BuiltInIndustryCatalog.MANUFACTURING)
                .sourceIndustryId(BuiltInIndustryCatalog.MANUFACTURING)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .transformationStage(ProductStage.FINISHED)
                .build();

        assertEquals(GoodCategory.PRODUCT, definition.category());
        assertEquals(BuiltInIndustryCatalog.MANUFACTURING, definition.sourceIndustryId());
        assertEquals(ProductStage.FINISHED, definition.transformationStage());
    }

    @Test
    void definitionsDefensivelyCopyImmutableMetadata() {
        Set<EconomicFlag> flags = new LinkedHashSet<>(Set.of(EconomicFlag.TRADEABLE));
        Set<ItemMappingMetadata> mappings = new LinkedHashSet<>(Set.of(
                ItemMappingMetadata.of("minecraft", "minecraft:water_bucket")
        ));
        CommodityDefinition definition = CommodityDefinition.builder()
                .id("butchercraft:water")
                .displayName("Water")
                .industryId(BuiltInIndustryCatalog.UTILITIES)
                .unitOfMeasure(UnitOfMeasure.LITER)
                .stackability(Stackability.STACKABLE)
                .economicFlags(flags)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.LIQUID)
                .itemMappings(mappings)
                .commodityType(CommodityType.WATER)
                .build();

        flags.add(EconomicFlag.HAZARDOUS);
        mappings.add(ItemMappingMetadata.of("minecraft", "minecraft:lava_bucket"));

        assertEquals(Set.of(EconomicFlag.TRADEABLE), definition.economicFlags());
        assertEquals(1, definition.itemMappings().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.economicFlags().add(EconomicFlag.CAPACITY));
        assertThrows(UnsupportedOperationException.class, () -> definition.itemMappings().clear());
    }

    @Test
    void equalityIncludesSubtypeFieldsAndAllBaseMetadata() {
        assertEquals(commodity("water"), commodity("water"));
        assertEquals(product("beef_carcass"), product("beef_carcass"));
        assertNotEquals(commodity("water"), product("water"));
        assertNotEquals(commodity("water"), commodity("steam"));
    }

    @Test
    void buildersRejectIncompleteDefinitions() {
        assertThrows(IllegalStateException.class, () -> CommodityDefinition.builder().build());
        assertThrows(IllegalStateException.class, () -> ProductDefinition.builder().build());
    }

    @Test
    void constructorsRejectInvalidIdentityDisplayNameSchemaAndYield() {
        assertThrows(IllegalArgumentException.class, () -> GoodId.of("Invalid Id"));
        assertThrows(IllegalArgumentException.class, () -> IndustryId.of("Bad Industry"));
        assertThrows(IllegalArgumentException.class, () -> new CommodityDefinition(
                GoodId.of("test:water"),
                " ",
                BuiltInIndustryCatalog.UTILITIES,
                UnitOfMeasure.LITER,
                Stackability.STACKABLE,
                Set.of(),
                StorageRequirement.AMBIENT,
                TransportRequirement.LIQUID,
                Set.of(),
                GoodSchema.CURRENT_VERSION,
                CommodityType.WATER
        ));
        assertThrows(IllegalArgumentException.class, () -> CommodityDefinition.builder()
                .id("test:water")
                .displayName("Water")
                .industryId(BuiltInIndustryCatalog.UTILITIES)
                .unitOfMeasure(UnitOfMeasure.LITER)
                .stackability(Stackability.STACKABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.LIQUID)
                .commodityType(CommodityType.WATER)
                .schemaVersion(99)
                .build());
        assertThrows(IllegalArgumentException.class, () -> new GoodYieldRatio(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new GoodYieldRatio(1, 0));
        assertFalse(BuiltInIndustryCatalog.all().isEmpty());
    }
}
