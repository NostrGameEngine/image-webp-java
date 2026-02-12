package io.github.imagewebp.decoder;

/** Decoded WebP still image as tightly-packed RGBA8888. */
public final class DecodedWebP {
    public final int width;
    public final int height;
    public final boolean hasAlpha;
    public final byte[] rgba;

    public DecodedWebP(int width, int height, boolean hasAlpha, byte[] rgba) {
        this.width = width;
        this.height = height;
        this.hasAlpha = hasAlpha;
        this.rgba = rgba;
    }
}
