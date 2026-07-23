package com.butchercraft.world.economy.actor;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.business.runtime.BusinessHours;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.business.runtime.BusinessRuntimeState;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.workforce.WorkforceDefinition;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import com.butchercraft.world.workforce.WorkforceRegistry;
import com.butchercraft.world.workforce.WorkforceSchema;
import com.butchercraft.world.workforce.WorkforceStaffingRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.BREAD;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.GRAIN;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.goods;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.producer;
import static com.butchercraft.world.economy.actor.EconomicActorTestFixtures.registry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicActorRegistryTest {
    @Test
    void registryUsesDeterministicOrderingAndSupportsQueries() {
        EconomicActorRegistry registry = registry();

        assertEquals(List.of("test:bakery", "test:farm", "test:warehouse"), registry.stream()
                .map(definition -> definition.id().value())
                .toList());
        assertEquals(3, registry.size());
        assertEquals(4, registry.relationshipCount());
        assertTrue(registry.contains(ActorId.of("test:farm")));
        assertFalse(registry.contains(ActorId.of("test:missing")));
        assertEquals(1, registry.findByType(ActorType.STORAGE).size());
        assertEquals(1, registry.findByIndustry(BuiltInIndustryCatalog.AGRICULTURE).size());
        assertEquals(1, registry.findByCapability(ActorCapability.STORE).size());
        assertEquals(2, registry.findByGood(BREAD).size());
        assertEquals("test:bakery", registry.findByGoodRole(BREAD, GoodRole.OUTPUT).getFirst().id().value());
        assertEquals("test:farm", registry.dependenciesOf(ActorId.of("test:bakery")).getFirst().id().value());
        assertEquals(2, registry.relationshipsFor(ActorId.of("test:bakery")).size());
    }

    @Test
    void inputOrderDoesNotChangeRegistryOrder() {
        List<EconomicActorDefinition> definitions = registry().definitions();

        EconomicActorRegistry first = EconomicActorRegistry.of(
                definitions,
                goods(),
                BuiltInIndustryCatalog.all()
        );
        EconomicActorRegistry second = EconomicActorRegistry.of(
                definitions.reversed(),
                goods(),
                BuiltInIndustryCatalog.all()
        );

        assertEquals(first.definitions(), second.definitions());
        assertEquals(first.findByGood(BREAD), second.findByGood(BREAD));
    }

    @Test
    void builderAndRegistryRejectDuplicateActorIds() {
        EconomicActorDefinition farm = producer("farm");

        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.builder(
                goods(),
                BuiltInIndustryCatalog.all()
        ).register(farm).register(farm));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(farm, farm),
                goods(),
                BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryRejectsUnknownIndustriesGoodsAndActorDependencies() {
        EconomicActorDefinition unknownIndustry = EconomicActorDefinition.builder()
                .id("test:unknown_industry")
                .displayName("Unknown Industry")
                .actorType(ActorType.PRODUCER)
                .industryId("test:unknown")
                .capability(ActorCapability.PRODUCE)
                .build();
        EconomicActorDefinition unknownGood = EconomicActorDefinition.builder()
                .id("test:unknown_good")
                .displayName("Unknown Good")
                .actorType(ActorType.PRODUCER)
                .industryId(BuiltInIndustryCatalog.AGRICULTURE)
                .capability(ActorCapability.PRODUCE)
                .relationship(ActorRelationship.of(GoodId.of("test:missing"), GoodRole.OUTPUT))
                .build();
        EconomicActorDefinition unknownActor = EconomicActorDefinition.builder()
                .id("test:unknown_actor")
                .displayName("Unknown Actor")
                .actorType(ActorType.CONSUMER)
                .industryId(BuiltInIndustryCatalog.RESTAURANTS)
                .capability(ActorCapability.CONSUME)
                .relationship(ActorRelationship.dependingOn(
                        GRAIN,
                        GoodRole.CONSUMED,
                        ActorId.of("test:missing")
                ))
                .build();
        EconomicActorDefinition unknownSupportedIndustry = EconomicActorDefinition.builder()
                .id("test:unknown_supported_industry")
                .displayName("Unknown Supported Industry")
                .actorType(ActorType.STORAGE)
                .industryId(BuiltInIndustryCatalog.TRANSPORTATION)
                .capability(ActorCapability.STORE)
                .relationship(ActorRelationship.supportingIndustries(
                        GRAIN,
                        GoodRole.STORED,
                        List.of(com.butchercraft.world.goods.IndustryId.of("test:unknown"))
                ))
                .build();

        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(unknownIndustry), goods(), BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(unknownGood), goods(), BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(unknownActor), goods(), BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(unknownSupportedIndustry), goods(), BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void registryRejectsSelfAndCircularDependencyChains() {
        EconomicActorDefinition self = EconomicActorDefinition.builder()
                .id("test:self")
                .displayName("Self")
                .actorType(ActorType.CONSUMER)
                .industryId(BuiltInIndustryCatalog.RESTAURANTS)
                .capability(ActorCapability.CONSUME)
                .relationship(ActorRelationship.dependingOn(GRAIN, GoodRole.CONSUMED, ActorId.of("test:self")))
                .build();
        EconomicActorDefinition first = dependentActor("first", "second");
        EconomicActorDefinition second = dependentActor("second", "first");

        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(self), goods(), BuiltInIndustryCatalog.all()
        ));
        assertThrows(IllegalArgumentException.class, () -> EconomicActorRegistry.of(
                List.of(first, second), goods(), BuiltInIndustryCatalog.all()
        ));
    }

    @Test
    void managerProvidesLookupRelationshipAndRuntimeAccess() {
        EconomicActorManager manager = new EconomicActorManager(registry());
        EconomicActorDefinition restaurant = EconomicActorTestFixtures.consumer(
                "restaurant",
                ActorId.of("test:farm")
        );

        manager.register(restaurant);

        assertEquals(4, manager.registry().size());
        assertEquals(1, manager.findByCapability(ActorCapability.CONSUME).size());
        assertEquals(1, manager.actorsForGoodRole(GRAIN, GoodRole.CONSUMED).size());
        assertEquals(1, manager.relationshipsFor(restaurant.id()).size());
        assertEquals(ActorRuntimeStatus.AVAILABLE, manager.requireRuntime(restaurant.id()).runtimeStatus());
        assertTrue(manager.runtimeFor(ActorId.of("test:missing")).isEmpty());
        manager.validate();
    }

    @Test
    void managerValidatesBusinessAndWorkforceRuntimeAssignments() {
        EconomicActorManager manager = new EconomicActorManager(registry());
        EconomicActorRuntime runtime = manager.requireRuntime(ActorId.of("test:farm"));
        BusinessId businessId = new BusinessId("farm_business");
        WorkforceDefinitionId workforceId = new WorkforceDefinitionId("farm_workforce");
        runtime.assignBusinessRuntime(businessId, 1L);
        runtime.assignWorkforce(workforceId, 1L);

        SimulationConfiguration configuration = SimulationConfiguration.standard();
        BusinessRuntimeState businessState = BusinessRuntimeState.closed(
                businessId,
                BusinessHours.weekdays(8, 0, 17, 0, configuration),
                List.of(),
                0,
                0L
        );
        BusinessRuntimeRegistry businessRegistry = BusinessRuntimeRegistry.of(List.of(businessState));
        WorkforceDefinition workforce = new WorkforceDefinition(
                businessId,
                workforceId,
                List.of(),
                List.of(),
                new WorkforceStaffingRule(List.of(), List.of(), 0, 0),
                WorkforceSchema.CURRENT_VERSION
        );
        WorkforceRegistry workforceRegistry = WorkforceRegistry.of(List.of(workforce));

        manager.validateRuntimeAssignments(businessRegistry, workforceRegistry);

        EconomicActorManager invalid = new EconomicActorManager(registry());
        invalid.requireRuntime(ActorId.of("test:farm"))
                .assignBusinessRuntime(new BusinessId("missing_business"), 1L);
        assertThrows(IllegalArgumentException.class, () -> invalid.validateRuntimeAssignments(
                businessRegistry,
                workforceRegistry
        ));
    }

    private static EconomicActorDefinition dependentActor(String id, String dependencyId) {
        return EconomicActorDefinition.builder()
                .id("test:" + id)
                .displayName(id)
                .actorType(ActorType.CONSUMER)
                .industryId(BuiltInIndustryCatalog.RESTAURANTS)
                .capability(ActorCapability.CONSUME)
                .relationship(ActorRelationship.dependingOn(
                        GRAIN,
                        GoodRole.CONSUMED,
                        ActorId.of("test:" + dependencyId)
                ))
                .build();
    }
}
