package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.workstation.condition.*;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Nonzero content exists only in the dedicated GameTest composition; production defaults remain inert. */
@EventBusSubscriber(modid = ButcherCraft.MOD_ID)
public final class MachineConditionGameTestPolicies {
    public static final MachineConditionPolicy GRINDER = testPolicy("butchercraft:grinder");
    public static final MachineConditionPolicy PATTY = testPolicy("butchercraft:patty_former");
    public static final MachineConditionPolicy FAULT = MachineConditionPolicy.create("butchercraft:grinder",
            "butchercraft:test_condition/v1/fault", 10_000, 3, 1, 5, 10, 8_000, Optional.of(10L),
            List.of(new ConditionExposurePolicy(ConditionExposureType.DRY_RUNNING, 6, 2, 5, 20, true)));

    private MachineConditionGameTestPolicies() { }

    private static MachineConditionPolicy testPolicy(String type) {
        return MachineConditionPolicy.create(type, "butchercraft:test_condition/v1/nonzero", 10_000,
                3, 1, 5, 10, 8_000, Optional.empty(),
                List.of(new ConditionExposurePolicy(ConditionExposureType.DRY_RUNNING, 6, 2, 5, 20, true)));
    }

    @SubscribeEvent
    public static void configure(ServerAboutToStartEvent event) {
        if (!(event.getServer() instanceof GameTestServer)) return;
        var grinder = MachineConditionPolicy.inert("butchercraft:grinder");
        var patty = MachineConditionPolicy.inert("butchercraft:patty_former");
        DurableWorkstationProjectionService.INSTANCE.installConditionPolicies(new MachineConditionPolicyRegistry(
                List.of(grinder, patty, GRINDER, PATTY, FAULT),
                Map.of(grinder.machineType(), grinder.identity(), patty.machineType(), patty.identity())));
    }
}
