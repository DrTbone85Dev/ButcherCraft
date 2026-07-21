package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.transformation.InMemoryTransformationMaterialStore;
import com.butchercraft.transformation.MaterialAmount;
import com.butchercraft.transformation.TransformationMaterialStore;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import net.minecraft.world.item.ItemStack;

/**
 * Minecraft-side adapter from workstation ItemStacks to pure transformation material stores.
 */
public final class WorkstationInventoryMaterialStore {
    private WorkstationInventoryMaterialStore() {
    }

    public static TransformationMaterialStore inputStore(WorkstationInventory inventory) {
        InMemoryTransformationMaterialStore.Builder builder = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(inventory.inputSlotCount());
        addStack(builder, inventory.input());
        return builder.build();
    }

    public static TransformationMaterialStore outputStore(WorkstationInventory inventory) {
        InMemoryTransformationMaterialStore.Builder builder = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(inventory.outputSlotCount());
        for (ItemStack output : inventory.outputs()) {
            if (output.isEmpty()) {
                continue;
            }
            Product product = product(output);
            builder.material(product.typeId(), product.quantity());
            builder.capacity(product.typeId(), product.quantity());
        }
        return builder.build();
    }

    private static void addStack(InMemoryTransformationMaterialStore.Builder builder, ItemStack stack) {
        if (!stack.isEmpty()) {
            Product product = product(stack);
            builder.material(product.typeId(), product.quantity());
        }
    }

    private static Product product(ItemStack stack) {
        ProductDataResult<Product> result = ProductStackAdapter.readProduct(stack);
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.failureReason().orElseThrow().message());
        }
        return result.orThrow();
    }

    public static MaterialAmount materialAmount(ItemStack stack) {
        Product product = product(stack);
        return new MaterialAmount(product.typeId(), product.quantity());
    }
}
