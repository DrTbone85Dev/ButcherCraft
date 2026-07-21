package com.butchercraft.product.integration;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.workstation.DevelopmentProductItemMapping;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeefFabricationProductItemMappingTest {
    @Test
    void fixtureMappingCreatesStacksForBeefFabricationProducts() {
        DevelopmentProductItemMapping mapping = DevelopmentProductItemMappings.fixtureMapping();

        for (Fixture fixture : fixtures()) {
            assertTrue(mapping.canCreate(fixture.productId()), "Missing mapping for " + fixture.productId());

            var stack = mapping.createStack(fixture.product()).orElseThrow();
            ProductDataResult<ProductStackData> data = ProductStackAdapter.readProductData(stack);

            assertTrue(data.succeeded(), data.toString());
            assertEquals(fixture.productId().toString(), data.orThrow().productTypeId());
            assertEquals(fixture.quantity(), data.orThrow().quantityValue());
            assertEquals(fixture.state().toString(), data.orThrow().processingStateId());
        }
    }

    @Test
    void fixtureMappingCreatesPackagedRetailGroundBeefStack() {
        DevelopmentProductItemMapping mapping = DevelopmentProductItemMappings.fixtureMapping();
        Product product = new Product(
                EngineId.of("butchercraft:retail_ground_beef"),
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of("butchercraft:retail_packaged")),
                ProductQuantity.grams(900),
                ProductQuality.ofScore(700)
        );

        var stack = mapping.createStack(product).orElseThrow();
        ProductStackData data = ProductStackAdapter.readProductData(stack).orThrow();

        assertEquals("butchercraft:retail_ground_beef", data.productTypeId());
        assertEquals("butchercraft:retail_packaged", data.processingStateId());
        assertEquals(900, data.quantityValue());
        assertTrue(data.packaging().isEmpty());
    }

    private static List<Fixture> fixtures() {
        return List.of(
                fixture(BuiltInDefinitionIds.BEEF_HINDQUARTER, "hindquarter", 100_000),
                fixture(BuiltInDefinitionIds.BEEF_ROUND, "primal", 30_000),
                fixture(BuiltInDefinitionIds.BEEF_SIRLOIN, "primal", 15_000),
                fixture(BuiltInDefinitionIds.BEEF_SHORT_LOIN, "primal", 15_000),
                fixture(BuiltInDefinitionIds.BEEF_FLANK, "primal", 7_500),
                fixture(BuiltInDefinitionIds.T_BONE_STEAK, "steak", 4_000),
                fixture(BuiltInDefinitionIds.PORTERHOUSE_STEAK, "steak", 3_000),
                fixture(BuiltInDefinitionIds.BEEF_STRIP_LOIN, "subprimal", 3_000),
                fixture(BuiltInDefinitionIds.BEEF_TENDERLOIN, "subprimal", 2_000),
                fixture(BuiltInDefinitionIds.TOP_ROUND, "subprimal", 7_500),
                fixture(BuiltInDefinitionIds.BOTTOM_ROUND, "subprimal", 6_500),
                fixture(BuiltInDefinitionIds.EYE_OF_ROUND, "subprimal", 3_500),
                fixture(BuiltInDefinitionIds.SIRLOIN_TIP, "subprimal", 5_000),
                fixture(BuiltInDefinitionIds.TOP_SIRLOIN, "subprimal", 5_000),
                fixture(BuiltInDefinitionIds.SIRLOIN_STEAK, "steak", 3_500),
                fixture(BuiltInDefinitionIds.TRI_TIP, "subprimal", 2_000)
        );
    }

    private static Fixture fixture(ResourceLocation productId, String statePath, long quantity) {
        return new Fixture(productId, BuiltInDefinitionIds.id(statePath), quantity);
    }

    private record Fixture(ResourceLocation productId, ResourceLocation state, long quantity) {
        Product product() {
            return new Product(
                    EngineId.of(productId.toString()),
                    ProductCategory.BEEF,
                    ProcessingState.fromId(EngineId.of(state.toString())),
                    new ProductQuantity(quantity, QuantityUnit.GRAM),
                    ProductQuality.ofScore(695)
            );
        }
    }
}
