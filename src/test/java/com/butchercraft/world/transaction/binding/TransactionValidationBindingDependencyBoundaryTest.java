package com.butchercraft.world.transaction.binding;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionValidationBindingDependencyBoundaryTest {
    @Test
    void transactionBindingFoundationRemainsPureJavaAndRuntimeIsolated() throws IOException {
        Path bindingPackage = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/transaction/binding"
        );
        try (var files = Files.walk(bindingPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("import net.minecraft"), file + " must remain Minecraft-independent");
                assertFalse(source.contains("import net.neoforged"), file + " must remain NeoForge-independent");
                assertFalse(source.contains("ItemStack"), file + " must not depend on ItemStack");
                assertFalse(source.contains("java.io.Serializable"), file + " must not persist runtime authority");
                assertFalse(source.contains("System.currentTimeMillis"), file + " must not use wall-clock time");
                assertFalse(source.contains("Instant.now"), file + " must not use wall-clock time");
                assertFalse(source.contains("UUID.randomUUID"), file + " must not use randomness");
            }
        }
    }

    @Test
    void validationConsumptionAuthorityIsPackagePrivateAndNotIssuedByOtherSubsystems() throws IOException {
        Path authorityFile = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/transaction/binding/ValidationConsumptionAuthority.java"
        );
        String authoritySource = Files.readString(authorityFile);
        assertTrue(authoritySource.contains("final class ValidationConsumptionAuthority"));
        assertFalse(authoritySource.contains("public final class ValidationConsumptionAuthority"));

        Path sourceRoot = TestProjectPaths.projectPath("src/main/java/com/butchercraft/world");
        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String normalized = file.toString().replace('\\', '/');
                String source = Files.readString(file);
                if (normalized.contains("/transaction/binding/")) {
                    continue;
                }
                assertFalse(
                        source.contains("ValidationConsumptionAuthority"),
                        file + " must not issue Transaction-owned validation consumption authority"
                );
            }
        }
    }

    @Test
    void immutableEvidenceAndLiveRuntimeDoNotPersistOrReuseConsumptionAuthority() throws IOException {
        Path evidenceFile = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/transaction/binding/AuthoritativeTransactionResultEvidence.java"
        );
        String evidenceSource = Files.readString(evidenceFile);
        assertFalse(evidenceSource.contains("ValidationConsumptionAuthority"));
        assertFalse(evidenceSource.contains("ValidationConsumptionResult"));

        List<Path> liveRuntimeFiles = List.of(
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/transaction/TransactionManager.java"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/transaction/TransactionValidator.java"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/transaction/TransactionExecutor.java"),
                TestProjectPaths.projectPath("src/main/java/com/butchercraft/world/transaction/TransactionRegistry.java")
        );
        for (Path file : liveRuntimeFiles) {
            String source = Files.readString(file);
            assertFalse(source.contains("ValidationConsumptionAuthority"));
            assertFalse(source.contains("java.io.Serializable"));
        }
    }

    @Test
    void inventoryFreshnessFoundationDoesNotEmbedGlobalRevisionAssumption() throws IOException {
        Path freshnessPackage = TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/inventory/freshness"
        );
        try (var files = Files.walk(freshnessPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);

                assertFalse(source.contains("InventoryRevision"), file + " must not define Inventory Revision");
                assertFalse(source.contains("globalRevision"), file + " must not require a global revision");
                assertFalse(source.contains("revisionVector"), file + " must not mandate revision vectors");
            }
        }
    }
}
