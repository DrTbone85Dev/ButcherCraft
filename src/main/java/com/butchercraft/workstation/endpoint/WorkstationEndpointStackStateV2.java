package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.Optional;

/** Exact schema-2 slot state; content identity includes authoritative count. */
public record WorkstationEndpointStackStateV2(
        Optional<WorkstationEndpointStackPayload> stack,
        String contentIdentity,
        String compatibilityIdentity
) {
    private static final String CONTENT_PREFIX = "butchercraft:workstation_stack_content/v2/";
    private static final String COMPATIBILITY_PREFIX = "butchercraft:workstation_stack_compatibility/v2/";
    private static final String EMPTY_COMPATIBILITY = "butchercraft:workstation_stack_compatibility/v2/empty";

    public WorkstationEndpointStackStateV2 {
        stack = Objects.requireNonNull(stack, "stack");
        contentIdentity = WorkstationEndpointValidation.id(contentIdentity, "stack content identity");
        compatibilityIdentity = WorkstationEndpointValidation.id(
                compatibilityIdentity,
                "stack compatibility identity"
        );
        String expectedContent = contentIdentity(stack);
        if (!expectedContent.equals(contentIdentity)) {
            throw new IllegalArgumentException("Schema-2 stack content identity is not canonical");
        }
        if (stack.isEmpty() && !EMPTY_COMPATIBILITY.equals(compatibilityIdentity)) {
            throw new IllegalArgumentException("Empty stack state must use the canonical empty compatibility identity");
        }
        if (stack.isPresent() && !compatibilityIdentity.startsWith(COMPATIBILITY_PREFIX)) {
            throw new IllegalArgumentException("Non-empty stack state has unsupported compatibility identity");
        }
    }

    public static WorkstationEndpointStackStateV2 empty() {
        return new WorkstationEndpointStackStateV2(Optional.empty(), contentIdentity(Optional.empty()), EMPTY_COMPATIBILITY);
    }

    public static WorkstationEndpointStackStateV2 create(
            WorkstationEndpointStackPayload exactStack,
            WorkstationEndpointStackPayload normalizedOneCountStack
    ) {
        Objects.requireNonNull(exactStack, "exactStack");
        Objects.requireNonNull(normalizedOneCountStack, "normalizedOneCountStack");
        if (normalizedOneCountStack.count() != 1
                || !exactStack.itemIdentity().equals(normalizedOneCountStack.itemIdentity())
                || !exactStack.encodingIdentity().equals(normalizedOneCountStack.encodingIdentity())) {
            throw new IllegalArgumentException("Compatibility payload must be the same exact stack normalized to one");
        }
        String compatibilityDigest = WorkstationEndpointCanonicalDigest
                .create("butchercraft:workstation_stack_compatibility")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(normalizedOneCountStack.contentDigest())
                .finish();
        return new WorkstationEndpointStackStateV2(
                Optional.of(exactStack),
                contentIdentity(Optional.of(exactStack)),
                COMPATIBILITY_PREFIX + WorkstationEndpointCanonicalDigest.suffix(compatibilityDigest)
        );
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    public int count() {
        return stack.map(WorkstationEndpointStackPayload::count).orElse(0);
    }

    public boolean compatibleWith(WorkstationEndpointStackStateV2 other) {
        Objects.requireNonNull(other, "other");
        return !isEmpty() && !other.isEmpty() && compatibilityIdentity.equals(other.compatibilityIdentity);
    }

    private static String contentIdentity(Optional<WorkstationEndpointStackPayload> stack) {
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_stack_content")
                .add(WorkstationEndpointSchema.STACK_AWARE_ENDPOINT_PROTOCOL_VERSION)
                .add(stack.map(WorkstationEndpointStackPayload::contentDigest).orElse("empty"))
                .add(stack.map(WorkstationEndpointStackPayload::count).orElse(0))
                .finish();
        return CONTENT_PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest);
    }
}
