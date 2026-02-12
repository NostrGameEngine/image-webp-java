package org.ngengine.webp.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Port of Rust unit tests in src/transform.rs. */
final class Vp8TransformTest {
    // Forward DCT is only used here to validate the inverse transform.
    private static void dct4x4(int[] block) {
        // vertical
        for (int i = 0; i < 4; i++) {
            int base = i * 4;
            long a = ((long) block[base] + (long) block[base + 3]) * 8L;
            long b = ((long) block[base + 1] + (long) block[base + 2]) * 8L;
            long c = ((long) block[base + 1] - (long) block[base + 2]) * 8L;
            long d = ((long) block[base] - (long) block[base + 3]) * 8L;

            block[base] = (int) (a + b);
            block[base + 2] = (int) (a - b);
            block[base + 1] = (int) ((c * 2217L + d * 5352L + 14500L) >> 12);
            block[base + 3] = (int) ((d * 2217L - c * 5352L + 7500L) >> 12);
        }

        // horizontal
        for (int i = 0; i < 4; i++) {
            long a = (long) block[i] + (long) block[i + 12];
            long b = (long) block[i + 4] + (long) block[i + 8];
            long c = (long) block[i + 4] - (long) block[i + 8];
            long d = (long) block[i] - (long) block[i + 12];

            block[i] = (int) ((a + b + 7) >> 4);
            block[i + 8] = (int) ((a - b + 7) >> 4);
            block[i + 4] = (int) (((c * 2217L + d * 5352L + 12000L) >> 16) + (d != 0 ? 1 : 0));
            block[i + 12] = (int) ((d * 2217L - c * 5352L + 51000L) >> 16);
        }
    }

    @Test
    void dctInverseRoundTrip() {
        int[] block = new int[] {38, 6, 210, 107, 42, 125, 185, 151, 241, 224, 125, 233, 227, 8, 57, 96};

        int[] dctBlock = block.clone();
        dct4x4(dctBlock);

        int[] inverse = dctBlock.clone();
        Vp8Transform.idct4x4(inverse);

        assertArrayEquals(block, inverse);
    }
}
