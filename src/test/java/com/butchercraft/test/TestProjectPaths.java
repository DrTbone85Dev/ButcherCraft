package com.butchercraft.test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestProjectPaths {
    private static final String PROJECT_DIR_PROPERTY = "butchercraft.projectDir";

    private TestProjectPaths() {
    }

    public static Path projectDir() {
        String configuredPath = System.getProperty(PROJECT_DIR_PROPERTY);
        if (configuredPath == null || configuredPath.isBlank()) {
            throw new IllegalStateException("Missing required test system property: " + PROJECT_DIR_PROPERTY);
        }

        Path projectDir = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(projectDir)) {
            throw new IllegalStateException("Configured ButcherCraft project directory does not exist: " + projectDir);
        }
        return projectDir;
    }

    public static Path projectPath(String first, String... more) {
        return projectDir().resolve(Path.of(first, more)).normalize();
    }
}
