package com.butchercraft.workstation.projection;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationProjectionStorageTest {
    private static final WorldIdentityRootIdentity WORLD = new WorldIdentityRootIdentity(
            "butchercraft:world/root/test",
            1,
            "sha256:" + "1".repeat(64)
    );
    private static final String INSTANCE_CONFIGURATION =
            "butchercraft:workstation_instance_configuration/v1/test";
    private static final String SLOT_CONFIGURATION =
            "butchercraft:workstation_slot_capacity/v1/test";

    @TempDir
    Path temporaryDirectory;

    @Test
    void exactIdentitySlotsComponentsAndCapacitiesRoundTrip() {
        WorkstationInstanceRecord instance = instance(1, 4, WorkstationInstanceLifecycle.ACTIVE);
        ItemStack named = new ItemStack(Items.STONE, 17);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lot A"));
        DurableWorkstationProjection projection = projection(
                instance, 1, 3, List.of(named, ItemStack.EMPTY, new ItemStack(Items.DIRT, 2)));
        WorkstationProjectionStorage storage = storage();

        FrozenWorkstationProjectionSnapshot frozen = storage.save(projection);
        WorkstationProjectionReadResult read = storage.read(instance);

        assertEquals(WorkstationProjectionReadCode.AVAILABLE, read.code());
        assertEquals(projection, read.projection().orElseThrow());
        assertEquals(WORLD, read.projection().orElseThrow().worldIdentity());
        assertEquals(instance.instanceId(), frozen.instanceId());
        assertEquals(List.of(0, 1, 2), read.projection().orElseThrow().slots().stream()
                .map(WorkstationProjectionSlot::slotIndex).toList());
        assertEquals(64, read.projection().orElseThrow().slots().getFirst().configuredCapacity());
        WorkstationEndpointStackPayload payload = read.projection().orElseThrow().slots().getFirst()
                .exactStack().orElseThrow();
        ItemStack decoded = new ExactItemStackCodec().decode(RegistryAccess.EMPTY, payload);
        assertEquals(17, decoded.getCount());
        assertEquals(Component.literal("Lot A"), decoded.get(DataComponents.CUSTOM_NAME));
        assertTrue(read.projection().orElseThrow().slots().get(1).exactStack().isEmpty());
    }

    @Test
    void revisionAdvancesMonotonicallyAndFrozenSnapshotIsImmutable() {
        WorkstationInstanceRecord instance = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionStorage storage = storage();
        DurableWorkstationProjection first = projection(instance, 1, 0, List.of(ItemStack.EMPTY));
        DurableWorkstationProjection second = projection(instance, 2, 1, List.of(new ItemStack(Items.STONE, 1)));
        storage.save(first);
        storage.save(second);

        FrozenWorkstationProjectionSnapshot frozen = storage.freezeForCheckpoint(instance);
        byte[] callerCopy = frozen.frozenBytes();
        callerCopy[0] ^= 0x7f;

        assertEquals(2, storage.read(instance).projection().orElseThrow().projectionRevision());
        assertNotEquals(callerCopy[0], frozen.frozenBytes()[0]);
        assertArrayEquals(storage.freezeForCheckpoint(instance).frozenBytes(), frozen.frozenBytes());
    }

    @Test
    void staleOrConflictingRevisionCannotReplaceDurableState() {
        WorkstationInstanceRecord instance = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionStorage storage = storage();
        DurableWorkstationProjection first = projection(instance, 1, 0, List.of(ItemStack.EMPTY));
        DurableWorkstationProjection second = projection(instance, 2, 1, List.of(new ItemStack(Items.STONE, 1)));
        DurableWorkstationProjection conflictingSecond = projection(
                instance, 2, 2, List.of(new ItemStack(Items.DIRT, 2)));
        storage.save(first);
        storage.save(second);

        assertThrows(IllegalStateException.class, () -> storage.save(first));
        assertThrows(IllegalStateException.class, () -> storage.save(conflictingSecond));
        assertEquals(second, storage.read(instance).projection().orElseThrow());
    }

    @Test
    void storageRestartReadsExactProjectionWithoutAnyBlockEntity() {
        WorkstationInstanceRecord instance = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        DurableWorkstationProjection expected = projection(
                instance, 3, 9, List.of(new ItemStack(Items.STONE, 7), ItemStack.EMPTY));
        storage().save(expected);

        WorkstationProjectionStorage restarted = new WorkstationProjectionStorage(
                temporaryDirectory.resolve("workstations/projections/v1"));

        assertEquals(expected, restarted.read(instance).projection().orElseThrow());
        assertArrayEquals(
                new WorkstationProjectionCodec().freeze(expected),
                restarted.freezeForCheckpoint(instance).frozenBytes());
    }

    @Test
    void replacementAtSamePositionUsesIndependentProjectionAndRevisionHistory() {
        WorkstationInstanceRecord first = instance(1, 7, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord replacement = instance(2, 7, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionStorage storage = storage();
        DurableWorkstationProjection firstProjection = projection(
                first, 9, 8, List.of(new ItemStack(Items.STONE, 8)));
        DurableWorkstationProjection replacementProjection = projection(
                replacement, 1, 0, List.of(ItemStack.EMPTY));
        storage.save(firstProjection);
        storage.save(replacementProjection);

        assertNotEquals(first.instanceId(), replacement.instanceId());
        assertNotEquals(storage.pathFor(first.instanceId()), storage.pathFor(replacement.instanceId()));
        assertEquals(9, storage.read(first).projection().orElseThrow().projectionRevision());
        assertEquals(1, storage.read(replacement).projection().orElseThrow().projectionRevision());
    }

    @Test
    void retirementPreservesPriorProjectionAndPublishesTombstone() {
        WorkstationInstanceRecord active = instance(1, 2, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord retired = new WorkstationInstanceRecord(
                active.schemaVersion(), active.instanceId(), active.worldIdentity(), active.endpointKey(),
                active.generation(), active.allocationEvidenceIdentity(), active.allocationContentDigest(),
                active.allocationConfigurationIdentity(), WorkstationInstanceLifecycle.RETIRED,
                active.creationRevision(), 3L, Optional.of("removed"), List.of());
        WorkstationProjectionStorage storage = storage();
        DurableWorkstationProjection projection = projection(
                active, 4, 2, List.of(new ItemStack(Items.STONE, 2)));
        DurableWorkstationProjection tombstone = projection.tombstone(5, retired.lastUpdateRevision(), "removed");
        storage.save(tombstone);

        WorkstationProjectionReadResult read = storage.read(retired);
        assertEquals(WorkstationProjectionReadCode.RETIRED, read.code());
        assertEquals(Optional.of(projection.stateDigest()), read.projection().orElseThrow().priorActiveProjectionDigest());
        assertEquals(2, read.projection().orElseThrow().slots().getFirst().exactStack().orElseThrow().count());
    }

    @Test
    void missingUnsupportedAndCorruptRecordsAreTypedAndIsolated() throws Exception {
        WorkstationInstanceRecord missing = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord unsupported = instance(2, 1, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord corrupt = instance(3, 2, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord healthy = instance(4, 3, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionStorage storage = storage();
        DurableWorkstationProjection unsupportedProjection = projection(unsupported, 1, 0, List.of(ItemStack.EMPTY));
        String unsupportedJson = new String(new WorkstationProjectionCodec().freeze(unsupportedProjection),
                StandardCharsets.UTF_8).replaceFirst("\\\"schema_version\\\"\\s*:\\s*1", "\"schema_version\": 999");
        Files.createDirectories(storage.pathFor(unsupported.instanceId()).getParent());
        Files.writeString(storage.pathFor(unsupported.instanceId()), unsupportedJson);
        Files.createDirectories(storage.pathFor(corrupt.instanceId()).getParent());
        Files.writeString(storage.pathFor(corrupt.instanceId()), "{not-json");
        storage.save(projection(healthy, 1, 0, List.of(ItemStack.EMPTY)));

        assertEquals(WorkstationProjectionReadCode.LEGACY_UNAVAILABLE, storage.read(missing).code());
        assertEquals(WorkstationProjectionReadCode.UNSUPPORTED_SCHEMA, storage.read(unsupported).code());
        assertEquals(WorkstationProjectionReadCode.CORRUPT, storage.read(corrupt).code());
        assertEquals(WorkstationProjectionReadCode.AVAILABLE, storage.read(healthy).code());
    }

    @Test
    void deterministicEnumerationIncludesAvailableRetiredLegacyAndBlockedStates() {
        WorkstationInstanceRecord available = instance(1, 3, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord legacy = instance(2, 1, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord recovery = instance(3, 2, WorkstationInstanceLifecycle.RECOVERY_REQUIRED);
        WorkstationProjectionStorage storage = storage();
        storage.save(projection(available, 1, 0, List.of(ItemStack.EMPTY)));
        storage.save(projection(recovery, 1, 0, List.of(ItemStack.EMPTY)));
        WorkstationInstanceRegistry registry = new WorkstationInstanceRegistry(
                1, 3, WORLD, 4, INSTANCE_CONFIGURATION, List.of(recovery, available, legacy));

        List<WorkstationProjectionReadResult> first = storage.enumerate(registry);
        List<WorkstationProjectionReadResult> second = storage.enumerate(registry);

        assertEquals(first, second);
        assertEquals(List.of(available.instanceId(), legacy.instanceId(), recovery.instanceId()).stream().sorted().toList(),
                first.stream().map(WorkstationProjectionReadResult::instanceId).toList());
        assertTrue(first.stream().anyMatch(result -> result.code() == WorkstationProjectionReadCode.AVAILABLE));
        assertTrue(first.stream().anyMatch(result -> result.code() == WorkstationProjectionReadCode.LEGACY_UNAVAILABLE));
        assertTrue(first.stream().anyMatch(result -> result.code() == WorkstationProjectionReadCode.RECOVERY_REQUIRED));
    }

    @Test
    void oneInstanceMutationDoesNotRewriteAnotherRecord() throws Exception {
        WorkstationInstanceRecord first = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationInstanceRecord second = instance(2, 1, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionStorage storage = storage();
        storage.save(projection(first, 1, 0, List.of(ItemStack.EMPTY)));
        storage.save(projection(second, 1, 0, List.of(ItemStack.EMPTY)));
        byte[] secondBefore = Files.readAllBytes(storage.pathFor(second.instanceId()));
        var secondTime = Files.getLastModifiedTime(storage.pathFor(second.instanceId()));

        storage.save(projection(first, 2, 1, List.of(new ItemStack(Items.STONE, 1))));

        assertArrayEquals(secondBefore, Files.readAllBytes(storage.pathFor(second.instanceId())));
        assertEquals(secondTime, Files.getLastModifiedTime(storage.pathFor(second.instanceId())));
    }

    @Test
    void thousandRecordScaleLookupEnumerationAndCheckpointFreezeRemainBounded() {
        assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
            WorkstationProjectionStorage storage = storage();
            List<WorkstationInstanceRecord> instances = new ArrayList<>();
            long publishStart = System.nanoTime();
            for (int index = 0; index < 1_000; index++) {
                WorkstationInstanceRecord instance = instance(index + 1L, index, WorkstationInstanceLifecycle.ACTIVE);
                instances.add(instance);
                storage.save(projection(instance, 1, index % 17,
                        List.of(index % 3 == 0 ? new ItemStack(Items.STONE, (index % 64) + 1) : ItemStack.EMPTY)));
            }
            long publishNanos = System.nanoTime() - publishStart;
            WorkstationInstanceRegistry registry = new WorkstationInstanceRegistry(
                    1, 2_000, WORLD, 1_001, INSTANCE_CONFIGURATION, instances);
            Random updates = new Random(31L);
            long updateStart = System.nanoTime();
            for (int update = 0; update < 100; update++) {
                WorkstationInstanceRecord instance = instances.get(updates.nextInt(instances.size()));
                storage.save(projection(instance, update + 2L, update + 1L,
                        List.of(new ItemStack(Items.DIRT, (update % 64) + 1))));
            }
            long updateNanos = System.nanoTime() - updateStart;
            long enumerateStart = System.nanoTime();
            List<WorkstationProjectionReadResult> enumeration = storage.enumerate(registry);
            long enumerateNanos = System.nanoTime() - enumerateStart;
            long lookupStart = System.nanoTime();
            WorkstationProjectionReadResult lookup = storage.read(instances.get(731));
            long lookupNanos = System.nanoTime() - lookupStart;
            FrozenWorkstationProjectionSnapshot frozen = storage.freezeForCheckpoint(instances.get(731));
            long totalSize = instances.stream().mapToLong(instance -> storage.size(instance.instanceId())).sum();

            assertEquals(1_000, enumeration.size());
            assertTrue(enumeration.stream().allMatch(result -> result.code() == WorkstationProjectionReadCode.AVAILABLE));
            assertEquals(WorkstationProjectionReadCode.AVAILABLE, lookup.code());
            assertFalse(frozen.frozenBytes().length == 0);
            System.out.printf("R3A scale: records=1000 totalBytes=%d avgBytes=%d publishMs=%.3f "
                            + "updateAvgMs=%.3f lookupMs=%.3f enumerateMs=%.3f%n",
                    totalSize, totalSize / 1_000, publishNanos / 1_000_000.0,
                    updateNanos / 100_000_000.0,
                    lookupNanos / 1_000_000.0, enumerateNanos / 1_000_000.0);
        });
    }

    @Test
    void concurrentDifferentInstanceWritesRemainIndependentAndLeaveNoAttemptFiles() throws Exception {
        WorkstationProjectionStorage storage = storage();
        List<WorkstationInstanceRecord> instances = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            WorkstationInstanceRecord instance = instance(index + 1L, index, WorkstationInstanceLifecycle.ACTIVE);
            instances.add(instance);
            storage.save(projection(instance, 1, 0, List.of(ItemStack.EMPTY)));
        }
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (WorkstationInstanceRecord instance : instances) {
                executor.submit(() -> {
                    for (int revision = 2; revision <= 20; revision++) {
                        storage.save(projection(instance, revision, revision,
                                List.of(new ItemStack(Items.STONE, revision))));
                    }
                });
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));
        }
        for (WorkstationInstanceRecord instance : instances) {
            assertEquals(20, storage.read(instance).projection().orElseThrow().projectionRevision());
        }
        try (var files = Files.walk(temporaryDirectory)) {
            assertTrue(files.filter(Files::isRegularFile)
                    .noneMatch(path -> path.getFileName().toString().contains(".tmp-")
                            || path.getFileName().toString().endsWith(".attempt")));
        }
    }

    @Test
    void projectionRejectsNonContiguousSlotsAndRegressedIdentity() {
        WorkstationInstanceRecord instance = instance(1, 0, WorkstationInstanceLifecycle.ACTIVE);
        WorkstationProjectionSlot slot = new WorkstationProjectionSlot(1, 64, 64, Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> DurableWorkstationProjection.active(
                WORLD, instance.instanceId(), instance.endpointKey(), instance.generation(),
                INSTANCE_CONFIGURATION, "butchercraft:grinder", instance.lastUpdateRevision(),
                1, 0, 0, 0, SLOT_CONFIGURATION, List.of(slot),
                WorkstationProjectionNbtCodec.encode(new net.minecraft.nbt.CompoundTag()),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty()));
    }

    private WorkstationProjectionStorage storage() {
        return new WorkstationProjectionStorage(temporaryDirectory.resolve("workstations/projections/v1"));
    }

    private static WorkstationInstanceRecord instance(
            long generation,
            int x,
            WorkstationInstanceLifecycle lifecycle
    ) {
        WorkstationEndpointKey key = new WorkstationEndpointKey(
                "butchercraft:grinder", "minecraft:overworld", x, 64, 0);
        WorkstationInstanceRecord pending = WorkstationInstanceRecord.pending(
                WORLD, key, generation, INSTANCE_CONFIGURATION, 1L);
        if (lifecycle == WorkstationInstanceLifecycle.PENDING_BINDING) return pending;
        return pending.transition(lifecycle, 2L,
                lifecycle == WorkstationInstanceLifecycle.ACTIVE ? Optional.empty() : Optional.of("test state"),
                lifecycle == WorkstationInstanceLifecycle.RECOVERY_REQUIRED ? List.of("test:effect") : List.of());
    }

    private static DurableWorkstationProjection projection(
            WorkstationInstanceRecord instance,
            long projectionRevision,
            long inventoryRevision,
            List<ItemStack> stacks
    ) {
        ExactItemStackCodec codec = new ExactItemStackCodec();
        List<WorkstationProjectionSlot> slots = new ArrayList<>();
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            slots.add(new WorkstationProjectionSlot(
                    index,
                    64,
                    stack.isEmpty() ? 64 : Math.min(64, stack.getMaxStackSize()),
                    stack.isEmpty() ? Optional.empty() : Optional.of(codec.encode(RegistryAccess.EMPTY, stack))));
        }
        net.minecraft.nbt.CompoundTag blockEntity = new net.minecraft.nbt.CompoundTag();
        blockEntity.putLong("InventoryRevision", inventoryRevision);
        blockEntity.putString("Fixture", instance.instanceId().value());
        return DurableWorkstationProjection.active(
                WORLD,
                instance.instanceId(),
                instance.endpointKey(),
                instance.generation(),
                INSTANCE_CONFIGURATION,
                "butchercraft:grinder",
                instance.lastUpdateRevision(),
                projectionRevision,
                inventoryRevision,
                inventoryRevision,
                inventoryRevision,
                SLOT_CONFIGURATION,
                slots,
                WorkstationProjectionNbtCodec.encode(blockEntity),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
