package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

final class CheckpointPublicationTiming implements CheckpointPublicationProbe {
    private final AtomicLong headStartNanos = new AtomicLong();
    private final AtomicLong headCommitDurationNanos = new AtomicLong();

    void beginPublication() {
        headStartNanos.set(0L);
        headCommitDurationNanos.set(0L);
    }

    long headCommitDurationNanos() {
        return headCommitDurationNanos.get();
    }

    @Override
    public void reached(CheckpointPublicationPhase phase) throws IOException {
        if (phase == CheckpointPublicationPhase.DURING_HEAD_WRITE) {
            headStartNanos.compareAndSet(0L, System.nanoTime());
        } else if (phase == CheckpointPublicationPhase.AFTER_HEAD_PUBLICATION) {
            long start = headStartNanos.get();
            if (start > 0L) {
                headCommitDurationNanos.set(System.nanoTime() - start);
            }
        }
    }
}
