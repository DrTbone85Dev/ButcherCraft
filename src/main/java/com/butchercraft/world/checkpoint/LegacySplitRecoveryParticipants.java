package com.butchercraft.world.checkpoint;

import java.util.List;

public final class LegacySplitRecoveryParticipants {
    public static final CheckpointOwnerId EXECUTION = CheckpointOwnerId.of("butchercraft:execution");
    public static final CheckpointOwnerId WORKSTATION = CheckpointOwnerId.of("butchercraft:workstation");
    public static final CheckpointOwnerId MATERIAL_HANDLING = CheckpointOwnerId.of("butchercraft:material_handling");
    public static final CheckpointOwnerId PLANNING = CheckpointOwnerId.of("butchercraft:planning");
    public static final CheckpointOwnerId PRODUCTION = CheckpointOwnerId.of("butchercraft:production");
    public static final CheckpointOwnerId TRANSACTIONS = CheckpointOwnerId.of("butchercraft:transactions");
    public static final CheckpointOwnerId INVENTORY = CheckpointOwnerId.of("butchercraft:inventory");
    public static final CheckpointOwnerId BUSINESS_RUNTIME = CheckpointOwnerId.of("butchercraft:business_runtime");
    public static final CheckpointOwnerId WORKFORCE = CheckpointOwnerId.of("butchercraft:workforce");
    public static final CheckpointOwnerId GOODS = CheckpointOwnerId.of("butchercraft:goods");
    public static final CheckpointOwnerId ECONOMIC_ACTORS = CheckpointOwnerId.of("butchercraft:economic_actors");
    public static final CheckpointOwnerId ORDERS = CheckpointOwnerId.of("butchercraft:orders");
    public static final CheckpointOwnerId CONTRACTS = CheckpointOwnerId.of("butchercraft:contracts");
    public static final CheckpointOwnerId PLAYER_IDENTITY = CheckpointOwnerId.of("butchercraft:player_identity");
    public static final CheckpointOwnerId CHECKPOINT_RECOVERY = CheckpointOwnerId.of("butchercraft:checkpoint_recovery");

    public static final List<CheckpointOwnerId> REQUIRED_R2_OWNERS = List.of(
            CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
            CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER,
            EXECUTION,
            WORKSTATION,
            MATERIAL_HANDLING,
            PLANNING,
            PRODUCTION,
            TRANSACTIONS,
            INVENTORY,
            BUSINESS_RUNTIME,
            WORKFORCE,
            GOODS,
            ECONOMIC_ACTORS,
            ORDERS,
            CONTRACTS,
            PLAYER_IDENTITY,
            CHECKPOINT_RECOVERY
    ).stream().sorted().toList();

    private LegacySplitRecoveryParticipants() {
    }
}
