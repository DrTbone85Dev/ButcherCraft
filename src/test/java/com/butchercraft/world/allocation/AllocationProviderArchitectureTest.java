package com.butchercraft.world.allocation;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationProviderArchitectureTest {
    private static final List<String> PROVIDER_FILES = List.of(
            "AllocationResourceProvider.java",
            "AllocationProviderDescriptor.java",
            "AllocationProviderRegistry.java",
            "AllocationProviderRegistryBuilder.java",
            "AllocationObservationContext.java",
            "AllocationObservationRequest.java",
            "AllocationObservationResult.java",
            "AllocationObservationService.java",
            "AllocationObservationBundle.java",
            "AllocationObservationReport.java"
    );

    @Test
    void providerFrameworkDoesNotAllocateMutateSchedulePersistOrExecute() {
        List<String> forbidden = List.of(
                "AllocationCycleExecutor",
                "AllocationRuntimeService",
                "WorkingCapacityLedger",
                "registerCommitment",
                "publishCycle",
                "WorkHandler",
                "WorkSubmission",
                "SavedData",
                "Persistence",
                "TransactionExecutor",
                "InventoryManager",
                "ProductionManager",
                "EconomicPlanningService"
        );

        List<Path> violations = providerFiles().stream()
                .filter(path -> containsAny(path, forbidden))
                .toList();
        assertTrue(
                violations.isEmpty(),
                () -> "Provider framework acquired forbidden authority: " + violations
        );
    }

    @Test
    void providerFrameworkUsesNoHiddenEnvironmentDiscoveryOrParallelism() {
        List<String> forbidden = List.of(
                "System.currentTimeMillis",
                "System.nanoTime",
                "java.time.",
                "java.util.Random",
                "RandomGenerator",
                "ThreadLocalRandom",
                "java.lang.reflect",
                "ClassLoader",
                "ServiceLoader",
                "CompletableFuture",
                "ExecutorService",
                "parallelStream",
                ".parallel()",
                "Files.",
                "Path."
        );

        List<Path> violations = providerFiles().stream()
                .filter(path -> containsAny(path, forbidden))
                .toList();
        assertTrue(
                violations.isEmpty(),
                () -> "Provider framework purity violations: " + violations
        );
    }

    @Test
    void providerContractReceivesOnlyImmutableObservationContext() {
        var observe = List.of(AllocationResourceProvider.class.getDeclaredMethods())
                .stream()
                .filter(method -> method.getName().equals("observe"))
                .findFirst()
                .orElseThrow();
        assertTrue(observe.getReturnType().equals(AllocationObservationResult.class));
        assertTrue(List.of(observe.getParameterTypes())
                .equals(List.of(AllocationObservationContext.class)));
        assertFalse(List.of(observe.getParameterTypes()).contains(
                AllocationRuntimeService.class
        ));
    }

    @Test
    void noProductionGradeConcreteProviderWasAdded() {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        try (var files = Files.list(root)) {
            List<Path> violations = files
                    .filter(path -> path.getFileName().toString().matches(
                            "(Production|Workforce|Inventory|Logistics|Utility|"
                                    + "Inspection|Infrastructure).*Provider\\.java"
                    ))
                    .toList();
            assertTrue(
                    violations.isEmpty(),
                    () -> "Concrete provider escaped M22D scope: " + violations
            );
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<Path> providerFiles() {
        Path root = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/allocation"
        );
        return PROVIDER_FILES.stream().map(root::resolve).toList();
    }

    private static boolean containsAny(Path path, List<String> forbidden) {
        try {
            String content = Files.readString(path);
            return forbidden.stream().anyMatch(content::contains);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
