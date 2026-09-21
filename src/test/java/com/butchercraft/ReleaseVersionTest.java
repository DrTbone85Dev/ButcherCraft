package com.butchercraft;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseVersionTest {
    private static final String RELEASE_VERSION = "0.10.7-alpha.1";
    private static final String PREVIOUS_RELEASE_VERSION = "0.10.6-alpha.1";

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
        assertTrue(Files.readString(TestProjectPaths.projectPath("README.md"))
                .contains("- Version: `%s`".formatted(RELEASE_VERSION)));
        assertTrue(Files.readString(TestProjectPaths.projectPath("CHANGELOG.md"))
                .startsWith("# Changelog\n\n## ButcherCraft v%s".formatted(RELEASE_VERSION)));
    }

    @Test
    void generatedMetadataUsesCurrentRelease() throws IOException {
        String metadata = Files.readString(TestProjectPaths.projectPath(
                "build/generated/sources/modMetadata/META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("modId=\"butchercraft\""));
        assertTrue(metadata.contains("version=\"%s\"".formatted(RELEASE_VERSION)));
        assertFalse(metadata.contains(PREVIOUS_RELEASE_VERSION));
    }

    @Test
    void packagedMetadataUsesCurrentReleaseWithoutTestClassesOrEvidence() throws IOException {
        try (var jar = new JarFile(releaseArtifact().toFile())) {
            var entry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            assertTrue(entry != null, "Packaged NeoForge metadata is required");
            String metadata;
            try (var input = jar.getInputStream(entry)) {
                metadata = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            assertTrue(metadata.contains("modId=\"butchercraft\""));
            assertTrue(metadata.contains("version=\"%s\"".formatted(RELEASE_VERSION)));
            assertFalse(metadata.contains(PREVIOUS_RELEASE_VERSION));
            assertTrue(jar.getJarEntry("com/butchercraft/ButcherCraft.class") != null);
            assertTrue(jar.getJarEntry("assets/butchercraft/lang/en_us.json") != null);
            assertFalse(jar.stream().anyMatch(candidate -> {
                String name = candidate.getName();
                return name.startsWith("com/butchercraft/test/")
                        || name.startsWith("org/junit/")
                        || name.endsWith("Test.class")
                        || name.contains("/.cache/")
                        || name.endsWith(".bbmodel")
                        || name.startsWith("test-results/")
                        || name.startsWith("reports/")
                        || name.startsWith("run/");
            }));
        }
    }

    @Test
    void artifactFilenameUsesCanonicalReleaseVersion() throws IOException {
        assertEquals("butchercraft-%s.jar".formatted(RELEASE_VERSION),
                releaseArtifact().getFileName().toString());
        assertTrue(Files.isRegularFile(releaseArtifact()));
        assertTrue(Files.readString(TestProjectPaths.projectPath("build.gradle"))
                .contains("archivesName = mod_id"));
    }

    @Test
    void previousReleaseVersionRemainsHistoricalOnly() throws IOException {
        for (String relativePath : List.of(
                "gradle.properties",
                "README.md",
                "KNOWN_LIMITATIONS.md",
                "build.gradle",
                "src/main/templates/META-INF/neoforge.mods.toml",
                "build/generated/sources/modMetadata/META-INF/neoforge.mods.toml"
        )) {
            assertFalse(
                    Files.readString(TestProjectPaths.projectPath(relativePath)).contains(PREVIOUS_RELEASE_VERSION),
                    "%s still uses previous release %s as live metadata"
                            .formatted(relativePath, PREVIOUS_RELEASE_VERSION)
            );
        }

        assertTrue(Files.readString(TestProjectPaths.projectPath("CHANGELOG.md"))
                .contains("## ButcherCraft v%s - Checkpoint Recovery & Continuous Processing Update"
                        .formatted(PREVIOUS_RELEASE_VERSION)));
    }

    private static Path releaseArtifact() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(TestProjectPaths.projectPath("gradle.properties"))) {
            properties.load(reader);
        }
        return TestProjectPaths.projectPath("build/libs/%s-%s.jar".formatted(
                properties.getProperty("mod_id"), properties.getProperty("mod_version")));
    }
}
