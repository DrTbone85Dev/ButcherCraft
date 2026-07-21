package com.butchercraft;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {
    private static final String RELEASE_VERSION = "0.8.0";
    private static final String STALE_RELEASE_VERSION = "0.5." + "1";

    @Test
    void releaseVersionIsConfiguredForGradleMetadataAndReadme() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(TestProjectPaths.projectPath("gradle.properties"))) {
            properties.load(reader);
        }

        assertEquals(RELEASE_VERSION, properties.getProperty("mod_version"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("build.gradle")).contains("version = mod_version"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("build.gradle")).contains("exclude('**/.cache/**')"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("src/main/templates/META-INF/neoforge.mods.toml"))
                .contains("version=\"${mod_version}\""));
        assertTrue(Files.readString(TestProjectPaths.projectPath("README.md"))
                .contains("- Version: `%s`".formatted(RELEASE_VERSION)));
    }

    @Test
    void staleReleaseVersionIsNotReferencedInSourceOrDocs() throws IOException {
        for (String relativePath : List.of(
                "gradle.properties",
                "README.md",
                "CHANGELOG.md",
                "build.gradle",
                "src/main/templates/META-INF/neoforge.mods.toml"
        )) {
            assertFalse(
                    Files.readString(TestProjectPaths.projectPath(relativePath)).contains(STALE_RELEASE_VERSION),
                    "%s still references %s".formatted(relativePath, STALE_RELEASE_VERSION)
            );
        }
    }
}
