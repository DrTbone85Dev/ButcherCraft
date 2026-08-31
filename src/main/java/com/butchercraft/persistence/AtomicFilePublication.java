package com.butchercraft.persistence;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Shared same-filesystem durable publication with per-target serialization and
 * bounded retry for transient Windows sharing denials.
 */
public final class AtomicFilePublication {
    static final int MAX_MOVE_ATTEMPTS = 6;
    private static final long[] RETRY_DELAYS_MILLIS = {10L, 20L, 40L, 80L, 100L};
    private static final AtomicFilePublication SHARED = new AtomicFilePublication(
            AtomicFilePublication::atomicMove,
            Thread::sleep
    );

    private final AtomicMoveOperation moveOperation;
    private final RetryPause retryPause;
    private final ConcurrentMap<Path, ReentrantLock> targetLocks = new ConcurrentHashMap<>();

    AtomicFilePublication(AtomicMoveOperation moveOperation, RetryPause retryPause) {
        this.moveOperation = Objects.requireNonNull(moveOperation, "moveOperation");
        this.retryPause = Objects.requireNonNull(retryPause, "retryPause");
    }

    public static void publishUtf8(Path target, String content, String label) {
        Objects.requireNonNull(content, "content");
        SHARED.publishFrozen(target, content.getBytes(StandardCharsets.UTF_8), label);
    }

    public static boolean publishUtf8IfChanged(Path target, String content, String label) {
        Objects.requireNonNull(content, "content");
        return SHARED.publishFrozenIfChanged(target, content.getBytes(StandardCharsets.UTF_8), label);
    }

    public static void publishBytes(Path target, byte[] content, String label) {
        SHARED.publishFrozen(target, content, label);
    }

    public static ConditionalPublication publishBytesIfDigestMatches(
            Path target,
            Optional<String> expectedCurrentDigest,
            byte[] content,
            String label
    ) {
        return SHARED.publishFrozenIfDigestMatches(target, expectedCurrentDigest, content, label);
    }

    public static PublicationGuarantee publishBytesAllowingReducedGuarantee(
            Path target,
            byte[] content,
            String label
    ) {
        return SHARED.publishFrozenWithPolicy(target, content, label, true);
    }

    public static String readUtf8(Path target, String label) {
        return new String(SHARED.readFrozen(target, label), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(Path target, String label) {
        return SHARED.readFrozen(target, label);
    }

    public static void requireNoInterruptedPublication(Path target, String label) {
        SHARED.requireNoInterruptedPublicationArtifacts(target, label);
    }

    public static void movePreparedAtomically(Path source, Path target, boolean replaceExisting, String label) {
        SHARED.movePrepared(source, target, replaceExisting, label);
    }

    void publishFrozen(Path rawTarget, byte[] content, String label) {
        publishFrozenWithPolicy(rawTarget, content, label, false);
    }

    PublicationGuarantee publishFrozenWithPolicy(
            Path rawTarget,
            byte[] content,
            String label,
            boolean allowReducedGuarantee
    ) {
        Path target = normalize(rawTarget);
        byte[] frozen = Objects.requireNonNull(content, "content").clone();
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            return publishLocked(target, frozen, publicationLabel, allowReducedGuarantee);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed atomic publication of " + publicationLabel + " to " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    boolean publishFrozenIfChanged(Path rawTarget, byte[] content, String label) {
        Path target = normalize(rawTarget);
        byte[] frozen = Objects.requireNonNull(content, "content").clone();
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            if (Files.exists(target) && Arrays.equals(readWithRetry(target), frozen)) return false;
            publishLocked(target, frozen, publicationLabel, false);
            return true;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed atomic publication of " + publicationLabel + " to " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    private ConditionalPublication publishFrozenIfDigestMatches(
            Path rawTarget,
            Optional<String> rawExpectedCurrentDigest,
            byte[] content,
            String label
    ) {
        Path target = normalize(rawTarget);
        Optional<String> expectedCurrentDigest = Objects.requireNonNull(
                rawExpectedCurrentDigest,
                "expectedCurrentDigest"
        );
        byte[] frozen = Objects.requireNonNull(content, "content").clone();
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            if (Files.exists(target)) {
                byte[] current = readWithRetry(target);
                if (Arrays.equals(current, frozen)) return ConditionalPublication.OBSERVED_EXACT;
                if (expectedCurrentDigest.isEmpty() || !sha256(current).equals(expectedCurrentDigest.orElseThrow())) {
                    throw new IllegalStateException(
                            "Current " + publicationLabel + " differs from its frozen restoration precondition"
                    );
                }
            } else if (expectedCurrentDigest.isPresent()) {
                throw new IllegalStateException(
                        "Current " + publicationLabel + " is absent but restoration expected prior content"
                );
            }
            publishLocked(target, frozen, publicationLabel, false);
            return ConditionalPublication.PUBLISHED_ATOMICALLY;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed conditional atomic publication of " + publicationLabel + " to " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    byte[] readFrozen(Path rawTarget, String label) {
        Path target = normalize(rawTarget);
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            return readWithRetry(target);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to read " + publicationLabel + " from " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    void movePrepared(Path rawSource, Path rawTarget, boolean replaceExisting, String label) {
        Path source = normalize(rawSource);
        Path target = normalize(rawTarget);
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            moveWithRetry(source, target, replaceExisting, null);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed atomic move of " + publicationLabel + " to " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    private void requireNoInterruptedPublicationArtifacts(Path rawTarget, String label) {
        Path target = normalize(rawTarget);
        String publicationLabel = requireLabel(label);
        ReentrantLock lock = lockFor(target);
        lock.lock();
        try {
            if (Files.exists(target)) return;
            Path parent = target.getParent();
            if (parent == null || !Files.isDirectory(parent)) return;
            String legacyName = target.getFileName() + ".tmp";
            String attemptPrefix = legacyName + "-";
            try (Stream<Path> paths = Files.list(parent)) {
                Path artifact = paths
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.equals(legacyName) || name.startsWith(attemptPrefix);
                        })
                        .findFirst()
                        .orElse(null);
                if (artifact != null) {
                    throw new IllegalStateException(
                            "Interrupted " + publicationLabel + " publication requires recovery: " + artifact
                    );
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to inspect interrupted " + publicationLabel + " publication artifacts for " + target,
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    private PublicationGuarantee publishLocked(
            Path target,
            byte[] frozen,
            String label,
            boolean allowReducedGuarantee
    ) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, target.getFileName() + ".tmp-", "");
        IOException publicationFailure = null;
        try {
            writeForced(temporary, frozen);
            PublicationGuarantee guarantee;
            try {
                moveWithRetry(temporary, target, true, frozen);
                guarantee = PublicationGuarantee.ATOMIC;
            } catch (AtomicMoveNotSupportedException exception) {
                if (!allowReducedGuarantee) {
                    throw new IOException("Atomic replacement is required for " + target, exception);
                }
                replaceWithRetry(temporary, target, true, frozen);
                guarantee = PublicationGuarantee.REDUCED_GUARANTEE;
            }
            byte[] published = readWithRetry(target);
            if (!Arrays.equals(published, frozen)) {
                throw new IOException("Published " + label + " failed byte-for-byte read-back verification");
            }
            return guarantee;
        } catch (IOException exception) {
            publicationFailure = exception;
            throw exception;
        } finally {
            try {
                deleteTemporaryWithRetry(temporary);
            } catch (IOException cleanupFailure) {
                if (publicationFailure != null) {
                    publicationFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private void moveWithRetry(Path source, Path target, boolean replaceExisting, byte[] expectedBytes)
            throws IOException {
        for (int attempt = 1; attempt <= MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                moveOperation.move(source, target, replaceExisting);
                return;
            } catch (AtomicMoveNotSupportedException exception) {
                throw exception;
            } catch (IOException exception) {
                if (expectedBytes != null && targetMatches(target, expectedBytes)) return;
                if (!retryableAccessDenial(exception) || attempt == MAX_MOVE_ATTEMPTS) throw exception;
                if (!Files.exists(source)) {
                    throw new IOException("Atomic publication source disappeared before retry: " + source, exception);
                }
                pauseBeforeRetry(attempt, exception);
            }
        }
        throw new IllegalStateException("Unreachable atomic-move retry state");
    }

    private void replaceWithRetry(Path source, Path target, boolean replaceExisting, byte[] expectedBytes)
            throws IOException {
        for (int attempt = 1; attempt <= MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                if (replaceExisting) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source, target);
                }
                return;
            } catch (IOException exception) {
                if (expectedBytes != null && targetMatches(target, expectedBytes)) return;
                if (!retryableAccessDenial(exception) || attempt == MAX_MOVE_ATTEMPTS) throw exception;
                if (!Files.exists(source)) {
                    throw new IOException("Publication source disappeared before retry: " + source, exception);
                }
                pauseBeforeRetry(attempt, exception);
            }
        }
        throw new IllegalStateException("Unreachable replacement-move retry state");
    }

    private byte[] readWithRetry(Path target) throws IOException {
        for (int attempt = 1; attempt <= MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                return Files.readAllBytes(target);
            } catch (IOException exception) {
                if (!retryableAccessDenial(exception) || attempt == MAX_MOVE_ATTEMPTS) throw exception;
                pauseBeforeRetry(attempt, exception);
            }
        }
        throw new IllegalStateException("Unreachable read retry state");
    }

    private void deleteTemporaryWithRetry(Path temporary) throws IOException {
        for (int attempt = 1; attempt <= MAX_MOVE_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(temporary);
                return;
            } catch (IOException exception) {
                if (!retryableAccessDenial(exception) || attempt == MAX_MOVE_ATTEMPTS) throw exception;
                pauseBeforeRetry(attempt, exception);
            }
        }
    }

    private void pauseBeforeRetry(int failedAttempt, IOException cause) throws IOException {
        long delay = RETRY_DELAYS_MILLIS[failedAttempt - 1];
        try {
            retryPause.pause(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Interrupted while retrying atomic filesystem publication"
            );
            interrupted.initCause(cause);
            interrupted.addSuppressed(exception);
            throw interrupted;
        }
    }

    private ReentrantLock lockFor(Path target) {
        return targetLocks.computeIfAbsent(target, ignored -> new ReentrantLock());
    }

    private static void writeForced(Path temporary, byte[] frozen) throws IOException {
        try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            ByteBuffer buffer = ByteBuffer.wrap(frozen);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void atomicMove(Path source, Path target, boolean replaceExisting) throws IOException {
        if (replaceExisting) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } else {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static boolean targetMatches(Path target, byte[] expectedBytes) {
        try {
            return Files.exists(target) && Arrays.equals(Files.readAllBytes(target), expectedBytes);
        } catch (IOException ignored) {
            return false;
        }
    }

    static boolean retryableAccessDenial(IOException exception) {
        if (exception instanceof AccessDeniedException) return true;
        if (!(exception instanceof FileSystemException fileSystemException)) return false;
        String reason = fileSystemException.getReason();
        if (reason == null) return false;
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("access is denied")
                || normalized.contains("being used by another process")
                || normalized.contains("sharing violation");
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static String requireLabel(String label) {
        String value = Objects.requireNonNull(label, "label");
        if (value.isBlank()) throw new IllegalArgumentException("label must not be blank");
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @FunctionalInterface
    interface AtomicMoveOperation {
        void move(Path source, Path target, boolean replaceExisting) throws IOException;
    }

    @FunctionalInterface
    interface RetryPause {
        void pause(long millis) throws InterruptedException;
    }

    public enum PublicationGuarantee {
        ATOMIC,
        REDUCED_GUARANTEE
    }

    public enum ConditionalPublication {
        OBSERVED_EXACT,
        PUBLISHED_ATOMICALLY
    }
}
