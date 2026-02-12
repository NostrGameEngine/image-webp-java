package org.ngengine.webp.decoder;

import java.nio.ByteBuffer;

/** Decoded WebP still image as tightly-packed RGBA8888. */
public final class DecodedWebP {
    /** Image width in pixels. */
    public final int width;
    /** Image height in pixels. */
    public final int height;
    /** True when the decoded source contains transparency. */
    public final boolean hasAlpha;

    /** Tightly-packed RGBA8888. Treat as direct-capable (do not assume array-backed). */
    public final ByteBuffer rgba;

    /**
     * Creates a decoded image container.
     *
     * @param width image width in pixels
     * @param height image height in pixels
     * @param hasAlpha whether source image includes alpha
     * @param rgba tightly-packed RGBA8888 buffer with size {@code width * height * 4}
     */
    public DecodedWebP(int width, int height, boolean hasAlpha, ByteBuffer rgba) {
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.rgba = rgba;
    }
}
