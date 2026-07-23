package com.butchercraft.world.planning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class PlanningStorage {
    public static final String OBSERVATIONS_FILE = "planning_observations.json";
    public static final String NEEDS_FILE = "planning_needs.json";
    public static final String OPPORTUNITIES_FILE = "planning_opportunities.json";
    public static final String CANDIDATES_FILE = "planning_candidates.json";
    public static final String APPROVED_PLANS_FILE = "planning_approved_plans.json";
    public static final String RUNTIME_FILE = "planning_runtime.json";

    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
            .registerTypeAdapter(OptionalLong.class, new OptionalLongAdapter())
            .serializeNulls()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private final Path directory;
    private final PlanningDependencies dependencies;
    private final PlanningSelectionPolicy policy;
    private final PlanningExecutionBudget budget;

    public PlanningStorage(
            Path directory,
            PlanningDependencies dependencies,
            PlanningSelectionPolicy policy,
            PlanningExecutionBudget budget
    ) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public PlanningManager load() {
        List<Path> persistenceFiles = files();
        int count = (int) persistenceFiles.stream().filter(Files::exists).count();
        if (count == 0) return new PlanningManager(dependencies, policy, budget);
        if (count != persistenceFiles.size()) {
            throw new IllegalArgumentException("Planning persistence is incomplete; all six files are required");
        }
        try {
            PlanningFile<CycleObservations> observationFile = read(
                    OBSERVATIONS_FILE, new TypeToken<PlanningFile<CycleObservations>>() {}.getType());
            PlanningFile<CycleNeeds> needFile = read(
                    NEEDS_FILE, new TypeToken<PlanningFile<CycleNeeds>>() {}.getType());
            PlanningFile<CycleOpportunities> opportunityFile = read(
                    OPPORTUNITIES_FILE, new TypeToken<PlanningFile<CycleOpportunities>>() {}.getType());
            PlanningFile<CycleCandidates> candidateFile = read(
                    CANDIDATES_FILE, new TypeToken<PlanningFile<CycleCandidates>>() {}.getType());
            PlanningFile<CycleApproved> approvedFile = read(
                    APPROVED_PLANS_FILE, new TypeToken<PlanningFile<CycleApproved>>() {}.getType());
            PlanningFile<CycleRuntime> runtimeFile = read(
                    RUNTIME_FILE, new TypeToken<PlanningFile<CycleRuntime>>() {}.getType());
            List<CycleObservations> observations = observationFile.records();
            List<CycleNeeds> needs = needFile.records();
            List<CycleOpportunities> opportunities = opportunityFile.records();
            List<CycleCandidates> candidates = candidateFile.records();
            List<CycleApproved> approved = approvedFile.records();
            List<CycleRuntime> runtimes = runtimeFile.records();
            Map<PlanningCycleId, CycleObservations> observationsById = index(observations);
            Map<PlanningCycleId, CycleNeeds> needsById = index(needs);
            Map<PlanningCycleId, CycleOpportunities> opportunitiesById = index(opportunities);
            Map<PlanningCycleId, CycleCandidates> candidatesById = index(candidates);
            Map<PlanningCycleId, CycleApproved> approvedById = index(approved);
            List<PlanningCycleSnapshot> cycles = new ArrayList<>();
            for (CycleRuntime runtime : runtimes) {
                CycleObservations cycleObservations = require(observationsById, runtime.id());
                CycleNeeds cycleNeeds = require(needsById, runtime.id());
                CycleOpportunities cycleOpportunities = require(opportunitiesById, runtime.id());
                CycleCandidates cycleCandidates = require(candidatesById, runtime.id());
                CycleApproved cycleApproved = require(approvedById, runtime.id());
                cycles.add(new PlanningCycleSnapshot(
                        runtime.id(), runtime.simulationTick(), runtime.policyId(), runtime.status(),
                        cycleObservations.observations(), cycleNeeds.needs(), runtime.constraints(),
                        cycleOpportunities.opportunities(), cycleCandidates.candidates(),
                        cycleApproved.approvedPlans(), runtime.needRuntimes(), runtime.submissionRuntimes(),
                        runtime.report(), runtime.revision(), runtime.schemaVersion()
                ));
            }
            if (cycles.size() != observationsById.size() || cycles.size() != needsById.size()
                    || cycles.size() != opportunitiesById.size() || cycles.size() != candidatesById.size()
                    || cycles.size() != approvedById.size()) {
                throw new IllegalArgumentException("Planning persistence contains unmatched cycle artifacts");
            }
            PlanningManager manager = new PlanningManager(dependencies, policy, budget, cycles);
            manager.validate();
            return manager;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Unable to load Planning persistence", exception);
        }
    }

    public void save(PlanningManager manager) {
        Objects.requireNonNull(manager, "manager").validate();
        List<PlanningCycleSnapshot> cycles = manager.cycles();
        try {
            Files.createDirectories(directory);
            write(OBSERVATIONS_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(value -> new CycleObservations(
                            value.id(), value.observations())).toList()));
            write(NEEDS_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(value -> new CycleNeeds(value.id(), value.needs())).toList()));
            write(OPPORTUNITIES_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(value -> new CycleOpportunities(
                            value.id(), value.opportunities())).toList()));
            write(CANDIDATES_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(value -> new CycleCandidates(
                            value.id(), value.candidates())).toList()));
            write(APPROVED_PLANS_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(value -> new CycleApproved(
                            value.id(), value.approvedPlans())).toList()));
            write(RUNTIME_FILE, new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                    cycles.stream().map(CycleRuntime::from).toList()));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save Planning persistence", exception);
        }
    }

    public String serializeRuntime(PlanningManager manager) {
        manager.validate();
        return GSON.toJson(new PlanningFile<>(PlanningValidation.SCHEMA_VERSION,
                manager.cycles().stream().map(CycleRuntime::from).toList()));
    }

    private List<Path> files() {
        return List.of(path(OBSERVATIONS_FILE), path(NEEDS_FILE), path(OPPORTUNITIES_FILE),
                path(CANDIDATES_FILE), path(APPROVED_PLANS_FILE), path(RUNTIME_FILE));
    }

    private Path path(String name) {
        return directory.resolve(name).toAbsolutePath().normalize();
    }

    private <T> T read(String name, Type type) throws IOException {
        T value = GSON.fromJson(Files.readString(path(name), StandardCharsets.UTF_8), type);
        if (value == null) throw new JsonParseException("Planning file is empty: " + name);
        PlanningFile<?> root = (PlanningFile<?>) value;
        PlanningValidation.schema(root.schemaVersion());
        return value;
    }

    private void write(String name, Object value) throws IOException {
        Path target = path(name);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(value) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static <T extends CycleRecord> Map<PlanningCycleId, T> index(List<T> values) {
        Map<PlanningCycleId, T> result = new LinkedHashMap<>();
        for (T value : values) {
            if (result.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate Planning Cycle artifact record: " + value.id().value());
            }
        }
        return result;
    }

    private static <T> T require(Map<PlanningCycleId, T> values, PlanningCycleId id) {
        T value = values.get(id);
        if (value == null) throw new IllegalArgumentException("Planning Cycle artifact file is incomplete");
        return value;
    }

    private record PlanningFile<T>(int schemaVersion, List<T> records) {
        private PlanningFile {
            PlanningValidation.schema(schemaVersion);
            records = List.copyOf(records);
        }
    }

    private interface CycleRecord {
        PlanningCycleId id();
    }

    private record CycleObservations(PlanningCycleId id, List<ObservationDefinition> observations)
            implements CycleRecord {
        private CycleObservations { Objects.requireNonNull(id); observations = List.copyOf(observations); }
    }
    private record CycleNeeds(PlanningCycleId id, List<NeedDefinition> needs) implements CycleRecord {
        private CycleNeeds { Objects.requireNonNull(id); needs = List.copyOf(needs); }
    }
    private record CycleOpportunities(PlanningCycleId id, List<OpportunityDefinition> opportunities)
            implements CycleRecord {
        private CycleOpportunities { Objects.requireNonNull(id); opportunities = List.copyOf(opportunities); }
    }
    private record CycleCandidates(PlanningCycleId id, List<CandidatePlanDefinition> candidates)
            implements CycleRecord {
        private CycleCandidates { Objects.requireNonNull(id); candidates = List.copyOf(candidates); }
    }
    private record CycleApproved(PlanningCycleId id, List<ApprovedPlanDefinition> approvedPlans)
            implements CycleRecord {
        private CycleApproved { Objects.requireNonNull(id); approvedPlans = List.copyOf(approvedPlans); }
    }
    private record CycleRuntime(
            PlanningCycleId id,
            long simulationTick,
            PlanningPolicyId policyId,
            PlanningCycleStatus status,
            List<ConstraintDefinition> constraints,
            List<NeedResolutionRuntime> needRuntimes,
            List<ApprovedPlanSubmissionRuntime> submissionRuntimes,
            PlanningCycleReport report,
            long revision,
            int schemaVersion
    ) implements CycleRecord {
        private CycleRuntime {
            Objects.requireNonNull(id); PlanningValidation.tick(simulationTick); Objects.requireNonNull(policyId);
            Objects.requireNonNull(status); constraints = List.copyOf(constraints);
            needRuntimes = List.copyOf(needRuntimes); submissionRuntimes = List.copyOf(submissionRuntimes);
            Objects.requireNonNull(report); PlanningValidation.schema(schemaVersion);
        }
        static CycleRuntime from(PlanningCycleSnapshot value) {
            return new CycleRuntime(value.id(), value.simulationTick(), value.policyId(), value.status(),
                    value.constraints(), value.needRuntimes(), value.submissionRuntimes(), value.report(),
                    value.revision(), value.schemaVersion());
        }
    }

    private static final class OptionalTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Optional.class) return null;
            Type valueType = ((ParameterizedType) type.getType()).getActualTypeArguments()[0];
            TypeAdapter valueAdapter = gson.getAdapter(TypeToken.get(valueType));
            return (TypeAdapter<T>) new TypeAdapter<Optional<?>>() {
                @Override
                public void write(JsonWriter output, Optional<?> source) throws IOException {
                    if (source == null || source.isEmpty()) {
                        output.nullValue();
                    } else {
                        valueAdapter.write(output, source.orElseThrow());
                    }
                }

                @Override
                public Optional<?> read(JsonReader input) throws IOException {
                    if (input.peek() == JsonToken.NULL) {
                        input.nextNull();
                        return Optional.empty();
                    }
                    return Optional.ofNullable(valueAdapter.read(input));
                }
            };
        }
    }

    private static final class OptionalLongAdapter extends TypeAdapter<OptionalLong> {
        @Override
        public void write(JsonWriter output, OptionalLong source) throws IOException {
            if (source == null || source.isEmpty()) {
                output.nullValue();
            } else {
                output.value(source.getAsLong());
            }
        }

        @Override
        public OptionalLong read(JsonReader input) throws IOException {
            if (input.peek() == JsonToken.NULL) {
                input.nextNull();
                return OptionalLong.empty();
            }
            return OptionalLong.of(input.nextLong());
        }
    }
}
