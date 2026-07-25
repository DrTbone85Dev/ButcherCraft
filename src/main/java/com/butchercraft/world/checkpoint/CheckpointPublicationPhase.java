package com.butchercraft.world.checkpoint;

enum CheckpointPublicationPhase {
    BEFORE_PAYLOAD_WRITE,
    DURING_PAYLOAD_SET,
    BEFORE_MANIFEST,
    AFTER_MANIFEST,
    BEFORE_FINAL_MOVE,
    AFTER_FINAL_MOVE,
    DURING_HEAD_WRITE,
    AFTER_HEAD_PUBLICATION
}
