package com.butchercraft.development.checkpoint;

import com.butchercraft.machine.pattyformer.execution.PattyFormerExecutionConstants;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryAnalysisSource;
import com.butchercraft.world.checkpoint.LegacyRecoverySourceBundle;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.LegacySplitRecoverySchema;
import com.butchercraft.world.checkpoint.LegacyTempArtifactClassifier;
import com.butchercraft.world.checkpoint.LegacyTempArtifactFinding;
import com.butchercraft.world.checkpoint.MaterialHandlingRecoveryReference;
import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;
import com.butchercraft.world.checkpoint.PreservedAuthorizedWork;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;
import com.butchercraft.world.checkpoint.ReplacementWorkstationConflict;
import com.butchercraft.world.checkpoint.SplitSnapshotRecoveryAnalysisInput;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.execution.ExecutionHandlerContract;
import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAssessment;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationProof;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationSourceEvidence;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Offline-only adapter for legacy split-snapshot evidence. It reads exact owner bytes and never invokes owner
 * startup, migration, publication, or runtime services.
 */
public final class ActualWorldLegacySplitRecoverySource implements LegacySplitRecoveryAnalysisSource {
    public static final String PLATFORM_MANIFEST_IDENTITY =
            "butchercraft:platform_determinism/legacy_split_recovery_r2a_v1";
    private static final int PLATFORM_MANIFEST_SCHEMA = 1;
    private static final Set<String> CONFIGURATION_KEYS = Set.of(
            "configuration_identity",
            "handler_registry_identity",
            "endpoint_configuration_identity",
            "allocation_configuration_identity"
    );
    private static final String PLANNING_WORK_ID = "butchercraft:economic_planning_cycle/continuation";

    private final Path worldRoot;

    public ActualWorldLegacySplitRecoverySource(Path worldRoot) {
        this.worldRoot = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
    }

    @Override
    public SplitSnapshotRecoveryAnalysisInput reloadReadOnly() {
        Path ownerRoot = worldRoot.resolve("butchercraft");
        Map<String, JsonDocument> documents = readDocuments(ownerRoot);
        validateSchemas(documents);

        WorldIdentityRootReference worldIdentity = worldIdentity(documents);
        long clockTick = longValue(object(documents, "simulation_state.json"), "simulation_tick");
        long schedulerTick = longValue(object(documents, "simulation_scheduler.json"),
                "last_finalized_simulation_tick");
        validateWorldIdentityReferences(documents, worldIdentity);
        validateMaterialHandling(documents);
        validateEndpointJournal(documents);

        List<RecoverySourceSnapshot> sources = sourceSnapshots(
                documents,
                worldIdentity,
                clockTick,
                schedulerTick
        );
        Map<CheckpointOwnerId, RecoverySourceSnapshot> sourceByOwner = new HashMap<>();
        sources.forEach(source -> sourceByOwner.put(source.ownerId(), source));

        TargetRun target = targetRun(documents, schedulerTick, worldIdentity);
        RecoverySourceSnapshot executionSource = requireSource(sourceByOwner, LegacySplitRecoveryParticipants.EXECUTION);
        RecoverySourceSnapshot workstationSource = requireSource(sourceByOwner, LegacySplitRecoveryParticipants.WORKSTATION);
        List<HistoricalCoordinationAssessment> assessments = historicalAssessments(
                target,
                executionSource,
                workstationSource,
                worldIdentity
        );
        List<OrdinaryWorkReconstructionProof> ordinaryProofs = ordinaryProofs(target, assessments);
        PreservedAuthorizedWork preserved = preservedAuthorizedWork(
                target,
                executionSource,
                workstationSource
        );
        List<PlanningRecoveryAuthorityBlock> planningBlocks = planningBlocks(
                documents,
                requireSource(sourceByOwner, LegacySplitRecoveryParticipants.PLANNING),
                schedulerTick,
                clockTick
        );
        LegacyTempArtifactFinding tempFinding = new LegacyTempArtifactClassifier().classify(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                ownerRoot.resolve("simulation_state.json"),
                ownerRoot.resolve("simulation_state.json.tmp"),
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID,
                LegacyTempArtifactFinding.OwnerValidation.OWNER_VALID
        );

        RecoverySourceSnapshot clockSource = requireSource(
                sourceByOwner,
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER
        );
        RecoverySourceSnapshot schedulerSource = requireSource(
                sourceByOwner,
                CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER
        );
        return new SplitSnapshotRecoveryAnalysisInput(
                LegacySplitRecoverySchema.CURRENT_VERSION,
                worldIdentity,
                platformManifest(sources),
                sources,
                clockTick,
                schedulerTick,
                clockSource.snapshotIdentity(),
                clockSource.contentDigest(),
                schedulerSource.snapshotIdentity(),
                schedulerSource.contentDigest(),
                assessments,
                ordinaryProofs,
                List.of(preserved),
                planningBlocks,
                List.<MaterialHandlingRecoveryReference>of(),
                List.<ReplacementWorkstationConflict>of(),
                List.of(tempFinding),
                false
        );
    }

    @Override
    public List<Path> sourceRoots() {
        return List.of(worldRoot);
    }

    public Path worldRoot() {
        return worldRoot;
    }

    private Map<String, JsonDocument> readDocuments(Path ownerRoot) {
        Map<String, JsonDocument> documents = new LinkedHashMap<>();
        for (String name : allSourceFiles()) {
            Path path = ownerRoot.resolve(name);
            try {
                byte[] bytes = Files.readAllBytes(path);
                JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    throw new IllegalArgumentException("Owner persistence root is not an object: " + name);
                }
                documents.put(name, new JsonDocument(name, bytes, parsed.getAsJsonObject()));
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed read-only actual-world owner inspection: " + path, exception);
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Malformed actual-world owner persistence: " + name, exception);
            }
        }
        return Map.copyOf(documents);
    }

    private void validateSchemas(Map<String, JsonDocument> documents) {
        requireSchema(documents, "simulation_state.json", 1);
        requireSchema(documents, "simulation_scheduler.json", 2);
        requireSchema(documents, "execution_operations.json", 1);
        requireSchema(documents, "execution_machine_runs.json", 1);
        requireSchema(documents, "machine_operating_states.json", 1);
        requireSchema(documents, "workstation_instances.json", 1);
        requireSchema(documents, "workstation_endpoint_journal.json", 2);
        requireSchema(documents, "workstation_reservations.json", 1);
        requireSchema(documents, "material_handling.json", 2);
        for (String name : List.of(
                "planning_observations.json",
                "planning_needs.json",
                "planning_opportunities.json",
                "planning_candidates.json",
                "planning_approved_plans.json",
                "planning_runtime.json",
                "planning_cadence.json",
                "production_processes.json",
                "production_plans.json",
                "production_runs.json",
                "transactions.json",
                "inventory.json",
                "business_calendar_runtime.json",
                "business_runtime.json",
                "world_time.json",
                "departments.json",
                "employee_records.json",
                "employee_material_handling_assignments.json",
                "workforce_definitions.json",
                "goods.json",
                "economic_actors.json",
                "orders.json",
                "contracts.json",
                "player_identities.json"
        )) {
            requireSchema(documents, name, 1);
        }
    }

    private WorldIdentityRootReference worldIdentity(Map<String, JsonDocument> documents) {
        JsonObject root = object(object(documents, "workstation_instances.json"), "world_identity");
        return new WorldIdentityRootReference(
                string(root, "identity"),
                intValue(root, "schema_version"),
                string(root, "root_digest")
        );
    }

    private void validateWorldIdentityReferences(
            Map<String, JsonDocument> documents,
            WorldIdentityRootReference worldIdentity
    ) {
        requireWorldReference(object(documents, "execution_machine_runs.json").get("world_identity"), worldIdentity);
        requireWorldReference(object(documents, "machine_operating_states.json").get("world_identity"), worldIdentity);
        requireWorldReference(object(documents, "workstation_endpoint_journal.json").get("world_identity"), worldIdentity);
        requireWorldReference(object(documents, "workstation_instances.json").get("world_identity"), worldIdentity);
        requireWorldReference(object(documents, "material_handling.json").get("world_identity"), worldIdentity);
    }

    private List<RecoverySourceSnapshot> sourceSnapshots(
            Map<String, JsonDocument> documents,
            WorldIdentityRootReference worldIdentity,
            long clockTick,
            long schedulerTick
    ) {
        List<RecoverySourceSnapshot> sources = new ArrayList<>();
        sources.add(bundle(documents, CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER, 1, List.of(1),
                longValue(object(documents, "simulation_state.json"), "simulation_tick"), clockTick,
                worldIdentity, List.of("simulation_state.json")));
        sources.add(bundle(documents, CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER, 2, List.of(1, 2),
                longValue(object(documents, "simulation_scheduler.json"), "next_submission_sequence"), schedulerTick,
                worldIdentity, List.of("simulation_scheduler.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.EXECUTION, 1, List.of(1),
                longValue(object(documents, "execution_machine_runs.json"), "owner_revision"),
                maximumTick(array(object(documents, "execution_operations.json"), "operations"),
                        "last_updated_simulation_tick"), worldIdentity,
                List.of("execution_operations.json", "execution_machine_runs.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.WORKSTATION, 2, List.of(1, 2),
                maximum(
                        longValue(object(documents, "machine_operating_states.json"), "owner_revision"),
                        longValue(object(documents, "workstation_instances.json"), "owner_revision"),
                        longValue(object(documents, "workstation_endpoint_journal.json"), "owner_revision")
                ),
                maximumTick(array(object(documents, "machine_operating_states.json"), "records"),
                        "last_observed_simulation_tick"), worldIdentity,
                List.of(
                        // Legacy processing owner results survive as immutable copies in Execution persistence.
                        "execution_operations.json",
                        "machine_operating_states.json",
                        "workstation_endpoint_journal.json",
                        "workstation_instances.json",
                        "workstation_reservations.json"
                )));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.MATERIAL_HANDLING, 2, List.of(1, 2),
                longValue(object(documents, "material_handling.json"), "owner_revision"), 0L, worldIdentity,
                List.of("material_handling.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.PLANNING, 1, List.of(1),
                longValue(object(documents, "planning_cadence.json"), "revision"),
                longValue(object(documents, "planning_cadence.json"), "next_periodic_eligibility_tick"),
                worldIdentity, List.of(
                        "planning_observations.json",
                        "planning_needs.json",
                        "planning_opportunities.json",
                        "planning_candidates.json",
                        "planning_approved_plans.json",
                        "planning_runtime.json",
                        "planning_cadence.json"
                )));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.PRODUCTION, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("production_processes.json", "production_plans.json", "production_runs.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.TRANSACTIONS, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("transactions.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.INVENTORY, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("inventory.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.BUSINESS_RUNTIME, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("business_calendar_runtime.json", "business_runtime.json", "world_time.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.WORKFORCE, 1, List.of(1),
                longValue(object(documents, "employee_material_handling_assignments.json"), "owner_revision"),
                0L, worldIdentity, List.of(
                        "departments.json",
                        "employee_records.json",
                        "employee_material_handling_assignments.json",
                        "workforce_definitions.json"
                )));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.GOODS, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("goods.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.ECONOMIC_ACTORS, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("economic_actors.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.ORDERS, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("orders.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.CONTRACTS, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("contracts.json")));
        sources.add(bundle(documents, LegacySplitRecoveryParticipants.PLAYER_IDENTITY, 1, List.of(1), 0L, 0L,
                worldIdentity, List.of("player_identities.json")));
        return sources.stream().sorted().toList();
    }

    private RecoverySourceSnapshot bundle(
            Map<String, JsonDocument> documents,
            CheckpointOwnerId owner,
            int ownerSchema,
            List<Integer> supportedSchemas,
            long ownerRevision,
            long representedTick,
            WorldIdentityRootReference worldIdentity,
            List<String> names
    ) {
        List<JsonDocument> ordered = names.stream()
                .map(name -> requireDocument(documents, name))
                .sorted(Comparator.comparing(JsonDocument::logicalName))
                .toList();
        String contentDigest = bundleDigest(owner, ordered);
        String ownerPath = owner.value().substring(owner.value().indexOf(':') + 1);
        String snapshotIdentity = "butchercraft:legacy_source_snapshot/v1/" + ownerPath + "/"
                + contentDigest.substring("sha256:".length());
        Set<String> configurations = new HashSet<>();
        ordered.forEach(document -> collectConfigurationIdentities(document.json(), configurations));
        return new RecoverySourceSnapshot(
                owner,
                ownerSchema,
                supportedSchemas,
                snapshotIdentity,
                contentDigest,
                ownerRevision,
                representedTick,
                worldIdentity,
                configurations.stream().sorted().toList(),
                true,
                Optional.empty()
        );
    }

    private PlatformDeterminismManifestReference platformManifest(List<RecoverySourceSnapshot> sources) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                write(output, PLATFORM_MANIFEST_IDENTITY);
                output.writeInt(PLATFORM_MANIFEST_SCHEMA);
                for (RecoverySourceSnapshot source : sources.stream().sorted().toList()) {
                    write(output, source.ownerId().value());
                    output.writeInt(source.ownerSchemaVersion());
                    output.writeInt(source.supportedSchemaVersions().size());
                    for (int schema : source.supportedSchemaVersions()) output.writeInt(schema);
                    output.writeInt(source.configurationIdentities().size());
                    for (String configuration : source.configurationIdentities()) write(output, configuration);
                }
            }
            return new PlatformDeterminismManifestReference(
                    PLATFORM_MANIFEST_IDENTITY,
                    PLATFORM_MANIFEST_SCHEMA,
                    CheckpointSnapshotDigest.sha256(bytes.toByteArray())
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to derive the recovery Platform Determinism Manifest", exception);
        }
    }

    private TargetRun targetRun(
            Map<String, JsonDocument> documents,
            long schedulerTick,
            WorldIdentityRootReference worldIdentity
    ) {
        Map<String, JsonObject> operations = index(
                array(object(documents, "execution_operations.json"), "operations"),
                "operation_id"
        );
        Set<String> schedulerWork = new HashSet<>();
        for (JsonElement element : array(object(documents, "simulation_scheduler.json"), "work")) {
            JsonObject definition = object(element, "scheduled Work").getAsJsonObject("definition");
            if (definition == null || !definition.has("id")) {
                throw new IllegalArgumentException("Scheduled Work is missing its definition identity: " + element);
            }
            schedulerWork.add(identity(definition.get("id")));
        }
        List<JsonObject> candidates = new ArrayList<>();
        for (JsonElement element : array(object(documents, "execution_machine_runs.json"), "runs")) {
            JsonObject run = object(element, "machine run");
            JsonElement currentElement = run.get("current_child");
            if (currentElement == null || currentElement.isJsonNull()) continue;
            JsonObject child = object(currentElement, "current machine run child");
            if (!PattyFormerExecutionConstants.OPERATION_TYPE.equals(string(child, "operation_type"))) continue;
            JsonObject operation = requireIndexed(operations, identity(child.get("operation_id")), "Execution operation");
            if ("authorized".equals(string(operation, "status"))) candidates.add(run);
        }
        if (candidates.size() != 1) {
            throw new IllegalArgumentException(
                    "Actual-world evidence must identify exactly one authorized Patty Former split-snapshot run; found "
                            + candidates.size()
            );
        }
        JsonObject run = candidates.getFirst();
        String runIdentity = identity(run.get("run_identity"));
        requireEqual(worldIdentity.identity(), identity(run.get("world_identity")), "Machine Run World Identity");
        requireEqual("AUTHORIZED", string(run, "lifecycle"), "Machine Run lifecycle");
        String workstationIdentity = string(run, "workstation_instance_identity");
        long machineRunGeneration = longValue(run, "generation");
        JsonObject currentChild = object(run.get("current_child"), "current machine run child");
        String currentOperationId = identity(currentChild.get("operation_id"));
        JsonObject currentOperation = requireIndexed(operations, currentOperationId, "current Execution operation");
        requireEqual("authorized", string(currentOperation, "status"), "current Execution operation status");
        if (booleanValue(currentOperation, "scheduler_invocation_started")) {
            throw new IllegalArgumentException("Current authorized Patty Former child already started Scheduler invocation");
        }
        if (!array(currentOperation, "attempts").isEmpty()
                || present(currentOperation, "owner_result_evidence")
                || present(currentOperation, "result_evidence")) {
            throw new IllegalArgumentException("Current authorized Patty Former child is not exact uninvoked state");
        }
        if (schedulerWork.contains(currentOperationId + "/work")) {
            throw new IllegalArgumentException("Current authorized Patty Former child already has Scheduler Work");
        }
        requireEqual(workstationIdentity, string(currentChild, "workstation_instance_identity"),
                "current child Workstation Instance Identity");
        requireEqual(
                string(currentChild, "authorization_content_digest"),
                string(object(currentOperation, "authorization_evidence"), "authorization_content_digest"),
                "current child authorization digest"
        );

        JsonObject instances = object(documents, "workstation_instances.json");
        JsonObject instance = requireIndexed(index(array(instances, "instances"), "instance_identity"),
                workstationIdentity, "Workstation instance");
        requireEqual("ACTIVE", string(instance, "lifecycle"), "Workstation instance lifecycle");
        requireWorldReference(instance.get("world_identity"), worldIdentity);
        long workstationGeneration = longValue(instance, "generation");

        JsonObject operating = findOperatingRecord(documents, workstationIdentity);
        requireEqual(runIdentity, identity(operating.get("current_run_identity")),
                "Machine Operating current Run Identity");
        requireEqual(workstationGeneration, longValue(object(operating, "workstation"), "generation"),
                "Machine Operating Workstation generation");

        List<JsonObject> terminalChildren = new ArrayList<>();
        for (JsonElement element : array(run, "terminal_children")) {
            JsonObject child = object(element, "terminal machine run child");
            if (longValue(child, "last_updated_simulation_tick") > schedulerTick) terminalChildren.add(child);
        }
        terminalChildren.sort(Comparator.comparingLong(child -> longValue(child, "sequence")));
        return new TargetRun(
                run,
                runIdentity,
                machineRunGeneration,
                workstationIdentity,
                workstationGeneration,
                currentChild,
                currentOperation,
                List.copyOf(terminalChildren),
                Map.copyOf(operations),
                schedulerWork,
                operating
        );
    }

    private List<HistoricalCoordinationAssessment> historicalAssessments(
            TargetRun target,
            RecoverySourceSnapshot executionSource,
            RecoverySourceSnapshot workstationSource,
            WorldIdentityRootReference worldIdentity
    ) {
        String handlerContract = ExecutionHandlerContract.idempotent(
                PattyFormerExecutionConstants.HANDLER_ID,
                PattyFormerExecutionConstants.OPERATION_TYPE,
                50,
                PattyFormerExecutionConstants.CONFIGURATION_IDENTITY
        ).contractIdentity();
        List<HistoricalCoordinationAssessment> assessments = new ArrayList<>();
        for (JsonObject child : target.terminalChildren()) {
            String operationId = identity(child.get("operation_id"));
            JsonObject operation = requireIndexed(target.operations(), operationId, "terminal Execution operation");
            JsonObject authorization = object(operation, "authorization_evidence");
            JsonObject ownerResult = object(operation, "owner_result_evidence");
            JsonObject result = object(operation, "result_evidence");
            JsonArray attempts = array(operation, "attempts");
            JsonObject attempt = attempts.size() == 1
                    ? object(attempts.get(0), "Execution attempt")
                    : new JsonObject();
            boolean conflicting = attempts.size() != 1
                    || !"COMPLETED".equals(string(child, "state"))
                    || !"succeeded".equals(string(operation, "status"))
                    || !booleanValue(operation, "scheduler_invocation_started")
                    || !string(child, "terminal_evidence_identity").equals(string(result, "evidence_identity"))
                    || !string(child, "authorization_content_digest")
                    .equals(string(authorization, "authorization_content_digest"))
                    || !target.workstationIdentity().equals(string(child, "workstation_instance_identity"))
                    || !worldIdentity.identity().equals(string(authorization, "world_identity"))
                    || !PattyFormerExecutionConstants.HANDLER_ID.equals(string(authorization, "handler_id"))
                    || !PattyFormerExecutionConstants.OPERATION_TYPE.equals(string(authorization, "operation_type"))
                    || !string(operation, "domain_effect_identity")
                    .equals(string(ownerResult, "domain_effect_identity"))
                    || !string(operation, "domain_effect_identity")
                    .equals(string(result, "domain_effect_identity"));
            OptionalLong effectTick = attempts.size() == 1
                    ? OptionalLong.of(longValue(attempt, "simulation_tick"))
                    : OptionalLong.empty();
            HistoricalCoordinationProof proof = new HistoricalCoordinationProof(
                    operationId,
                    Optional.of(SimulationWorkId.of(operationId + "/work")),
                    optionalInvocation(attempt, "scheduler_invocation_identity"),
                    optionalEffect(attempt, "scheduler_effect_identity"),
                    Optional.of(string(authorization, "authorization_identity")),
                    Optional.of(string(authorization, "authorization_content_digest")),
                    Optional.of(string(operation, "domain_effect_identity")),
                    Optional.of(handlerContract),
                    Optional.of(string(ownerResult, "owner_subsystem_id")),
                    Optional.of(string(ownerResult, "owner_result_identity")),
                    Optional.of(string(ownerResult, "content_digest")),
                    Optional.of(string(result, "evidence_identity")),
                    Optional.of(string(result, "result_content_digest")),
                    Optional.of(target.workstationIdentity()),
                    OptionalLong.of(target.workstationGeneration()),
                    Optional.of(target.runIdentity()),
                    OptionalLong.of(longValue(child, "sequence")),
                    Optional.of(HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED),
                    true,
                    effectTick,
                    effectTick,
                    List.of(
                            new HistoricalCoordinationSourceEvidence(
                                    LegacySplitRecoveryParticipants.EXECUTION.value(),
                                    executionSource.snapshotIdentity(),
                                    executionSource.contentDigest(),
                                    string(result, "evidence_identity"),
                                    string(result, "result_content_digest")
                            ),
                            new HistoricalCoordinationSourceEvidence(
                                    LegacySplitRecoveryParticipants.WORKSTATION.value(),
                                    workstationSource.snapshotIdentity(),
                                    workstationSource.contentDigest(),
                                    string(ownerResult, "owner_result_identity"),
                                    string(ownerResult, "content_digest")
                            )
                    ),
                    conflicting
            );
            assessments.add(HistoricalCoordinationAssessment.evaluate(proof));
        }
        return assessments.stream().sorted().toList();
    }

    private List<OrdinaryWorkReconstructionProof> ordinaryProofs(
            TargetRun target,
            List<HistoricalCoordinationAssessment> assessments
    ) {
        Map<String, HistoricalCoordinationAssessment> byOperation = new HashMap<>();
        assessments.forEach(value -> byOperation.put(value.executionOperationIdentity(), value));
        List<OrdinaryWorkReconstructionProof> proofs = new ArrayList<>();
        for (JsonObject child : target.terminalChildren()) {
            String operationId = identity(child.get("operation_id"));
            JsonObject operation = requireIndexed(target.operations(), operationId, "terminal Execution operation");
            JsonObject attempt = object(array(operation, "attempts").get(0), "Execution attempt");
            JsonObject ownerResult = object(operation, "owner_result_evidence");
            long tick = longValue(attempt, "simulation_tick");
            HistoricalCoordinationAssessment assessment = Objects.requireNonNull(byOperation.get(operationId));
            proofs.add(new OrdinaryWorkReconstructionProof(
                    operationId + "/ordinary_work_reconstruction",
                    Optional.of(SimulationWorkId.of(operationId + "/work")),
                    OptionalLong.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(SchedulerInvocationIdentity.of(string(attempt, "scheduler_invocation_identity"))),
                    Optional.of(SchedulerEffectIdentity.of(string(attempt, "scheduler_effect_identity"))),
                    Optional.of(string(ownerResult, "owner_result_identity")),
                    Optional.of(HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED),
                    OptionalLong.of(tick),
                    OptionalLong.of(tick),
                    assessment.eligibility()
                            == HistoricalCoordinationAssessment.Eligibility.ACKNOWLEDGEMENT_ELIGIBLE,
                    assessment.eligibility()
                            == HistoricalCoordinationAssessment.Eligibility.CONFLICTING_EVIDENCE
            ));
        }
        return proofs.stream().sorted().toList();
    }

    private PreservedAuthorizedWork preservedAuthorizedWork(
            TargetRun target,
            RecoverySourceSnapshot executionSource,
            RecoverySourceSnapshot workstationSource
    ) {
        JsonObject child = target.currentChild();
        JsonObject operation = target.currentOperation();
        JsonObject authorization = object(operation, "authorization_evidence");
        return new PreservedAuthorizedWork(
                identity(child.get("operation_id")),
                target.runIdentity(),
                target.machineRunGeneration(),
                string(child, "child_identity"),
                longValue(child, "sequence"),
                target.workstationIdentity(),
                target.workstationGeneration(),
                string(authorization, "authorization_content_digest"),
                List.of(
                        executionSource.snapshotIdentity(),
                        workstationSource.snapshotIdentity(),
                        string(child, "child_identity"),
                        string(authorization, "authorization_identity")
                ),
                PreservedAuthorizedWork.POLICY_B
        );
    }

    private List<PlanningRecoveryAuthorityBlock> planningBlocks(
            Map<String, JsonDocument> documents,
            RecoverySourceSnapshot planningSource,
            long schedulerTick,
            long clockTick
    ) {
        JsonObject cadence = object(documents, "planning_cadence.json");
        long eligibilityTick = longValue(cadence, "next_periodic_eligibility_tick");
        if (eligibilityTick <= schedulerTick || eligibilityTick > clockTick) return List.of();
        if (present(cadence, "active_cycle_id") || !array(cadence, "pending_triggers").isEmpty()) {
            throw new IllegalArgumentException("Planning split evidence is not the supported deferred periodic shape");
        }
        JsonObject planningWork = null;
        for (JsonElement element : array(object(documents, "simulation_scheduler.json"), "work")) {
            JsonObject candidate = object(element, "Scheduler Work");
            if (PLANNING_WORK_ID.equals(identity(object(candidate, "definition").get("id")))) {
                planningWork = candidate;
                break;
            }
        }
        if (planningWork == null) throw new IllegalArgumentException("Planning continuation Work is absent");
        JsonObject runtime = object(planningWork, "runtime");
        requireEqual("deferred", string(runtime, "status"), "Planning continuation status");
        requireEqual(eligibilityTick, longValue(runtime, "next_eligible_tick"),
                "Planning continuation eligibility tick");
        return List.of(PlanningRecoveryAuthorityBlock.unresolvedNonRepeatable(
                PLANNING_WORK_ID,
                eligibilityTick,
                eligibilityTick,
                List.of(new PlanningRecoveryAuthorityBlock.EvidenceReference(
                        LegacySplitRecoveryParticipants.PLANNING.value(),
                        planningSource.snapshotIdentity(),
                        planningSource.contentDigest()
                )),
                PlanningRecoveryAuthorityBlock.DependencyScope.WHOLE_WORLD_MUTATION,
                List.of(),
                false
        ));
    }

    private void validateMaterialHandling(Map<String, JsonDocument> documents) {
        JsonObject material = object(documents, "material_handling.json");
        if (!array(material, "transfers").isEmpty()) {
            throw new IllegalArgumentException(
                    "R2A actual-world adapter found active schema-2 Material Handling custody requiring owner analysis"
            );
        }
        String legacyJson = string(material, "immutable_legacy_schema_1_runtime");
        JsonObject legacy;
        try {
            legacy = object(JsonParser.parseString(legacyJson), "immutable Material Handling schema-1 runtime");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed immutable Material Handling schema-1 runtime", exception);
        }
        requireEqual(1L, longValue(legacy, "schema_version"), "Material Handling legacy schema");
        for (JsonElement element : array(legacy, "transfers")) {
            String lifecycle = string(object(element, "legacy Material Handling transfer"), "lifecycle");
            if (!Set.of("COMPLETED", "CANCELLED", "FAILED").contains(lifecycle)) {
                throw new IllegalArgumentException(
                        "R2A actual-world adapter found unresolved legacy Material Handling transfer: " + lifecycle
                );
            }
        }
    }

    private void validateEndpointJournal(Map<String, JsonDocument> documents) {
        JsonObject journal = object(documents, "workstation_endpoint_journal.json");
        if (!array(journal, "endpoint_effects").isEmpty()) {
            throw new IllegalArgumentException(
                    "R2A actual-world adapter found schema-2 Workstation endpoint effects requiring owner analysis"
            );
        }
        JsonObject legacy = object(
                JsonParser.parseString(string(journal, "immutable_legacy_schema_1_journal")),
                "immutable Workstation endpoint schema-1 journal"
        );
        requireEqual(1L, longValue(legacy, "schema_version"), "Workstation endpoint legacy schema");
        for (JsonElement element : array(legacy, "endpoint_effects")) {
            JsonObject effect = object(element, "legacy Workstation endpoint effect");
            if (!"RECONCILED".equals(string(effect, "state")) || !present(effect, "owner_result")) {
                throw new IllegalArgumentException("Unreconciled Workstation endpoint owner result evidence");
            }
        }
    }

    private JsonObject findOperatingRecord(Map<String, JsonDocument> documents, String workstationIdentity) {
        List<JsonObject> matches = new ArrayList<>();
        for (JsonElement element : array(object(documents, "machine_operating_states.json"), "records")) {
            JsonObject record = object(element, "Machine Operating record");
            if (workstationIdentity.equals(identity(object(record, "workstation").get("instance_id")))) {
                matches.add(record);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected one Machine Operating record for " + workstationIdentity);
        }
        return matches.getFirst();
    }

    private void requireSchema(Map<String, JsonDocument> documents, String name, int expected) {
        int actual = intValue(object(documents, name), "schema_version");
        if (actual != expected) {
            throw new IllegalArgumentException(
                    "Unsupported actual-world owner schema for " + name + ": " + actual + " (expected "
                            + expected + ")"
            );
        }
    }

    private String bundleDigest(CheckpointOwnerId owner, List<JsonDocument> documents) {
        return LegacyRecoverySourceBundle.digest(owner, documents.stream()
                .map(document -> new LegacyRecoverySourceBundle.SourceFile(
                        document.logicalName(), document.exactBytes()))
                .toList());
    }

    private void collectConfigurationIdentities(JsonElement element, Set<String> output) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(value -> collectConfigurationIdentities(value, output));
            return;
        }
        if (!element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (CONFIGURATION_KEYS.contains(entry.getKey())
                    && entry.getValue().isJsonPrimitive()
                    && entry.getValue().getAsJsonPrimitive().isString()) {
                output.add(entry.getValue().getAsString());
            }
            collectConfigurationIdentities(entry.getValue(), output);
        }
    }

    private void requireWorldReference(JsonElement value, WorldIdentityRootReference expected) {
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException("Owner persistence omitted World Identity");
        }
        if (value.isJsonPrimitive()) {
            requireEqual(expected.identity(), value.getAsString(), "World Identity");
            return;
        }
        JsonObject object = object(value, "World Identity");
        requireEqual(expected.identity(), string(object, "identity"), "World Identity");
        requireEqual(expected.schemaVersion(), intValue(object, "schema_version"), "World Identity schema");
        requireEqual(expected.rootDigest(), string(object, "root_digest"), "World Identity digest");
    }

    private Map<String, JsonObject> index(JsonArray values, String identityField) {
        Map<String, JsonObject> result = new HashMap<>();
        for (JsonElement element : values) {
            JsonObject object = object(element, identityField + " record");
            String identity = identity(object.get(identityField));
            if (result.putIfAbsent(identity, object) != null) {
                throw new IllegalArgumentException("Duplicate persisted identity: " + identity);
            }
        }
        return result;
    }

    private Optional<SchedulerInvocationIdentity> optionalInvocation(JsonObject object, String name) {
        return optionalString(object, name).map(SchedulerInvocationIdentity::of);
    }

    private Optional<SchedulerEffectIdentity> optionalEffect(JsonObject object, String name) {
        return optionalString(object, name).map(SchedulerEffectIdentity::of);
    }

    private Optional<String> optionalString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? Optional.empty() : Optional.of(value.getAsString());
    }

    private long maximumTick(JsonArray records, String field) {
        long maximum = 0L;
        for (JsonElement element : records) maximum = Math.max(maximum, longValue(object(element, field), field));
        return maximum;
    }

    private static long maximum(long... values) {
        long maximum = 0L;
        for (long value : values) maximum = Math.max(maximum, value);
        return maximum;
    }

    private static void write(DataOutputStream output, String value) throws IOException {
        byte[] bytes = Objects.requireNonNull(value, "canonical value").getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static RecoverySourceSnapshot requireSource(
            Map<CheckpointOwnerId, RecoverySourceSnapshot> sources,
            CheckpointOwnerId owner
    ) {
        RecoverySourceSnapshot source = sources.get(owner);
        if (source == null) throw new IllegalArgumentException("Missing recovery source for " + owner.value());
        return source;
    }

    private static JsonDocument requireDocument(Map<String, JsonDocument> documents, String name) {
        JsonDocument document = documents.get(name);
        if (document == null) throw new IllegalArgumentException("Missing actual-world owner file: " + name);
        return document;
    }

    private static JsonObject object(Map<String, JsonDocument> documents, String name) {
        return requireDocument(documents, name).json();
    }

    private static JsonObject object(JsonObject parent, String name) {
        return object(parent.get(name), name);
    }

    private static JsonObject object(JsonElement element, String label) {
        if (element == null || element.isJsonNull() || !element.isJsonObject()) {
            throw new IllegalArgumentException("Missing or invalid object: " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            throw new IllegalArgumentException("Missing or invalid array: " + name);
        }
        return element.getAsJsonArray();
    }

    private static String string(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing or invalid string: " + name);
        }
        return element.getAsString();
    }

    private static String identity(JsonElement element) {
        if (element == null || element.isJsonNull()) throw new IllegalArgumentException("Missing identity");
        if (element.isJsonPrimitive()) return element.getAsString();
        return string(object(element, "identity"), "value");
    }

    private static long longValue(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing or invalid integer: " + name);
        }
        return element.getAsLong();
    }

    private static int intValue(JsonObject parent, String name) {
        return Math.toIntExact(longValue(parent, name));
    }

    private static boolean booleanValue(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Missing or invalid boolean: " + name);
        }
        return element.getAsBoolean();
    }

    private static boolean present(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        return element != null && !element.isJsonNull();
    }

    private static JsonObject requireIndexed(Map<String, JsonObject> values, String identity, String label) {
        JsonObject value = values.get(identity);
        if (value == null) throw new IllegalArgumentException(label + " is absent: " + identity);
        return value;
    }

    private static void requireEqual(Object expected, Object actual, String label) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalArgumentException(label + " mismatch: expected " + expected + ", found " + actual);
        }
    }

    private static List<String> allSourceFiles() {
        return List.of(
                "business_calendar_runtime.json",
                "business_runtime.json",
                "contracts.json",
                "departments.json",
                "economic_actors.json",
                "employee_material_handling_assignments.json",
                "employee_records.json",
                "execution_machine_runs.json",
                "execution_operations.json",
                "goods.json",
                "inventory.json",
                "machine_operating_states.json",
                "material_handling.json",
                "orders.json",
                "planning_approved_plans.json",
                "planning_candidates.json",
                "planning_needs.json",
                "planning_observations.json",
                "planning_opportunities.json",
                "planning_runtime.json",
                "planning_cadence.json",
                "player_identities.json",
                "production_plans.json",
                "production_processes.json",
                "production_runs.json",
                "simulation_scheduler.json",
                "simulation_state.json",
                "transactions.json",
                "workforce_definitions.json",
                "workstation_endpoint_journal.json",
                "workstation_instances.json",
                "workstation_reservations.json",
                "world_time.json"
        );
    }

    private record JsonDocument(String logicalName, byte[] exactBytes, JsonObject json) {
        private JsonDocument {
            logicalName = Objects.requireNonNull(logicalName, "logicalName");
            exactBytes = Objects.requireNonNull(exactBytes, "exactBytes").clone();
            json = Objects.requireNonNull(json, "json");
        }

        @Override
        public byte[] exactBytes() {
            return exactBytes.clone();
        }
    }

    private record TargetRun(
            JsonObject run,
            String runIdentity,
            long machineRunGeneration,
            String workstationIdentity,
            long workstationGeneration,
            JsonObject currentChild,
            JsonObject currentOperation,
            List<JsonObject> terminalChildren,
            Map<String, JsonObject> operations,
            Set<String> schedulerWork,
            JsonObject operatingState
    ) {
    }
}
