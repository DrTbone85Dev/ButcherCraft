package com.butchercraft.workstation.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.persistence.AtomicFilePublication;
import com.butchercraft.workstation.block.AbstractInventoryWorkstationBlockEntity;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.workstation.projection.DurableWorkstationProjectionService;
import com.butchercraft.workstation.projection.FrozenWorkstationProjectionSnapshot;
import com.butchercraft.workstation.projection.WorkstationProjectionCodec;
import com.butchercraft.workstation.projection.WorkstationProjectionReadCode;
import com.butchercraft.workstation.projection.WorkstationProjectionReadResult;
import com.butchercraft.workstation.projection.WorkstationProjectionStorage;
import com.butchercraft.workstation.projection.WorkstationProjectionStatus;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.Arrays;

/** Workstation-owned serialization of loaded block-entity projections needed for later reconciliation. */
public final class WorkstationCheckpointProjectionService {
    public static final int SCHEMA_VERSION = 2;
    public static final int LEGACY_SCHEMA_VERSION = 1;
    private static final Gson GSON = StrictJsonPersistence.gson();
    private static final WorkstationProjectionCodec PROJECTION_CODEC = new WorkstationProjectionCodec();

    private WorkstationCheckpointProjectionService() {
    }

    public static WorkstationCheckpointProjectionSnapshot capture(
            MinecraftServer server,
            WorkstationInstanceRegistry instances
    ) {
        return capture(server, instances, List.of());
    }

    public static WorkstationCheckpointProjectionSnapshot capture(
            MinecraftServer server,
            WorkstationInstanceRegistry instances,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(instances, "instances");
        return capture(instances, dependencies, new ProjectionAccess() {
            @Override
            public WorkstationProjectionReadResult read(WorkstationInstanceRecord instance) {
                return DurableWorkstationProjectionService.INSTANCE
                        .readWithLoadedValidation(server, instance.instanceId());
            }

            @Override
            public FrozenWorkstationProjectionSnapshot freeze(WorkstationInstanceRecord instance) {
                return DurableWorkstationProjectionService.INSTANCE
                        .freezeForCheckpoint(server, instance.instanceId());
            }

            @Override
            public boolean loaded(WorkstationInstanceRecord instance) {
                return isLoaded(server, instance);
            }
        });
    }

    static WorkstationCheckpointProjectionSnapshot capture(
            WorkstationInstanceRegistry instances,
            List<WorkstationCheckpointDependency> dependencies,
            ProjectionAccess projectionAccess
    ) {
        Objects.requireNonNull(instances, "instances");
        Objects.requireNonNull(projectionAccess, "projectionAccess");
        long requiredStarted = System.nanoTime();
        RequiredSet required = requiredSet(instances, dependencies);
        long requiredNanos = System.nanoTime() - requiredStarted;

        long freezeStarted = System.nanoTime();
        JsonArray entries = new JsonArray();
        List<WorkstationCheckpointBlocker> blockers = new ArrayList<>(required.blockers());
        int loaded = 0;
        int unloaded = 0;
        for (Requirement requirement : required.requirements()) {
            WorkstationInstanceRecord instance = requirement.instance();
            WorkstationProjectionReadResult read = projectionAccess.read(instance);
            if (!acceptable(instance, read)) {
                blockers.add(new WorkstationCheckpointBlocker(
                        instance.instanceId(), read.code(), read.detail()));
                continue;
            }
            try {
                FrozenWorkstationProjectionSnapshot frozen = projectionAccess.freeze(instance);
                DurableWorkstationProjection decoded = PROJECTION_CODEC.decode(frozen.frozenBytes());
                DurableWorkstationProjection authoritative = read.projection().orElseThrow();
                if (!decoded.equals(authoritative)
                        || !frozen.instanceId().equals(instance.instanceId())
                        || frozen.projectionRevision() != authoritative.projectionRevision()
                        || !frozen.stateDigest().equals(authoritative.stateDigest())
                        || !decoded.worldIdentity().equals(instances.worldIdentity())
                        || decoded.instanceGeneration() != instance.generation()
                        || !decoded.endpointKey().equals(instance.endpointKey())) {
                    blockers.add(new WorkstationCheckpointBlocker(
                            instance.instanceId(), WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                            "Frozen projection does not bind the exact registry and durable projection state"));
                    continue;
                }
                boolean loadedNow = projectionAccess.loaded(instance);
                if (loadedNow) loaded++; else unloaded++;
                entries.add(entry(instance, requirement.dependencies(), frozen, decoded, loadedNow));
            } catch (RuntimeException exception) {
                blockers.add(new WorkstationCheckpointBlocker(
                        instance.instanceId(), WorkstationProjectionReadCode.CORRUPT,
                        exception.getMessage() == null
                                ? "Durable projection could not be frozen and verified"
                                : exception.getMessage()));
            }
        }
        long freezeNanos = System.nanoTime() - freezeStarted;

        long serializationStarted = System.nanoTime();
        WorkstationCheckpointCompletenessStatus status = completeness(blockers);
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("workstation_restorable_status", status.serializedName());
        root.addProperty("workstation_instance_registry_revision", instances.ownerRevision());
        root.addProperty("required_projection_count", required.requirements().size());
        root.addProperty("available_projection_count", entries.size());
        root.addProperty("loaded_projection_count", loaded);
        root.addProperty("unloaded_projection_count", unloaded);
        root.addProperty("projection_collection_digest", CheckpointSnapshotDigest.sha256(
                GSON.toJson(entries).getBytes(StandardCharsets.UTF_8)));
        root.add("required_projections", entries);
        JsonArray encodedBlockers = new JsonArray();
        blockers.stream().sorted().forEach(blocker -> {
            JsonObject encoded = new JsonObject();
            encoded.addProperty("instance_id", blocker.instanceId().value());
            encoded.addProperty("projection_state", blocker.projectionState().name().toLowerCase(Locale.ROOT));
            encoded.addProperty("detail", blocker.detail());
            encodedBlockers.add(encoded);
        });
        root.add("blockers", encodedBlockers);
        String json = GSON.toJson(root) + "\n";
        long serializationNanos = System.nanoTime() - serializationStarted;
        return new WorkstationCheckpointProjectionSnapshot(
                json,
                status,
                required.requirements().size(),
                entries.size(),
                loaded,
                unloaded,
                json.getBytes(StandardCharsets.UTF_8).length,
                requiredNanos,
                freezeNanos,
                serializationNanos,
                blockers
        );
    }

    public static List<WorkstationInstanceId> requiredInstanceIds(
            WorkstationInstanceRegistry instances,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        return requiredSet(
                Objects.requireNonNull(instances, "instances"),
                Objects.requireNonNull(dependencies, "dependencies")
        ).requirements().stream().map(requirement -> requirement.instance().instanceId()).toList();
    }

    /** Freezes already durable projections without loading chunks or starting a world runtime. */
    public static WorkstationCheckpointProjectionSnapshot captureOffline(
            WorkstationInstanceRegistry instances,
            List<WorkstationCheckpointDependency> dependencies,
            WorkstationProjectionStorage storage
    ) {
        Objects.requireNonNull(storage, "storage");
        return capture(instances, dependencies, new ProjectionAccess() {
            @Override
            public WorkstationProjectionReadResult read(WorkstationInstanceRecord instance) {
                return storage.read(instance);
            }

            @Override
            public FrozenWorkstationProjectionSnapshot freeze(WorkstationInstanceRecord instance) {
                return storage.freezeForCheckpoint(instance);
            }

            @Override
            public boolean loaded(WorkstationInstanceRecord instance) {
                return false;
            }
        });
    }

    private static RequiredSet requiredSet(
            WorkstationInstanceRegistry instances,
            List<WorkstationCheckpointDependency> suppliedDependencies
    ) {
        Map<WorkstationInstanceId, List<WorkstationCheckpointDependency>> dependencies = new TreeMap<>();
        instances.records().stream()
                .filter(instance -> instance.lifecycle() != WorkstationInstanceLifecycle.RETIRED)
                .forEach(instance -> dependencies.computeIfAbsent(instance.instanceId(), ignored -> new ArrayList<>())
                        .add(new WorkstationCheckpointDependency(
                                instance.instanceId(),
                                WorkstationCheckpointDependencyCategory.INSTANCE_LIFECYCLE,
                                "workstation_instance:" + instance.lifecycle().name().toLowerCase(Locale.ROOT))));
        Objects.requireNonNull(suppliedDependencies, "dependencies").stream().sorted().forEach(dependency ->
                dependencies.computeIfAbsent(dependency.instanceId(), ignored -> new ArrayList<>()).add(dependency));
        instances.records().stream()
                .filter(instance -> !instance.unresolvedJournalReferences().isEmpty())
                .forEach(instance -> instance.unresolvedJournalReferences().forEach(reference ->
                        dependencies.computeIfAbsent(instance.instanceId(), ignored -> new ArrayList<>())
                                .add(new WorkstationCheckpointDependency(
                                        instance.instanceId(),
                                        WorkstationCheckpointDependencyCategory.OWNER_RECOVERY_REFERENCE,
                                        reference))));

        List<Requirement> requirements = new ArrayList<>();
        List<WorkstationCheckpointBlocker> blockers = new ArrayList<>();
        dependencies.forEach((identity, reasons) -> instances.find(identity).ifPresentOrElse(
                instance -> requirements.add(new Requirement(instance, reasons)),
                () -> blockers.add(new WorkstationCheckpointBlocker(
                        identity,
                        WorkstationProjectionReadCode.IDENTITY_CONFLICT,
                        "Durable owner dependency references an unknown Workstation Instance Identity"))));
        return new RequiredSet(requirements, blockers);
    }

    private static boolean acceptable(
            WorkstationInstanceRecord instance,
            WorkstationProjectionReadResult read
    ) {
        if (instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED) {
            return read.code() == WorkstationProjectionReadCode.RETIRED;
        }
        return read.code() == WorkstationProjectionReadCode.AVAILABLE;
    }

    private static boolean isLoaded(MinecraftServer server, WorkstationInstanceRecord instance) {
        ResourceLocation dimension = ResourceLocation.tryParse(instance.endpointKey().dimensionIdentity());
        if (dimension == null) return false;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        return level != null && level.hasChunkAt(position(instance));
    }

    private static JsonObject entry(
            WorkstationInstanceRecord instance,
            List<WorkstationCheckpointDependency> dependencies,
            FrozenWorkstationProjectionSnapshot frozen,
            DurableWorkstationProjection projection,
            boolean loaded
    ) {
        byte[] payload = frozen.frozenBytes();
        JsonObject record = identity(instance);
        record.addProperty("projection_schema", projection.schemaVersion());
        record.addProperty("projection_revision", frozen.projectionRevision());
        record.addProperty("projection_state_digest", frozen.stateDigest());
        record.addProperty("payload_length", payload.length);
        record.addProperty("payload_digest", CheckpointSnapshotDigest.sha256(payload));
        record.addProperty("payload_base64", Base64.getEncoder().encodeToString(payload));
        record.addProperty("loaded_at_freeze", loaded);
        JsonArray reasons = new JsonArray();
        dependencies.stream().sorted().distinct().forEach(dependency -> {
            JsonObject reason = new JsonObject();
            reason.addProperty("category", dependency.category().serializedName());
            reason.addProperty("evidence_identity", dependency.evidenceIdentity());
            reasons.add(reason);
        });
        record.add("required_by", reasons);
        return record;
    }

    private static WorkstationCheckpointCompletenessStatus completeness(
            List<WorkstationCheckpointBlocker> blockers
    ) {
        if (blockers.isEmpty()) return WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE;
        if (blockers.stream().anyMatch(blocker -> blocker.projectionState()
                == WorkstationProjectionReadCode.IDENTITY_CONFLICT)) {
            return WorkstationCheckpointCompletenessStatus.CONFLICT;
        }
        if (blockers.stream().anyMatch(blocker -> blocker.projectionState()
                == WorkstationProjectionReadCode.UNSUPPORTED_SCHEMA)) {
            return WorkstationCheckpointCompletenessStatus.UNSUPPORTED;
        }
        if (blockers.stream().anyMatch(blocker -> blocker.projectionState()
                == WorkstationProjectionReadCode.RECOVERY_REQUIRED)) {
            return WorkstationCheckpointCompletenessStatus.RECOVERY_REQUIRED;
        }
        return WorkstationCheckpointCompletenessStatus.INCOMPLETE_REQUIRED_PROJECTION;
    }

    private record Requirement(
            WorkstationInstanceRecord instance,
            List<WorkstationCheckpointDependency> dependencies
    ) implements Comparable<Requirement> {
        private Requirement {
            instance = Objects.requireNonNull(instance, "instance");
            dependencies = Objects.requireNonNull(dependencies, "dependencies").stream()
                    .sorted().distinct().toList();
        }

        @Override
        public int compareTo(Requirement other) {
            return instance.compareTo(other.instance);
        }
    }

    private record RequiredSet(
            List<Requirement> requirements,
            List<WorkstationCheckpointBlocker> blockers
    ) {
        private RequiredSet {
            requirements = Objects.requireNonNull(requirements, "requirements").stream().sorted().toList();
            blockers = Objects.requireNonNull(blockers, "blockers").stream().sorted().toList();
        }
    }

    interface ProjectionAccess {
        WorkstationProjectionReadResult read(WorkstationInstanceRecord instance);

        FrozenWorkstationProjectionSnapshot freeze(WorkstationInstanceRecord instance);

        boolean loaded(WorkstationInstanceRecord instance);
    }

    public static void restore(
            MinecraftServer server,
            OwnerNativeRestorationContext context,
            OwnerNativeRestorationPlan plan
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(plan, "plan");
        WorkstationInstanceRegistry instances = parseInstances(plan);
        if (plan.workstationProjection().isEmpty()) {
            throw new IllegalArgumentException(
                    "R4 Workstation restoration requires schema-2 durable projection evidence");
        }
        JsonObject root = JsonParser.parseString(new String(
                plan.workstationProjection().orElseThrow(), StandardCharsets.UTF_8)).getAsJsonObject();
        List<RestorationProjection> projections = restorationProjections(context, instances, root);
        WorkstationProjectionStorage storage = projectionStorage(context.ownerRoot());
        for (RestorationProjection projection : projections) {
            byte[] observed = AtomicFilePublication.readBytes(
                    storage.pathFor(projection.instance().instanceId()),
                    "restored durable Workstation projection");
            if (!Arrays.equals(observed, projection.frozenBytes())) {
                throw new IllegalStateException(
                        "Restored durable Workstation projection differs from frozen checkpoint bytes");
            }
            WorkstationProjectionReadResult read = storage.read(projection.instance());
            WorkstationProjectionReadCode expectedCode = projection.instance().lifecycle()
                    == WorkstationInstanceLifecycle.RETIRED
                    ? WorkstationProjectionReadCode.RETIRED : WorkstationProjectionReadCode.AVAILABLE;
            if (read.code() != expectedCode
                    || !read.projection().orElseThrow().equals(projection.projection())) {
                throw new IllegalStateException(
                        "Restored durable Workstation projection failed owner-native verification");
            }
        }
    }

    public static List<OwnerNativeRestorationPlan.NativeFile> prepareRestorationProjectionFiles(
            OwnerNativeRestorationContext context,
            WorkstationInstanceRegistry instances,
            byte[] projectionEvidence
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(instances, "instances");
        JsonObject root = JsonParser.parseString(new String(
                Objects.requireNonNull(projectionEvidence, "projectionEvidence"), StandardCharsets.UTF_8))
                .getAsJsonObject();
        WorkstationProjectionStorage storage = projectionStorage(context.ownerRoot());
        return restorationProjections(context, instances, root).stream()
                .map(projection -> {
                    Path target = storage.pathFor(projection.instance().instanceId());
                    String relative = context.ownerRoot().toAbsolutePath().normalize().relativize(target)
                            .toString().replace('\\', '/');
                    return OwnerNativeRestorationPlan.NativeFile.of(
                            "workstation_projection/" + projection.instance().instanceId().value(),
                            relative,
                            projection.frozenBytes());
                })
                .toList();
    }

    private static List<RestorationProjection> restorationProjections(
            OwnerNativeRestorationContext context,
            WorkstationInstanceRegistry instances,
            JsonObject root
    ) {
        int schema = root.get("schema_version").getAsInt();
        if (schema != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "R4 cannot restore non-schema-2 Workstation projection evidence: " + schema);
        }
        if (!WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE.serializedName().equals(
                root.get("workstation_restorable_status").getAsString())
                || root.get("workstation_instance_registry_revision").getAsLong() != instances.ownerRevision()
                || !root.getAsJsonArray("blockers").isEmpty()) {
            throw new IllegalArgumentException("Workstation projection evidence is not complete-restorable");
        }
        JsonArray entries = root.getAsJsonArray("required_projections");
        if (root.get("required_projection_count").getAsInt() != entries.size()
                || root.get("available_projection_count").getAsInt() != entries.size()
                || root.get("loaded_projection_count").getAsInt()
                + root.get("unloaded_projection_count").getAsInt() != entries.size()
                || !CheckpointSnapshotDigest.sha256(GSON.toJson(entries).getBytes(StandardCharsets.UTF_8))
                .equals(root.get("projection_collection_digest").getAsString())) {
            throw new IllegalArgumentException("Workstation projection completeness evidence is inconsistent");
        }
        Set<WorkstationInstanceId> represented = new HashSet<>();
        List<RestorationProjection> projections = new ArrayList<>();
        for (JsonElement element : entries) {
            JsonObject record = element.getAsJsonObject();
            WorkstationInstanceRecord instance = requireInstance(instances, record, represented);
            byte[] frozen;
            try {
                frozen = Base64.getDecoder().decode(record.get("payload_base64").getAsString());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Workstation projection payload is not valid base64", exception);
            }
            if (frozen.length != record.get("payload_length").getAsInt()
                    || !CheckpointSnapshotDigest.sha256(frozen).equals(record.get("payload_digest").getAsString())) {
                throw new IllegalArgumentException("Workstation projection frozen payload digest mismatch");
            }
            DurableWorkstationProjection projection = PROJECTION_CODEC.decode(frozen);
            if (!projection.instanceId().equals(instance.instanceId())
                    || projection.instanceGeneration() != instance.generation()
                    || !projection.endpointKey().equals(instance.endpointKey())
                    || projection.projectionRevision() != record.get("projection_revision").getAsLong()
                    || projection.schemaVersion() != record.get("projection_schema").getAsInt()
                    || !projection.stateDigest().equals(record.get("projection_state_digest").getAsString())
                    || !projection.worldIdentity().identity().equals(context.worldIdentityRoot().identity())
                    || projection.worldIdentity().schemaVersion() != context.worldIdentityRoot().schemaVersion()
                    || !projection.worldIdentity().rootDigest().equals(context.worldIdentityRoot().rootDigest())) {
                throw new IllegalArgumentException(
                        "Workstation projection payload differs from exact instance or World Identity authority");
            }
            boolean retired = instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED;
            if (retired != (projection.status() == WorkstationProjectionStatus.TOMBSTONED)) {
                throw new IllegalArgumentException(
                        "Workstation projection lifecycle differs from retirement/tombstone authority");
            }
            projections.add(new RestorationProjection(instance, projection, frozen));
        }
        Set<WorkstationInstanceId> requiredActive = instances.records().stream()
                .filter(WorkstationCheckpointProjectionService::requiresProjectionEvidence)
                .map(WorkstationInstanceRecord::instanceId)
                .collect(java.util.stream.Collectors.toSet());
        if (!represented.containsAll(requiredActive)) {
            throw new IllegalArgumentException("Workstation projection omits an active instance");
        }
        return projections.stream().sorted(Comparator.comparing(value -> value.instance().instanceId())).toList();
    }

    private static WorkstationProjectionStorage projectionStorage(Path ownerRoot) {
        return new WorkstationProjectionStorage(ownerRoot.toAbsolutePath().normalize()
                .resolve(com.butchercraft.workstation.projection.WorkstationProjectionSchema.DIRECTORY_NAME)
                .resolve(com.butchercraft.workstation.projection.WorkstationProjectionSchema.PROJECTION_DIRECTORY_NAME)
                .resolve(com.butchercraft.workstation.projection.WorkstationProjectionSchema.SCHEMA_DIRECTORY_NAME));
    }

    private record RestorationProjection(
            WorkstationInstanceRecord instance,
            DurableWorkstationProjection projection,
            byte[] frozenBytes
    ) {
        private RestorationProjection {
            frozenBytes = frozenBytes.clone();
        }

        @Override
        public byte[] frozenBytes() {
            return frozenBytes.clone();
        }
    }

    private static void captureInstance(
            MinecraftServer server,
            WorkstationInstanceRecord instance,
            JsonArray loaded,
            JsonArray unavailable
    ) {
        ResourceLocation dimension = ResourceLocation.tryParse(instance.endpointKey().dimensionIdentity());
        if (dimension == null) {
            unavailable.add(unavailable(instance, "invalid_dimension_identity"));
            return;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        BlockPos position = new BlockPos(
                instance.endpointKey().x(),
                instance.endpointKey().y(),
                instance.endpointKey().z()
        );
        if (level == null) {
            unavailable.add(unavailable(instance, "dimension_unavailable"));
            return;
        }
        if (!level.isLoaded(position)) {
            unavailable.add(unavailable(instance, "chunk_unloaded"));
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof AbstractInventoryWorkstationBlockEntity workstation)) {
            unavailable.add(unavailable(instance, "workstation_projection_unavailable"));
            return;
        }
        if (workstation.checkpointInstanceIdentity().filter(instance.instanceId()::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != instance.generation()) {
            unavailable.add(unavailable(instance, "instance_identity_mismatch"));
            return;
        }

        CompoundTag projection = workstation.checkpointProjectionSnapshot(level.registryAccess());
        JsonElement canonicalProjection = canonicalTag(projection);
        byte[] projectionBytes = GSON.toJson(canonicalProjection).getBytes(StandardCharsets.UTF_8);
        JsonObject record = identity(instance);
        ResourceLocation blockEntityType = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        record.addProperty("block_entity_type", Objects.requireNonNull(blockEntityType, "blockEntityType").toString());
        record.addProperty("projection_digest", CheckpointSnapshotDigest.sha256(projectionBytes));
        record.add("projection", canonicalProjection);
        loaded.add(record);
    }

    private static JsonObject unavailable(WorkstationInstanceRecord instance, String reason) {
        JsonObject record = identity(instance);
        record.addProperty("reason", reason);
        return record;
    }

    private static JsonObject identity(WorkstationInstanceRecord instance) {
        JsonObject record = new JsonObject();
        record.addProperty("instance_id", instance.instanceId().value());
        record.addProperty("instance_generation", instance.generation());
        record.addProperty("workstation_type", instance.endpointKey().workstationTypeIdentity());
        record.addProperty("dimension", instance.endpointKey().dimensionIdentity());
        record.addProperty("x", instance.endpointKey().x());
        record.addProperty("y", instance.endpointKey().y());
        record.addProperty("z", instance.endpointKey().z());
        record.addProperty("lifecycle", instance.lifecycle().name());
        return record;
    }

    private static boolean requiresProjectionEvidence(WorkstationInstanceRecord instance) {
        return instance.lifecycle() != WorkstationInstanceLifecycle.RETIRED;
    }

    private static WorkstationInstanceRegistry parseInstances(OwnerNativeRestorationPlan plan) {
        OwnerNativeRestorationPlan.NativeFile file = plan.nativeFiles().stream()
                .filter(value -> value.logicalName().equals("workstation_instances.json"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Workstation instance authority is absent"));
        return new WorkstationInstanceStorage(Path.of("workstation_instances.json"))
                .deserialize(new String(file.bytes(), StandardCharsets.UTF_8));
    }

    private static WorkstationInstanceRecord requireInstance(
            WorkstationInstanceRegistry instances,
            JsonObject record,
            Set<WorkstationInstanceId> represented
    ) {
        WorkstationInstanceId identity = new WorkstationInstanceId(record.get("instance_id").getAsString());
        WorkstationInstanceRecord instance = instances.find(identity)
                .orElseThrow(() -> new IllegalArgumentException("Projection references unknown Workstation instance"));
        if (!represented.add(identity)
                || record.get("instance_generation").getAsLong() != instance.generation()
                || !record.get("workstation_type").getAsString().equals(instance.endpointKey().workstationTypeIdentity())
                || !record.get("dimension").getAsString().equals(instance.endpointKey().dimensionIdentity())
                || record.get("x").getAsInt() != instance.endpointKey().x()
                || record.get("y").getAsInt() != instance.endpointKey().y()
                || record.get("z").getAsInt() != instance.endpointKey().z()
                || !record.get("lifecycle").getAsString().equals(instance.lifecycle().name())) {
            throw new IllegalArgumentException("Workstation projection identity binding mismatch");
        }
        return instance;
    }

    private static ServerLevel requireLevel(MinecraftServer server, WorkstationInstanceRecord instance) {
        ResourceLocation dimension = ResourceLocation.tryParse(instance.endpointKey().dimensionIdentity());
        if (dimension == null) throw new IllegalArgumentException("Invalid Workstation dimension identity");
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) throw new IllegalStateException("Checkpointed Workstation dimension is unavailable");
        return level;
    }

    private static BlockPos position(WorkstationInstanceRecord instance) {
        return new BlockPos(instance.endpointKey().x(), instance.endpointKey().y(), instance.endpointKey().z());
    }

    private static void validateLoadedLegacyInstances(
            MinecraftServer server,
            WorkstationInstanceRegistry instances
    ) {
        instances.records().stream().filter(WorkstationCheckpointProjectionService::requiresProjectionEvidence)
                .forEach(instance -> {
                    ServerLevel level = requireLevel(server, instance);
                    BlockPos position = position(instance);
                    if (!level.isLoaded(position)) return;
                    BlockEntity blockEntity = level.getBlockEntity(position);
                    if (!(blockEntity instanceof AbstractInventoryWorkstationBlockEntity workstation)) {
                        throw new IllegalStateException("Legacy recovery Workstation block entity is unavailable");
                    }
                    requireLiveIdentity(workstation, instance);
                });
    }

    private static void requireLiveIdentity(
            AbstractInventoryWorkstationBlockEntity workstation,
            WorkstationInstanceRecord instance
    ) {
        if (workstation.checkpointInstanceIdentity().filter(instance.instanceId()::equals).isEmpty()
                || workstation.checkpointInstanceGeneration() != instance.generation()) {
            throw new IllegalStateException("Live Workstation projection differs from instance authority");
        }
    }

    private static CompoundTag decodeCompound(JsonElement encoded) {
        Tag decoded = decodeTag(encoded.getAsJsonObject());
        if (!(decoded instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("Workstation checkpoint projection is not a compound tag");
        }
        return compound;
    }

    private static Tag decodeTag(JsonObject encoded) {
        int type = encoded.get("tag_type").getAsInt();
        if (type == Tag.TAG_COMPOUND) {
            CompoundTag compound = new CompoundTag();
            encoded.getAsJsonObject("value").entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> compound.put(entry.getKey(), decodeTag(entry.getValue().getAsJsonObject())));
            return compound;
        }
        if (type == Tag.TAG_LIST) {
            ListTag list = new ListTag();
            encoded.getAsJsonArray("value").forEach(value -> list.add(decodeTag(value.getAsJsonObject())));
            return list;
        }
        if (type == Tag.TAG_BYTE_ARRAY) {
            List<Byte> values = new ArrayList<>();
            encoded.getAsJsonArray("value").forEach(value ->
                    values.add(((ByteTag) decodeTag(value.getAsJsonObject())).getAsByte()));
            byte[] array = new byte[values.size()];
            for (int index = 0; index < values.size(); index++) array[index] = values.get(index);
            return new ByteArrayTag(array);
        }
        if (type == Tag.TAG_INT_ARRAY) {
            List<Integer> values = new ArrayList<>();
            encoded.getAsJsonArray("value").forEach(value ->
                    values.add(((IntTag) decodeTag(value.getAsJsonObject())).getAsInt()));
            return new IntArrayTag(values);
        }
        if (type == Tag.TAG_LONG_ARRAY) {
            List<Long> values = new ArrayList<>();
            encoded.getAsJsonArray("value").forEach(value ->
                    values.add(((LongTag) decodeTag(value.getAsJsonObject())).getAsLong()));
            return new LongArrayTag(values);
        }
        try {
            CompoundTag wrapper = TagParser.parseTag("{value:" + encoded.get("snbt").getAsString() + "}");
            Tag value = wrapper.get("value");
            if (value == null || value.getId() != type) {
                throw new IllegalArgumentException("Workstation projection tag type mismatch");
            }
            return value;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalArgumentException("Invalid Workstation checkpoint projection tag", exception);
        }
    }

    private static JsonElement canonicalTag(Tag tag) {
        JsonObject encoded = new JsonObject();
        encoded.addProperty("tag_type", tag.getId());
        if (tag instanceof CompoundTag compound) {
            JsonObject values = new JsonObject();
            compound.getAllKeys().stream().sorted().forEach(key ->
                    values.add(key, canonicalTag(Objects.requireNonNull(compound.get(key), "compoundTagValue"))));
            encoded.add("value", values);
        } else if (tag instanceof CollectionTag<?> collection) {
            JsonArray values = new JsonArray();
            for (Tag value : collection) {
                values.add(canonicalTag(value));
            }
            encoded.add("value", values);
        } else {
            encoded.addProperty("snbt", tag.toString());
        }
        return encoded;
    }
}
