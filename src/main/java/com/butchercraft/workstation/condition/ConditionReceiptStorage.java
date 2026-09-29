package com.butchercraft.workstation.condition;

import com.butchercraft.persistence.AtomicFilePublication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable Workstation evidence; only the projection head determines which staged receipts are applied. */
public final class ConditionReceiptStorage {
    private static final String LABEL = "Workstation condition effect receipt";
    private static final int HOT_RECEIPTS = 256;
    private final Path directory;
    private final ConditionCodec codec = new ConditionCodec();
    private final Map<String, ConditionEffectReceipt> hot = new LinkedHashMap<>(HOT_RECEIPTS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ConditionEffectReceipt> eldest) {
            return size() > HOT_RECEIPTS;
        }
    };

    public ConditionReceiptStorage(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    public Path pathFor(String digest) {
        if (digest == null || !digest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid condition receipt digest");
        }
        String hash = digest.substring(7);
        return directory.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4)).resolve(hash + ".json");
    }

    public synchronized void stage(ConditionEffectReceipt receipt) {
        byte[] frozen = codec.freeze(receipt);
        Path target = pathFor(receipt.digest());
        AtomicFilePublication.requireNoInterruptedPublication(target, LABEL);
        if (!Files.exists(target)) {
            AtomicFilePublication.publishBytesIfDigestMatches(target, Optional.empty(), frozen, LABEL);
        }
        ConditionEffectReceipt verified = codec.receipt(AtomicFilePublication.readBytes(target, LABEL));
        if (!receipt.equals(verified)) throw new IllegalStateException("Conflicting immutable condition receipt");
        hot.put(receipt.digest(), verified);
    }

    public synchronized ConditionEffectReceipt read(String digest) {
        ConditionEffectReceipt cached = hot.get(digest);
        if (cached != null) return cached;
        Path target = pathFor(digest);
        AtomicFilePublication.requireNoInterruptedPublication(target, LABEL);
        ConditionEffectReceipt receipt = codec.receipt(AtomicFilePublication.readBytes(target, LABEL));
        if (!receipt.digest().equals(digest)) throw new IllegalStateException("Condition receipt path/digest mismatch");
        hot.put(digest, receipt);
        return receipt;
    }

    public List<ConditionEffectReceipt> closure(ConditionProjection projection) {
        return ConditionEvidenceClosure.verify(projection, this::read);
    }

    public Optional<ConditionEffectReceipt> observe(ConditionProjection committed, String effectIdentity,
            String requestDigest) {
        Optional<ConditionEffectReceipt> result = closure(committed).stream()
                .filter(receipt -> receipt.effectIdentity().equals(effectIdentity)).findFirst();
        if (result.isPresent() && !result.orElseThrow().requestDigest().equals(requestDigest)) {
            throw new IllegalStateException("Same condition effect identity with conflicting request");
        }
        return result;
    }

    public byte[] frozen(String digest) {
        byte[] bytes = AtomicFilePublication.readBytes(pathFor(digest), LABEL);
        if (!codec.receipt(bytes).digest().equals(digest)) throw new IllegalStateException("Condition receipt digest mismatch");
        return bytes;
    }

    public synchronized int hotSize() { return hot.size(); }
}
