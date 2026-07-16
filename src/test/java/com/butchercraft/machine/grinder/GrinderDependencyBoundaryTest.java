package com.butchercraft.machine.grinder;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderDependencyBoundaryTest {
    @Test
    void grinderCodeDoesNotContainSpeciesSwitchesOrProductIdBranches() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/machine/grinder")).stream()
                .filter(path -> sourceContains(path, "switch (species")
                        || sourceContains(path, "switch(species")
                        || sourceContains(path, "case \"butchercraft:beef\"")
                        || sourceContains(path, "case \"butchercraft:chicken\"")
                        || sourceContains(path, "beef_trim")
                        || sourceContains(path, "ground_beef")
                        || sourceContains(path, "grind_beef"))
                .toList();

        assertTrue(offenders.isEmpty(), "Grinder code must use capability-based definition resolution: " + offenders);
    }

    @Test
    void genericWorkstationCodeDoesNotHardcodeGrinderIds() throws IOException {
        List<Path> offenders = javaFiles(TestProjectPaths.projectPath("src/main/java/com/butchercraft/workstation")).stream()
                .filter(path -> sourceContains(path, "Grinder")
                        || sourceContains(path, "GRINDER")
                        || sourceContains(path, "butchercraft:grinding"))
                .toList();

        assertTrue(offenders.isEmpty(), "Generic workstation code must not hardcode grinder content: " + offenders);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static boolean sourceContains(Path path, String value) {
        try {
            return Files.readString(path).contains(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan " + path, exception);
        }
    }
}
