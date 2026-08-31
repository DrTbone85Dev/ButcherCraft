package com.butchercraft.workstation.menu;

import com.butchercraft.workstation.operation.MachineOperatingState;
import net.minecraft.network.chat.Component;

public interface MachineRunMenuView {
    MachineOperatingState machineOperatingState();

    boolean activeChild();

    Component machineStatusComponent();

    Component cycleStatusComponent();

    int cycleProgressPercent();

    Component startControlLabel();

    Component stopControlLabel();

    Component resumeControlLabel();

    int startButtonId();

    int stopButtonId();

    int resumeButtonId();
}
