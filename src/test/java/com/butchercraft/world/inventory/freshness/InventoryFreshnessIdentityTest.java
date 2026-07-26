package com.butchercraft.world.inventory.freshness;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import com.butchercraft.world.inventory.InventoryType;
import com.butchercraft.world.inventory.StorageCapacity;
import com.butchercraft.world.inventory.StorageNode;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionType;

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

    @Test
    void liveFreshnessChangesAfterRelevantInventoryMutation() {
        InventoryManager inventory = InventoryTestFixtures.manager();
        EconomicTransaction transaction = beefAdd("test:freshness_add", InventoryTestFixtures.BEEF_INVENTORY, 1L);
        InventoryFreshnessIdentity before = inventory.freshnessIdentityForValidation(
                List.of(com.butchercraft.world.inventory.InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new com.butchercraft.world.inventory.InventoryEntry(
                                InventoryTestFixtures.BEEF,
                                1L,
                                UnitOfMeasure.POUND
                        )
                )),
                List.of(InventoryTestFixtures.BEEF),
                List.of(InventoryTestFixtures.WAREHOUSE_ACTOR)
        );

        assertTrue(new TransactionManager(inventory).submit(transaction).success());
        InventoryFreshnessIdentity after = inventory.freshnessIdentityForValidation(
                List.of(com.butchercraft.world.inventory.InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new com.butchercraft.world.inventory.InventoryEntry(
                                InventoryTestFixtures.BEEF,
                                1L,
                                UnitOfMeasure.POUND
                        )
                )),
                List.of(InventoryTestFixtures.BEEF),
                List.of(InventoryTestFixtures.WAREHOUSE_ACTOR)
        );

        assertFalse(before.identityDigest().equals(after.identityDigest()));
    }

    @Test
    void unrelatedInventoryMutationOutsideScopedStorageDoesNotChangeFreshness() {
        InventoryManager inventory = InventoryTestFixtures.manager();
        InventoryId remoteInventoryId = InventoryId.of("test:remote_inventory");
        StorageNode remoteNode = StorageNode.builder()
                .id("test:remote_node")
                .displayName("Remote Node")
                .storageRequirement(com.butchercraft.world.goods.StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.unlimited())
                .build();
        inventory.registerStorageNode(remoteNode);
        inventory.registerContainer(InventoryContainer.builder()
                .id(remoteInventoryId)
                .displayName("Remote Inventory")
                .ownerActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .storageNodeId(remoteNode.id())
                .inventoryType(InventoryType.WAREHOUSE)
                .capacity(StorageCapacity.unlimited())
                .build());

        var beefChange = com.butchercraft.world.inventory.InventoryChange.add(
                InventoryTestFixtures.BEEF_INVENTORY,
                new com.butchercraft.world.inventory.InventoryEntry(
                        InventoryTestFixtures.BEEF,
                        1L,
                        UnitOfMeasure.POUND
                )
        );
        InventoryFreshnessIdentity before = inventory.freshnessIdentityForValidation(
                List.of(beefChange),
                List.of(InventoryTestFixtures.BEEF),
                List.of(InventoryTestFixtures.WAREHOUSE_ACTOR)
        );

        assertTrue(new TransactionManager(inventory).submit(boxAdd("test:remote_add", remoteInventoryId, 1L)).success());
        InventoryFreshnessIdentity after = inventory.freshnessIdentityForValidation(
                List.of(beefChange),
                List.of(InventoryTestFixtures.BEEF),
                List.of(InventoryTestFixtures.WAREHOUSE_ACTOR)
        );

        assertEquals(before, after);
    }

    private static EconomicTransaction beefAdd(String id, InventoryId inventoryId, long quantity) {
        return EconomicTransaction.builder()
                .id(TransactionId.of(id))
                .type(TransactionType.INVENTORY_ADD)
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationInventoryId(inventoryId)
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(quantity)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(26L)
                .build();
    }

    private static EconomicTransaction boxAdd(String id, InventoryId inventoryId, long quantity) {
        return EconomicTransaction.builder()
                .id(TransactionId.of(id))
                .type(TransactionType.INVENTORY_ADD)
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationInventoryId(inventoryId)
                .goodId(InventoryTestFixtures.BOX)
                .quantity(quantity)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .simulationTick(26L)
                .build();
    }
}
