package com.butchercraft.development.checkpoint;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DevelopmentCheckpointOperationGuard {
    private final AtomicBoolean active = new AtomicBoolean();

    public Optional<GuardLease> tryBegin() {
        if (!active.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(new GuardLease());
    }

    public final class GuardLease implements AutoCloseable {
        private boolean closed;

        private GuardLease() {
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                active.set(false);
            }
        }
    }
}
