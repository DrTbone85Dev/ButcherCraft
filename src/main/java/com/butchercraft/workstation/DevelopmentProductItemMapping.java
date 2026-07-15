package com.butchercraft.workstation;

import com.butchercraft.ButcherCraft;
import com.butchercraft.engine.product.Product;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DevelopmentProductItemMapping {
    private final Map<ResourceLocation, Supplier<? extends Item>> itemByProductId;

    private DevelopmentProductItemMapping(Map<ResourceLocation, Supplier<? extends Item>> itemByProductId) {
        this.itemByProductId = Map.copyOf(Objects.requireNonNull(itemByProductId, "itemByProductId"));
    }

    public static DevelopmentProductItemMapping fixtureMapping() {
        return new DevelopmentProductItemMapping(Map.of(
                ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "beef_trim"), ModItems.BEEF_TRIM_TEST,
                ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "ground_beef"), ModItems.GROUND_BEEF_TEST
        ));
    }

    public Optional<ItemStack> createStack(Product product) {
        Objects.requireNonNull(product, "product");
        ResourceLocation productId = ResourceLocation.parse(product.typeId().value());
        Supplier<? extends Item> item = itemByProductId.get(productId);
        if (item == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item.get());
        ProductDataResult<?> result = ProductStackAdapter.writeProduct(stack, product);
        if (!result.succeeded()) {
            return Optional.empty();
        }
        return Optional.of(stack);
    }

    public boolean canCreate(ResourceLocation productId) {
        return itemByProductId.containsKey(productId);
    }
}
