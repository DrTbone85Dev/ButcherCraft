package com.butchercraft.world.goods;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.goods.GoodTestFixtures.product;
import static com.butchercraft.world.goods.GoodTestFixtures.transformation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoodManagerTest {
    @Test
    void managerRegistersDefinitionsAndRelationshipsWithoutRuntimeQuantities() {
        GoodManager manager = new GoodManager(GoodRegistry.empty(BuiltInIndustryCatalog.all()));

        manager.registerAll(List.of(product("input"), product("output")));
        manager.registerTransformation(transformation("input", "output"));
        manager.validate();

        assertTrue(manager.contains(GoodId.of("test:input")));
        assertEquals("Input", manager.find(GoodId.of("test:input")).orElseThrow().displayName());
        assertEquals(2, manager.findByCategory(GoodCategory.PRODUCT).size());
        assertEquals(1, manager.transformationsFrom(GoodId.of("test:input")).size());
        assertEquals(1, manager.transformationsTo(GoodId.of("test:output")).size());
        assertEquals(1, manager.transformationsForIndustry(BuiltInIndustryCatalog.MEAT_PROCESSING).size());
    }

    @Test
    void managerPreservesRegistryValidation() {
        GoodManager manager = new GoodManager(GoodRegistry.empty(BuiltInIndustryCatalog.all()));
        manager.register(product("duplicate"));

        assertThrows(IllegalArgumentException.class, () -> manager.register(product("duplicate")));
        assertThrows(IllegalArgumentException.class, () -> manager.registerTransformation(
                transformation("missing", "duplicate")
        ));
    }
}
