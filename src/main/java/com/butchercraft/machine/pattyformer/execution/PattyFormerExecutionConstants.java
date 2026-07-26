package com.butchercraft.machine.pattyformer.execution;

import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

public final class PattyFormerExecutionConstants {
    public static final String OWNER_SUBSYSTEM_ID = "butchercraft:workstation";
    public static final String EXECUTABLE_REFERENCE_TYPE = "butchercraft:workstation/patty_former";
    public static final String OPERATION_TYPE = "butchercraft:workstation/patty_former_operation";
    public static final String HANDLER_ID = "butchercraft:execution_handler/patty_former_player_operation";
    public static final String CONFIGURATION_IDENTITY =
            "butchercraft:execution_configuration/patty_former_player_operation_v1";
    public static final Set<ResourceLocation> PROMOTED_PATTY_FORMER_OPERATIONS = Set.of(
            BuiltInDefinitionIds.FORM_BEEF_PATTIES
    );

    private PattyFormerExecutionConstants() {
    }
}
