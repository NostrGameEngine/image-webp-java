package io.github.imagewebp.decoder;

import java.nio.ByteBuffer;

/** Decoded WebP still image as tightly-packed RGBA8888. */
public final class DecodedWebP {
    public final int width;
    public final int height;
    public final boolean hasAlpha;

    /** Tightly-packed RGBA8888. Treat as direct-capable (do not assume array-backed). */
    public final ByteBuffer rgba;

    public DecodedWebP(int width, int height, boolean hasAlpha, ByteBuffer rgba) {
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.rgba = rgba;
    }
}
