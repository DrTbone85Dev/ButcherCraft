package com.butchercraft.workstation.endpoint;

import java.util.Base64;

public record WorkstationEndpointStackPayload(
        String encodingIdentity,
        String itemIdentity,
        int count,
        String contentDigest,
        String encodedStack
) {
    public WorkstationEndpointStackPayload {
        encodingIdentity = WorkstationEndpointValidation.id(encodingIdentity, "stack encoding identity");
        itemIdentity = WorkstationEndpointValidation.id(itemIdentity, "item identity");
        count = WorkstationEndpointValidation.positive(count, "stack count");
        contentDigest = WorkstationEndpointValidation.digest(contentDigest, "stack content digest");
        encodedStack = WorkstationEndpointValidation.text(encodedStack, "encoded stack");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedStack);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Encoded stack must use canonical Base64", exception);
        }
        String canonical = Base64.getEncoder().encodeToString(decoded);
        if (!canonical.equals(encodedStack)) {
            throw new IllegalArgumentException("Encoded stack must use canonical Base64");
        }
        String expectedDigest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_stack")
                .add(encodingIdentity)
                .add(decoded)
                .finish();
        if (!expectedDigest.equals(contentDigest)) {
            throw new IllegalArgumentException("Encoded stack does not match its content digest");
        }
    }

    public static WorkstationEndpointStackPayload create(
            String encodingIdentity,
            String itemIdentity,
            int count,
            byte[] encodedStack
    ) {
        String base64 = Base64.getEncoder().encodeToString(encodedStack);
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_endpoint_stack")
                .add(encodingIdentity)
                .add(encodedStack)
                .finish();
        return new WorkstationEndpointStackPayload(encodingIdentity, itemIdentity, count, digest, base64);
    }

    public byte[] decodedStack() {
        return Base64.getDecoder().decode(encodedStack);
    }
}
