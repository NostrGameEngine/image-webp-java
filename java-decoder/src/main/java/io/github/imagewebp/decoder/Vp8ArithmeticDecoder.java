package io.github.imagewebp.decoder;

/** VP8 boolean arithmetic decoder (ported from Rust src/vp8_arithmetic_decoder.rs; correctness-first). */
final class Vp8ArithmeticDecoder {
    private static final int FINAL_BYTES_REMAINING_EOF = -0xE;

    private int[] chunks; // big-endian u32 chunks
    private int chunkIndex;
    private long value;
    private int range;
    private int bitCount;

    private final byte[] finalBytes = new byte[3];
    private int finalBytesRemaining;

    Vp8ArithmeticDecoder() {
        this.chunks = new int[0];
        this.chunkIndex = 0;
        this.value = 0;
        this.range = 255;
        this.bitCount = -8;
        this.finalBytesRemaining = FINAL_BYTES_REMAINING_EOF;
    }

    void init(byte[] buf, int len) throws WebPDecodeException {
        if (len < 0 || len > buf.length) {
            throw new WebPDecodeException("Invalid partition length");
        }

        int numFullChunks = len / 4;
        int rem = len - 4 * numFullChunks;

        this.chunks = new int[numFullChunks];
        for (int i = 0; i < numFullChunks; i++) {
            int off = i * 4;
            this.chunks[i] = ((buf[off] & 0xFF) << 24)
                    | ((buf[off + 1] & 0xFF) << 16)
                    | ((buf[off + 2] & 0xFF) << 8)
                    | (buf[off + 3] & 0xFF);
        }

        for (int i = 0; i < 3; i++) {
            this.finalBytes[i] = 0;
        }
        if (rem != 0) {
            System.arraycopy(buf, numFullChunks * 4, this.finalBytes, 0, rem);
        }
        this.finalBytesRemaining = rem;

        this.chunkIndex = 0;
        this.value = 0;
        this.range = 255;
        this.bitCount = -8;
    }

    boolean isPastEof() {
        return finalBytesRemaining == FINAL_BYTES_REMAINING_EOF;
    }

    private void loadFromFinalBytes() {
        if (finalBytesRemaining > 0) {
            finalBytesRemaining -= 1;
            byte b = finalBytes[0];
            // rotate left
            finalBytes[0] = finalBytes[1];
            finalBytes[1] = finalBytes[2];
            finalBytes[2] = 0;

            value <<= 8;
            value |= (b & 0xFFL);
            bitCount += 8;
        } else if (finalBytesRemaining == 0) {
            // libwebp tolerance: allow reading one byte past end
            finalBytesRemaining -= 1;
            value <<= 8;
            bitCount += 8;
        } else {
            finalBytesRemaining = FINAL_BYTES_REMAINING_EOF;
        }
    }

    private boolean readBit(int probability) throws WebPDecodeException {
        if (bitCount < 0) {
            if (chunkIndex < chunks.length) {
                long v = chunks[chunkIndex++] & 0xFFFF_FFFFL;
                value <<= 32;
                value |= v;
                bitCount += 32;
            } else {
                loadFromFinalBytes();
                if (isPastEof()) {
                    throw new WebPDecodeException("VP8 bitstream ended early");
                }
            }
        }

        int split = 1 + (((range - 1) * probability) >> 8);
        long bigSplit = ((long) split) << bitCount;

        boolean retval;
        if (value >= bigSplit) {
            range -= split;
            value -= bigSplit;
            retval = true;
        } else {
            range = split;
            retval = false;
        }

        int shift = Integer.numberOfLeadingZeros(range) - 24;
        if (shift < 0) {
            shift = 0;
        }
        range <<= shift;
        bitCount -= shift;

        return retval;
    }

    boolean readBool(int probability) throws WebPDecodeException {
        return readBit(probability & 0xFF);
    }

    boolean readFlag() throws WebPDecodeException {
        return readBit(128);
    }

    int readLiteral(int n) throws WebPDecodeException {
        int v = 0;
        for (int i = 0; i < n; i++) {
            v = (v << 1) | (readFlag() ? 1 : 0);
        }
        return v;
    }

    int readOptionalSignedValue(int n) throws WebPDecodeException {
        boolean flag = readFlag();
        if (!flag) {
            return 0;
        }
        int magnitude = readLiteral(n);
        boolean sign = readFlag();
        return sign ? -magnitude : magnitude;
    }

    boolean readSign() throws WebPDecodeException {
        return readFlag();
    }

    int readWithTree(Vp8TreeNode[] tree) throws WebPDecodeException {
        Vp8TreeNode first = tree[0];
        return readWithTreeWithFirstNode(tree, first);
    }

    int readWithTreeWithFirstNode(Vp8TreeNode[] tree, Vp8TreeNode firstNode) throws WebPDecodeException {
        int index = firstNode.index;
        while (true) {
            Vp8TreeNode node = tree[index];
            boolean b = readBit(node.prob);
            int t = b ? node.right : node.left;
            if (t < tree.length) {
                index = t;
            } else {
                return Vp8TreeNode.valueFromBranch(t);
            }
        }
    }
}
