package com.butchercraft.productioncontrol;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionControlBoundaryTest {
    @Test
    void productionControlDoesNotEnterPureProductionPackage() throws IOException {
        Path productionRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/production");

        try (var files = Files.walk(productionRoot)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "productioncontrol"))
                    .toList();

            assertTrue(violations.isEmpty(), () -> "Production control leaked into Production owner package: " + violations);
        }
    }

    @Test
    void controlSurfaceDoesNotReferenceFutureAutomationOrBusinessAuthorities() throws IOException {
        Path root = TestProjectPaths.projectPath("src/main/java/com/butchercraft/productioncontrol");
        List<String> forbidden = List.of(
                "AllocationService",
                "OrderContractService",
                "WorkforceService",
                "BusinessRuntimeService",
                "automatic transfer",
                "customer",
                "retail"
        );

        try (var files = Files.walk(root)) {
            List<Path> violations = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> forbidden.stream().anyMatch(token -> contains(path, token)))
                    .toList();

            assertTrue(violations.isEmpty(), () -> "Production control exceeded IM-019 authority: " + violations);
        }
    }

    @Test
    void menuStatusUsesBoundedDataSlotsInsteadOfPersistingRuntimeState() throws IOException {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/productioncontrol/ProductionOrderMenu.java"
        ));

        assertTrue(source.contains("private static final int DATA_COUNT = 30"));
        assertTrue(source.contains("addDataSlots(data)"));
        assertTrue(source.contains("ProductionOrderControl.refreshStatus"));
        assertTrue(source.contains("public void set(int index, int value)"));
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
