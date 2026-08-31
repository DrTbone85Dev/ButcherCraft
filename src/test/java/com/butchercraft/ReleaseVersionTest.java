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
    private static final String RELEASE_VERSION = "0.10.6-alpha.1";
    private static final String PREVIOUS_RELEASE_VERSION = "0.10.5-alpha.1";

    @Test
    void releaseVersionIsConfiguredForGradleMetadataReadmeAndChangelog() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(TestProjectPaths.projectPath("gradle.properties"))) {
            properties.load(reader);
        }

        assertEquals(RELEASE_VERSION, properties.getProperty("mod_version"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("build.gradle")).contains("version = mod_version"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("build.gradle")).contains("exclude('**/.cache/**')"));
        assertTrue(Files.readString(TestProjectPaths.projectPath("src/main/templates/META-INF/neoforge.mods.toml"))
                .contains("version=\"${mod_version}\""));
        assertTrue(Files.readString(TestProjectPaths.projectPath(
                        "build/generated/sources/modMetadata/META-INF/neoforge.mods.toml"))
                .contains("version=\"%s\"".formatted(RELEASE_VERSION)));
        assertTrue(Files.readString(TestProjectPaths.projectPath("README.md"))
                .contains("- Version: `%s`".formatted(RELEASE_VERSION)));
        assertTrue(Files.readString(TestProjectPaths.projectPath("CHANGELOG.md"))
                .startsWith("# Changelog\n\n## ButcherCraft v%s".formatted(RELEASE_VERSION)));
    }

    @Test
    void previousReleaseVersionRemainsHistoricalOnly() throws IOException {
        for (String relativePath : List.of(
                "gradle.properties",
                "README.md",
                "build.gradle",
                "src/main/templates/META-INF/neoforge.mods.toml"
        )) {
            assertFalse(
                    Files.readString(TestProjectPaths.projectPath(relativePath)).contains(PREVIOUS_RELEASE_VERSION),
                    "%s still uses previous release %s as live metadata"
                            .formatted(relativePath, PREVIOUS_RELEASE_VERSION)
            );
        }

        assertTrue(Files.readString(TestProjectPaths.projectPath("CHANGELOG.md"))
                .contains("## ButcherCraft v%s - Continuous Processing Update"
                        .formatted(PREVIOUS_RELEASE_VERSION)));
    }
}
