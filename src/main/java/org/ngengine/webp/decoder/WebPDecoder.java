package org.ngengine.webp.decoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

/** Pure-Java WebP decoder */
public final class WebPDecoder {
    private WebPDecoder() {}

    /**
     * Decodes a WebP still image from complete file bytes.
     *
     * @param webpBytes full WebP file bytes
     * @return decoded image in RGBA8888 format
     * @throws WebPDecodeException if the input is invalid, unsupported, or truncated
     */
    public static DecodedWebP decode(byte[] webpBytes) throws WebPDecodeException {
        return decode(webpBytes, ByteBuffer::allocate);
    }

    /**
     * Decodes a WebP still image from complete file bytes.
     *
     * @param webpBytes full WebP file bytes
     * @param rgbaAllocator Allocator used for the output RGBA buffer (and internal temporary RGBA buffers).
     *                      The returned buffer may be direct; code must not assume array-backed buffers.
     * @return decoded image in RGBA8888 format
     * @throws WebPDecodeException if the input is invalid, unsupported, truncated, or decode fails
     */
    public static DecodedWebP decode(byte[] webpBytes, IntFunction<ByteBuffer> rgbaAllocator) throws WebPDecodeException {
        Objects.requireNonNull(rgbaAllocator, "rgbaAllocator");
        try {
            return WebPRiffDecoder.decode(webpBytes, rgbaAllocator);
        } catch (IOException e) {
            throw new WebPDecodeException("IO error while decoding", e);
        } catch (RuntimeException e) {
            // e.g. BufferUnderflowException from corrupt/short input
            throw new WebPDecodeException("Invalid or corrupt WebP", e);
        }
    }
}
