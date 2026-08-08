package com.butchercraft.machine.cuttingtable.execution;

import com.butchercraft.processing.definition.BuiltInDefinitionIds;

import java.util.Set;

public final class CuttingTableExecutionConstants {
    public static final String OWNER_SUBSYSTEM_ID = "butchercraft:workstation";
    public static final String EXECUTABLE_REFERENCE_TYPE = "butchercraft:workstation/cutting_table";
    public static final String OPERATION_TYPE = "butchercraft:workstation/cutting_table_operation";
    public static final String HANDLER_ID = "butchercraft:execution_handler/cutting_table_player_operation";
    public static final String CONFIGURATION_IDENTITY =
            "butchercraft:execution_configuration/cutting_table_player_operation_v1";
    public static final Set<net.minecraft.resources.ResourceLocation> AUTHORIZED_OPERATIONS = Set.of(
            BuiltInDefinitionIds.FABRICATE_T_BONE_STEAK
    );

    private CuttingTableExecutionConstants() {
    }
}
