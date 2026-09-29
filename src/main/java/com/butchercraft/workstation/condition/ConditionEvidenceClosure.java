package com.butchercraft.workstation.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ConditionEvidenceClosure {
    private ConditionEvidenceClosure() { }

    public static List<ConditionEffectReceipt> verify(ConditionProjection projection,
            Function<String, ConditionEffectReceipt> resolver) {
        if (projection.pendingOperatingTransition().isPresent()) {
            throw new IllegalStateException("Condition/operating transition has not reconciled");
        }
        if (projection.state().isEmpty()) return List.of();
        MachineConditionState state = projection.state().orElseThrow();
        long remaining = state.effectCount();
        String expectedPost = state.digest();
        Optional<String> next = projection.receiptHead();
        List<ConditionEffectReceipt> receipts = new ArrayList<>();
        java.util.Set<String> effects = new java.util.HashSet<>();
        while (next.isPresent()) {
            String reference = next.orElseThrow();
            ConditionEffectReceipt receipt = resolver.apply(reference);
            if (receipt == null || !receipt.digest().equals(reference) || remaining <= 0
                    || !receipt.postState().instanceId().equals(state.instanceId())
                    || receipt.postState().effectCount() != remaining || !receipt.postState().digest().equals(expectedPost)
                    || !effects.add(receipt.effectIdentity())) {
                throw new IllegalStateException("Condition receipt closure conflicts with committed owner state");
            }
            receipts.add(receipt);
            remaining--;
            expectedPost = receipt.preConditionDigest();
            next = receipt.previousReceiptDigest();
        }
        if (remaining != 0) throw new IllegalStateException("Required condition effect evidence is missing");
        state.fault().ifPresent(fault -> {
            if (receipts.stream().noneMatch(receipt -> receipt.effectIdentity().equals(fault.causeEffectIdentity())
                    && receipt.postState().fault().filter(fault::equals).isPresent())) {
                throw new IllegalStateException("Controlling mechanical fault has no exact causing receipt");
            }
        });
        return List.copyOf(receipts);
    }
}
