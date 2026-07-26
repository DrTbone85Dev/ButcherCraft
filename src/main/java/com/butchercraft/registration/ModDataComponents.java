package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.productioncontrol.ProductionOrderData;
import com.butchercraft.product.component.ProductStackData;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(ButcherCraft.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProductStackData>> PRODUCT_DATA =
            DATA_COMPONENTS.registerComponentType("product_data", builder -> builder
                    .persistent(ProductStackData.CODEC)
                    .networkSynchronized(ProductStackData.STREAM_CODEC)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProductionOrderData>> PRODUCTION_ORDER =
            DATA_COMPONENTS.registerComponentType("production_order", builder -> builder
                    .persistent(ProductionOrderData.CODEC)
                    .networkSynchronized(ProductionOrderData.STREAM_CODEC)
                    .cacheEncoding());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
