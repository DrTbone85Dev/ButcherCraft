package com.butchercraft.workstation.condition;

import java.util.Objects;
import java.util.Optional;

/** Staged immutable evidence is not an applied effect until referenced by a committed owner projection. */
public record ConditionEffectReceipt(
        int schemaVersion, String effectIdentity, ConditionEffectKind kind, String requestDigest,
        String preConditionDigest, MachineConditionState postState, Optional<String> previousReceiptDigest,
        String ownerCandidateIdentity, Optional<ConditionTransitionBinding> transition,
        Optional<ConditionProcessingBinding> processing, String digest
) {
    public ConditionEffectReceipt {
        if (schemaVersion != 1) throw new UnsupportedConditionSchemaException("condition effect receipt", schemaVersion);
        ConditionDigest.text(effectIdentity, "effectIdentity");
        Objects.requireNonNull(kind, "kind");
        ConditionDigest.text(requestDigest, "requestDigest");
        ConditionDigest.text(preConditionDigest, "preConditionDigest");
        Objects.requireNonNull(postState, "postState");
        previousReceiptDigest = Objects.requireNonNull(previousReceiptDigest, "previousReceiptDigest");
        ConditionDigest.text(ownerCandidateIdentity, "ownerCandidateIdentity");
        transition = Objects.requireNonNull(transition, "transition");
        processing = Objects.requireNonNull(processing, "processing");
        if (processing.isPresent() && (kind != ConditionEffectKind.SUCCESSFUL_PROCESSING || transition.isPresent())) {
            throw new IllegalArgumentException("Processing binding belongs only to a joint processing effect");
        }
        if (transition.filter(value -> !value.successor().workstationInstanceIdentity()
                .equals(postState.instanceId().value())).isPresent()) {
            throw new IllegalArgumentException("Condition transition targets another instance");
        }
        if (!postState.lastEffectIdentity().orElseThrow().equals(effectIdentity)
                || (postState.effectCount() == 1) != previousReceiptDigest.isEmpty()) {
            throw new IllegalArgumentException("Condition receipt lineage mismatch");
        }
        if (!calculate(effectIdentity, kind, requestDigest, preConditionDigest, postState, previousReceiptDigest,
                ownerCandidateIdentity, transition, processing).equals(digest)) throw new IllegalArgumentException("Condition receipt digest mismatch");
    }

    public static ConditionEffectReceipt prepare(MachineConditionState before, MachineConditionState after,
            ConditionEffectKind kind, String requestDigest, Optional<String> previousReceipt, String ownerCandidate) {
        return prepare(before, after, kind, requestDigest, previousReceipt, ownerCandidate, Optional.empty());
    }

    public static ConditionEffectReceipt prepare(MachineConditionState before, MachineConditionState after,
            ConditionEffectKind kind, String requestDigest, Optional<String> previousReceipt, String ownerCandidate,
            Optional<ConditionTransitionBinding> transition) {
        return prepare(before, after, kind, requestDigest, previousReceipt, ownerCandidate, transition, Optional.empty());
    }

    public static ConditionEffectReceipt prepare(MachineConditionState before, MachineConditionState after,
            ConditionEffectKind kind, String requestDigest, Optional<String> previousReceipt, String ownerCandidate,
            Optional<ConditionTransitionBinding> transition, Optional<ConditionProcessingBinding> processing) {
        if (!before.instanceId().equals(after.instanceId()) || after.revision() != Math.incrementExact(before.revision())
                || after.effectCount() != Math.incrementExact(before.effectCount())) {
            throw new IllegalArgumentException("Condition effect is not an exact instance successor");
        }
        String effect = after.lastEffectIdentity().orElseThrow();
        return new ConditionEffectReceipt(1, effect, kind, requestDigest, before.digest(), after,
                previousReceipt, ownerCandidate, transition, processing,
                calculate(effect, kind, requestDigest, before.digest(), after, previousReceipt, ownerCandidate, transition, processing));
    }

    private static String calculate(String effect, ConditionEffectKind kind, String request, String before,
            MachineConditionState after, Optional<String> previous, String owner, Optional<ConditionTransitionBinding> transition,
            Optional<ConditionProcessingBinding> processing) {
        ConditionDigest digest = new ConditionDigest("butchercraft:condition_effect_receipt/v1")
                .add(1).add(effect).add(kind.name()).add(request).add(before).add(after.digest())
                .add(previous.isPresent());
        previous.ifPresent(digest::add);
        digest.add(owner).add(transition.isPresent());
        transition.ifPresent(value -> digest.add(value.identity()));
        digest.add(processing.isPresent());
        processing.ifPresent(value -> digest.add(value.identity()));
        return digest.finish();
    }
}
