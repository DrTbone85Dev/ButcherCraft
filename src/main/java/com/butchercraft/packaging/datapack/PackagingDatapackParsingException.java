package com.butchercraft.packaging.datapack;

/**
 * Parser failure carrying a structured packaging datapack validation code.
 */
final class PackagingDatapackParsingException extends RuntimeException {
    private final PackagingDatapackErrorCode code;

    PackagingDatapackParsingException(PackagingDatapackErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    PackagingDatapackErrorCode code() {
        return code;
    }
}
