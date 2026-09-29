package com.butchercraft.integration.machine;

import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.machine.pattyformer.PattyFormerWorkstation;
import com.butchercraft.workstation.condition.MachineConditionPolicy;
import com.butchercraft.workstation.condition.MachineConditionPolicyRegistry;
import java.util.List;
import java.util.Map;

/** Content composition only. IM-033A deliberately activates no mechanical consequence. */
public final class MachineConditionPolicies {
    private MachineConditionPolicies() { }

    public static MachineConditionPolicyRegistry standard() {
        MachineConditionPolicy grinder = MachineConditionPolicy.inert(GrinderWorkstation.ID.toString());
        MachineConditionPolicy former = MachineConditionPolicy.inert(PattyFormerWorkstation.ID.toString());
        return new MachineConditionPolicyRegistry(List.of(grinder, former), Map.of(
                grinder.machineType(), grinder.identity(), former.machineType(), former.identity()));
    }
}
