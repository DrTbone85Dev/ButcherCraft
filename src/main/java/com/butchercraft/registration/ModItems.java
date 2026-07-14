package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ButcherCraft.MOD_ID);

    /**
     * Harmless development-only item used to verify registration, assets, creative-tab wiring, and diagnostics.
     */
    public static final DeferredItem<Item> DEVELOPMENT_TEST_ITEM = ITEMS.registerSimpleItem(
            "development_test_item",
            new Item.Properties()
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
