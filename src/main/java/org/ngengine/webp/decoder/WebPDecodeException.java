package org.ngengine.webp.decoder;

/** Thrown when the input is not a supported or valid WebP bitstream. */
public final class WebPDecodeException extends Exception {
    /**
     * Creates a decode exception with a detail message.
     *
     * @param message failure description
     */
    public WebPDecodeException(String message) {
        super(message);
    }

    /**
     * Creates a decode exception with a detail message and root cause.
     *
     * @param message failure description
     * @param cause underlying error that triggered decode failure
     */
    public WebPDecodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
