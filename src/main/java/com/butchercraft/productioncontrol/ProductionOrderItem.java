package com.butchercraft.productioncontrol;

import com.butchercraft.registration.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ProductionOrderItem extends Item {
    public ProductionOrderItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(ModDataComponents.PRODUCTION_ORDER.get(), ProductionOrderData.beefPattiesOrder());
        return stack;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ProductionOrderControl.openOrCreate(player, stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public ItemInteractionResult useOnWorkstation(
            ItemStack stack,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand
    ) {
        if (!level.isClientSide) {
            ProductionOrderControl.assignClickedWorkstation(player, stack, hand, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ProductionOrderData data = ProductionOrderControl.dataOrDefault(stack);
        tooltip.add(Component.translatable("tooltip.butchercraft.production_order.chain").withStyle(ChatFormatting.GRAY));
        if (data.runId().isPresent()) {
            tooltip.add(Component.translatable("tooltip.butchercraft.production_order.linked").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.butchercraft.production_order.new").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.butchercraft.production_order.assign").withStyle(ChatFormatting.DARK_GRAY));
    }
}
