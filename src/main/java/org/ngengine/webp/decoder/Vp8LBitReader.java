package org.ngengine.webp.decoder;

/** Little-endian bit reader used by VP8L lossless decoding. */
final class Vp8LBitReader {
    private final byte[] data;
    private final int end;
    private int pos;

    private long buffer;
    /** Number of currently buffered bits in {@link #buffer}. */
    int nbits;

    Vp8LBitReader(byte[] data, int off, int len) {
        this.data = data;
        this.pos = off;
        this.end = off + len;
        this.buffer = 0;
        this.nbits = 0;
    }

    /** Pulls additional bytes into the little-endian bit buffer. */
    void fill() throws WebPDecodeException {
        while (nbits <= 56 && pos < end) {
            buffer |= ((long) data[pos] & 0xFFL) << nbits;
            nbits += 8;
            pos++;
        }
    }

    /** Peeks {@code num} least-significant bits without consuming them. */
    long peek(int num) {
        return buffer & ((1L << num) - 1);
    }

    /** Returns the full current bit buffer without consuming any bits. */
    long peekFull() {
        return buffer;
    }

    /** Consumes {@code num} bits from the bit buffer. */
    void consume(int num) throws WebPDecodeException {
        if (nbits < num) {
            throw new WebPDecodeException("Corrupt bitstream");
        }
        buffer >>>= num;
        nbits -= num;
    }

    /** Reads an unsigned value composed of {@code num} least-significant buffered bits. */
    int readBits(int num) throws WebPDecodeException {
        if (num < 0 || num > 32) {
            throw new WebPDecodeException("Invalid bit count");
        }
        if (nbits < num) {
            fill();
        }
        if (nbits < num) {
            throw new WebPDecodeException("Corrupt bitstream");
        }
        int v = (int) peek(num);
        consume(num);
        return v;
    }
}
