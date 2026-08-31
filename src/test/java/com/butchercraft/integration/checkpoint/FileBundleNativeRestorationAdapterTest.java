package com.butchercraft.integration.checkpoint;

import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileBundleNativeRestorationAdapterTest {
    private static final WorldIdentityRootReference WORLD = new WorldIdentityRootReference(
            "butchercraft:world_identity/test", 6, "sha256:" + "1".repeat(64));

    @Test
    void legacyDomainWorldIdentityIsNotMisclassifiedAsPlatformRootIdentity() {
        var document = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "authorization_evidence": {
                    "world_identity": "butchercraft:world/test"
                  }
                }
                """);

        assertDoesNotThrow(() -> FileBundleNativeRestorationAdapter.validateWorldReferences(document, WORLD));
    }

    @Test
    void structuredAndExplicitRootReferencesRemainStrict() {
        var structuredMismatch = JsonParser.parseString("""
                {"world_identity":{"identity":"butchercraft:world_identity/other","root_digest":"sha256:%s"}}
                """.formatted("1".repeat(64)));
        var explicitMismatch = JsonParser.parseString("""
                {"world_identity_root":"butchercraft:world_identity/other"}
                """);

        assertThrows(IllegalArgumentException.class, () ->
                FileBundleNativeRestorationAdapter.validateWorldReferences(structuredMismatch, WORLD));
        assertThrows(IllegalArgumentException.class, () ->
                FileBundleNativeRestorationAdapter.validateWorldReferences(explicitMismatch, WORLD));
    }
}
