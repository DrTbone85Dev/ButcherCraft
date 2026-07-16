package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.bandsaw.BandsawBlock;
import com.butchercraft.machine.bandsaw.BandsawUpperBlock;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.workstation.block.ProcessingWorkstationBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ButcherCraft.MOD_ID);

    public static final DeferredBlock<ProcessingWorkstationBlock> DEVELOPMENT_PROCESSING_WORKSTATION =
            BLOCKS.registerBlock(
                    "development_processing_workstation",
                    ProcessingWorkstationBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(2.0F)
                            .sound(SoundType.METAL)
            );

    public static final DeferredBlock<GrinderBlock> GRINDER =
            BLOCKS.registerBlock(
                    "grinder",
                    GrinderBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(2.5F)
                            .sound(SoundType.METAL)
            );

    public static final DeferredBlock<BandsawBlock> BANDSAW =
            BLOCKS.registerBlock(
                    "bandsaw",
                    BandsawBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .sound(SoundType.METAL)
            );

    public static final DeferredBlock<BandsawUpperBlock> BANDSAW_UPPER =
            BLOCKS.registerBlock(
                    "bandsaw_upper",
                    BandsawUpperBlock::new,
                    BlockBehaviour.Properties.of()
                            .strength(3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
            );

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
