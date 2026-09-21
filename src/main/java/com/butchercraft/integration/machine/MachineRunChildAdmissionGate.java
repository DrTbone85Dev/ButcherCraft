package com.butchercraft.integration.machine;

import com.butchercraft.world.execution.MachineRunRecord;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;

/** Read-only intent boundary consulted before a powered machine prepares another bounded child. */
@FunctionalInterface
public interface MachineRunChildAdmissionGate {
    MachineRunChildAdmissionGate ALLOW_ALL = (server, run) -> true;

    boolean mayAdmit(MinecraftServer server, MachineRunRecord run);

    static MachineRunChildAdmissionGate require(MachineRunChildAdmissionGate gate) {
        return Objects.requireNonNull(gate, "gate");
    }
}
