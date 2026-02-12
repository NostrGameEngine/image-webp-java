package io.github.imagewebp.decoder;

/** Minimal little-endian byte reader for VP8/WebP payloads. */
final class Vp8Reader {
    private final byte[] data;
    private int pos;

    Vp8Reader(byte[] data, int off, int len) throws WebPDecodeException {
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new WebPDecodeException("Invalid VP8 buffer bounds");
        }
        this.data = data;
        this.pos = off;
        // caller enforces limit via len; we only bounds-check on reads
        this.limit = off + len;
    }

    private final int limit;

    /** Returns current read position relative to the backing byte array. */
    int position() {
        return pos;
    }

    /** Returns remaining unread bytes inside the configured slice. */
    int remaining() {
        return limit - pos;
    }

    /** Reads an unsigned 8-bit value. */
    int readU8() throws WebPDecodeException {
        if (pos >= limit) {
            throw new WebPDecodeException("Unexpected EOF");
        }
        return data[pos++] & 0xFF;
    }

    /** Reads an unsigned 16-bit little-endian value. */
    int readU16LE() throws WebPDecodeException {
        int b0 = readU8();
        int b1 = readU8();
        return b0 | (b1 << 8);
    }

    /** Reads an unsigned 24-bit little-endian value. */
    int readU24LE() throws WebPDecodeException {
        int b0 = readU8();
        int b1 = readU8();
        int b2 = readU8();
        return b0 | (b1 << 8) | (b2 << 16);
    }

    /** Copies exactly {@code len} bytes into {@code out}. */
    void readFully(byte[] out, int off, int len) throws WebPDecodeException {
        if (len < 0 || off < 0 || off + len > out.length) {
            throw new WebPDecodeException("Invalid output slice");
        }
        if (pos + len > limit) {
            throw new WebPDecodeException("Unexpected EOF");
        }
        System.arraycopy(data, pos, out, off, len);
        pos += len;
    }

    /** Reads and returns a new byte array of length {@code len}. */
    byte[] readBytes(int len) throws WebPDecodeException {
        byte[] out = new byte[len];
        readFully(out, 0, len);
        return out;
    }

    /** Advances the read position by {@code len} bytes. */
    void skip(int len) throws WebPDecodeException {
        if (pos + len > limit) {
            throw new WebPDecodeException("Unexpected EOF");
        }
        pos += len;
    }
}
