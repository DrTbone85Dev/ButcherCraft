package com.butchercraft.product.datapack;

/**
 * Parser failure carrying a structured product datapack validation code.
 */
final class ProductDatapackParsingException extends RuntimeException {
    private final ProductDatapackErrorCode code;

    ProductDatapackParsingException(ProductDatapackErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    ProductDatapackErrorCode code() {
        return code;
    }
}
