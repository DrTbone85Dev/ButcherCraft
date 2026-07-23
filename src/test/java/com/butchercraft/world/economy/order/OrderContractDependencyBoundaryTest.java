package com.butchercraft.world.economy.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderContractDependencyBoundaryTest {
    @Test
    void pureOrderDomainHasNoMinecraftOrNeoForgeDependencies() throws IOException {
        Path project = Path.of(System.getProperty("butchercraft.projectDir"));
        Path sourceRoot = project.resolve("src/main/java/com/butchercraft/world/economy/order");
        List<Path> violations;
        try (var files = Files.walk(sourceRoot)) {
            violations = files.filter(path -> path.toString().endsWith(".java")).filter(path -> {
                try {
                    String source = Files.readString(path);
                    return source.contains("import net.minecraft") || source.contains("import net.neoforged")
                            || source.contains("ItemStack") || source.contains("BlockEntity");
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            }).toList();
        }
        assertTrue(violations.isEmpty(), () -> "Forbidden order-domain dependencies: " + violations);
    }
}
