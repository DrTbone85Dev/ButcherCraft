package com.butchercraft.machine.grinder.execution;

import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import net.minecraft.resources.ResourceLocation;

public final class GrinderExecutionConstants {
    public static final String OWNER_SUBSYSTEM_ID = "butchercraft:workstation";
    public static final String EXECUTABLE_REFERENCE_TYPE = "butchercraft:workstation/grinder";
    public static final String OPERATION_TYPE = "butchercraft:workstation/grinder_operation";
    public static final String HANDLER_ID = "butchercraft:execution_handler/grinder_player_operation";
    public static final String CONFIGURATION_IDENTITY = "butchercraft:execution_configuration/grinder_player_operation_v1";
    public static final ResourceLocation GRIND_BEEF = BuiltInDefinitionIds.GRIND_BEEF;

    private GrinderExecutionConstants() {
    }
}
