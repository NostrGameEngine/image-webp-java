package io.github.imagewebp.decoder;

import java.io.IOException;

/** Pure-Java WebP decoder (no AWT). */
public final class WebPDecoder {
    private WebPDecoder() {}

    /** Decode a WebP still image from its full file bytes. */
    public static DecodedWebP decode(byte[] webpBytes) throws WebPDecodeException {
        try {
            return WebPRiffDecoder.decode(webpBytes);
        } catch (IOException e) {
            throw new WebPDecodeException("IO error while decoding", e);
        } catch (RuntimeException e) {
            // e.g. BufferUnderflowException from corrupt/short input
            throw new WebPDecodeException("Invalid or corrupt WebP", e);
        }
    }
}
