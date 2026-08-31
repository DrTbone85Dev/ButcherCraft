package com.butchercraft.world.checkpoint;

import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class StartupMutationGateService {
    public static final StartupMutationGateService INSTANCE = new StartupMutationGateService();
    private static final RecoveryMutationGate OPEN_GATE = new RecoveryMutationGate(1, false, List.of(), List.of());

    private final AtomicReference<InstalledGate> installed = new AtomicReference<>();

    private StartupMutationGateService() {
    }

    public void begin(MinecraftServer server) {
        installed.set(new InstalledGate(Objects.requireNonNull(server, "server"), false, OPEN_GATE));
    }

    public void install(MinecraftServer server, RecoveryMutationGate gate) {
        installed.set(new InstalledGate(
                Objects.requireNonNull(server, "server"),
                true,
                Objects.requireNonNull(gate, "gate")
        ));
    }

    public void clear(MinecraftServer server) {
        InstalledGate current = installed.get();
        if (current != null && current.server() == server) installed.compareAndSet(current, null);
    }

    public boolean permits(MinecraftServer server, CheckpointOwnerId ownerId) {
        InstalledGate current = installed.get();
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerId, "ownerId");
        if (current == null || current.server() != server) return true;
        return current.startupComplete() && current.gate().permitsConsequentialMutation(ownerId);
    }

    public boolean startupDecisionComplete(MinecraftServer server) {
        InstalledGate current = installed.get();
        return current != null && current.server() == Objects.requireNonNull(server, "server")
                && current.startupComplete();
    }

    public void require(MinecraftServer server, CheckpointOwnerId ownerId) {
        if (!permits(server, ownerId)) {
            throw new IllegalStateException("Consequential mutation is blocked by startup recovery authority: "
                    + ownerId.value());
        }
    }

    public Optional<RecoveryMutationGate> currentGate(MinecraftServer server) {
        InstalledGate current = installed.get();
        return current != null && current.server() == server && current.startupComplete()
                ? Optional.of(current.gate()) : Optional.empty();
    }

    private record InstalledGate(
            MinecraftServer server,
            boolean startupComplete,
            RecoveryMutationGate gate
    ) {
    }
}
