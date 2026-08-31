package com.butchercraft.workstation.projection;

import com.butchercraft.integration.materialhandling.ExactItemStackCodec;
import com.butchercraft.persistence.StrictJsonPersistence;
import com.butchercraft.workstation.WorkstationSlotCapacityPolicy;
import com.butchercraft.workstation.WorkstationStackMutationPlan;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointDependency;
import com.butchercraft.workstation.checkpoint.WorkstationCheckpointProjectionService;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournal;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalRecord;
import com.butchercraft.workstation.endpoint.WorkstationEndpointJournalState;
import com.butchercraft.workstation.endpoint.WorkstationEndpointResultCode;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRecord;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalStorage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationEndpointJournalV2Storage;
import com.butchercraft.workstation.endpoint.persistence.WorkstationInstanceStorage;
import com.butchercraft.workstation.operation.MachineOperatingRecord;
import com.butchercraft.workstation.operation.MachineOperatingRegistry;
import com.butchercraft.workstation.operation.persistence.MachineOperatingStorage;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Workstation-owned proof construction for pre-R3A physical endpoint state. */
public final class LegacyWorkstationProjectionAnalyzer {
    public static final String PROVENANCE = "LEGACY_BOOTSTRAP_FROM_PROVEN_STATE";
    private static final Gson GSON = StrictJsonPersistence.gson();
    private static final WorkstationProjectionCodec PROJECTION_CODEC = new WorkstationProjectionCodec();
    private static final ExactItemStackCodec STACK_CODEC = new ExactItemStackCodec();

    private final OfflineWorkstationChunkEvidenceReader chunkReader;
    private final CapacityPolicyResolver capacityPolicyResolver;

    public LegacyWorkstationProjectionAnalyzer(
            OfflineWorkstationChunkEvidenceReader chunkReader,
            CapacityPolicyResolver capacityPolicyResolver
    ) {
        this.chunkReader = Objects.requireNonNull(chunkReader, "chunkReader");
        this.capacityPolicyResolver = Objects.requireNonNull(capacityPolicyResolver, "capacityPolicyResolver");
    }

    public LegacyWorkstationProjectionAnalysis analyze(
            Path worldRoot,
            HolderLookup.Provider registries,
            List<WorkstationCheckpointDependency> dependencies
    ) {
        long started = System.nanoTime();
        Path root = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        Path ownerRoot = root.resolve("butchercraft");
        WorkstationInstanceRegistry instances = new WorkstationInstanceStorage(
                ownerRoot.resolve("workstation_instances.json")).loadExisting().orElseThrow(() ->
                new IllegalArgumentException("Historical Workstation instance registry is absent"));
        OwnerEvidence owners = loadOwnerEvidence(ownerRoot, instances);
        WorkstationProjectionStorage storage = projectionStorage(ownerRoot);
        List<WorkstationInstanceId> required = WorkstationCheckpointProjectionService.requiredInstanceIds(
                instances, Objects.requireNonNull(dependencies, "dependencies"));
        List<LegacyWorkstationProjectionAnalysis.Entry> entries = new ArrayList<>();
        for (WorkstationInstanceId instanceId : required) {
            WorkstationInstanceRecord instance = instances.find(instanceId).orElseThrow();
            WorkstationProjectionReadResult existing = storage.read(instance);
            if (existing.code() == WorkstationProjectionReadCode.AVAILABLE
                    || existing.code() == WorkstationProjectionReadCode.RETIRED) {
                entries.add(entry(instanceId, LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE,
                        "Exact durable R3A projection already exists"));
                continue;
            }
            if (existing.code() != WorkstationProjectionReadCode.LEGACY_UNAVAILABLE
                    && existing.code() != WorkstationProjectionReadCode.RECOVERY_REQUIRED) {
                entries.add(entry(instanceId, classification(existing.code()), existing.detail()));
                continue;
            }
            if (instance.lifecycle() == WorkstationInstanceLifecycle.RETIRED) {
                entries.add(entry(instanceId, LegacyWorkstationProjectionClassification.RETIRED_PROVEN,
                        "Workstation Instance authority proves retirement; no active state was bootstrapped"));
                continue;
            }
            entries.add(analyzeInstance(root, registries, instances, instance, owners));
        }
        String digest = analysisDigest(instances, entries);
        return new LegacyWorkstationProjectionAnalysis(
                LegacyWorkstationProjectionAnalysis.SCHEMA_VERSION,
                instances.worldIdentity(),
                instances.ownerRevision(),
                entries,
                digest,
                System.nanoTime() - started
        );
    }

    public static WorkstationProjectionStorage projectionStorage(Path ownerRoot) {
        return new WorkstationProjectionStorage(ownerRoot.resolve(WorkstationProjectionSchema.DIRECTORY_NAME)
                .resolve(WorkstationProjectionSchema.PROJECTION_DIRECTORY_NAME)
                .resolve(WorkstationProjectionSchema.SCHEMA_DIRECTORY_NAME));
    }

    private LegacyWorkstationProjectionAnalysis.Entry analyzeInstance(
            Path root,
            HolderLookup.Provider registries,
            WorkstationInstanceRegistry instances,
            WorkstationInstanceRecord instance,
            OwnerEvidence owners
    ) {
        try {
            Optional<OfflineWorkstationBlockEntityEvidence> found = chunkReader.read(root, instance);
            if (found.isEmpty()) {
                return entry(instance.instanceId(), LegacyWorkstationProjectionClassification.MISSING_PHYSICAL_EVIDENCE,
                        "No exact block entity exists at the active Workstation endpoint");
            }
            OfflineWorkstationBlockEntityEvidence physical = found.orElseThrow();
            if (!physical.instanceId().equals(instance.instanceId())
                    || physical.instanceGeneration() != instance.generation()
                    || !physical.endpointKey().equals(instance.endpointKey())
                    || !physical.blockEntityTypeIdentity().equals(instance.endpointKey().workstationTypeIdentity())) {
                return entry(instance.instanceId(), LegacyWorkstationProjectionClassification.IDENTITY_CONFLICT,
                        "Physical block entity does not match exact World/type/position/instance/generation authority");
            }
            CompoundTag blockEntity = physical.blockEntityNbt();
            CompoundTag endpoint = requireCompound(blockEntity, "TransferEndpointProjection");
            if (endpoint.contains("PreparedEffectIdentity", Tag.TAG_STRING)
                    || endpoint.contains("StackAwarePreparedEffectIdentity", Tag.TAG_STRING)) {
                return entry(instance.instanceId(), LegacyWorkstationProjectionClassification.UNRESOLVED_EFFECT,
                        "Physical endpoint retains a prepared consequential effect");
            }
            List<String> ownerEvidence = new ArrayList<>();
            EndpointBinding endpointBinding = reconcileEndpoint(instance, endpoint, owners, ownerEvidence);
            ProcessingBinding processing = reconcileProcessing(instance, blockEntity, owners, ownerEvidence);
            Optional<WorkstationOperatingStateReference> operating = reconcileOperating(
                    instance, owners, ownerEvidence);
            ownerEvidence.addAll(owners.materialEvidenceByInstance().getOrDefault(instance.instanceId(), List.of()));

            CompoundTag reconstructedProjection = physical.projectionNbt();
            CompoundTag reconstructedEndpoint = reconstructedProjection.getCompound("TransferEndpointProjection");
            endpointBinding.effectIdentity().ifPresent(value ->
                    reconstructedEndpoint.putString("StackAwareLastEffectIdentity", value));
            endpointBinding.ownerResultIdentity().ifPresent(value ->
                    reconstructedEndpoint.putString("StackAwareLastOwnerResultIdentity", value));
            reconstructedProjection.put("TransferEndpointProjection", reconstructedEndpoint);

            WorkstationSlotCapacityPolicy capacity = capacityPolicyResolver
                    .resolve(instance.endpointKey().workstationTypeIdentity())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unsupported legacy Workstation type: "
                                    + instance.endpointKey().workstationTypeIdentity()));
            List<WorkstationProjectionSlot> slots = slots(registries, blockEntity, capacity);
            DurableWorkstationProjection projection = DurableWorkstationProjection.active(
                    instances.worldIdentity(),
                    instance.instanceId(),
                    instance.endpointKey(),
                    instance.generation(),
                    instance.allocationConfigurationIdentity(),
                    physical.blockEntityTypeIdentity(),
                    instances.ownerRevision(),
                    1L,
                    endpoint.getLong("InventoryRevision"),
                    endpoint.getLong("EndpointEffectRevision"),
                    endpoint.getLong("LastAppliedJournalSequence"),
                    capacity.configurationIdentity(),
                    slots,
                    PROJECTION_CODEC.encodeBlockEntityProjection(reconstructedProjection),
                    Optional.empty(),
                    endpointBinding.effectIdentity(),
                    endpointBinding.ownerResultIdentity(),
                    processing.operationIdentity(),
                    processing.ownerResultIdentity(),
                    operating
            );
            List<String> evidence = ownerEvidence.stream().distinct().sorted().toList();
            String candidateDigest = candidateDigest(projection, physical.physicalEvidenceDigest(), evidence);
            LegacyWorkstationProjectionAnalysis.Candidate candidate =
                    new LegacyWorkstationProjectionAnalysis.Candidate(
                            projection, physical.physicalEvidenceDigest(), evidence, PROVENANCE, candidateDigest);
            return new LegacyWorkstationProjectionAnalysis.Entry(
                    instance.instanceId(), LegacyWorkstationProjectionClassification.PROOF_COMPLETE,
                    "Exact physical state and every applicable owner consequence are reconciled", Optional.of(candidate));
        } catch (UnsupportedWorkstationProjectionSchemaException exception) {
            return entry(instance.instanceId(), LegacyWorkstationProjectionClassification.UNSUPPORTED_SCHEMA,
                    detail(exception));
        } catch (IllegalArgumentException exception) {
            LegacyWorkstationProjectionClassification classification = exception.getMessage() != null
                    && exception.getMessage().contains("UNKNOWN_OUTCOME")
                    ? LegacyWorkstationProjectionClassification.UNKNOWN_OUTCOME
                    : LegacyWorkstationProjectionClassification.CORRUPT_EVIDENCE;
            return entry(instance.instanceId(), classification, detail(exception));
        } catch (RuntimeException exception) {
            return entry(instance.instanceId(), LegacyWorkstationProjectionClassification.RECOVERY_REQUIRED,
                    detail(exception));
        }
    }

    private EndpointBinding reconcileEndpoint(
            WorkstationInstanceRecord instance,
            CompoundTag endpoint,
            OwnerEvidence owners,
            List<String> evidence
    ) {
        List<WorkstationEndpointJournalRecord> records = owners.legacyJournal().records().stream()
                .filter(record -> record.instanceId().equals(instance.instanceId())).toList();
        if (records.isEmpty()) {
            if (endpoint.getLong("EndpointEffectRevision") != 0L
                    || endpoint.getLong("LastAppliedJournalSequence") != 0L
                    || endpoint.contains("LastEffectIdentity", Tag.TAG_STRING)
                    || endpoint.contains("LastOwnerResultIdentity", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Physical endpoint references owner effects absent from its journal");
            }
            return new EndpointBinding(Optional.empty(), Optional.empty());
        }
        for (WorkstationEndpointJournalRecord record : records) {
            if (record.state() != WorkstationEndpointJournalState.RECONCILED || record.ownerResult().isEmpty()) {
                throw new IllegalArgumentException("Unresolved consequential Workstation endpoint effect");
            }
            if (record.ownerResult().orElseThrow().resultCode() != WorkstationEndpointResultCode.APPLIED) {
                throw new IllegalArgumentException("Endpoint effect has a non-applied terminal owner result");
            }
        }
        WorkstationEndpointJournalRecord latest = records.getLast();
        String physicalEffect = endpoint.contains("StackAwareLastEffectIdentity", Tag.TAG_STRING)
                ? endpoint.getString("StackAwareLastEffectIdentity")
                : endpoint.getString("LastEffectIdentity");
        String physicalResult = endpoint.contains("StackAwareLastOwnerResultIdentity", Tag.TAG_STRING)
                ? endpoint.getString("StackAwareLastOwnerResultIdentity")
                : endpoint.getString("LastOwnerResultIdentity");
        if (endpoint.getLong("EndpointEffectRevision") != latest.endpointEffectRevision()
                || endpoint.getLong("LastAppliedJournalSequence") != latest.journalSequence()
                || (!physicalEffect.isEmpty() && !physicalEffect.equals(latest.effectId().value()))
                || (!physicalResult.isEmpty()
                && !physicalResult.equals(latest.ownerResult().orElseThrow().evidenceIdentity()))
                || endpoint.getLong("InventoryRevision") < latest.postInventoryRevision()) {
            throw new IllegalArgumentException("Physical endpoint revisions contradict immutable endpoint owner evidence: "
                    + "physical inventory=" + endpoint.getLong("InventoryRevision")
                    + ", endpoint=" + endpoint.getLong("EndpointEffectRevision")
                    + ", journal=" + endpoint.getLong("LastAppliedJournalSequence")
                    + ", effect=" + physicalEffect + ", result=" + physicalResult
                    + "; owner inventory=" + latest.postInventoryRevision()
                    + ", endpoint=" + latest.endpointEffectRevision()
                    + ", journal=" + latest.journalSequence()
                    + ", effect=" + latest.effectId().value()
                    + ", result=" + latest.ownerResult().orElseThrow().evidenceIdentity());
        }
        records.forEach(record -> {
            evidence.add(record.effectId().value());
            evidence.add(record.ownerResult().orElseThrow().evidenceIdentity());
        });
        return new EndpointBinding(
                Optional.of(latest.effectId().value()),
                Optional.of(latest.ownerResult().orElseThrow().evidenceIdentity())
        );
    }

    private ProcessingBinding reconcileProcessing(
            WorkstationInstanceRecord instance,
            CompoundTag blockEntity,
            OwnerEvidence owners,
            List<String> evidence
    ) {
        CompoundTag controller = blockEntity.contains("Controller", Tag.TAG_COMPOUND)
                ? blockEntity.getCompound("Controller") : new CompoundTag();
        Optional<String> operation = optionalString(controller, "ActiveExecutionOperation");
        Optional<String> ownerResult = optionalString(controller, "OwnerResultIdentity");
        if (operation.isEmpty() != ownerResult.isEmpty()) {
            throw new IllegalArgumentException("Processing operation and owner-result bindings are incomplete");
        }
        if (operation.isEmpty()) return new ProcessingBinding(operation, ownerResult);
        ExecutionOwnerResult persisted = owners.executionResults().get(operation.orElseThrow());
        if (persisted == null
                || !"succeeded".equalsIgnoreCase(persisted.status())
                || !ownerResult.orElseThrow().equals(persisted.ownerResultIdentity())) {
            throw new IllegalArgumentException("Physical processing state contradicts immutable Execution owner result");
        }
        if (!persisted.workstationInstanceIdentities().isEmpty()
                && !persisted.workstationInstanceIdentities().contains(instance.instanceId().value())) {
            throw new IllegalArgumentException("Execution operation binds another Workstation instance");
        }
        if (persisted.workstationInstanceIdentities().isEmpty()
                && !persisted.executableWorkReference().equals(executableWorkReference(instance))) {
            throw new IllegalArgumentException("Legacy Execution operation does not bind the exact Workstation endpoint");
        }
        evidence.add(operation.orElseThrow());
        evidence.add(ownerResult.orElseThrow());
        evidence.add(persisted.contentDigest());
        return new ProcessingBinding(operation, ownerResult);
    }

    private Optional<WorkstationOperatingStateReference> reconcileOperating(
            WorkstationInstanceRecord instance,
            OwnerEvidence owners,
            List<String> evidence
    ) {
        Optional<MachineOperatingRecord> record = owners.operating().find(instance.instanceId().value());
        if (record.isEmpty()) return Optional.empty();
        MachineOperatingRecord value = record.orElseThrow();
        if (!value.workstation().endpointKey().equals(instance.endpointKey())
                || value.workstation().generation() != instance.generation()) {
            throw new IllegalArgumentException("Machine Operating State references another Workstation generation");
        }
        value.currentRunIdentity().ifPresent(run -> {
            String runId = run.value();
            if (!owners.machineRuns().contains(runId)
                    || !owners.machineRunsJson().contains(instance.instanceId().value())) {
                throw new IllegalArgumentException("Machine Run dependency is absent or identity-conflicted");
            }
            evidence.add(runId);
        });
        evidence.add(value.contentDigest());
        return Optional.of(new WorkstationOperatingStateReference(
                instance.instanceId().value(), value.revision(), value.state().name(), value.contentDigest()));
    }

    private List<WorkstationProjectionSlot> slots(
            HolderLookup.Provider registries,
            CompoundTag blockEntity,
            WorkstationSlotCapacityPolicy capacity
    ) {
        CompoundTag inventory = requireCompound(blockEntity, "Inventory");
        ItemStack[] stacks = new ItemStack[capacity.slotCount()];
        java.util.Arrays.fill(stacks, ItemStack.EMPTY);
        ListTag items = inventory.getList("Items", Tag.TAG_COMPOUND);
        Set<Integer> represented = new LinkedHashSet<>();
        for (int index = 0; index < items.size(); index++) {
            CompoundTag itemTag = items.getCompound(index);
            int slot = itemTag.getInt("Slot");
            if (slot < 0 || slot >= stacks.length || !represented.add(slot)) {
                throw new IllegalArgumentException("Physical Workstation inventory slot evidence is invalid");
            }
            stacks[slot] = ItemStack.parse(Objects.requireNonNull(registries, "registries"), itemTag)
                    .orElseThrow(() -> new IllegalArgumentException("Physical ItemStack evidence cannot be decoded"));
        }
        List<WorkstationProjectionSlot> result = new ArrayList<>();
        for (int slot = 0; slot < stacks.length; slot++) {
            ItemStack stack = stacks[slot];
            int configured = capacity.capacity(slot);
            int effective = stack.isEmpty() ? configured
                    : WorkstationStackMutationPlan.effectiveCapacity(stack, configured);
            result.add(new WorkstationProjectionSlot(slot, configured, effective,
                    stack.isEmpty() ? Optional.empty() : Optional.of(STACK_CODEC.encode(registries, stack))));
        }
        return List.copyOf(result);
    }

    private OwnerEvidence loadOwnerEvidence(Path ownerRoot, WorkstationInstanceRegistry instances) {
        Path journalPath = ownerRoot.resolve("workstation_endpoint_journal.json");
        WorkstationEndpointJournalV2Storage.LoadedJournal loaded = new WorkstationEndpointJournalV2Storage(journalPath)
                .loadVersioned().orElseThrow(() -> new IllegalArgumentException("Endpoint journal is absent"));
        WorkstationEndpointJournal legacy = switch (loaded) {
            case WorkstationEndpointJournalV2Storage.LegacyJournal value -> value.journal();
            case WorkstationEndpointJournalV2Storage.StackAwareJournal value -> value.journal()
                    .immutableLegacySchema1Journal().map(json ->
                            new WorkstationEndpointJournalStorage(journalPath).deserialize(json))
                    .orElseThrow(() -> new IllegalArgumentException("Immutable legacy endpoint journal is absent"));
        };
        if (!legacy.worldIdentity().equals(instances.worldIdentity())) {
            throw new IllegalArgumentException("Endpoint journal World Identity differs from instance authority");
        }
        MachineOperatingRegistry operating = new MachineOperatingStorage(
                ownerRoot.resolve("machine_operating_states.json")).loadExisting().orElseThrow(() ->
                new IllegalArgumentException("Machine Operating State persistence is absent"));
        if (!operating.worldIdentity().equals(instances.worldIdentity().identity())) {
            throw new IllegalArgumentException("Machine Operating State World Identity differs");
        }
        String executionsJson = read(ownerRoot.resolve("execution_operations.json"));
        Map<String, ExecutionOwnerResult> executionResults = executionResults(executionsJson);
        String runsJson = read(ownerRoot.resolve("execution_machine_runs.json"));
        Set<String> machineRuns = identities(JsonParser.parseString(runsJson).getAsJsonObject(), "runs", "run_identity");
        Map<WorkstationInstanceId, List<String>> material = materialEvidence(
                read(ownerRoot.resolve("material_handling.json")), legacy);
        return new OwnerEvidence(legacy, operating, executionResults, machineRuns, runsJson, material);
    }

    private Map<String, ExecutionOwnerResult> executionResults(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, ExecutionOwnerResult> result = new HashMap<>();
        for (JsonElement element : array(root, "operations")) {
            JsonObject operation = element.getAsJsonObject();
            String id = identity(operation.get("operation_id"));
            if (!operation.has("owner_result_evidence") || operation.get("owner_result_evidence").isJsonNull()) continue;
            JsonObject owner = operation.getAsJsonObject("owner_result_evidence");
            JsonObject authorization = operation.getAsJsonObject("authorization_evidence");
            Set<String> workstationInstances = new LinkedHashSet<>();
            for (JsonElement input : array(authorization, "explicit_input_identities")) {
                String inputIdentity = identity(input);
                if (inputIdentity.startsWith("butchercraft:workstation_instance/")) {
                    workstationInstances.add(inputIdentity);
                }
            }
            result.put(id, new ExecutionOwnerResult(
                    operation.get("status").getAsString(),
                    owner.get("owner_result_identity").getAsString(),
                    owner.get("content_digest").getAsString(),
                    authorization.get("executable_work_reference_id").getAsString(),
                    Set.copyOf(workstationInstances)
            ));
        }
        return Map.copyOf(result);
    }

    private Map<WorkstationInstanceId, List<String>> materialEvidence(
            String json,
            WorkstationEndpointJournal journal
    ) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!array(root, "transfers").isEmpty()) {
            throw new IllegalArgumentException("Active schema-2 Material Handling transfer requires recovery");
        }
        JsonObject legacy = JsonParser.parseString(root.get("immutable_legacy_schema_1_runtime").getAsString())
                .getAsJsonObject();
        Set<String> endpointResults = journal.records().stream()
                .flatMap(record -> record.ownerResult().stream())
                .map(result -> result.evidenceIdentity()).collect(java.util.stream.Collectors.toSet());
        Map<WorkstationInstanceId, List<String>> byInstance = new HashMap<>();
        for (JsonElement element : array(legacy, "transfers")) {
            JsonObject transfer = element.getAsJsonObject();
            String lifecycle = transfer.get("lifecycle").getAsString();
            if (!Set.of("COMPLETED", "CANCELLED", "FAILED").contains(lifecycle)) {
                throw new IllegalArgumentException("UNKNOWN_OUTCOME: unresolved Material Handling custody");
            }
            if (!transfer.has("terminal_evidence") || transfer.get("terminal_evidence").isJsonNull()) continue;
            JsonObject terminal = transfer.getAsJsonObject("terminal_evidence");
            for (String role : List.of("source", "destination")) {
                JsonObject endpoint = transfer.getAsJsonObject(role);
                WorkstationInstanceId id = new WorkstationInstanceId(endpoint.get("instance_identity").getAsString());
                String resultIdentity = terminal.getAsJsonObject(role + "_result")
                        .get("evidence_identity").getAsString();
                if (!endpointResults.contains(resultIdentity)) {
                    throw new IllegalArgumentException("Material Handling terminal evidence is absent from endpoint journal");
                }
                byInstance.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(List.of(
                        transfer.get("transfer_identity").getAsString(),
                        transfer.get("state_evidence_identity").getAsString(),
                        resultIdentity
                ));
            }
        }
        Map<WorkstationInstanceId, List<String>> immutable = new HashMap<>();
        byInstance.forEach((id, values) -> immutable.put(id, values.stream().distinct().sorted().toList()));
        return Map.copyOf(immutable);
    }

    private static String executableWorkReference(WorkstationInstanceRecord instance) {
        String typePath = instance.endpointKey().workstationTypeIdentity().substring(
                instance.endpointKey().workstationTypeIdentity().indexOf(':') + 1);
        String dimension = instance.endpointKey().dimensionIdentity();
        int separator = dimension.indexOf(':');
        String dimensionPath = separator < 0 ? dimension : dimension.substring(0, separator) + "/" + dimension.substring(separator + 1);
        return "butchercraft:workstation/" + typePath + "/" + dimensionPath
                + "/" + instance.endpointKey().x()
                + "/" + instance.endpointKey().y()
                + "/" + instance.endpointKey().z();
    }

    private static LegacyWorkstationProjectionAnalysis.Entry entry(
            WorkstationInstanceId id,
            LegacyWorkstationProjectionClassification classification,
            String detail
    ) {
        return new LegacyWorkstationProjectionAnalysis.Entry(id, classification, detail, Optional.empty());
    }

    private static LegacyWorkstationProjectionClassification classification(WorkstationProjectionReadCode code) {
        return switch (code) {
            case IDENTITY_CONFLICT -> LegacyWorkstationProjectionClassification.IDENTITY_CONFLICT;
            case UNSUPPORTED_SCHEMA -> LegacyWorkstationProjectionClassification.UNSUPPORTED_SCHEMA;
            case CORRUPT -> LegacyWorkstationProjectionClassification.CORRUPT_EVIDENCE;
            case RECOVERY_REQUIRED -> LegacyWorkstationProjectionClassification.RECOVERY_REQUIRED;
            case LEGACY_UNAVAILABLE -> LegacyWorkstationProjectionClassification.MISSING_PHYSICAL_EVIDENCE;
            case AVAILABLE, RETIRED -> LegacyWorkstationProjectionClassification.ALREADY_AVAILABLE;
        };
    }

    private static String analysisDigest(
            WorkstationInstanceRegistry instances,
            List<LegacyWorkstationProjectionAnalysis.Entry> entries
    ) {
        Digest digest = new Digest("butchercraft:legacy_workstation_projection_analysis/v1")
                .add(instances.worldIdentity().identity()).add(instances.worldIdentity().rootDigest())
                .add(instances.ownerRevision()).add(entries.size());
        entries.stream().sorted().forEach(entry -> {
            digest.add(entry.instanceId().value()).add(entry.classification().name()).add(entry.detail());
            entry.candidate().ifPresent(candidate -> digest.add(candidate.candidateDigest()));
        });
        return digest.finish();
    }

    private static String candidateDigest(
            DurableWorkstationProjection projection,
            String physicalDigest,
            List<String> ownerEvidence
    ) {
        Digest digest = new Digest("butchercraft:legacy_workstation_projection_candidate/v1")
                .add(projection.instanceId().value()).add(projection.stateDigest()).add(physicalDigest)
                .add(PROVENANCE).add(ownerEvidence.size());
        ownerEvidence.forEach(digest::add);
        return digest.finish();
    }

    private static Optional<String> optionalString(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? Optional.of(tag.getString(key)) : Optional.empty();
    }

    private static CompoundTag requireCompound(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Physical Workstation evidence omits " + key);
        }
        return parent.getCompound(key);
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing owner array: " + key);
        return value.getAsJsonArray();
    }

    private static Set<String> identities(JsonObject root, String key, String identityField) {
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement element : array(root, key)) {
            values.add(identity(element.getAsJsonObject().get(identityField)));
        }
        return Set.copyOf(values);
    }

    private static String identity(JsonElement value) {
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing owner identity");
        if (value.isJsonPrimitive()) return value.getAsString();
        JsonObject object = value.getAsJsonObject();
        return object.has("value") ? object.get("value").getAsString()
                : object.get("identity").getAsString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Historical owner evidence could not be read: " + path.getFileName(), exception);
        }
    }

    private static String detail(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record EndpointBinding(Optional<String> effectIdentity, Optional<String> ownerResultIdentity) {}

    private record ProcessingBinding(Optional<String> operationIdentity, Optional<String> ownerResultIdentity) {}

    private record ExecutionOwnerResult(
            String status,
            String ownerResultIdentity,
            String contentDigest,
            String executableWorkReference,
            Set<String> workstationInstanceIdentities
    ) {}

    private record OwnerEvidence(
            WorkstationEndpointJournal legacyJournal,
            MachineOperatingRegistry operating,
            Map<String, ExecutionOwnerResult> executionResults,
            Set<String> machineRuns,
            String machineRunsJson,
            Map<WorkstationInstanceId, List<String>> materialEvidenceByInstance
    ) {}

    @FunctionalInterface
    public interface CapacityPolicyResolver {
        Optional<WorkstationSlotCapacityPolicy> resolve(String workstationTypeIdentity);
    }

    private static final class Digest {
        private final MessageDigest digest;

        private Digest(String domain) {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required", exception);
            }
            add(domain);
        }

        private Digest add(String value) {
            byte[] bytes = Objects.requireNonNull(value, "digestValue").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 0);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(bytes);
            return this;
        }

        private Digest add(long value) {
            return add(Long.toString(value));
        }

        private String finish() {
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        }
    }
}
