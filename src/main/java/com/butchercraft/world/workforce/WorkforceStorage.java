package com.butchercraft.world.workforce;

import com.butchercraft.world.business.BusinessId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

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

public final class WorkforceStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private static final String SCHEMA_VERSION = "schema_version";
    private static final String DEFINITIONS = "workforce_definitions";
    private static final String WORKFORCE_DEFINITION_ID = "workforce_definition_id";
    private static final String BUSINESS_ID = "business_id";
    private static final String POSITIONS = "positions";
    private static final String POSITION_ID = "position_id";
    private static final String POSITION_TYPE = "position_type";
    private static final String DISPLAY_NAME = "display_name";
    private static final String REQUIRED_SKILL_LEVEL = "required_skill_level";
    private static final String REQUIRED_CERTIFICATIONS = "required_certifications";
    private static final String ASSIGNED_SHIFT = "assigned_shift";
    private static final String REQUIRED = "required";
    private static final String MAXIMUM_WORKERS = "maximum_workers";
    private static final String SHIFT_ASSIGNMENTS = "shift_assignments";
    private static final String SHIFT_ID = "shift_id";
    private static final String MINIMUM_WORKERS = "minimum_workers";
    private static final String STAFFING_RULE = "staffing_rule";
    private static final String REQUIRED_POSITIONS = "required_positions";
    private static final String OPTIONAL_POSITIONS = "optional_positions";
    private static final String MINIMUM_STAFFING = "minimum_staffing";
    private static final String MAXIMUM_STAFFING = "maximum_staffing";

    private final Path filePath;

    public WorkforceStorage(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
    }

    public Path filePath() {
        return filePath;
    }

    public WorkforceRegistry load() {
        if (!Files.exists(filePath)) {
            return WorkforceRegistry.empty();
        }
        try {
            return deserialize(Files.readString(filePath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load workforce definitions from " + filePath, exception);
        }
    }

    public void save(WorkforceRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temporaryFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            Files.writeString(temporaryFile, serialize(registry), StandardCharsets.UTF_8);
            moveIntoPlace(temporaryFile);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to save workforce definitions to " + filePath, exception);
        }
    }

    public String serialize(WorkforceRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        JsonObject root = new JsonObject();
        root.addProperty(SCHEMA_VERSION, WorkforceSchema.CURRENT_VERSION);
        JsonArray definitions = new JsonArray();
        for (WorkforceDefinition definition : registry.definitions()) {
            definitions.add(serializeDefinition(definition));
        }
        root.add(DEFINITIONS, definitions);
        return GSON.toJson(root) + System.lineSeparator();
    }

    public WorkforceRegistry deserialize(String json) {
        Objects.requireNonNull(json, "json");
        try {
            JsonObject root = requireObject(JsonParser.parseString(json), "workforce root");
            int schemaVersion = requireInt(root, SCHEMA_VERSION);
            if (schemaVersion != WorkforceSchema.CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported workforce schema version: " + schemaVersion);
            }
            JsonArray definitions = requireArray(root, DEFINITIONS);
            List<WorkforceDefinition> records = new ArrayList<>();
            for (JsonElement element : definitions) {
                records.add(deserializeDefinition(requireObject(element, "workforce definition")));
            }
            return WorkforceRegistry.of(records);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new IllegalArgumentException("Corrupt workforce persistence", exception);
        }
    }

    private JsonObject serializeDefinition(WorkforceDefinition definition) {
        JsonObject object = new JsonObject();
        object.addProperty(SCHEMA_VERSION, definition.schemaVersion());
        object.addProperty(WORKFORCE_DEFINITION_ID, definition.workforceDefinitionId().value());
        object.addProperty(BUSINESS_ID, definition.businessId().value());
        JsonArray positions = new JsonArray();
        for (WorkforcePosition position : definition.positions()) {
            positions.add(serializePosition(position));
        }
        object.add(POSITIONS, positions);
        JsonArray shiftAssignments = new JsonArray();
        for (WorkforceShiftAssignment assignment : definition.shiftAssignments()) {
            shiftAssignments.add(serializeShiftAssignment(assignment));
        }
        object.add(SHIFT_ASSIGNMENTS, shiftAssignments);
        object.add(STAFFING_RULE, serializeStaffingRule(definition.staffingRule()));
        return object;
    }

    private WorkforceDefinition deserializeDefinition(JsonObject object) {
        int schemaVersion = requireInt(object, SCHEMA_VERSION);
        if (schemaVersion != WorkforceSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported workforce definition schema version: " + schemaVersion);
        }
        return new WorkforceDefinition(
                new BusinessId(requireString(object, BUSINESS_ID)),
                new WorkforceDefinitionId(requireString(object, WORKFORCE_DEFINITION_ID)),
                deserializePositions(requireArray(object, POSITIONS)),
                deserializeShiftAssignments(requireArray(object, SHIFT_ASSIGNMENTS)),
                deserializeStaffingRule(requireObject(requireField(object, STAFFING_RULE), STAFFING_RULE)),
                schemaVersion
        );
    }

    private JsonObject serializePosition(WorkforcePosition position) {
        JsonObject object = new JsonObject();
        object.addProperty(POSITION_ID, position.positionId().value());
        object.addProperty(POSITION_TYPE, position.positionType().serializedName());
        object.addProperty(DISPLAY_NAME, position.displayName());
        object.addProperty(REQUIRED_SKILL_LEVEL, position.requiredSkillLevel().serializedName());
        JsonArray certifications = new JsonArray();
        for (CertificationType certification : position.requiredCertifications()) {
            certifications.add(certification.serializedName());
        }
        object.add(REQUIRED_CERTIFICATIONS, certifications);
        object.addProperty(ASSIGNED_SHIFT, position.assignedShiftId());
        object.addProperty(REQUIRED, position.required());
        object.addProperty(MAXIMUM_WORKERS, position.maximumWorkers());
        return object;
    }

    private List<WorkforcePosition> deserializePositions(JsonArray array) {
        List<WorkforcePosition> positions = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = requireObject(element, "workforce position");
            positions.add(new WorkforcePosition(
                    new PositionId(requireString(object, POSITION_ID)),
                    WorkforcePositionType.fromSerializedName(requireString(object, POSITION_TYPE)),
                    requireString(object, DISPLAY_NAME),
                    WorkforceSkillLevel.fromSerializedName(requireString(object, REQUIRED_SKILL_LEVEL)),
                    deserializeCertifications(requireArray(object, REQUIRED_CERTIFICATIONS)),
                    requireString(object, ASSIGNED_SHIFT),
                    requireBoolean(object, REQUIRED),
                    requireInt(object, MAXIMUM_WORKERS)
            ));
        }
        return positions;
    }

    private List<CertificationType> deserializeCertifications(JsonArray array) {
        List<CertificationType> certifications = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Workforce certification must be a string");
            }
            certifications.add(CertificationType.fromSerializedName(element.getAsString()));
        }
        return certifications;
    }

    private JsonObject serializeShiftAssignment(WorkforceShiftAssignment assignment) {
        JsonObject object = new JsonObject();
        object.addProperty(SHIFT_ID, assignment.shiftId());
        object.addProperty(POSITION_ID, assignment.positionId().value());
        object.addProperty(MINIMUM_WORKERS, assignment.minimumWorkers());
        object.addProperty(MAXIMUM_WORKERS, assignment.maximumWorkers());
        return object;
    }

    private List<WorkforceShiftAssignment> deserializeShiftAssignments(JsonArray array) {
        List<WorkforceShiftAssignment> assignments = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject object = requireObject(element, "workforce shift assignment");
            assignments.add(new WorkforceShiftAssignment(
                    requireString(object, SHIFT_ID),
                    new PositionId(requireString(object, POSITION_ID)),
                    requireInt(object, MINIMUM_WORKERS),
                    requireInt(object, MAXIMUM_WORKERS)
            ));
        }
        return assignments;
    }

    private JsonObject serializeStaffingRule(WorkforceStaffingRule staffingRule) {
        JsonObject object = new JsonObject();
        object.add(REQUIRED_POSITIONS, serializePositionIds(staffingRule.requiredPositions()));
        object.add(OPTIONAL_POSITIONS, serializePositionIds(staffingRule.optionalPositions()));
        object.addProperty(MINIMUM_STAFFING, staffingRule.minimumStaffing());
        object.addProperty(MAXIMUM_STAFFING, staffingRule.maximumStaffing());
        return object;
    }

    private WorkforceStaffingRule deserializeStaffingRule(JsonObject object) {
        return new WorkforceStaffingRule(
                deserializePositionIds(requireArray(object, REQUIRED_POSITIONS)),
                deserializePositionIds(requireArray(object, OPTIONAL_POSITIONS)),
                requireInt(object, MINIMUM_STAFFING),
                requireInt(object, MAXIMUM_STAFFING)
        );
    }

    private JsonArray serializePositionIds(List<PositionId> positionIds) {
        JsonArray array = new JsonArray();
        for (PositionId positionId : positionIds) {
            array.add(positionId.value());
        }
        return array;
    }

    private List<PositionId> deserializePositionIds(JsonArray array) {
        List<PositionId> positionIds = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Workforce position id must be a string");
            }
            positionIds.add(new PositionId(element.getAsString()));
        }
        return positionIds;
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static JsonObject requireObject(JsonElement element, String label) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object for " + label);
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("Workforce field must be an array: " + fieldName);
        }
        return element.getAsJsonArray();
    }

    private static int requireInt(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Workforce field must be a number: " + fieldName);
        }
        return element.getAsInt();
    }

    private static boolean requireBoolean(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Workforce field must be a boolean: " + fieldName);
        }
        return element.getAsBoolean();
    }

    private static String requireString(JsonObject object, String fieldName) {
        JsonElement element = requireField(object, fieldName);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Workforce field must be a string: " + fieldName);
        }
        String value = element.getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Workforce field must not be blank: " + fieldName);
        }
        return value;
    }

    private static JsonElement requireField(JsonObject object, String fieldName) {
        JsonElement element = object.get(fieldName);
        if (element == null) {
            throw new IllegalArgumentException("Missing workforce field: " + fieldName);
        }
        return element;
    }
}
