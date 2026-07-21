package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.bandsaw.BandsawBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.packaging.PackagingTableBlockEntity;
import com.butchercraft.workstation.block.ProcessingWorkstationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntityTypes {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ButcherCraft.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProcessingWorkstationBlockEntity>>
            DEVELOPMENT_PROCESSING_WORKSTATION = BLOCK_ENTITY_TYPES.register(
                    "development_processing_workstation",
                    () -> BlockEntityType.Builder.of(
                            ProcessingWorkstationBlockEntity::new,
                            ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrinderBlockEntity>>
            GRINDER = BLOCK_ENTITY_TYPES.register(
                    "grinder",
                    () -> BlockEntityType.Builder.of(
                            GrinderBlockEntity::new,
                            ModBlocks.GRINDER.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BandsawBlockEntity>>
            BANDSAW = BLOCK_ENTITY_TYPES.register(
                    "bandsaw",
                    () -> BlockEntityType.Builder.of(
                            BandsawBlockEntity::new,
                            ModBlocks.BANDSAW.get()
                    ).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PackagingTableBlockEntity>>
            PACKAGING_TABLE = BLOCK_ENTITY_TYPES.register(
                    "packaging_table",
                    () -> BlockEntityType.Builder.of(
                            PackagingTableBlockEntity::new,
                            ModBlocks.PACKAGING_TABLE.get()
                    ).build(null)
            );

    private ModBlockEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
