package com.butchercraft.world.checkpoint;

import java.util.List;

public interface CheckpointOwnerRestorationCandidate {
    CheckpointOwnerId ownerId();

    CheckpointOwnerValidationMetadata validationMetadata();

    List<CheckpointFailure> validatePublication();

    CheckpointOwnerRestorationPublicationResult publish();

    CheckpointOwnerRestorationPublicationResult rollbackPublication();
}
