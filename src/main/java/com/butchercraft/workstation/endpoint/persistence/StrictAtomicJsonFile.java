package com.butchercraft.workstation.endpoint.persistence;

import com.butchercraft.persistence.AtomicFilePublication;

import java.nio.file.Path;
import java.util.Objects;

final class StrictAtomicJsonFile {
    private StrictAtomicJsonFile() {
    }

    static void publish(Path filePath, String canonicalJson) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(canonicalJson, "canonicalJson");
        AtomicFilePublication.publishUtf8(filePath, canonicalJson, "Workstation endpoint state");
    }

    static String read(Path filePath) {
        return AtomicFilePublication.readUtf8(filePath, "Workstation endpoint state");
    }

    static void requireNoInterruptedPublication(Path filePath) {
        AtomicFilePublication.requireNoInterruptedPublication(filePath, "Workstation endpoint state");
    }
}
