package com.butchercraft.world.checkpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerNativeRestorationHardCrashProcessTest {
    @TempDir
    Path temporary;

    @Test
    void freshProcessResumesSameRestorationAfterEightOwnersWereDurablyPublished() throws Exception {
        Path worldRoot = temporary.resolve("world");
        ProcessResult crashed = runChild(worldRoot, "crash");
        assertEquals(OwnerNativeRestorationHardCrashProcess.HARD_CRASH_EXIT_CODE, crashed.exitCode(),
                crashed.output());

        OwnerNativeRestorationHardCrashProcess.Fixture fixture =
                OwnerNativeRestorationHardCrashProcess.fixture(worldRoot);
        List<RestorationIntent> intents = fixture.storage().incompleteIntents();
        assertEquals(1, intents.size());
        RestorationIntent intent = intents.getFirst();
        assertEquals(8, intent.expectedOwners().stream().filter(expected ->
                fixture.storage().loadParticipant(
                        intent.restorationIdentity(), expected.ownerId()).isPresent()).count());

        ProcessResult resumed = runChild(worldRoot, "resume");
        assertEquals(0, resumed.exitCode(), resumed.output());
        assertTrue(resumed.output().contains(
                "RESTORATION_RESUMED " + intent.restorationIdentity().value()));

        RestorationResult result = fixture.storage().loadResult(intent.restorationIdentity()).orElseThrow();
        assertEquals(17, result.participants().size());
        assertFalse(fixture.storage().incompleteIntents().contains(intent));
        for (CheckpointOwnerId owner : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            assertArrayEquals(
                    OwnerNativeRestorationHardCrashProcess.nativeBytes(owner),
                    Files.readAllBytes(fixture.ownerRoot().resolve(
                            OwnerNativeRestorationHardCrashProcess.relativePath(owner)))
            );
        }
    }

    private ProcessResult runChild(Path worldRoot, String mode) throws Exception {
        String classpath = System.getProperty("butchercraft.testRuntimeClasspath");
        assertFalse(classpath == null || classpath.isBlank(), "Gradle must expose the test runtime classpath");
        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java"
        );
        Path argumentFile = temporary.resolve(mode + ".args");
        Files.writeString(argumentFile, String.join(System.lineSeparator(),
                "-cp",
                quote(classpath),
                OwnerNativeRestorationHardCrashProcess.class.getName(),
                quote(worldRoot.toAbsolutePath().normalize().toString()),
                mode
        ) + System.lineSeparator(), StandardCharsets.UTF_8);
        Path outputFile = temporary.resolve(mode + ".log");
        Process process = new ProcessBuilder(java.toString(), "@" + argumentFile)
                .redirectErrorStream(true)
                .redirectOutput(outputFile.toFile())
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        String output = Files.exists(outputFile)
                ? Files.readString(outputFile, StandardCharsets.UTF_8)
                : "";
        assertTrue(finished, () -> "Child restoration process timed out:\n" + output);
        return new ProcessResult(process.exitValue(), output);
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
