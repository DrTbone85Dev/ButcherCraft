package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.bandsaw.BandsawMenu;
import com.butchercraft.machine.grinder.GrinderMenu;
import com.butchercraft.machine.packaging.PackagingTableMenu;
import com.butchercraft.workstation.menu.ProcessingWorkstationMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ButcherCraft.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ProcessingWorkstationMenu>>
            DEVELOPMENT_PROCESSING_WORKSTATION = MENU_TYPES.register(
                    "development_processing_workstation",
                    () -> IMenuTypeExtension.create(ProcessingWorkstationMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<GrinderMenu>>
            GRINDER = MENU_TYPES.register(
                    "grinder",
                    () -> IMenuTypeExtension.create(GrinderMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<BandsawMenu>>
            BANDSAW = MENU_TYPES.register(
                    "bandsaw",
                    () -> IMenuTypeExtension.create(BandsawMenu::new)
            );

    public static final DeferredHolder<MenuType<?>, MenuType<PackagingTableMenu>>
            PACKAGING_TABLE = MENU_TYPES.register(
                    "packaging_table",
                    () -> IMenuTypeExtension.create(PackagingTableMenu::new)
            );

    private ModMenuTypes() {
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
