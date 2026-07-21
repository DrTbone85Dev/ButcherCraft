package com.butchercraft.machine.packaging;

import com.butchercraft.engine.EngineId;
import com.butchercraft.packaging.definition.BuiltInPackagingRegistry;
import com.butchercraft.registration.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Fixed Minecraft-side mapping for packaging supply items known to ButcherCraft core.
 */
public final class PackagingSupplyItemMappings {
    private static final Map<EngineId, Supplier<? extends Item>> ITEMS_BY_SUPPLY_ID = supplyItems();

    private PackagingSupplyItemMappings() {
    }

    public static boolean isKnownSupplyItem(ItemStack stack) {
        return identify(stack).isPresent();
    }

    public static boolean matches(ItemStack stack, EngineId requiredSupplyItemId) {
        Objects.requireNonNull(requiredSupplyItemId, "requiredSupplyItemId");
        return identify(stack).filter(requiredSupplyItemId::equals).isPresent();
    }

    public static Optional<EngineId> identify(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        for (var entry : ITEMS_BY_SUPPLY_ID.entrySet()) {
            if (stack.getItem() == entry.getValue().get()) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public static Set<EngineId> knownSupplyItemIds() {
        return ITEMS_BY_SUPPLY_ID.keySet();
    }

    private static Map<EngineId, Supplier<? extends Item>> supplyItems() {
        LinkedHashMap<EngineId, Supplier<? extends Item>> supplies = new LinkedHashMap<>();
        supplies.put(BuiltInPackagingRegistry.FOAM_TRAY, ModItems.FOAM_TRAY);
        supplies.put(BuiltInPackagingRegistry.PLASTIC_WRAP_ROLL, ModItems.PLASTIC_WRAP_ROLL);
        supplies.put(BuiltInPackagingRegistry.VACUUM_BAG, ModItems.VACUUM_BAG);
        supplies.put(BuiltInPackagingRegistry.BUTCHER_PAPER_ROLL, ModItems.BUTCHER_PAPER_ROLL);
        supplies.put(BuiltInPackagingRegistry.FREEZER_PAPER_ROLL, ModItems.FREEZER_PAPER_ROLL);
        supplies.put(BuiltInPackagingRegistry.RETAIL_LABEL_ROLL, ModItems.RETAIL_LABEL_ROLL);
        return Map.copyOf(supplies);
    }
}
