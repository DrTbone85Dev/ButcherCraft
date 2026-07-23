package com.butchercraft.world.production;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRegistryTest {
    @Test
    void processRegistryPreservesRegistrationOrderAndIndexesDefinitions() {
        ProductionProcessDefinition process = ProductionTestFixtures.process();
        ProductionProcessDefinition second = copyWithId(process, "test:second_process");
        ProductionProcessRegistry registry = ProductionProcessRegistry.builder()
                .register(second)
                .register(process)
                .build();

        assertEquals(List.of(second, process), registry.definitions());
        assertEquals(2, registry.findByIndustry(ProductionTestFixtures.INDUSTRY).size());
        assertEquals(2, registry.findByInputGood(ProductionTestFixtures.INPUT).size());
        assertEquals(2, registry.findByOutputGood(ProductionTestFixtures.OUTPUT).size());
        assertEquals(2, registry.findByCapability(
                com.butchercraft.world.economy.actor.ActorCapability.TRANSFORM).size());
        assertTrue(registry.find(process.id()).isPresent());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcessRegistry.builder()
                .register(process).register(process));
    }

    @Test
    void planRegistryProvidesDeterministicCrossReferenceQueries() {
        ProductionPlanDefinition first = ProductionTestFixtures.plan();
        ProductionPlanDefinition second = copyPlan(first, "test:second_plan", 5L);
        ProductionPlanRegistry registry = ProductionPlanRegistry.builder()
                .register(second)
                .register(first)
                .build();

        assertEquals(List.of(second, first), registry.definitions());
        assertEquals(2, registry.findByProcess(first.processId()).size());
        assertEquals(2, registry.findByActor(first.producerActorId()).size());
        assertEquals(2, registry.findByInputInventory(ProductionTestFixtures.INPUT_INVENTORY).size());
        assertEquals(List.of(first), registry.findCreatedBetween(0L, 0L));
        assertEquals(List.of(second), registry.findCreatedBetween(5L, 5L));
        assertThrows(IllegalArgumentException.class, () -> ProductionPlanRegistry.builder()
                .register(first).register(first));
    }

    private static ProductionProcessDefinition copyWithId(
            ProductionProcessDefinition source,
            String id
    ) {
        ProductionProcessDefinition.Builder builder = ProductionProcessDefinition.builder()
                .id(id)
                .displayName(source.displayName())
                .owningIndustryId(source.owningIndustryId())
                .requiredActorCapability(source.requiredActorCapability())
                .duration(source.duration())
                .batchPolicy(source.batchPolicy())
                .workforceRequirement(source.workforceRequirement())
                .businessRequirement(source.businessRequirement())
                .executionPolicy(source.executionPolicy())
                .metadata(source.metadata());
        source.inputs().forEach(builder::input);
        source.outputs().forEach(builder::output);
        source.transformationReferences().forEach(builder::transformationReference);
        return builder.build();
    }

    private static ProductionPlanDefinition copyPlan(
            ProductionPlanDefinition source,
            String id,
            long tick
    ) {
        ProductionPlanDefinition.Builder builder = ProductionPlanDefinition.builder()
                .id(id)
                .processId(source.processId())
                .producerActorId(source.producerActorId())
                .batchCount(source.batchCount())
                .createdSimulationTick(tick)
                .earliestStartTick(tick)
                .priority(source.priority())
                .metadata(source.metadata());
        source.inventoryBindings().forEach(builder::inventoryBinding);
        return builder.build();
    }
}
