package com.butchercraft.workstation.condition;

import java.util.Objects;
import java.util.Optional;

/** The only activation head for retained condition receipts lives inside the Workstation projection. */
public record ConditionProjection(
        Applicability applicability, Optional<MachineConditionState> state, Optional<String> receiptHead,
        Optional<String> pendingOperatingTransition
) {
    public enum Applicability { ENABLED, NOT_APPLICABLE }

    public ConditionProjection {
        Objects.requireNonNull(applicability, "applicability");
        state = Objects.requireNonNull(state, "state");
        receiptHead = Objects.requireNonNull(receiptHead, "receiptHead");
        pendingOperatingTransition = Objects.requireNonNull(pendingOperatingTransition, "pendingOperatingTransition");
        if ((applicability == Applicability.ENABLED) != state.isPresent()) {
            throw new IllegalArgumentException("Condition applicability/state mismatch");
        }
        if (state.isEmpty() && (receiptHead.isPresent() || pendingOperatingTransition.isPresent())) {
            throw new IllegalArgumentException("Non-applicable Workstation cannot hold condition authority");
        }
        if (state.isPresent() && (state.orElseThrow().effectCount() == 0) != receiptHead.isEmpty()) {
            throw new IllegalArgumentException("Condition effect evidence head is missing or unexpected");
        }
    }

    public static ConditionProjection notApplicable() {
        return new ConditionProjection(Applicability.NOT_APPLICABLE, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static ConditionProjection initial(MachineConditionState state) {
        return new ConditionProjection(Applicability.ENABLED, Optional.of(state), Optional.empty(), Optional.empty());
    }

    public ConditionProjection committed(ConditionEffectReceipt receipt) {
        MachineConditionState before = state.orElseThrow();
        if (pendingOperatingTransition.isPresent() || !receipt.postState().instanceId().equals(before.instanceId())
                || receipt.postState().revision() != Math.incrementExact(before.revision())
                || receipt.postState().effectCount() != Math.incrementExact(before.effectCount())
                || !receipt.preConditionDigest().equals(before.digest()) || !receipt.previousReceiptDigest().equals(receiptHead)) {
            throw new IllegalArgumentException("Stale condition effect publication");
        }
        return new ConditionProjection(applicability, Optional.of(receipt.postState()), Optional.of(receipt.digest()),
                pendingOperatingTransition);
    }

    public String digest() {
        ConditionDigest digest = new ConditionDigest("butchercraft:condition_projection/v1").add(applicability.name());
        state.ifPresent(value -> digest.add(value.digest()));
        digest.add(receiptHead.isPresent());
        receiptHead.ifPresent(digest::add);
        digest.add(pendingOperatingTransition.isPresent());
        pendingOperatingTransition.ifPresent(digest::add);
        return digest.finish();
    }
}
