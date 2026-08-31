package com.butchercraft.workstation.block;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationEndpointProjectionCompatibilityTest {
    private static final String LEGACY_EFFECT =
            "butchercraft:workstation_endpoint_effect/v1/" + "1".repeat(64);
    private static final String STACK_AWARE_EFFECT =
            "butchercraft:workstation_endpoint_effect/v2/" + "2".repeat(64);
    private static final String OWNER_RESULT = "butchercraft:workstation_endpoint_result/v1/test";

    @Test
    void recoveredLegacyEffectRemainsEvidenceAndDoesNotBecomeSchemaTwoAuthority() {
        var markers = AbstractInventoryWorkstationBlockEntity.readStackAwareEffectMarkers(
                markers(LEGACY_EFFECT, OWNER_RESULT));

        assertTrue(markers.activeEffectId().isEmpty());
        assertTrue(markers.activeOwnerResultIdentity().isEmpty());
        assertEquals(LEGACY_EFFECT, markers.recoveredLegacyEffectId().orElseThrow().value());
        assertEquals(OWNER_RESULT, markers.recoveredLegacyOwnerResultIdentity().orElseThrow());
    }

    @Test
    void schemaTwoEffectRemainsActiveAuthority() {
        var markers = AbstractInventoryWorkstationBlockEntity.readStackAwareEffectMarkers(
                markers(STACK_AWARE_EFFECT, OWNER_RESULT));

        assertEquals(STACK_AWARE_EFFECT, markers.activeEffectId().orElseThrow().value());
        assertEquals(OWNER_RESULT, markers.activeOwnerResultIdentity().orElseThrow());
        assertTrue(markers.recoveredLegacyEffectId().isEmpty());
        assertTrue(markers.recoveredLegacyOwnerResultIdentity().isEmpty());
    }

    @Test
    void incompleteOrUnknownMarkersFailVisibly() {
        CompoundTag incomplete = new CompoundTag();
        incomplete.putString("StackAwareLastEffectIdentity", LEGACY_EFFECT);
        assertThrows(IllegalStateException.class, () ->
                AbstractInventoryWorkstationBlockEntity.readStackAwareEffectMarkers(incomplete));

        assertThrows(IllegalArgumentException.class, () ->
                AbstractInventoryWorkstationBlockEntity.readStackAwareEffectMarkers(
                        markers("butchercraft:workstation_endpoint_effect/v3/" + "3".repeat(64), OWNER_RESULT)));
    }

    private static CompoundTag markers(String effectIdentity, String ownerResultIdentity) {
        CompoundTag tag = new CompoundTag();
        tag.putString("StackAwareLastEffectIdentity", effectIdentity);
        tag.putString("StackAwareLastOwnerResultIdentity", ownerResultIdentity);
        return tag;
    }
}
