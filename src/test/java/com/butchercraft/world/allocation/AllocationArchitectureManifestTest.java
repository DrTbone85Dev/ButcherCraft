package com.butchercraft.world.allocation;

import com.butchercraft.architecture.ButcherCraftArchitectureManifest;
import com.butchercraft.architecture.ButcherCraftArchitectureValidation;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ValidationCategory;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationArchitectureManifestTest {
    private static final ArchitectureId ALLOCATION = ArchitectureId.of("butchercraft:allocation");

    @Test
    void manifestDeclaresAllocationOwnershipAndPackageBoundary() {
        var context = ButcherCraftArchitectureManifest.current();
        assertTrue(context.components().stream().anyMatch(component ->
                component.id().equals(ALLOCATION)
                        && component.packageRoot().equals("com.butchercraft.world.allocation")));

        Set<String> owned = context.ownershipAssignments().stream()
                .filter(assignment -> assignment.ownerId().equals(ALLOCATION))
                .map(assignment -> assignment.responsibilityId().value())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "butchercraft:responsibility/allocation_requests",
                "butchercraft:responsibility/allocation_sets",
                "butchercraft:responsibility/allocation_commitments",
                "butchercraft:responsibility/allocation_lifecycle",
                "butchercraft:responsibility/allocation_registries",
                "butchercraft:responsibility/allocation_reports",
                "butchercraft:responsibility/allocation_history",
                "butchercraft:responsibility/allocation_cycles",
                "butchercraft:responsibility/allocation_capacity_accounting",
                "butchercraft:responsibility/allocation_commitment_selection"
        ), owned);
        assertTrue(context.ownershipContracts().stream()
                .filter(contract -> contract.expectedOwnerId().equals(ALLOCATION))
                .allMatch(contract -> contract.category() == ValidationCategory.ALLOCATION));
    }

    @Test
    void manifestRecordsM22BBoundariesAndRegistriesWithoutPersistenceOrStage() {
        var context = ButcherCraftArchitectureManifest.current();
        Set<String> forbiddenProviders = context.dependencyConstraints().stream()
                .filter(constraint -> constraint.consumerId().equals(ALLOCATION))
                .map(constraint -> constraint.forbiddenProviderId().value())
                .collect(Collectors.toSet());

        assertEquals(Set.of(
                "butchercraft:planning",
                "butchercraft:production",
                "butchercraft:simulation_scheduler",
                "butchercraft:inventory",
                "butchercraft:transactions"
        ), forbiddenProviders);
        assertFalse(context.dependencies().stream()
                .anyMatch(dependency -> dependency.consumerId().equals(ALLOCATION)));
        assertEquals(Set.of(
                "butchercraft:allocation_definitions",
                "butchercraft:allocation_runtime",
                "butchercraft:allocation_reports",
                "butchercraft:allocation_cycle_traces"
        ), context.registries().stream()
                .map(registry -> registry.id())
                .filter(id -> id.startsWith("butchercraft:allocation_"))
                .collect(Collectors.toSet()));
        assertTrue(context.registries().stream()
                .filter(registry -> registry.id().startsWith("butchercraft:allocation_"))
                .allMatch(registry ->
                        registry.orderingPolicy()
                                == com.butchercraft.architecture.validation.OrderingPolicy.CANONICAL_ID
                                && registry.entries().isEmpty()));
        assertFalse(context.persistenceDescriptors().stream()
                .anyMatch(descriptor -> descriptor.ownerId().equals(ALLOCATION)));
        assertFalse(context.schedulers().stream()
                .flatMap(scheduler -> scheduler.stages().stream())
                .anyMatch(stage -> stage.id().equals("butchercraft:allocation")
                        || stage.executionOrder() == 350));
    }

    @Test
    void currentArchitectureStillPassesAllocationRules() {
        var report = ButcherCraftArchitectureValidation.validateCurrentArchitecture();
        assertTrue(report.successful(), () -> "Architecture failures: " + report.failedRules());
        assertTrue(report.findByCategory(ValidationCategory.ALLOCATION).stream()
                .allMatch(result -> result.isSuccessful()));
    }
}
