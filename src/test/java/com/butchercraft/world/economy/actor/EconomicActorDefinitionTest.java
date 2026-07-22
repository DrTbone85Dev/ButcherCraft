package com.butchercraft.world.economy.actor;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.GRAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicActorDefinitionTest {
    @Test
    void builderCreatesCompleteImmutableDefinition() {
        Set<ActorCapability> capabilities = EnumSet.of(ActorCapability.PRODUCE, ActorCapability.SELL);
        List<ActorRelationship> relationships = new ArrayList<>(List.of(
                ActorRelationship.of(GRAIN, GoodRole.OUTPUT)
        ));

        EconomicActorDefinition definition = EconomicActorDefinition.builder()
                .id("test:grain_farm")
                .displayName(" Grain Farm ")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capabilities(capabilities)
                .relationships(relationships)
                .build();

        capabilities.clear();
        relationships.clear();
        assertEquals(ActorId.of("test:grain_farm"), definition.id());
        assertEquals("Grain Farm", definition.displayName());
        assertEquals(ActorType.PRODUCER, definition.actorType());
        assertEquals(EconomicActorSchema.CURRENT_VERSION, definition.schemaVersion());
        assertEquals(Set.of(ActorCapability.PRODUCE, ActorCapability.SELL), definition.capabilities());
        assertEquals(1, definition.relationships().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.capabilities().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.relationships().clear());
    }

    @Test
    void definitionRejectsIncompleteInvalidAndDuplicateData() {
        assertThrows(IllegalArgumentException.class, () -> ActorId.of("Invalid Actor"));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorDefinition.builder()
                .id("test:invalid")
                .displayName(" ")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .build());
        assertThrows(IllegalArgumentException.class, () -> EconomicActorDefinition.builder()
                .id("test:no_capability")
                .displayName("No Capability")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .build());
        ActorRelationship relationship = ActorRelationship.of(GRAIN, GoodRole.OUTPUT);
        assertThrows(IllegalArgumentException.class, () -> EconomicActorDefinition.builder()
                .id("test:duplicate")
                .displayName("Duplicate")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .relationship(relationship)
                .relationship(relationship)
                .build());
        assertThrows(IllegalArgumentException.class, () -> EconomicActorDefinition.builder()
                .id("test:wrong_capability")
                .displayName("Wrong Capability")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.STORE)
                .relationship(relationship)
                .build());
        assertThrows(IllegalArgumentException.class, () -> EconomicActorDefinition.builder()
                .id("test:future")
                .displayName("Future")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .schemaVersion(99)
                .build());
    }

    @Test
    void enumSerializationRejectsUnknownValues() {
        assertEquals(ActorType.MULTI_ROLE, ActorType.fromSerializedName("multi_role"));
        assertEquals(ActorCapability.EXPORT, ActorCapability.fromSerializedName("export"));
        assertEquals(GoodRole.TRANSPORTED, GoodRole.fromSerializedName("transported"));
        assertThrows(IllegalArgumentException.class, () -> ActorType.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> ActorCapability.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> GoodRole.fromSerializedName("unknown"));
    }

    @Test
    void mutableRuntimeMaintainsExplicitConsistentState() {
        ActorId actorId = ActorId.of("test:farm");
        EconomicActorRuntime runtime = EconomicActorRuntime.available(actorId);

        runtime.assignBusinessRuntime(new BusinessId("farm_business"), 10L);
        runtime.assignWorkforce(new WorkforceDefinitionId("farm_workforce"), 11L);
        runtime.transitionTo(ActorRuntimeStatus.OPERATIONAL, 12L);

        assertEquals(actorId, runtime.actorId());
        assertEquals(ActorRuntimeStatus.OPERATIONAL, runtime.runtimeStatus());
        assertTrue(runtime.enabled());
        assertTrue(runtime.operational());
        assertEquals("farm_business", runtime.assignedBusinessRuntime().orElseThrow().value());
        assertEquals("farm_workforce", runtime.assignedWorkforce().orElseThrow().value());
        assertEquals(12L, runtime.lastSimulationTick());

        runtime.transitionTo(ActorRuntimeStatus.SUSPENDED, 13L);
        assertTrue(runtime.enabled());
        assertFalse(runtime.operational());
        assertThrows(IllegalArgumentException.class, () -> runtime.transitionTo(ActorRuntimeStatus.AVAILABLE, 12L));
    }

    @Test
    void runtimeRejectsInvalidFlagsTicksAndNullAssignments() {
        ActorId actorId = ActorId.of("test:farm");

        assertThrows(IllegalArgumentException.class, () -> new EconomicActorRuntime(
                actorId,
                ActorRuntimeStatus.OPERATIONAL,
                true,
                false,
                Optional.empty(),
                Optional.empty(),
                0L
        ));
        assertThrows(IllegalArgumentException.class, () -> new EconomicActorRuntime(
                actorId,
                ActorRuntimeStatus.AVAILABLE,
                true,
                false,
                Optional.empty(),
                Optional.empty(),
                -1L
        ));
        EconomicActorRuntime runtime = EconomicActorRuntime.available(actorId);
        assertThrows(NullPointerException.class, () -> runtime.assignBusinessRuntime(null, 0L));
        assertThrows(NullPointerException.class, () -> runtime.assignWorkforce(null, 0L));
    }
}
