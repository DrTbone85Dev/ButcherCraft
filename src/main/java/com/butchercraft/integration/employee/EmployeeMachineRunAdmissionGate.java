package com.butchercraft.integration.employee;

import com.butchercraft.integration.machine.MachineRunChildAdmissionGate;
import com.butchercraft.world.execution.MachineRunRecord;
import net.minecraft.server.MinecraftServer;

/** Allows Execution-owned child dispatch to observe the finite Workforce assignment boundary. */
public final class EmployeeMachineRunAdmissionGate implements MachineRunChildAdmissionGate {
    public static final EmployeeMachineRunAdmissionGate INSTANCE = new EmployeeMachineRunAdmissionGate();

    private EmployeeMachineRunAdmissionGate() {
    }

    @Override
    public boolean mayAdmit(MinecraftServer server, MachineRunRecord run) {
        return EmployeePersistentMachineOperationService.INSTANCE.mayAdmitChild(server, run);
    }
}
