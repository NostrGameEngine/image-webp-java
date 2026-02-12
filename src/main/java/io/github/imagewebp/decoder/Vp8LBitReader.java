package io.github.imagewebp.decoder;

/** Little-endian bit reader used by VP8L lossless decoding. */
final class Vp8LBitReader {
    private final byte[] data;
    private final int end;
    private int pos;

    private long buffer;
    int nbits;

    Vp8LBitReader(byte[] data, int off, int len) {
        this.data = data;
        this.pos = off;
        this.end = off + len;
        this.buffer = 0;
        this.nbits = 0;
    }

    void fill() throws WebPDecodeException {
        while (nbits <= 56 && pos < end) {
            buffer |= ((long) data[pos] & 0xFFL) << nbits;
            nbits += 8;
            pos++;
        }
    }

    long peek(int num) {
        return buffer & ((1L << num) - 1);
    }

    long peekFull() {
        return buffer;
    }

    void consume(int num) throws WebPDecodeException {
        if (nbits < num) {
            throw new WebPDecodeException("Corrupt bitstream");
        }
        buffer >>>= num;
        nbits -= num;
    }

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
