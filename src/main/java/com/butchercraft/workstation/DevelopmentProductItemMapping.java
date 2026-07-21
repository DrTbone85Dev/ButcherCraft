package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.EngineId;
import com.butchercraft.product.item.ProductTestItem;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class DevelopmentProductItemMapping {
    private final Map<ResourceLocation, Supplier<? extends Item>> itemByProductId;

    private DevelopmentProductItemMapping(Map<ResourceLocation, Supplier<? extends Item>> itemByProductId) {
        this.itemByProductId = Map.copyOf(Objects.requireNonNull(itemByProductId, "itemByProductId"));
    }

    @SafeVarargs
    public static DevelopmentProductItemMapping fromFixtureItems(Supplier<? extends ProductTestItem>... itemSuppliers) {
        Map<ResourceLocation, Supplier<? extends Item>> mappings = new LinkedHashMap<>();
        for (Supplier<? extends ProductTestItem> itemSupplier : itemSuppliers) {
            ProductTestItem item = itemSupplier.get();
            ResourceLocation productId = ResourceLocation.parse(item.defaultProductData().productTypeId());
            if (mappings.put(productId, itemSupplier) != null) {
                throw new IllegalStateException("Duplicate development product fixture mapping for " + productId);
            }
        }
        return new DevelopmentProductItemMapping(mappings);
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

    public boolean canCreate(EngineId productId) {
        Objects.requireNonNull(productId, "productId");
        return canCreate(ResourceLocation.parse(productId.value()));
    }

    public boolean canCreateAll(List<Product> products) {
        for (Product product : List.copyOf(Objects.requireNonNull(products, "products"))) {
            if (!canCreate(product.typeId())) {
                return false;
            }
        }
        return true;
    }
}
