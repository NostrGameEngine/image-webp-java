package io.github.imagewebp.decoder;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

/** ALPH chunk decoding and application onto an existing RGBA buffer. */
final class AlphaChunkDecoder {
    private AlphaChunkDecoder() {}

    static void applyAlph(
            byte[] webp,
            int alphOff,
            int alphLen,
            int width,
            int height,
            ByteBuffer rgba,
            IntFunction<ByteBuffer> rgbaAllocator
    ) throws WebPDecodeException {
        if (alphLen < 1) {
            throw new WebPDecodeException("Invalid ALPH chunk");
        }

        int info = webp[alphOff] & 0xFF;
        int preprocessing = (info >> 4) & 0b11;
        int filtering = (info >> 2) & 0b11;
        int compression = info & 0b11;

        if (preprocessing != 0 && preprocessing != 1) {
            throw new WebPDecodeException("Invalid alpha preprocessing");
        }
        if (compression != 0 && compression != 1) {
            throw new WebPDecodeException("Invalid alpha compression method");
        }

        byte[] alpha;
        int payloadOff = alphOff + 1;
        int payloadLen = alphLen - 1;
        if (compression == 0) {
            if (payloadLen < width * height) {
                throw new WebPDecodeException("ALPH payload too short");
            }
            alpha = new byte[width * height];
            System.arraycopy(webp, payloadOff, alpha, 0, alpha.length);
        } else {
            // Lossless-compressed alpha plane: decode as VP8L with implicit dimensions and read GREEN.
            int rgbaSize = width * height * 4;
            ByteBuffer tmp = rgbaAllocator.apply(rgbaSize);
            if (tmp == null || tmp.capacity() < rgbaSize) {
                throw new WebPDecodeException("RGBA allocator returned too-small buffer");
            }
            tmp.clear();
            tmp.limit(rgbaSize);
            Vp8LDecoder.decodeToRgba(webp, payloadOff, payloadLen, width, height, true, tmp, rgbaAllocator);

            alpha = new byte[width * height];
            for (int i = 0; i < alpha.length; i++) {
                alpha[i] = tmp.get(i * 4 + 1);
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int pred = AlphaPredictor.predict(x, y, width, filtering, rgba);
                rgba.put(idx * 4 + 3, (byte) ((pred + (alpha[idx] & 0xFF)) & 0xFF));
            }
        }
    }
}
