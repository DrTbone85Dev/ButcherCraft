package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointGenerationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DevelopmentCheckpointGenerationSelector {
    private static final Pattern CANONICAL =
            Pattern.compile("^butchercraft:checkpoint/(\\d{20})/(\\d+)$");

    private DevelopmentCheckpointGenerationSelector() {
    }

    public static Selection parse(String selector) {
        String cleaned = selector == null ? "" : selector.strip();
        Matcher matcher = CANONICAL.matcher(cleaned);
        if (!matcher.matches()) {
            return Selection.failed(new DevelopmentCheckpointFailure(
                    DevelopmentCheckpointFailureCode.INVALID_GENERATION_SELECTOR,
                    "generation",
                    "Generation selector must be a canonical checkpoint generation id"
            ));
        }
        try {
            long sequence = Long.parseLong(matcher.group(1));
            long tick = Long.parseLong(matcher.group(2));
            CheckpointGenerationId generationId = CheckpointGenerationId.of(sequence, tick);
            if (!generationId.canonicalValue().equals(cleaned)) {
                return Selection.failed(new DevelopmentCheckpointFailure(
                        DevelopmentCheckpointFailureCode.INVALID_GENERATION_SELECTOR,
                        "generation",
                        "Generation selector is not in canonical checkpoint form"
                ));
            }
            return Selection.selected(generationId);
        } catch (RuntimeException exception) {
            return Selection.failed(new DevelopmentCheckpointFailure(
                    DevelopmentCheckpointFailureCode.INVALID_GENERATION_SELECTOR,
                    "generation",
                    "Generation selector could not be parsed"
            ));
        }
    }

    public record Selection(
            Optional<CheckpointGenerationId> generationId,
            List<DevelopmentCheckpointFailure> failures
    ) {
        public Selection {
            generationId = Objects.requireNonNull(generationId, "generationId");
            failures = Objects.requireNonNull(failures, "failures").stream()
                    .map(failure -> Objects.requireNonNull(failure, "failure"))
                    .sorted()
                    .toList();
        }

        public boolean successful() {
            return generationId.isPresent();
        }

        private static Selection selected(CheckpointGenerationId generationId) {
            return new Selection(Optional.of(generationId), List.of());
        }

        private static Selection failed(DevelopmentCheckpointFailure failure) {
            return new Selection(Optional.empty(), List.of(failure));
        }
    }
}
