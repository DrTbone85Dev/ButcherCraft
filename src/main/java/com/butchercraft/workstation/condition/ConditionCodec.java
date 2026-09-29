package com.butchercraft.workstation.condition;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ConditionCodec {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    public JsonObject projection(ConditionProjection value) {
        JsonObject json = new JsonObject();
        json.addProperty("applicability", value.applicability().name());
        value.state().ifPresent(state -> json.add("state", state(state)));
        optional(json, "receipt_head", value.receiptHead());
        optional(json, "pending_operating_transition", value.pendingOperatingTransition());
        return json;
    }

    public ConditionProjection projection(JsonObject json) {
        return new ConditionProjection(ConditionProjection.Applicability.valueOf(text(json, "applicability")),
                json.has("state") ? Optional.of(state(object(json, "state"))) : Optional.empty(),
                optional(json, "receipt_head"), optional(json, "pending_operating_transition"));
    }

    public byte[] freeze(ConditionEffectReceipt value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", value.schemaVersion());
        json.addProperty("effect_identity", value.effectIdentity());
        json.addProperty("kind", value.kind().name());
        json.addProperty("request_digest", value.requestDigest());
        json.addProperty("pre_condition_digest", value.preConditionDigest());
        json.add("post_state", state(value.postState()));
        optional(json, "previous_receipt_digest", value.previousReceiptDigest());
        json.addProperty("owner_candidate_identity", value.ownerCandidateIdentity());
        value.processing().ifPresent(processing -> {
            JsonObject binding = new JsonObject();
            binding.addProperty("operation", processing.operation().value());
            binding.addProperty("post_inventory_digest", processing.postInventoryDigest());
            binding.addProperty("planned_projection_revision", processing.plannedProjectionRevision());
            json.add("processing", binding);
        });
        value.transition().ifPresent(transition -> {
            JsonObject binding = new JsonObject();
            transition.previous().ifPresent(prior -> binding.add("previous", operatingReference(prior)));
            binding.add("successor", operatingReference(transition.successor()));
            binding.addProperty("tick", transition.tick());
            json.add("operating_transition", binding);
        });
        json.addProperty("digest", value.digest());
        return (GSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    public ConditionEffectReceipt receipt(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > 131_072) throw new IllegalArgumentException("Invalid condition receipt size");
        JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        return new ConditionEffectReceipt(Math.toIntExact(number(json, "schema_version")), text(json, "effect_identity"),
                ConditionEffectKind.valueOf(text(json, "kind")), text(json, "request_digest"),
                text(json, "pre_condition_digest"), state(object(json, "post_state")),
                optional(json, "previous_receipt_digest"), text(json, "owner_candidate_identity"),
                json.has("operating_transition") ? Optional.of(transition(object(json, "operating_transition")))
                        : Optional.empty(), json.has("processing") ? Optional.of(processing(object(json, "processing")))
                        : Optional.empty(), text(json, "digest"));
    }

    private ConditionProcessingBinding processing(JsonObject json) {
        return new ConditionProcessingBinding(new com.butchercraft.world.execution.ExecutionOperationId(text(json, "operation")),
                text(json, "post_inventory_digest"), number(json, "planned_projection_revision"));
    }

    private JsonObject operatingReference(com.butchercraft.workstation.projection.WorkstationOperatingStateReference value) {
        JsonObject result = new JsonObject();
        result.addProperty("instance", value.workstationInstanceIdentity());
        result.addProperty("revision", value.revision());
        result.addProperty("state", value.state());
        result.addProperty("digest", value.contentDigest());
        return result;
    }

    private com.butchercraft.workstation.projection.WorkstationOperatingStateReference operatingReference(JsonObject value) {
        return new com.butchercraft.workstation.projection.WorkstationOperatingStateReference(
                text(value, "instance"), number(value, "revision"), text(value, "state"), text(value, "digest"));
    }

    private ConditionTransitionBinding transition(JsonObject value) {
        return new ConditionTransitionBinding(value.has("previous")
                ? Optional.of(operatingReference(object(value, "previous"))) : Optional.empty(),
                operatingReference(object(value, "successor")), number(value, "tick"));
    }

    public JsonObject state(MachineConditionState value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", value.schemaVersion());
        json.addProperty("instance_identity", value.instanceId().value());
        json.add("policy", policy(value.policy()));
        json.addProperty("revision", value.revision());
        json.addProperty("mechanical_loss", value.mechanicalLoss());
        json.addProperty("service_debt", value.serviceDebt());
        JsonArray accounts = new JsonArray();
        for (ConditionExposureAccount account : value.exposureAccounts()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", account.type().name());
            entry.addProperty("eligible_ticks", account.eligibleTicks());
            entry.addProperty("grace_progress", account.graceProgress());
            entry.addProperty("remainder_numerator", account.remainder().numerator());
            entry.addProperty("remainder_denominator", account.remainder().denominator());
            accounts.add(entry);
        }
        json.add("exposure_accounts", accounts);
        value.activeExposure().ifPresent(active -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("identity", active.identity());
            entry.addProperty("type", active.type().name());
            entry.addProperty("policy_identity", active.policyIdentity());
            entry.addProperty("operating_transition_identity", active.operatingTransitionIdentity());
            entry.addProperty("operating_revision", active.operatingRevision());
            entry.addProperty("availability_proof_identity", active.availabilityProofIdentity());
            entry.addProperty("start_tick", active.startTick());
            entry.addProperty("accounted_through_tick", active.accountedThroughTick());
            entry.addProperty("opening_condition_revision", active.openingConditionRevision());
            json.add("active_exposure", entry);
        });
        value.fault().ifPresent(fault -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("instance_identity", fault.instanceId().value());
            entry.addProperty("type", fault.type().name());
            entry.addProperty("cause_effect_identity", fault.causeEffectIdentity());
            entry.addProperty("condition_revision", fault.conditionRevision());
            entry.addProperty("policy_identity", fault.policyIdentity());
            entry.addProperty("authoritative_tick", fault.authoritativeTick());
            entry.addProperty("identity", fault.identity());
            json.add("fault", entry);
        });
        json.addProperty("last_accounted_tick", value.lastAccountedTick());
        json.addProperty("suspension", value.suspension().name());
        json.addProperty("unproven_tail_discarded", value.unprovenTailDiscarded());
        ConditionInitializationEvidence initialization = value.initializationEvidence();
        JsonObject initial = new JsonObject();
        initial.addProperty("origin", initialization.origin().name());
        initial.addProperty("instance_identity", initialization.instanceId().value());
        initial.addProperty("policy_identity", initialization.policyIdentity());
        optional(initial, "legacy_projection_digest", initialization.legacyProjectionDigest());
        initial.addProperty("migration_policy_identity", initialization.migrationPolicyIdentity());
        initial.addProperty("identity", initialization.identity());
        json.add("initialization_evidence", initial);
        json.addProperty("effect_count", value.effectCount());
        optional(json, "last_effect_identity", value.lastEffectIdentity());
        json.addProperty("digest", value.digest());
        return json;
    }

    public MachineConditionState state(JsonObject json) {
        List<ConditionExposureAccount> accounts = new ArrayList<>();
        for (JsonElement element : array(json, "exposure_accounts")) {
            JsonObject entry = element.getAsJsonObject();
            accounts.add(new ConditionExposureAccount(ConditionExposureType.valueOf(text(entry, "type")),
                    number(entry, "eligible_ticks"), number(entry, "grace_progress"),
                    new ConditionRemainder(number(entry, "remainder_numerator"), number(entry, "remainder_denominator"))));
        }
        Optional<ConditionActiveExposure> active = Optional.empty();
        if (json.has("active_exposure")) {
            JsonObject entry = object(json, "active_exposure");
            active = Optional.of(new ConditionActiveExposure(text(entry, "identity"),
                    ConditionExposureType.valueOf(text(entry, "type")), text(entry, "policy_identity"),
                    text(entry, "operating_transition_identity"), number(entry, "operating_revision"),
                    text(entry, "availability_proof_identity"), number(entry, "start_tick"),
                    number(entry, "accounted_through_tick"), number(entry, "opening_condition_revision")));
        }
        Optional<ConditionFault> fault = Optional.empty();
        if (json.has("fault")) {
            JsonObject entry = object(json, "fault");
            fault = Optional.of(new ConditionFault(new WorkstationInstanceId(text(entry, "instance_identity")),
                    ConditionFault.Type.valueOf(text(entry, "type")), text(entry, "cause_effect_identity"),
                    number(entry, "condition_revision"), text(entry, "policy_identity"),
                    number(entry, "authoritative_tick"), text(entry, "identity")));
        }
        JsonObject initial = object(json, "initialization_evidence");
        ConditionInitializationEvidence initialization = new ConditionInitializationEvidence(
                ConditionInitializationEvidence.Origin.valueOf(text(initial, "origin")),
                new WorkstationInstanceId(text(initial, "instance_identity")), text(initial, "policy_identity"),
                optional(initial, "legacy_projection_digest"), text(initial, "migration_policy_identity"),
                text(initial, "identity"));
        return new MachineConditionState(Math.toIntExact(number(json, "schema_version")),
                new WorkstationInstanceId(text(json, "instance_identity")), policy(object(json, "policy")),
                number(json, "revision"), number(json, "mechanical_loss"), number(json, "service_debt"),
                accounts, active, fault, number(json, "last_accounted_tick"),
                MachineConditionState.Suspension.valueOf(text(json, "suspension")), bool(json, "unproven_tail_discarded"),
                initialization, number(json, "effect_count"),
                optional(json, "last_effect_identity"), text(json, "digest"));
    }

    private JsonObject policy(MachineConditionPolicy value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", value.schemaVersion());
        json.addProperty("machine_type", value.machineType());
        json.addProperty("configuration_identity", value.configurationIdentity());
        json.addProperty("maximum_loss", value.maximumLoss());
        json.addProperty("processing_loss", value.processingLoss());
        json.addProperty("processing_service_debt", value.processingServiceDebt());
        json.addProperty("service_soon", value.serviceSoon());
        json.addProperty("service_due", value.serviceDue());
        json.addProperty("critical_loss", value.criticalLoss());
        value.faultLoss().ifPresent(loss -> json.addProperty("fault_loss", loss));
        JsonArray exposures = new JsonArray();
        for (ConditionExposurePolicy exposure : value.exposures()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", exposure.type().name());
            entry.addProperty("grace_ticks", exposure.graceTicks());
            entry.addProperty("loss_numerator", exposure.lossNumerator());
            entry.addProperty("loss_denominator", exposure.lossDenominator());
            entry.addProperty("settlement_ticks", exposure.settlementTicks());
            entry.addProperty("reset_grace_on_success", exposure.resetGraceOnSuccessfulProcessing());
            exposures.add(entry);
        }
        json.add("exposures", exposures);
        json.addProperty("identity", value.identity());
        return json;
    }

    private MachineConditionPolicy policy(JsonObject json) {
        List<ConditionExposurePolicy> exposures = new ArrayList<>();
        for (JsonElement element : array(json, "exposures")) {
            JsonObject entry = element.getAsJsonObject();
            exposures.add(new ConditionExposurePolicy(ConditionExposureType.valueOf(text(entry, "type")),
                    number(entry, "grace_ticks"), number(entry, "loss_numerator"), number(entry, "loss_denominator"),
                    number(entry, "settlement_ticks"), bool(entry, "reset_grace_on_success")));
        }
        return new MachineConditionPolicy(Math.toIntExact(number(json, "schema_version")), text(json, "machine_type"),
                text(json, "configuration_identity"), number(json, "maximum_loss"), number(json, "processing_loss"),
                number(json, "processing_service_debt"), number(json, "service_soon"), number(json, "service_due"),
                number(json, "critical_loss"), json.has("fault_loss") ? Optional.of(number(json, "fault_loss"))
                : Optional.empty(), exposures, text(json, "identity"));
    }

    private static JsonElement required(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull()) throw new IllegalArgumentException("Missing condition field: " + key);
        return value;
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = required(json, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected condition text: " + key);
        }
        return ConditionDigest.text(value.getAsString(), key);
    }

    private static long number(JsonObject json, String key) {
        JsonElement value = required(json, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected condition integer: " + key);
        }
        return value.getAsBigDecimal().longValueExact();
    }

    private static boolean bool(JsonObject json, String key) {
        JsonElement value = required(json, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected condition boolean: " + key);
        }
        return value.getAsBoolean();
    }

    private static JsonObject object(JsonObject json, String key) { return required(json, key).getAsJsonObject(); }
    private static JsonArray array(JsonObject json, String key) { return required(json, key).getAsJsonArray(); }
    private static Optional<String> optional(JsonObject json, String key) {
        return json.has(key) ? Optional.of(text(json, key)) : Optional.empty();
    }
    private static void optional(JsonObject json, String key, Optional<String> value) {
        value.ifPresent(text -> json.addProperty(key, text));
    }
}
