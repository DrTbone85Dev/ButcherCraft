package com.butchercraft.world.inventory.freshness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryFreshnessIdentityTest {
    private static final String DIGEST_A = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DIGEST_B = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DIGEST_C = "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    @Test
    void componentOrderDoesNotAlterCanonicalFreshnessIdentity() {
        InventoryFreshnessComponent beef = InventoryFreshnessComponent.of(
                "test:inventory/beef",
                "test:inventory_runtime/beef",
                DIGEST_A,
                42L
        );
        InventoryFreshnessComponent grain = InventoryFreshnessComponent.of(
                "test:inventory/grain",
                "test:inventory_runtime/grain",
                DIGEST_B,
                42L
        );

        InventoryFreshnessIdentity first = InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(beef, grain)
        );
        InventoryFreshnessIdentity second = InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(grain, beef)
        );

        assertEquals(first, second);
        assertTrue(first.digestMatches());
    }

    @Test
    void changedSourceContentChangesFreshnessIdentity() {
        InventoryFreshnessIdentity first = InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(InventoryFreshnessComponent.of(
                        "test:inventory/beef",
                        "test:inventory_runtime/beef",
                        DIGEST_A,
                        42L
                ))
        );
        InventoryFreshnessIdentity second = InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(InventoryFreshnessComponent.of(
                        "test:inventory/beef",
                        "test:inventory_runtime/beef",
                        DIGEST_C,
                        42L
                ))
        );

        assertFalse(first.identityDigest().equals(second.identityDigest()));
    }

    @Test
    void freshnessIdentitySupportsMultipleSourceComponentsWithoutGlobalRevision() {
        InventoryFreshnessIdentity freshness = InventoryFreshnessIdentity.fromComponents(
                "butchercraft:inventory",
                List.of(
                        InventoryFreshnessComponent.of(
                                "test:inventory/beef",
                                "test:inventory_runtime/beef",
                                DIGEST_A,
                                42L
                        ),
                        InventoryFreshnessComponent.of(
                                "test:inventory/grain",
                                "test:inventory_runtime/grain",
                                DIGEST_B,
                                43L
                        )
                )
        );

        assertEquals(2, freshness.components().size());
        assertTrue(freshness.digestMatches());
    }
}
