package com.butchercraft.integration.checkpoint;

import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileBundleCodec;
import com.butchercraft.world.checkpoint.CheckpointOwnerFileSnapshot;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.LegacyRecoverySourceBundle;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationAdapter;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.butchercraft.world.checkpoint.RestorationAdapterSupport;
import com.butchercraft.world.checkpoint.RestorationSource;
import com.butchercraft.world.checkpoint.RecoveryMutationGate;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class FileBundleNativeRestorationAdapter implements OwnerNativeRestorationAdapter {
    private final CheckpointOwnerId ownerId;
    private final Set<Integer> supportedOwnerSchemas;
    private final List<String> legacySourceFiles;
    private final Set<String> virtualFiles;
    private final NativeTransform transform;
    private final NativeValidation validation;
    private final ProjectionReconciler projectionReconciler;
    private final NativeFileAugmentor nativeFileAugmentor;
    private final RestorationMetadataExtractor metadataExtractor;
    private final boolean legacySourceRequired;

    FileBundleNativeRestorationAdapter(
            CheckpointOwnerId ownerId,
            Set<Integer> supportedOwnerSchemas,
            List<String> legacySourceFiles,
            Set<String> virtualFiles,
            boolean legacySourceRequired,
            NativeTransform transform,
            NativeValidation validation
    ) {
        this(
                ownerId,
                supportedOwnerSchemas,
                legacySourceFiles,
                virtualFiles,
                legacySourceRequired,
                transform,
                validation,
                (context, state) -> RestorationMetadata.open(),
                (context, plan) -> { },
                (context, state) -> List.of()
        );
    }

    FileBundleNativeRestorationAdapter(
            CheckpointOwnerId ownerId,
            Set<Integer> supportedOwnerSchemas,
            List<String> legacySourceFiles,
            Set<String> virtualFiles,
            boolean legacySourceRequired,
            NativeTransform transform,
            NativeValidation validation,
            RestorationMetadataExtractor metadataExtractor,
            ProjectionReconciler projectionReconciler
    ) {
        this(
                ownerId, supportedOwnerSchemas, legacySourceFiles, virtualFiles, legacySourceRequired,
                transform, validation, metadataExtractor, projectionReconciler,
                (context, state) -> List.of()
        );
    }

    FileBundleNativeRestorationAdapter(
            CheckpointOwnerId ownerId,
            Set<Integer> supportedOwnerSchemas,
            List<String> legacySourceFiles,
            Set<String> virtualFiles,
            boolean legacySourceRequired,
            NativeTransform transform,
            NativeValidation validation,
            RestorationMetadataExtractor metadataExtractor,
            ProjectionReconciler projectionReconciler,
            NativeFileAugmentor nativeFileAugmentor
    ) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.supportedOwnerSchemas = Set.copyOf(Objects.requireNonNull(
                supportedOwnerSchemas, "supportedOwnerSchemas"));
        this.legacySourceFiles = List.copyOf(Objects.requireNonNull(legacySourceFiles, "legacySourceFiles"));
        this.virtualFiles = Set.copyOf(Objects.requireNonNull(virtualFiles, "virtualFiles"));
        this.legacySourceRequired = legacySourceRequired;
        this.transform = Objects.requireNonNull(transform, "transform");
        this.validation = Objects.requireNonNull(validation, "validation");
        this.metadataExtractor = Objects.requireNonNull(metadataExtractor, "metadataExtractor");
        this.projectionReconciler = Objects.requireNonNull(projectionReconciler, "projectionReconciler");
        this.nativeFileAugmentor = Objects.requireNonNull(nativeFileAugmentor, "nativeFileAugmentor");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return ownerId;
    }

    @Override
    public OwnerNativeRestorationPlan prepare(
            OwnerNativeRestorationContext context,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        var descriptor = RestorationAdapterSupport.requireSnapshot(context, ownerId, snapshot);
        int ownerSchema;
        Map<String, byte[]> sourceFiles = new LinkedHashMap<>();
        Optional<byte[]> projection = Optional.empty();
        Optional<CheckpointOwnerFileSnapshot> fileBundle = currentFileBundle(context, snapshot);
        if (fileBundle.isPresent()) {
            CheckpointOwnerFileSnapshot bundle = fileBundle.orElseThrow();
            if (!bundle.ownerId().equals(ownerId)) {
                throw new IllegalArgumentException("Checkpoint file bundle belongs to another owner");
            }
            requireExactBundleFiles(bundle);
            ownerSchema = bundle.ownerSchemaVersion();
            for (CheckpointOwnerFileSnapshot.FilePayload file : bundle.files()) {
                if (file.logicalName().equals("workstation_projections.json")) {
                    projection = Optional.of(file.bytes());
                } else if (!virtualFiles.contains(file.logicalName())) {
                    sourceFiles.put(file.logicalName(), file.bytes());
                }
            }
        } else {
            RestorationAdapterSupport.requireLegacyDocument(context, ownerId, snapshot);
            if (legacySourceRequired) {
                var source = RestorationAdapterSupport.requireLegacySource(context, ownerId);
                ownerSchema = source.ownerSchemaVersion();
                List<LegacyRecoverySourceBundle.SourceFile> files =
                        RestorationAdapterSupport.readAndVerifyLegacySource(
                                context, ownerId, legacySourceFiles);
                files.forEach(file -> sourceFiles.put(file.logicalName(), file.bytes()));
            } else {
                ownerSchema = descriptor.snapshotSchemaVersion();
            }
        }
        requireSupportedSchema(ownerSchema);
        PreparedNativeState prepared = transform.apply(
                context,
                new PreparedNativeState(ownerSchema, sourceFiles, projection)
        );
        requireSupportedSchema(prepared.ownerSchemaVersion());
        validateGenericJson(context, prepared.files());
        validation.validate(context, prepared);
        RestorationMetadata metadata = metadataExtractor.extract(context, prepared);
        List<OwnerNativeRestorationPlan.NativeFile> nativeFiles = new java.util.ArrayList<>(prepared.files().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> OwnerNativeRestorationPlan.NativeFile.of(
                        entry.getKey(), entry.getKey(), entry.getValue()))
                .toList());
        nativeFiles.addAll(nativeFileAugmentor.augment(context, prepared));
        return OwnerNativeRestorationPlan.create(
                descriptor,
                prepared.ownerSchemaVersion(),
                nativeFiles,
                prepared.workstationProjection(),
                metadata.mutationGate(),
                metadata.policyBRunIdentities()
        );
    }

    private void requireExactBundleFiles(CheckpointOwnerFileSnapshot bundle) {
        Set<String> expected = new java.util.TreeSet<>(legacySourceFiles);
        expected.addAll(virtualFiles);
        Set<String> actual = bundle.files().stream()
                .map(CheckpointOwnerFileSnapshot.FilePayload::logicalName)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("Checkpoint owner file set differs for "
                    + ownerId.value() + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private Optional<CheckpointOwnerFileSnapshot> currentFileBundle(
            OwnerNativeRestorationContext context,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        if (context.source() == RestorationSource.CHECKPOINT) {
            return Optional.of(CheckpointOwnerFileBundleCodec.decode(snapshot.payloadBytes()));
        }
        if (virtualFiles.isEmpty()) return Optional.empty();
        try {
            CheckpointOwnerFileSnapshot bundle = CheckpointOwnerFileBundleCodec.decode(snapshot.payloadBytes());
            return bundle.ownerId().equals(ownerId) ? Optional.of(bundle) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        plan.nativeFiles().forEach(file -> files.put(file.logicalName(), file.bytes()));
        PreparedNativeState prepared = new PreparedNativeState(
                plan.ownerSchemaVersion(), files, plan.workstationProjection());
        requireSupportedSchema(prepared.ownerSchemaVersion());
        validateGenericJson(context, prepared.files());
        validation.validate(context, prepared);
        RestorationMetadata metadata = metadataExtractor.extract(context, prepared);
        if (!metadata.mutationGate().equals(plan.mutationGate())
                || !metadata.policyBRunIdentities().equals(plan.policyBRunIdentities())) {
            throw new IllegalArgumentException("Owner restoration metadata differs from the frozen plan");
        }
    }

    @Override
    public void reconcileProjection(
            OwnerNativeRestorationContext context,
            OwnerNativeRestorationPlan plan
    ) {
        projectionReconciler.reconcile(context, plan);
    }

    private void requireSupportedSchema(int schema) {
        if (!supportedOwnerSchemas.contains(schema)) {
            throw new IllegalArgumentException("Unsupported native owner schema for "
                    + ownerId.value() + ": " + schema);
        }
    }

    private void validateGenericJson(OwnerNativeRestorationContext context, Map<String, byte[]> files) {
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            try {
                JsonObject root = JsonParser.parseString(
                        new String(entry.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonElement schema = root.get("schema_version");
                if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Native owner file omits schema_version");
                }
                validateWorldReferences(root, context.worldIdentityRoot());
            } catch (RuntimeException exception) {
                throw StrictJsonPersistence.corrupt(
                        "native restoration file " + ownerId.value() + "/" + entry.getKey(), exception);
            }
        }
    }

    static void validateWorldReferences(JsonElement value, WorldIdentityRootReference expected) {
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(child -> validateWorldReferences(child, expected));
            return;
        }
        if (!value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getKey().equals("world_identity") && entry.getValue().isJsonObject()) {
                JsonObject identity = entry.getValue().getAsJsonObject();
                if (identity.has("identity")
                        && !identity.get("identity").getAsString()
                        .equals(expected.identity())) {
                    throw new IllegalArgumentException("Native owner file crosses World Identity");
                }
                if (identity.has("root_digest")
                        && !identity.get("root_digest").getAsString()
                        .equals(expected.rootDigest())) {
                    throw new IllegalArgumentException("Native owner file crosses World Identity digest");
                }
            }
            if (entry.getKey().equals("world_identity_root") && entry.getValue().isJsonPrimitive()
                    && !entry.getValue().getAsString().equals(expected.identity())) {
                throw new IllegalArgumentException("Native owner record crosses World Identity");
            }
            if (entry.getKey().equals("world_identity_root") && entry.getValue().isJsonObject()) {
                JsonObject identity = entry.getValue().getAsJsonObject();
                if (identity.has("identity")
                        && !identity.get("identity").getAsString().equals(expected.identity())) {
                    throw new IllegalArgumentException("Native owner record crosses World Identity");
                }
                if (identity.has("root_digest")
                        && !identity.get("root_digest").getAsString().equals(expected.rootDigest())) {
                    throw new IllegalArgumentException("Native owner record crosses World Identity digest");
                }
            }
            if (entry.getKey().equals("world_identity_root_digest") && entry.getValue().isJsonPrimitive()
                    && !entry.getValue().getAsString().equals(expected.rootDigest())) {
                throw new IllegalArgumentException("Native owner record crosses World Identity digest");
            }
            validateWorldReferences(entry.getValue(), expected);
        }
    }

    record PreparedNativeState(
            int ownerSchemaVersion,
            Map<String, byte[]> files,
            Optional<byte[]> workstationProjection
    ) {
        PreparedNativeState {
            files = Map.copyOf(Objects.requireNonNull(files, "files"));
            workstationProjection = Objects.requireNonNull(
                    workstationProjection, "workstationProjection").map(byte[]::clone);
        }

        @Override
        public Optional<byte[]> workstationProjection() {
            return workstationProjection.map(byte[]::clone);
        }
    }

    @FunctionalInterface
    interface NativeTransform {
        PreparedNativeState apply(OwnerNativeRestorationContext context, PreparedNativeState source);
    }

    @FunctionalInterface
    interface NativeValidation {
        void validate(OwnerNativeRestorationContext context, PreparedNativeState state);
    }

    @FunctionalInterface
    interface ProjectionReconciler {
        void reconcile(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan);
    }

    @FunctionalInterface
    interface NativeFileAugmentor {
        List<OwnerNativeRestorationPlan.NativeFile> augment(
                OwnerNativeRestorationContext context,
                PreparedNativeState state
        );
    }

    @FunctionalInterface
    interface RestorationMetadataExtractor {
        RestorationMetadata extract(OwnerNativeRestorationContext context, PreparedNativeState state);
    }

    record RestorationMetadata(
            RecoveryMutationGate mutationGate,
            List<String> policyBRunIdentities
    ) {
        private static final RecoveryMutationGate OPEN_GATE =
                new RecoveryMutationGate(1, false, List.of(), List.of());

        RestorationMetadata {
            mutationGate = Objects.requireNonNull(mutationGate, "mutationGate");
            policyBRunIdentities = Objects.requireNonNull(policyBRunIdentities, "policyBRunIdentities")
                    .stream().distinct().sorted().toList();
        }

        static RestorationMetadata open() {
            return new RestorationMetadata(OPEN_GATE, List.of());
        }
    }
}
