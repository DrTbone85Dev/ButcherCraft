package com.butchercraft.integration.machine;

import com.butchercraft.workstation.operation.MachineOperatingState;

import java.util.Objects;

public final class MachineRunPresentation {
    private MachineRunPresentation() {
    }

    public static String machineStatusKey(String machinePath, MachineOperatingState state) {
        return prefix(machinePath) + ".machine_state." + Objects.requireNonNull(state, "state").serializedName();
    }

    public static String cycleStatusKey(String machinePath, MachineOperatingState state, boolean activeChild) {
        String prefix = prefix(machinePath) + ".cycle_state.";
        if (displaysActiveCycle(state, activeChild)) return prefix + "processing";
        return prefix + switch (state) {
            case OFF -> "idle";
            case STARTING -> "starting";
            case RUNNING -> "waiting_next_cycle";
            case RUNNING_EMPTY -> "waiting_input";
            case OUTPUT_BLOCKED -> "waiting_output_space";
            case STOPPING -> "stopping";
            case RESTART_REQUIRED -> "waiting_operator";
            case FAULTED -> "faulted";
            case RECOVERY_REQUIRED -> "recovery_required";
        };
    }

    public static int cycleProgressPercent(
            MachineOperatingState state,
            boolean activeChild,
            int controllerProgressPercent
    ) {
        return displaysActiveCycle(state, activeChild)
                ? Math.max(0, Math.min(100, controllerProgressPercent))
                : 0;
    }

    private static boolean displaysActiveCycle(MachineOperatingState state, boolean activeChild) {
        return activeChild && (state == MachineOperatingState.RUNNING || state == MachineOperatingState.STOPPING);
    }

    private static String prefix(String machinePath) {
        return "screen.butchercraft." + Objects.requireNonNull(machinePath, "machinePath");
    }
}
