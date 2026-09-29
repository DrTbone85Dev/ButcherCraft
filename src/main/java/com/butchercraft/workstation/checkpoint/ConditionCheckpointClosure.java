package com.butchercraft.workstation.checkpoint;

import com.butchercraft.workstation.condition.ConditionCodec;
import com.butchercraft.workstation.condition.ConditionEffectReceipt;
import com.butchercraft.workstation.condition.ConditionEvidenceClosure;
import com.butchercraft.workstation.condition.ConditionProjection;
import com.butchercraft.workstation.condition.ConditionReceiptStorage;
import com.butchercraft.workstation.projection.DurableWorkstationProjection;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Workstation-owned receipt closure, frozen with the projection that activates it. */
public final class ConditionCheckpointClosure {
    private static final ConditionCodec CODEC = new ConditionCodec();
    private final Map<String, byte[]> receipts = new TreeMap<>();

    public void include(DurableWorkstationProjection projection, Function<String, byte[]> source) {
        ConditionProjection condition = projection.condition().orElseThrow(() ->
                new IllegalStateException("Current checkpoint requires explicit condition applicability"));
        ConditionEvidenceClosure.verify(condition, digest -> {
            byte[] bytes = source.apply(digest);
            ConditionEffectReceipt receipt = CODEC.receipt(bytes);
            byte[] existing = receipts.putIfAbsent(digest, bytes.clone());
            if (existing != null && !java.util.Arrays.equals(existing, bytes)) {
                throw new IllegalArgumentException("Conflicting frozen condition receipt bytes");
            }
            return receipt;
        });
    }

    public JsonArray encode() {
        JsonArray result = new JsonArray();
        receipts.forEach((digest, bytes) -> {
            JsonObject record = new JsonObject();
            record.addProperty("receipt_digest", digest);
            record.addProperty("payload_length", bytes.length);
            record.addProperty("payload_digest", CheckpointSnapshotDigest.sha256(bytes));
            record.addProperty("payload_base64", Base64.getEncoder().encodeToString(bytes));
            result.add(record);
        });
        return result;
    }

    public static ConditionCheckpointClosure decode(JsonArray records,
            List<DurableWorkstationProjection> projections) {
        Map<String, byte[]> supplied = new TreeMap<>();
        for (JsonElement element : records) {
            JsonObject record = element.getAsJsonObject();
            String digest = record.get("receipt_digest").getAsString();
            byte[] bytes = Base64.getDecoder().decode(record.get("payload_base64").getAsString());
            if (bytes.length != record.get("payload_length").getAsBigDecimal().intValueExact()
                    || !CheckpointSnapshotDigest.sha256(bytes).equals(record.get("payload_digest").getAsString())
                    || !CODEC.receipt(bytes).digest().equals(digest)
                    || supplied.putIfAbsent(digest, bytes) != null) {
                throw new IllegalArgumentException("Invalid or duplicate checkpoint condition receipt");
            }
        }
        ConditionCheckpointClosure closure = new ConditionCheckpointClosure();
        projections.forEach(projection -> closure.include(projection, digest -> {
            byte[] bytes = supplied.get(digest);
            if (bytes == null) throw new IllegalArgumentException("Required condition evidence is missing");
            return bytes;
        }));
        if (!closure.receipts.keySet().equals(supplied.keySet())) {
            throw new IllegalArgumentException("Checkpoint contains condition receipts outside required closure");
        }
        return closure;
    }

    public List<OwnerNativeRestorationPlan.NativeFile> nativeFiles(Path ownerRoot) {
        Path root = ownerRoot.toAbsolutePath().normalize();
        ConditionReceiptStorage storage = new ConditionReceiptStorage(root.resolve("workstations/condition_effects/v1"));
        List<OwnerNativeRestorationPlan.NativeFile> files = new ArrayList<>();
        receipts.forEach((digest, bytes) -> files.add(OwnerNativeRestorationPlan.NativeFile.of(
                "workstation_condition_receipt/" + digest,
                root.relativize(storage.pathFor(digest)).toString().replace('\\', '/'), bytes)));
        return List.copyOf(files);
    }
}
