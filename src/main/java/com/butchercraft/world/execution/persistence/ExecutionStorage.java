package com.butchercraft.world.execution.persistence;

import com.butchercraft.world.execution.ExecutionAttemptId;
import com.butchercraft.world.execution.ExecutionAttemptRecord;
import com.butchercraft.world.execution.ExecutionAuthorizationEvidence;
import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionFailure;
import com.butchercraft.world.execution.ExecutionFailureCode;
import com.butchercraft.world.execution.ExecutionHandlerRegistry;
import com.butchercraft.world.execution.ExecutionRegistryCompatibilityClassification;
import com.butchercraft.world.execution.ExecutionRegistryCompatibilityClassifier;
import com.butchercraft.world.execution.ExecutionRegistryCompatibilityException;
import com.butchercraft.world.execution.ExecutionRegistryCompatibilityObservation;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;
import com.butchercraft.world.execution.ExecutionResultEvidence;
import com.butchercraft.world.execution.ExecutionRuntimeConfiguration;
import com.butchercraft.world.execution.ExecutionSchema;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class ExecutionStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

    private final Path filePath;
    private final ExecutionHandlerRegistry handlerRegistry;
    private final ExecutionRuntimeConfiguration configuration;
    private final ExecutionRegistryCompatibilityClassifier compatibilityClassifier;
    private volatile ExecutionRegistryCompatibilityObservation compatibilityObservation;
    private LoadedPersistenceBaseline loadedBaseline;

    public ExecutionStorage(
            Path filePath,
            ExecutionHandlerRegistry handlerRegistry,
            ExecutionRuntimeConfiguration configuration
    ) {
        this(filePath, handlerRegistry, configuration, ExecutionRegistryCompatibilityClassifier.standard());
    }

    public ExecutionStorage(
            Path filePath,
            ExecutionHandlerRegistry handlerRegistry,
            ExecutionRuntimeConfiguration configuration,
            ExecutionRegistryCompatibilityClassifier compatibilityClassifier
    ) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.compatibilityClassifier = Objects.requireNonNull(compatibilityClassifier, "compatibilityClassifier");
    }

    public Path filePath() {
        return filePath;
    }

    public synchronized ExecutionManager load() {
        if (!Files.exists(filePath)) {
            ExecutionManager manager = new ExecutionManager(handlerRegistry, configuration);
            compatibilityObservation = compatibilityClassifier.classify(
                    ExecutionSchema.CURRENT_VERSION,
                    handlerRegistry.registryIdentity(),
                    configuration.configurationIdentity(),
                    handlerRegistry,
                    configuration,
                    List.of()
            );
            loadedBaseline = null;
            return manager;
        }
        try {
            ExecutionManager manager = deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
            loadedBaseline = new LoadedPersistenceBaseline(
                    manager,
                    manager.operations(),
                    compatibilityObservation.classification()
            );
            return manager;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load Execution runtime from " + filePath, exception);
        }
    }

    public synchronized void save(ExecutionManager manager) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        if (loadedBaseline != null && loadedBaseline.preserveHistoricalPersistence(manager)) {
            return;
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporary, serialize(manager), StandardCharsets.UTF_8);
            moveIntoPlace(temporary);
            loadedBaseline = null;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save Execution runtime to " + filePath, exception);
        }
    }

    public String serialize(ExecutionManager manager) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        ExecutionDocument document = new ExecutionDocument(
                ExecutionSchema.CURRENT_VERSION,
                manager.handlerRegistry().registryIdentity(),
                manager.configuration().configurationIdentity(),
                manager.operations().stream().map(ExecutionStorage::toOperationRecord).toList()
        );
        return GSON.toJson(document) + System.lineSeparator();
    }

    public synchronized ExecutionManager deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            ExecutionDocument document = Objects.requireNonNull(
                    GSON.fromJson(json, ExecutionDocument.class),
                    "Execution persistence root"
            );
            int schema = Objects.requireNonNull(document.schemaVersion(), "Execution schema version");
            if (schema != ExecutionSchema.CURRENT_VERSION) {
                compatibilityObservation = compatibilityClassifier.classify(
                        schema,
                        document.handlerRegistryIdentity(),
                        document.configurationIdentity(),
                        handlerRegistry,
                        configuration,
                        List.of()
                );
                throw new ExecutionRegistryCompatibilityException(compatibilityObservation);
            }
            List<ExecutionOperationSnapshot> snapshots = requireList(document.operations(), "operations").stream()
                    .map(record -> fromOperationRecord(record, schema))
                    .toList();
            compatibilityObservation = compatibilityClassifier.classify(
                    schema,
                    document.handlerRegistryIdentity(),
                    document.configurationIdentity(),
                    handlerRegistry,
                    configuration,
                    snapshots
            );
            if (!compatibilityObservation.permitsExecutionAuthority()) {
                throw new ExecutionRegistryCompatibilityException(compatibilityObservation);
            }
            return new ExecutionManager(handlerRegistry, configuration, snapshots);
        } catch (ExecutionRegistryCompatibilityException exception) {
            throw exception;
        } catch (JsonParseException | NullPointerException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt Execution persistence", exception);
        }
    }

    public Optional<ExecutionRegistryCompatibilityObservation> compatibilityObservation() {
        return Optional.ofNullable(compatibilityObservation);
    }

    private static OperationRecord toOperationRecord(ExecutionOperationSnapshot snapshot) {
        return new OperationRecord(
                snapshot.schemaVersion(),
                snapshot.operationId().value(),
                toAuthorizationRecord(snapshot.authorizationEvidence()),
                snapshot.domainEffectIdentity().value(),
                snapshot.status().serializedName(),
                snapshot.createdSimulationTick(),
                snapshot.lastUpdatedSimulationTick(),
                snapshot.revision(),
                snapshot.attemptSequence(),
                snapshot.schedulerInvocationStarted(),
                snapshot.failure().map(ExecutionStorage::toFailureRecord).orElse(null),
                snapshot.ownerResultEvidence().map(ExecutionStorage::toOwnerResultRecord).orElse(null),
                snapshot.resultEvidence().map(ExecutionStorage::toResultEvidenceRecord).orElse(null),
                snapshot.attempts().stream().map(ExecutionStorage::toAttemptRecord).toList()
        );
    }

    private static ExecutionOperationSnapshot fromOperationRecord(OperationRecord record, int documentSchema) {
        Objects.requireNonNull(record, "operation record");
        return new ExecutionOperationSnapshot(
                recordSchema(record.schemaVersion(), documentSchema),
                ExecutionOperationId.of(record.operationId()),
                fromAuthorizationRecord(record.authorizationEvidence(), documentSchema),
                new ExecutionDomainEffectIdentity(record.domainEffectIdentity()),
                ExecutionStatus.fromSerializedName(record.status()),
                record.createdSimulationTick(),
                record.lastUpdatedSimulationTick(),
                record.revision(),
                record.attemptSequence(),
                record.schedulerInvocationStarted(),
                Optional.ofNullable(record.failure()).map(ExecutionStorage::fromFailureRecord),
                Optional.ofNullable(record.ownerResultEvidence()).map(ExecutionStorage::fromOwnerResultRecord),
                Optional.ofNullable(record.resultEvidence()).map(value ->
                        fromResultEvidenceRecord(value, record, documentSchema)),
                requireList(record.attempts(), "attempts").stream()
                        .map(value -> fromAttemptRecord(value, documentSchema))
                        .toList()
        );
    }

    private static AuthorizationRecord toAuthorizationRecord(ExecutionAuthorizationEvidence evidence) {
        return new AuthorizationRecord(
                evidence.schemaVersion(),
                evidence.authorizationIdentity(),
                evidence.authorizationSourceOwner(),
                evidence.executableWorkReferenceType(),
                evidence.executableWorkReferenceId(),
                evidence.operationType(),
                evidence.handlerId(),
                evidence.frozenInputIdentity(),
                evidence.sourceFreshnessIdentity(),
                evidence.configurationIdentity(),
                evidence.worldIdentity(),
                evidence.issuedSimulationTick(),
                optionalLong(evidence.validUntilSimulationTick()),
                evidence.explicitInputIdentities(),
                evidence.authorizationContentDigest()
        );
    }

    private static ExecutionAuthorizationEvidence fromAuthorizationRecord(AuthorizationRecord record, int documentSchema) {
        Objects.requireNonNull(record, "authorization record");
        return new ExecutionAuthorizationEvidence(
                recordSchema(record.schemaVersion(), documentSchema),
                record.authorizationIdentity(),
                record.authorizationSourceOwner(),
                record.executableWorkReferenceType(),
                record.executableWorkReferenceId(),
                record.operationType(),
                record.handlerId(),
                record.frozenInputIdentity(),
                record.sourceFreshnessIdentity(),
                record.configurationIdentity(),
                record.worldIdentity(),
                record.issuedSimulationTick(),
                optionalLong(record.validUntilSimulationTick()),
                requireList(record.explicitInputIdentities(), "explicit input identities"),
                record.authorizationContentDigest()
        );
    }

    private static AttemptRecord toAttemptRecord(ExecutionAttemptRecord attempt) {
        return new AttemptRecord(
                attempt.schemaVersion(),
                attempt.attemptId().value(),
                attempt.operationId().value(),
                attempt.attemptSequence(),
                attempt.simulationTick(),
                attempt.schedulerInvocationIdentity().value(),
                attempt.schedulerEffectIdentity().value(),
                attempt.startingStatus().serializedName(),
                attempt.endingStatus().serializedName(),
                attempt.handlerId(),
                attempt.ownerResultIdentity().orElse(null),
                attempt.failure().map(ExecutionStorage::toFailureRecord).orElse(null),
                attempt.workUnits(),
                attempt.attemptContentDigest()
        );
    }

    private static ExecutionAttemptRecord fromAttemptRecord(AttemptRecord record, int documentSchema) {
        Objects.requireNonNull(record, "attempt record");
        return new ExecutionAttemptRecord(
                recordSchema(record.schemaVersion(), documentSchema),
                new ExecutionAttemptId(record.attemptId()),
                ExecutionOperationId.of(record.operationId()),
                record.attemptSequence(),
                record.simulationTick(),
                SchedulerInvocationIdentity.of(record.schedulerInvocationIdentity()),
                SchedulerEffectIdentity.of(record.schedulerEffectIdentity()),
                ExecutionStatus.fromSerializedName(record.startingStatus()),
                ExecutionStatus.fromSerializedName(record.endingStatus()),
                record.handlerId(),
                Optional.ofNullable(record.ownerResultIdentity()),
                Optional.ofNullable(record.failure()).map(ExecutionStorage::fromFailureRecord),
                record.workUnits(),
                record.attemptContentDigest()
        );
    }

    private static OwnerResultRecord toOwnerResultRecord(ExecutionOwnerResultEvidence evidence) {
        return new OwnerResultRecord(
                evidence.schemaVersion(),
                evidence.ownerSubsystemId(),
                evidence.ownerResultIdentity(),
                evidence.domainEffectIdentity().value(),
                evidence.ownerResultDigest(),
                evidence.contentDigest()
        );
    }

    private static ExecutionOwnerResultEvidence fromOwnerResultRecord(OwnerResultRecord record) {
        Objects.requireNonNull(record, "owner result record");
        return new ExecutionOwnerResultEvidence(
                record.schemaVersion(),
                record.ownerSubsystemId(),
                record.ownerResultIdentity(),
                new ExecutionDomainEffectIdentity(record.domainEffectIdentity()),
                record.ownerResultDigest(),
                record.contentDigest()
        );
    }

    private static ResultEvidenceRecord toResultEvidenceRecord(ExecutionResultEvidence evidence) {
        return new ResultEvidenceRecord(
                evidence.schemaVersion(),
                evidence.evidenceIdentity(),
                evidence.operationId().value(),
                evidence.terminalStatus().serializedName(),
                evidence.authorizationIdentity(),
                evidence.authorizationContentDigest(),
                evidence.frozenInputIdentity(),
                evidence.domainEffectIdentity().value(),
                evidence.schedulerInvocationIdentity().map(SchedulerInvocationIdentity::value).orElse(null),
                evidence.schedulerEffectIdentity().map(SchedulerEffectIdentity::value).orElse(null),
                evidence.ownerResultEvidence().map(ExecutionStorage::toOwnerResultRecord).orElse(null),
                evidence.failure().map(ExecutionStorage::toFailureRecord).orElse(null),
                evidence.resultContentDigest()
        );
    }

    private static ExecutionResultEvidence fromResultEvidenceRecord(
            ResultEvidenceRecord record,
            OperationRecord operation,
            int documentSchema
    ) {
        Objects.requireNonNull(record, "result evidence record");
        return new ExecutionResultEvidence(
                recordSchema(record.schemaVersion(), documentSchema),
                record.evidenceIdentity(),
                ExecutionOperationId.of(record.operationId()),
                ExecutionStatus.fromSerializedName(record.terminalStatus()),
                record.authorizationIdentity(),
                record.authorizationContentDigest(),
                record.frozenInputIdentity(),
                new ExecutionDomainEffectIdentity(record.domainEffectIdentity()),
                Optional.ofNullable(record.schedulerInvocationIdentity()).map(SchedulerInvocationIdentity::of),
                Optional.ofNullable(record.schedulerEffectIdentity()).map(SchedulerEffectIdentity::of),
                Optional.ofNullable(record.ownerResultEvidence()).map(ExecutionStorage::fromOwnerResultRecord),
                Optional.ofNullable(record.failure()).map(ExecutionStorage::fromFailureRecord),
                record.resultContentDigest()
        );
    }

    private static FailureRecord toFailureRecord(ExecutionFailure failure) {
        return new FailureRecord(
                failure.schemaVersion(),
                failure.code().serializedName(),
                failure.message(),
                failure.referenceIdentity()
        );
    }

    private static ExecutionFailure fromFailureRecord(FailureRecord record) {
        Objects.requireNonNull(record, "failure record");
        return new ExecutionFailure(
                record.schemaVersion(),
                ExecutionFailureCode.fromSerializedName(record.code()),
                record.message(),
                record.referenceIdentity()
        );
    }

    private void moveIntoPlace(Path temporary) throws IOException {
        try {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static int recordSchema(Integer value, int documentSchema) {
        return value == null ? documentSchema : value;
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

    private record ExecutionDocument(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("handler_registry_identity") String handlerRegistryIdentity,
            @SerializedName("configuration_identity") String configurationIdentity,
            List<OperationRecord> operations
    ) { }

    private record OperationRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("operation_id") String operationId,
            @SerializedName("authorization_evidence") AuthorizationRecord authorizationEvidence,
            @SerializedName("domain_effect_identity") String domainEffectIdentity,
            String status,
            @SerializedName("created_simulation_tick") Long createdSimulationTick,
            @SerializedName("last_updated_simulation_tick") Long lastUpdatedSimulationTick,
            Long revision,
            @SerializedName("attempt_sequence") Integer attemptSequence,
            @SerializedName("scheduler_invocation_started") Boolean schedulerInvocationStarted,
            FailureRecord failure,
            @SerializedName("owner_result_evidence") OwnerResultRecord ownerResultEvidence,
            @SerializedName("result_evidence") ResultEvidenceRecord resultEvidence,
            List<AttemptRecord> attempts
    ) { }

    private record AuthorizationRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("authorization_identity") String authorizationIdentity,
            @SerializedName("authorization_source_owner") String authorizationSourceOwner,
            @SerializedName("executable_work_reference_type") String executableWorkReferenceType,
            @SerializedName("executable_work_reference_id") String executableWorkReferenceId,
            @SerializedName("operation_type") String operationType,
            @SerializedName("handler_id") String handlerId,
            @SerializedName("frozen_input_identity") String frozenInputIdentity,
            @SerializedName("source_freshness_identity") String sourceFreshnessIdentity,
            @SerializedName("configuration_identity") String configurationIdentity,
            @SerializedName("world_identity") String worldIdentity,
            @SerializedName("issued_simulation_tick") Long issuedSimulationTick,
            @SerializedName("valid_until_simulation_tick") Long validUntilSimulationTick,
            @SerializedName("explicit_input_identities") List<String> explicitInputIdentities,
            @SerializedName("authorization_content_digest") String authorizationContentDigest
    ) { }

    private record AttemptRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("attempt_id") String attemptId,
            @SerializedName("operation_id") String operationId,
            @SerializedName("attempt_sequence") Integer attemptSequence,
            @SerializedName("simulation_tick") Long simulationTick,
            @SerializedName("scheduler_invocation_identity") String schedulerInvocationIdentity,
            @SerializedName("scheduler_effect_identity") String schedulerEffectIdentity,
            @SerializedName("starting_status") String startingStatus,
            @SerializedName("ending_status") String endingStatus,
            @SerializedName("handler_id") String handlerId,
            @SerializedName("owner_result_identity") String ownerResultIdentity,
            FailureRecord failure,
            @SerializedName("work_units") Integer workUnits,
            @SerializedName("attempt_content_digest") String attemptContentDigest
    ) { }

    private record OwnerResultRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("owner_subsystem_id") String ownerSubsystemId,
            @SerializedName("owner_result_identity") String ownerResultIdentity,
            @SerializedName("domain_effect_identity") String domainEffectIdentity,
            @SerializedName("owner_result_digest") String ownerResultDigest,
            @SerializedName("content_digest") String contentDigest
    ) { }

    private record ResultEvidenceRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            @SerializedName("evidence_identity") String evidenceIdentity,
            @SerializedName("operation_id") String operationId,
            @SerializedName("terminal_status") String terminalStatus,
            @SerializedName("authorization_identity") String authorizationIdentity,
            @SerializedName("authorization_content_digest") String authorizationContentDigest,
            @SerializedName("frozen_input_identity") String frozenInputIdentity,
            @SerializedName("domain_effect_identity") String domainEffectIdentity,
            @SerializedName("scheduler_invocation_identity") String schedulerInvocationIdentity,
            @SerializedName("scheduler_effect_identity") String schedulerEffectIdentity,
            @SerializedName("owner_result_evidence") OwnerResultRecord ownerResultEvidence,
            FailureRecord failure,
            @SerializedName("result_content_digest") String resultContentDigest
    ) { }

    private record FailureRecord(
            @SerializedName("schema_version") Integer schemaVersion,
            String code,
            String message,
            @SerializedName("reference_identity") String referenceIdentity
    ) { }

    private record LoadedPersistenceBaseline(
            ExecutionManager manager,
            List<ExecutionOperationSnapshot> operations,
            ExecutionRegistryCompatibilityClassification classification
    ) {
        private LoadedPersistenceBaseline {
            Objects.requireNonNull(manager, "manager");
            operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
            Objects.requireNonNull(classification, "classification");
        }

        private boolean preserveHistoricalPersistence(ExecutionManager candidate) {
            return classification == ExecutionRegistryCompatibilityClassification.ADDITIVE_COMPATIBLE
                    && manager == candidate
                    && operations.equals(candidate.operations());
        }
    }
}
