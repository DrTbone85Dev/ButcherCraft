package com.butchercraft.world.ownership;

import com.butchercraft.world.identity.WorldIdentity;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnershipRegistryTest {
    private final WorldIdentity identity = new WorldIdentityGenerator().generate(555L);
    private final FamilyRegistry familyRegistry = FamilyRegistry.of(
            identity.families(),
            identity.historicalPersons(),
            identity.settlements()
    );
    private final OwnershipRegistry ownershipRegistry = OwnershipRegistry.of(
            identity.ownershipEntities(),
            identity.ownershipHistories(),
            identity.families(),
            identity.historicalPersons(),
            identity.businesses()
    );

    @Test
    void registriesLoadGeneratedOwnershipInStableIdOrder() {
        assertEquals(identity.families().size(), familyRegistry.familyCount());
        assertEquals(identity.historicalPersons().size(), familyRegistry.personCount());
        assertEquals(identity.ownershipEntities().size(), ownershipRegistry.entityCount());
        assertEquals(identity.ownershipHistories().size(), ownershipRegistry.historyCount());
        assertEquals(
                identity.ownershipEntities().stream().sorted(Comparator.comparing(entity -> entity.id().value())).toList(),
                ownershipRegistry.ownershipEntities()
        );
        assertThrows(UnsupportedOperationException.class, () -> ownershipRegistry.ownershipEntities().clear());
    }

    @Test
    void familyRegistryFindsFamiliesAndPeople() {
        Family family = familyRegistry.families().getFirst();
        PersonIdentity person = familyRegistry.people().getFirst();

        assertTrue(familyRegistry.containsFamily(family.id()));
        assertEquals(family, familyRegistry.findFamily(family.id()).orElseThrow());
        assertTrue(familyRegistry.containsPerson(person.id()));
        assertEquals(person, familyRegistry.findPerson(person.id()).orElseThrow());
        assertTrue(familyRegistry.findBySettlement(family.foundingSettlementId()).contains(family));
        assertTrue(familyRegistry.findByReputation(family.reputation()).contains(family));
        assertTrue(familyRegistry.searchFamilies(family.surname()).contains(family));
    }

    @Test
    void ownershipRegistryFindsEntitiesAndRecords() {
        OwnershipEntity entity = ownershipRegistry.ownershipEntities().getFirst();
        OwnershipHistory history = ownershipRegistry.ownershipHistories().getFirst();

        assertTrue(ownershipRegistry.contains(entity.id()));
        assertEquals(entity, ownershipRegistry.find(entity.id()).orElseThrow());
        assertEquals(history, ownershipRegistry.findHistory(history.businessId()).orElseThrow());
        assertTrue(ownershipRegistry.findByType(entity.type()).contains(entity));
        entity.familyId().ifPresent(familyId -> assertTrue(ownershipRegistry.findByFamily(familyId).contains(entity)));
        assertEquals(history.ownershipRecords(), ownershipRegistry.findRecordsByBusiness(history.businessId()));
        assertTrue(ownershipRegistry.findRecordsByEntity(entity.id()).stream()
                .allMatch(record -> record.ownershipEntityId().equals(entity.id())));
        assertTrue(ownershipRegistry.search(entity.displayName()).contains(entity));
    }
}
