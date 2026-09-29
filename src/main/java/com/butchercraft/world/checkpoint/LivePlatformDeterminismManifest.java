package com.butchercraft.world.checkpoint;

import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LivePlatformDeterminismManifest {
    public static final String IDENTITY = "butchercraft:platform_determinism/live_checkpoint_v1";
    public static final int SCHEMA_VERSION = 1;
    public static final PlatformDeterminismManifestReference LEGACY_R2A_REFERENCE =
            new PlatformDeterminismManifestReference(
                    "butchercraft:platform_determinism/legacy_split_recovery_r2a_v1",
                    1,
                    "sha256:569f30f4c720ac23eefa4a2773fb6fc192d2dcd5b763aad426e11f58463b8c9b"
            );

    private LivePlatformDeterminismManifest() {
    }

    public static PlatformDeterminismManifestReference currentReference(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return reference(ExecutionService.INSTANCE.configuredHandlerRegistry(server).registryIdentity(), true);
    }

    private static PlatformDeterminismManifestReference reference(String executionRegistry, boolean conditionEnabled) {
        List<String> lines = new ArrayList<>();
        lines.add("identity=" + IDENTITY);
        lines.add("schemaVersion=" + SCHEMA_VERSION);
        lines.add("checkpointSchema=" + CheckpointSchema.CURRENT_VERSION);
        lines.add("cadenceTicks=" + LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS);
        lines.add("executionRegistry=" + executionRegistry);
        if (conditionEnabled) {
            lines.add("conditionPolicies=" + com.butchercraft.workstation.projection.DurableWorkstationProjectionService.INSTANCE.conditionPolicyRegistryIdentity());
            lines.add("conditionProjectionSchema=" + com.butchercraft.workstation.projection.WorkstationProjectionSchema.CURRENT_VERSION);
            lines.add("conditionCheckpointSchema=" + com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService.SCHEMA_VERSION);
        }
        SimulationSchedulerService.INSTANCE.configuredHandlerRegistry().handlers().stream()
                .map(handler -> "schedulerHandler=" + handler.supportedTypeId().value()
                        + "|" + handler.effectPolicy().effectType().name())
                .sorted()
                .forEach(lines::add);
        LiveCheckpointParticipantRegistry.requiredOwners().stream()
                .map(owner -> "participant=" + owner.value())
                .forEach(lines::add);
        byte[] bytes = (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
        return new PlatformDeterminismManifestReference(
                IDENTITY,
                SCHEMA_VERSION,
                CheckpointSnapshotDigest.sha256(bytes)
        );
    }

    public static List<PlatformDeterminismManifestReference> acceptedRecoveryReferences(
            MinecraftServer server
    ) {
        return List.of(currentReference(server), reference(
                com.butchercraft.world.execution.ExecutionLegacyRegistryProfiles.PRE_CONDITION_SCHEMA_1_REGISTRY_IDENTITY,
                false), LEGACY_R2A_REFERENCE);
    }
}
