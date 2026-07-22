package com.butchercraft.world.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerLegacySerializationTest {
    @Test
    void enumSerializedNamesRoundTrip() {
        for (CareerProfile value : CareerProfile.values()) {
            assertEquals(value, CareerProfile.fromSerializedName(value.serializedName()));
        }
        for (StartingScenarioType value : StartingScenarioType.values()) {
            assertEquals(value, StartingScenarioType.fromSerializedName(value.serializedName()));
        }
        for (LegacyAcquisitionType value : LegacyAcquisitionType.values()) {
            assertEquals(value, LegacyAcquisitionType.fromSerializedName(value.serializedName()));
        }
        for (InitialReputation value : InitialReputation.values()) {
            assertEquals(value, InitialReputation.fromSerializedName(value.serializedName()));
        }
        for (StartingRelationshipType value : StartingRelationshipType.values()) {
            assertEquals(value, StartingRelationshipType.fromSerializedName(value.serializedName()));
        }
    }

    @Test
    void enumDeserializationRejectsUnknownNames() {
        assertThrows(IllegalArgumentException.class, () -> CareerProfile.fromSerializedName("missing"));
        assertThrows(IllegalArgumentException.class, () -> StartingScenarioType.fromSerializedName("missing"));
        assertThrows(IllegalArgumentException.class, () -> LegacyAcquisitionType.fromSerializedName("missing"));
        assertThrows(IllegalArgumentException.class, () -> InitialReputation.fromSerializedName("missing"));
        assertThrows(IllegalArgumentException.class, () -> StartingRelationshipType.fromSerializedName("missing"));
    }
}
