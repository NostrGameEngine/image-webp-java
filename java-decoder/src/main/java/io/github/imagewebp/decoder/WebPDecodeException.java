package io.github.imagewebp.decoder;

/** Thrown when the input is not a supported or valid WebP bitstream. */
public final class WebPDecodeException extends Exception {
    public WebPDecodeException(String message) {
        super(message);
    }

    public WebPDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
