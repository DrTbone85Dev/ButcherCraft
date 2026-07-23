package com.butchercraft.world.allocation;

public record AllocationCycleDigests(
        String inputDigest,
        String orderingDigest,
        String initialLedgerDigest,
        String finalLedgerDigest,
        String commitmentDigest,
        String reportDigest,
        String publicationDigest,
        String resultDigest
) {
    public AllocationCycleDigests {
        inputDigest = AllocationCanonicalDigest.validate(
                inputDigest,
                "inputDigest"
        );
        orderingDigest = AllocationCanonicalDigest.validate(
                orderingDigest,
                "orderingDigest"
        );
        initialLedgerDigest = AllocationCanonicalDigest.validate(
                initialLedgerDigest,
                "initialLedgerDigest"
        );
        finalLedgerDigest = AllocationCanonicalDigest.validate(
                finalLedgerDigest,
                "finalLedgerDigest"
        );
        commitmentDigest = AllocationCanonicalDigest.validate(
                commitmentDigest,
                "commitmentDigest"
        );
        reportDigest = AllocationCanonicalDigest.validate(
                reportDigest,
                "reportDigest"
        );
        publicationDigest = AllocationCanonicalDigest.validate(
                publicationDigest,
                "publicationDigest"
        );
        resultDigest = AllocationCanonicalDigest.validate(
                resultDigest,
                "resultDigest"
        );
    }
}
