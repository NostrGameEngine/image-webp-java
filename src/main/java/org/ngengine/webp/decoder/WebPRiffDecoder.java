package org.ngengine.webp.decoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.IntFunction;

/** RIFF/WebP container parser that dispatches to VP8, VP8L, and optional ALPH decoding paths. */
final class WebPRiffDecoder {
    private WebPRiffDecoder() {}

    static DecodedWebP decode(byte[] bytes) throws IOException, WebPDecodeException {
        return decode(bytes, ByteBuffer::allocate);
    }

    static DecodedWebP decode(byte[] bytes, IntFunction<ByteBuffer> rgbaAllocator) throws IOException, WebPDecodeException {
        if (bytes.length < 12) {
            throw new WebPDecodeException("Input too short");
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        byte[] riff = new byte[4];
        bb.get(riff);
        if (!isFourCC(riff, 'R', 'I', 'F', 'F')) {
            throw new WebPDecodeException("Invalid RIFF signature");
        }

        int riffSize = bb.getInt();

        byte[] webp = new byte[4];
        bb.get(webp);
        if (!isFourCC(webp, 'W', 'E', 'B', 'P')) {
            throw new WebPDecodeException("Invalid WEBP signature");
        }

        int vp8Start = -1;
        int vp8Size = -1;
        int vp8lStart = -1;
        int vp8lSize = -1;
        int vp8xStart = -1;
        int alphStart = -1;
        int alphSize = -1;

        boolean extended = false;
        boolean hasAlpha = false;
        boolean animated = false;
        int width = 0;
        int height = 0;

        // For RIFF, size includes everything after the size field. In practice we just bounds-check on the byte[].
        int maxPos = Math.min(bytes.length, 8 + riffSize);
        if (maxPos < 12) {
            maxPos = bytes.length;
        }

        while (bb.position() + 8 <= maxPos) {
            int chunkHeaderPos = bb.position();
            byte[] fourcc = new byte[4];
            bb.get(fourcc);
            int chunkSize = bb.getInt();
            if (chunkSize < 0) {
                throw new WebPDecodeException("Invalid chunk size");
            }
            int dataStart = bb.position();
            int dataEnd = dataStart + chunkSize;
            if (dataEnd < dataStart || dataEnd > bytes.length) {
                throw new WebPDecodeException("Invalid chunk bounds");
            }

            if (isFourCC(fourcc, 'V', 'P', '8', 'X')) {
                extended = true;
                vp8xStart = dataStart;

                if (chunkSize < 10) {
                    throw new WebPDecodeException("Invalid VP8X chunk");
                }

                int flags = bytes[dataStart] & 0xFF;
                hasAlpha = (flags & 0b0001_0000) != 0;
                animated = (flags & 0b0000_0010) != 0;
                if (animated) {
                    throw new WebPDecodeException("Animated WebP not supported in this Java port");
                }

                width = read3LE(bytes, dataStart + 4) + 1;
                height = read3LE(bytes, dataStart + 7) + 1;
            } else if (isFourCC(fourcc, 'V', 'P', '8', ' ')) {
                vp8Start = dataStart;
                vp8Size = chunkSize;

                if (!extended) {
                    // Parse width/height from VP8 keyframe header.
                    if (chunkSize < 10) {
                        throw new WebPDecodeException("Invalid VP8 chunk");
                    }
                    int tag = (bytes[dataStart] & 0xFF)
                            | ((bytes[dataStart + 1] & 0xFF) << 8)
                            | ((bytes[dataStart + 2] & 0xFF) << 16);
                    boolean keyframe = (tag & 1) == 0;
                    if (!keyframe) {
                        throw new WebPDecodeException("Non-keyframe VP8 not supported");
                    }
                    if ((bytes[dataStart + 3] & 0xFF) != 0x9D
                            || (bytes[dataStart + 4] & 0xFF) != 0x01
                            || (bytes[dataStart + 5] & 0xFF) != 0x2A) {
                        throw new WebPDecodeException("Invalid VP8 magic");
                    }
                    int w = ((bytes[dataStart + 6] & 0xFF) | ((bytes[dataStart + 7] & 0xFF) << 8)) & 0x3FFF;
                    int h = ((bytes[dataStart + 8] & 0xFF) | ((bytes[dataStart + 9] & 0xFF) << 8)) & 0x3FFF;
                    width = w;
                    height = h;
                }
            } else if (isFourCC(fourcc, 'V', 'P', '8', 'L')) {
                vp8lStart = dataStart;
                vp8lSize = chunkSize;

                if (!extended) {
                    if (chunkSize < 5) {
                        throw new WebPDecodeException("Invalid VP8L chunk");
                    }
                    int sig = bytes[dataStart] & 0xFF;
                    if (sig != 0x2F) {
                        throw new WebPDecodeException("Invalid VP8L signature");
                    }
                    int header = (bytes[dataStart + 1] & 0xFF)
                            | ((bytes[dataStart + 2] & 0xFF) << 8)
                            | ((bytes[dataStart + 3] & 0xFF) << 16)
                            | ((bytes[dataStart + 4] & 0xFF) << 24);
                    int version = header >>> 29;
                    if (version != 0) {
                        throw new WebPDecodeException("Unsupported VP8L version: " + version);
                    }
                    width = (1 + header) & 0x3FFF;
                    height = (1 + (header >>> 14)) & 0x3FFF;
                    hasAlpha = ((header >>> 28) & 1) != 0;
                }
            } else if (isFourCC(fourcc, 'A', 'L', 'P', 'H')) {
                alphStart = dataStart;
                alphSize = chunkSize;
            }

            // Skip payload (+ padding to even).
            int rounded = chunkSize + (chunkSize & 1);
            bb.position(dataStart + rounded);

            // Guard against malformed sizes that don't advance.
            if (bb.position() <= chunkHeaderPos) {
                throw new WebPDecodeException("Invalid chunk size (no progress)");
            }
        }

        if (width <= 0 || height <= 0) {
            throw new WebPDecodeException("Missing/invalid dimensions");
        }

        boolean hasVp8 = vp8Start >= 0;
        boolean hasVp8l = vp8lStart >= 0;
        if (hasVp8 == hasVp8l) {
            throw new WebPDecodeException("Expected exactly one of VP8 or VP8L");
        }

        // Decode.
        int rgbaSize = width * height * 4;
        ByteBuffer rgba = rgbaAllocator.apply(rgbaSize);
        if (rgba == null || rgba.capacity() < rgbaSize) {
            throw new WebPDecodeException("RGBA allocator returned too-small buffer");
        }
        rgba.clear();
        rgba.limit(rgbaSize);

        if (hasVp8l) {
            Vp8LDecoder.decodeToRgba(bytes, vp8lStart, vp8lSize, width, height, false, rgba, rgbaAllocator);
            rgba.position(0);
            return new DecodedWebP(width, height, hasAlpha, rgba);
        }

        // VP8 lossy
        Vp8Decoder.decodeToRgba(bytes, vp8Start, vp8Size, width, height, rgba);
        if (hasAlpha) {
            if (alphStart < 0) {
                throw new WebPDecodeException("VP8X alpha flag set but no ALPH chunk found");
            }
            AlphaChunkDecoder.applyAlph(bytes, alphStart, alphSize, width, height, rgba, rgbaAllocator);
        } else {
            // ensure opaque
            for (int i = 3; i < rgbaSize; i += 4) {
                rgba.put(i, (byte) 0xFF);
            }
        }

        rgba.position(0);
        return new DecodedWebP(width, height, hasAlpha, rgba);
    }

    static int read3LE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16);
    }

    private static boolean isFourCC(byte[] a, char c0, char c1, char c2, char c3) {
        return (a[0] & 0xFF) == c0
                && (a[1] & 0xFF) == c1
                && (a[2] & 0xFF) == c2
                && (a[3] & 0xFF) == c3;
    }
}
