package com.butchercraft.product.item;

import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Development-only item that carries immutable product component data.
 *
 * <p>The item has no food, processing, or world-changing behavior. It exists only to verify the
 * ItemStack data-component boundary. Product-bearing stacks are max stack size one so ItemStack
 * count cannot conflict with engine quantity.</p>
 */
public final class ProductTestItem extends Item implements ProductDataCarrier {
    private final ProductStackData defaultProductData;
    private final Supplier<DataComponentType<ProductStackData>> componentType;

    public ProductTestItem(Properties properties, ProductStackData defaultProductData) {
        this(properties, defaultProductData, ModDataComponents.PRODUCT_DATA::get);
    }

    public ProductTestItem(
            Properties properties,
            ProductStackData defaultProductData,
            Supplier<DataComponentType<ProductStackData>> componentType
    ) {
        super(properties.stacksTo(1));
        this.defaultProductData = Objects.requireNonNull(defaultProductData, "defaultProductData");
        this.componentType = Objects.requireNonNull(componentType, "componentType");
    }

    @Override
    public ProductStackData defaultProductData() {
        return defaultProductData;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ProductStackAdapter.writeProductData(stack, defaultProductData, componentType.get());
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ProductDataResult<ProductStackData> dataResult = ProductStackAdapter.readProductData(stack, componentType.get());
        if (!dataResult.succeeded()) {
            tooltip.add(Component.translatable("tooltip.butchercraft.product_data.missing").withStyle(ChatFormatting.RED));
            return;
        }

        ProductStackData data = dataResult.orThrow();
        var productResult = ProductStackAdapter.toProduct(data);
        if (!productResult.succeeded()) {
            tooltip.add(Component.translatable("tooltip.butchercraft.product_data.invalid").withStyle(ChatFormatting.RED));
            return;
        }

        var product = productResult.orThrow();
        tooltip.add(Component.translatable("tooltip.butchercraft.product_data.product", data.productTypeId()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.butchercraft.product_data.source", data.sourceCategoryId()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.butchercraft.product_data.state", data.processingStateId()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.butchercraft.product_data.quantity", data.quantityValue(), data.quantityUnitId()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.butchercraft.product_data.quality", product.quality().grade().displayName()).withStyle(ChatFormatting.GRAY));
        data.packaging().ifPresent(packaging -> {
            tooltip.add(Component.translatable(
                    "tooltip.butchercraft.product_data.packaging",
                    packaging.packagingDefinitionId(),
                    packaging.packagingFormatId()
            ).withStyle(ChatFormatting.GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable(
                        "tooltip.butchercraft.product_data.packaging_source",
                        packaging.sourceProductId()
                ).withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable("tooltip.butchercraft.product_data.quality_score", data.qualityScore()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
