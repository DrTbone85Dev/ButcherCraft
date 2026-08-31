package com.butchercraft.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicFilePublicationTest {
    @TempDir
    private Path temporaryDirectory;

    @Test
    void transientAccessDenialRetriesTheSameFrozenBytesAndSucceeds() throws Exception {
        Path target = temporaryDirectory.resolve("state.json");
        String frozenRevision = "{\"revision\":41,\"identity\":\"test:same\"}\n";
        List<byte[]> attemptedPayloads = Collections.synchronizedList(new ArrayList<>());
        List<Long> delays = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger attempts = new AtomicInteger();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            attemptedPayloads.add(Files.readAllBytes(source));
            if (attempts.getAndIncrement() == 0) throw new AccessDeniedException(destination.toString());
            move(source, destination, replace);
        }, delays::add);

        publisher.publishFrozen(target, frozenRevision.getBytes(StandardCharsets.UTF_8), "test state");

        assertEquals(2, attempts.get());
        assertEquals(List.of(10L), delays);
        assertEquals(frozenRevision, Files.readString(target));
        AtomicFilePublication restarted = new AtomicFilePublication(AtomicFilePublicationTest::move, ignored -> { });
        assertArrayEquals(
                frozenRevision.getBytes(StandardCharsets.UTF_8),
                restarted.readFrozen(target, "restarted test state")
        );
        assertArrayEquals(attemptedPayloads.get(0), attemptedPayloads.get(1));
        assertNoAttemptFiles(target);
    }

    @Test
    void separatePublicationsUseUniqueAttemptFiles() {
        Path target = temporaryDirectory.resolve("unique.json");
        List<Path> attemptFiles = new ArrayList<>();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            attemptFiles.add(source);
            move(source, destination, replace);
        }, ignored -> { });

        publisher.publishFrozen(target, "first".getBytes(StandardCharsets.UTF_8), "unique target");
        publisher.publishFrozen(target, "second".getBytes(StandardCharsets.UTF_8), "unique target");

        assertEquals(2, attemptFiles.size());
        assertNotEquals(attemptFiles.get(0), attemptFiles.get(1));
    }

    @Test
    void repeatedAccessDenialExhaustsBoundedBudgetWithoutChangingAuthoritativeState() throws Exception {
        Path target = temporaryDirectory.resolve("state.json");
        Files.writeString(target, "old-authoritative-state");
        AtomicInteger moveAttempts = new AtomicInteger();
        AtomicInteger logicalMutations = new AtomicInteger();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            moveAttempts.incrementAndGet();
            throw new AccessDeniedException(destination.toString());
        }, ignored -> { });

        logicalMutations.incrementAndGet();
        assertThrows(UncheckedIOException.class, () -> publisher.publishFrozen(
                target,
                "new-frozen-state".getBytes(StandardCharsets.UTF_8),
                "test state"
        ));

        assertEquals(AtomicFilePublication.MAX_MOVE_ATTEMPTS, moveAttempts.get());
        assertEquals(1, logicalMutations.get(), "Filesystem retry must not rerun the logical mutation");
        assertEquals("old-authoritative-state", Files.readString(target));
        assertNoAttemptFiles(target);
    }

    @Test
    void arbitraryIoFailureIsNotRetried() {
        Path target = temporaryDirectory.resolve("state.json");
        AtomicInteger attempts = new AtomicInteger();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            attempts.incrementAndGet();
            throw new IOException("disk corruption");
        }, ignored -> { });

        assertThrows(UncheckedIOException.class, () -> publisher.publishFrozen(
                target,
                "payload".getBytes(StandardCharsets.UTF_8),
                "test state"
        ));

        assertEquals(1, attempts.get());
    }

    @Test
    void unsupportedAtomicMoveFailsWithoutNonAtomicFallback() {
        Path target = temporaryDirectory.resolve("state.json");
        AtomicInteger attempts = new AtomicInteger();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            attempts.incrementAndGet();
            throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "unsupported");
        }, ignored -> { });

        assertThrows(UncheckedIOException.class, () -> publisher.publishFrozen(
                target,
                "payload".getBytes(StandardCharsets.UTF_8),
                "test state"
        ));

        assertEquals(1, attempts.get());
        assertFalse(Files.exists(target));
    }

    @Test
    void explicitlyApprovedReducedGuaranteeIsReportedAndPublishesExactBytes() throws Exception {
        Path target = temporaryDirectory.resolve("reduced.json");
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), destination.toString(), "unsupported");
        }, ignored -> { });

        AtomicFilePublication.PublicationGuarantee guarantee = publisher.publishFrozenWithPolicy(
                target,
                "checkpoint-head".getBytes(StandardCharsets.UTF_8),
                "checkpoint head",
                true
        );

        assertEquals(AtomicFilePublication.PublicationGuarantee.REDUCED_GUARANTEE, guarantee);
        assertEquals("checkpoint-head", Files.readString(target));
        assertNoAttemptFiles(target);
    }

    @Test
    void unchangedCheckIsSerializedAndCannotTrustStaleCallerState() {
        Path target = temporaryDirectory.resolve("conditional.json");
        AtomicFilePublication publisher = new AtomicFilePublication(AtomicFilePublicationTest::move, ignored -> { });
        byte[] first = "first".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".getBytes(StandardCharsets.UTF_8);

        assertTrue(publisher.publishFrozenIfChanged(target, first, "conditional state"));
        assertFalse(publisher.publishFrozenIfChanged(target, first, "conditional state"));
        assertTrue(publisher.publishFrozenIfChanged(target, second, "conditional state"));
        assertTrue(publisher.publishFrozenIfChanged(target, first, "conditional state"));

        assertEquals("first", AtomicFilePublication.readUtf8(target, "conditional state"));
    }

    @Test
    void sameTargetPublicationsSerializeFromWriteThroughReadBack() throws Exception {
        Path target = temporaryDirectory.resolve("same.json");
        CountDownLatch firstMoveEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstMove = new CountDownLatch(1);
        AtomicInteger moveCalls = new AtomicInteger();
        AtomicInteger activeMoves = new AtomicInteger();
        AtomicInteger maximumActiveMoves = new AtomicInteger();
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            int active = activeMoves.incrementAndGet();
            maximumActiveMoves.accumulateAndGet(active, Math::max);
            try {
                if (moveCalls.getAndIncrement() == 0) {
                    firstMoveEntered.countDown();
                    await(releaseFirstMove);
                }
                move(source, destination, replace);
            } finally {
                activeMoves.decrementAndGet();
            }
        }, ignored -> { });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> publisher.publishFrozen(
                    target, "first".getBytes(StandardCharsets.UTF_8), "same target"));
            assertTrue(firstMoveEntered.await(5, TimeUnit.SECONDS));
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                publisher.publishFrozen(target, "second".getBytes(StandardCharsets.UTF_8), "same target");
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertFalse(second.isDone(), "Second same-target publication must wait for the first target lock");
            assertEquals(1, moveCalls.get());
            releaseFirstMove.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertEquals(1, maximumActiveMoves.get());
        assertEquals("second", Files.readString(target));
        assertNoAttemptFiles(target);
    }

    @Test
    void unrelatedTargetsRemainIndependent() throws Exception {
        Path targetA = temporaryDirectory.resolve("a.json");
        Path targetB = temporaryDirectory.resolve("b.json");
        CountDownLatch targetAEntered = new CountDownLatch(1);
        CountDownLatch releaseTargetA = new CountDownLatch(1);
        CountDownLatch targetBEntered = new CountDownLatch(1);
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            if (destination.equals(targetA.toAbsolutePath().normalize())) {
                targetAEntered.countDown();
                await(releaseTargetA);
            } else if (destination.equals(targetB.toAbsolutePath().normalize())) {
                targetBEntered.countDown();
            }
            move(source, destination, replace);
        }, ignored -> { });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> publisher.publishFrozen(
                    targetA, "a".getBytes(StandardCharsets.UTF_8), "target A"));
            assertTrue(targetAEntered.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> publisher.publishFrozen(
                    targetB, "b".getBytes(StandardCharsets.UTF_8), "target B"));
            assertTrue(targetBEntered.await(5, TimeUnit.SECONDS),
                    "A blocked target must not globally serialize another target");
            second.get(5, TimeUnit.SECONDS);
            releaseTargetA.countDown();
            first.get(5, TimeUnit.SECONDS);
        }

        assertEquals("a", Files.readString(targetA));
        assertEquals("b", Files.readString(targetB));
    }

    @Test
    void sharedReadsCannotOverlapSameTargetPublication() throws Exception {
        Path target = temporaryDirectory.resolve("state.json");
        Files.writeString(target, "old");
        CountDownLatch moveEntered = new CountDownLatch(1);
        CountDownLatch releaseMove = new CountDownLatch(1);
        AtomicFilePublication publisher = new AtomicFilePublication((source, destination, replace) -> {
            moveEntered.countDown();
            await(releaseMove);
            move(source, destination, replace);
        }, ignored -> { });

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> write = executor.submit(() -> publisher.publishFrozen(
                    target, "new".getBytes(StandardCharsets.UTF_8), "state"));
            assertTrue(moveEntered.await(5, TimeUnit.SECONDS));
            Future<byte[]> read = executor.submit(() -> publisher.readFrozen(target, "state"));
            assertFalse(read.isDone(), "Same-target read must wait until atomic publication and read-back finish");
            releaseMove.countDown();
            write.get(5, TimeUnit.SECONDS);
            assertEquals("new", new String(read.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8));
        }
    }

    @Test
    void boundedConcurrentStressLeavesOneExactPayloadAndNoAttemptFiles() throws Exception {
        Path target = temporaryDirectory.resolve("stress.json");
        AtomicFilePublication publisher = new AtomicFilePublication(AtomicFilePublicationTest::move, ignored -> { });
        int workers = 8;
        int publicationsPerWorker = 250;

        try (ExecutorService executor = Executors.newFixedThreadPool(workers)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int workerId = worker;
                futures.add(executor.submit(() -> {
                    for (int revision = 0; revision < publicationsPerWorker; revision++) {
                        String payload = "{\"worker\":" + workerId + ",\"revision\":" + revision + "}\n";
                        publisher.publishFrozen(target, payload.getBytes(StandardCharsets.UTF_8), "stress state");
                    }
                }));
            }
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        }

        String finalPayload = Files.readString(target);
        assertTrue(finalPayload.matches("\\{\"worker\":\\d+,\"revision\":\\d+}\\R"));
        assertNoAttemptFiles(target);
    }

    @Test
    void accessDenialClassificationIsNarrow() {
        assertTrue(AtomicFilePublication.retryableAccessDenial(new AccessDeniedException("target")));
        assertFalse(AtomicFilePublication.retryableAccessDenial(new IOException("unrelated")));
        assertFalse(AtomicFilePublication.retryableAccessDenial(
                new AtomicMoveNotSupportedException("source", "target", "unsupported")));
    }

    private static void move(Path source, Path target, boolean replaceExisting) throws IOException {
        if (replaceExisting) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } else {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static void await(CountDownLatch latch) throws IOException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IOException("Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted during test coordination", exception);
        }
    }

    private static void assertNoAttemptFiles(Path target) throws IOException {
        try (var files = Files.list(target.getParent())) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString()
                    .startsWith(target.getFileName() + ".tmp-")));
        }
    }
}
