package com.butchercraft.world.simulation.scheduler.persistence;

import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.RetryPolicyType;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationStageDefinition;
import com.butchercraft.world.simulation.scheduler.SimulationStageId;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRuntime;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.StageFailurePolicy;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPayloadValueType;
import com.butchercraft.world.simulation.scheduler.WorkPriority;
import com.butchercraft.world.simulation.scheduler.WorkReference;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class SimulationSchedulerStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

    private final Path filePath;
    private final SimulationWorkHandlerRegistry handlerRegistry;
    private final long initialFinalizedSimulationTick;

    public SimulationSchedulerStorage(
            Path filePath,
            SimulationWorkHandlerRegistry handlerRegistry,
            long initialFinalizedSimulationTick
    ) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        if (initialFinalizedSimulationTick < 0L) {
            throw new IllegalArgumentException("Initial finalized simulation tick must not be negative");
        }
        this.initialFinalizedSimulationTick = initialFinalizedSimulationTick;
    }

    public Path filePath() { return filePath; }

    public SimulationSchedulerManager load() {
        if (!Files.exists(filePath)) {
            return new SimulationSchedulerManager(
                    SimulationStageRegistry.builtIn(), handlerRegistry, initialFinalizedSimulationTick
            );
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load simulation scheduler from " + filePath, exception);
        }
    }

    public void save(SimulationSchedulerManager manager) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        try {
            Path parent = filePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporary, serialize(manager), StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save simulation scheduler to " + filePath, exception);
        }
    }

    public String serialize(SimulationSchedulerManager manager) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        List<StageRecord> stages = manager.stageRegistry().definitions().stream()
                .map(SimulationSchedulerStorage::toStageRecord).toList();
        List<SimulationWorkRuntime> runtimes = manager.runtimeRecords();
        List<WorkRecord> work = new ArrayList<>(manager.registry().size());
        for (int index = 0; index < manager.registry().size(); index++) {
            work.add(new WorkRecord(
                    toDefinitionRecord(manager.registry().definitions().get(index)),
                    toRuntimeRecord(runtimes.get(index))
            ));
        }
        SchedulerDocument document = new SchedulerDocument(
                SchedulerSchema.CURRENT_VERSION, manager.lastFinalizedSimulationTick(),
                manager.nextSubmissionSequence(), stages, work
        );
        return GSON.toJson(document) + System.lineSeparator();
    }

    public SimulationSchedulerManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            SchedulerDocument document = Objects.requireNonNull(
                    GSON.fromJson(json, SchedulerDocument.class), "scheduler persistence root"
            );
            if (document.schemaVersion() != SchedulerSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported scheduler schema version: " + document.schemaVersion());
            }
            SimulationStageRegistry stages = SimulationStageRegistry.of(
                    requireList(document.stages(), "stages").stream().map(SimulationSchedulerStorage::fromStageRecord)
                            .toList()
            );
            validateBuiltInStages(stages);
            List<ScheduledSimulationWork> definitions = new ArrayList<>();
            List<SimulationWorkRuntime> runtimes = new ArrayList<>();
            for (WorkRecord record : requireList(document.work(), "work")) {
                WorkRecord value = Objects.requireNonNull(record, "work record");
                definitions.add(fromDefinitionRecord(value.definition()));
                runtimes.add(fromRuntimeRecord(value.runtime()));
            }
            return new SimulationSchedulerManager(
                    stages, handlerRegistry, SimulationSchedulerRegistry.of(definitions), runtimes,
                    document.nextSubmissionSequence(), document.lastFinalizedSimulationTick()
            );
        } catch (JsonParseException | NullPointerException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt simulation scheduler persistence", exception);
        }
    }

    private static StageRecord toStageRecord(SimulationStageDefinition stage) {
        return new StageRecord(
                stage.schemaVersion(), stage.id().value(), stage.displayName(), stage.executionOrder(),
                stage.defaultFailurePolicy().serializedName(), stage.allowsSameTickEnqueue()
        );
    }

    private static SimulationStageDefinition fromStageRecord(StageRecord record) {
        Objects.requireNonNull(record, "stage record");
        return new SimulationStageDefinition(
                SimulationStageId.of(record.id()), record.displayName(), record.executionOrder(),
                StageFailurePolicy.fromSerializedName(record.defaultFailurePolicy()),
                record.allowsSameTickEnqueue(), record.schemaVersion()
        );
    }

    private static DefinitionRecord toDefinitionRecord(ScheduledSimulationWork work) {
        WorkOrigin origin = work.origin();
        return new DefinitionRecord(
                work.schemaVersion(), work.id().value(), work.typeId().value(), work.stageId().value(),
                work.scheduledTick(), work.priority().serializedName(),
                new OriginRecord(
                        origin.sourceSubsystemId(), origin.sourceReferenceType().orElse(null),
                        origin.sourceReferenceId().orElse(null), origin.submissionTick(),
                        origin.submittingAuthority(), origin.correlationId().orElse(null),
                        origin.parentWorkId().map(SimulationWorkId::value).orElse(null)
                ),
                toPayloadRecords(work.payload()),
                new RetryRecord(
                        work.retryPolicy().type().serializedName(),
                        optionalLong(work.retryPolicy().intervalSimulationTicks()),
                        optionalLong(work.retryPolicy().maximumRetryTick())
                ),
                work.maximumAttempts(), work.authoritativeSubmissionSequence(),
                optionalLong(work.expirationTick()),
                work.references().stream().map(reference ->
                        new ReferenceRecord(reference.referenceType(), reference.referenceId())).toList(),
                work.generationDepth()
        );
    }

    private static ScheduledSimulationWork fromDefinitionRecord(DefinitionRecord record) {
        Objects.requireNonNull(record, "work definition");
        OriginRecord origin = Objects.requireNonNull(record.origin(), "work origin");
        RetryRecord retry = Objects.requireNonNull(record.retryPolicy(), "retry policy");
        return new ScheduledSimulationWork(
                SimulationWorkId.of(record.id()), SimulationWorkTypeId.of(record.typeId()),
                SimulationStageId.of(record.stageId()), record.scheduledTick(),
                WorkPriority.fromSerializedName(record.priority()),
                new WorkOrigin(
                        origin.sourceSubsystemId(), Optional.ofNullable(origin.sourceReferenceType()),
                        Optional.ofNullable(origin.sourceReferenceId()), origin.submissionTick(),
                        origin.submittingAuthority(), Optional.ofNullable(origin.correlationId()),
                        Optional.ofNullable(origin.parentWorkId()).map(SimulationWorkId::of)
                ),
                fromPayloadRecords(record.payload()),
                new RetryPolicy(
                        RetryPolicyType.fromSerializedName(retry.type()), optionalLong(retry.intervalTicks()),
                        optionalLong(retry.maximumRetryTick())
                ),
                record.maximumAttempts(), record.authoritativeSubmissionSequence(),
                optionalLong(record.expirationTick()),
                requireList(record.references(), "work references").stream().map(reference ->
                        new WorkReference(reference.referenceType(), reference.referenceId())).toList(),
                record.generationDepth(), record.schemaVersion()
        );
    }

    private static RuntimeRecord toRuntimeRecord(SimulationWorkRuntime runtime) {
        return new RuntimeRecord(
                runtime.schemaVersion(), runtime.workId().value(), runtime.status().serializedName(),
                runtime.attemptCount(), runtime.lastUpdatedSimulationTick(), optionalLong(runtime.startedTick()),
                optionalLong(runtime.completedTick()), optionalLong(runtime.nextEligibleTick()),
                runtime.lastFailureCode().map(WorkFailureCode::serializedName).orElse(null),
                runtime.diagnosticSummary().orElse(null), toPayloadRecords(runtime.resultSummary()), runtime.revision()
        );
    }

    private static SimulationWorkRuntime fromRuntimeRecord(RuntimeRecord record) {
        Objects.requireNonNull(record, "work runtime");
        return new SimulationWorkRuntime(
                SimulationWorkId.of(record.workId()), SimulationWorkStatus.fromSerializedName(record.status()),
                record.attemptCount(), record.lastUpdatedSimulationTick(), optionalLong(record.startedTick()),
                optionalLong(record.completedTick()), optionalLong(record.nextEligibleTick()),
                Optional.ofNullable(record.lastFailureCode()).map(WorkFailureCode::fromSerializedName),
                Optional.ofNullable(record.diagnosticSummary()), fromPayloadRecords(record.resultSummary()),
                record.revision(), record.schemaVersion()
        );
    }

    private static List<PayloadRecord> toPayloadRecords(WorkPayload payload) {
        return payload.entries().stream().map(entry ->
                new PayloadRecord(entry.key(), entry.type().serializedName(), entry.canonicalValue())).toList();
    }

    private static WorkPayload fromPayloadRecords(List<PayloadRecord> records) {
        return new WorkPayload(requireList(records, "payload entries").stream().map(record ->
                new WorkPayloadEntry(
                        record.key(), WorkPayloadValueType.fromSerializedName(record.type()), record.value()
                )).toList());
    }

    private static void validateBuiltInStages(SimulationStageRegistry stages) {
        for (SimulationStageDefinition expected : BuiltInSimulationStages.definitions()) {
            SimulationStageDefinition actual = stages.find(expected.id()).orElseThrow(() ->
                    new IllegalArgumentException("Scheduler persistence is missing built-in stage: " + expected.id()));
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException("Built-in scheduler stage definition has changed: " + expected.id());
            }
        }
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Long optionalLong(OptionalLong value) {
        return value.isPresent() ? value.getAsLong() : null;
    }

    private static OptionalLong optionalLong(Long value) {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static <T> List<T> requireList(List<T> values, String label) {
        return List.copyOf(Objects.requireNonNull(values, label));
    }

    private record SchedulerDocument(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("last_finalized_simulation_tick") Long lastFinalizedSimulationTick,
            @SerializedName("next_submission_sequence") Long nextSubmissionSequence,
            List<StageRecord> stages,
            List<WorkRecord> work
    ) { }

    private record StageRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            String id,
            @SerializedName("display_name") String displayName,
            @SerializedName("execution_order") Integer executionOrder,
            @SerializedName("default_failure_policy") String defaultFailurePolicy,
            @SerializedName("allows_same_tick_enqueue") Boolean allowsSameTickEnqueue
    ) { }

    private record WorkRecord(DefinitionRecord definition, RuntimeRecord runtime) { }

    private record DefinitionRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            String id,
            @SerializedName("type_id") String typeId,
            @SerializedName("stage_id") String stageId,
            @SerializedName("scheduled_tick") Long scheduledTick,
            String priority,
            OriginRecord origin,
            List<PayloadRecord> payload,
            @SerializedName("retry_policy") RetryRecord retryPolicy,
            @SerializedName("maximum_attempts") Integer maximumAttempts,
            @SerializedName("authoritative_submission_sequence") Long authoritativeSubmissionSequence,
            @SerializedName("expiration_tick") Long expirationTick,
            List<ReferenceRecord> references,
            @SerializedName("generation_depth") Integer generationDepth
    ) { }

    private record OriginRecord(
            @SerializedName("source_subsystem_id") String sourceSubsystemId,
            @SerializedName("source_reference_type") String sourceReferenceType,
            @SerializedName("source_reference_id") String sourceReferenceId,
            @SerializedName("submission_tick") Long submissionTick,
            @SerializedName("submitting_authority") String submittingAuthority,
            @SerializedName("correlation_id") String correlationId,
            @SerializedName("parent_work_id") String parentWorkId
    ) { }

    private record RetryRecord(
            String type,
            @SerializedName("interval_ticks") Long intervalTicks,
            @SerializedName("maximum_retry_tick") Long maximumRetryTick
    ) { }

    private record ReferenceRecord(
            @SerializedName("reference_type") String referenceType,
            @SerializedName("reference_id") String referenceId
    ) { }

    private record PayloadRecord(String key, String type, String value) { }

    private record RuntimeRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("work_id") String workId,
            String status,
            @SerializedName("attempt_count") Integer attemptCount,
            @SerializedName("last_updated_simulation_tick") Long lastUpdatedSimulationTick,
            @SerializedName("started_tick") Long startedTick,
            @SerializedName("completed_tick") Long completedTick,
            @SerializedName("next_eligible_tick") Long nextEligibleTick,
            @SerializedName("last_failure_code") String lastFailureCode,
            @SerializedName("diagnostic_summary") String diagnosticSummary,
            @SerializedName("result_summary") List<PayloadRecord> resultSummary,
            Long revision
    ) { }
}
