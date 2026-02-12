package io.github.imagewebp.decoder;

/** Inverse transforms used by VP8 decoding (ported from Rust src/transform.rs). */
final class Vp8Transform {
    private static final long CONST1 = 20091L;
    private static final long CONST2 = 35468L;

    private Vp8Transform() {}

    static void idct4x4(int[] block) {
        // block length must be >= 16
        for (int i = 0; i < 4; i++) {
            long a1 = (long) block[i] + (long) block[8 + i];
            long b1 = (long) block[i] - (long) block[8 + i];

            long t1 = ((long) block[4 + i] * CONST2) >> 16;
            long t2 = (long) block[12 + i] + (((long) block[12 + i] * CONST1) >> 16);
            long c1 = t1 - t2;

            t1 = (long) block[4 + i] + (((long) block[4 + i] * CONST1) >> 16);
            t2 = ((long) block[12 + i] * CONST2) >> 16;
            long d1 = t1 + t2;

            block[i] = (int) (a1 + d1);
            block[4 + i] = (int) (b1 + c1);
            block[12 + i] = (int) (a1 - d1);
            block[8 + i] = (int) (b1 - c1);
        }

        for (int i = 0; i < 4; i++) {
            int base = 4 * i;
            long a1 = (long) block[base] + (long) block[base + 2];
            long b1 = (long) block[base] - (long) block[base + 2];

            long t1 = ((long) block[base + 1] * CONST2) >> 16;
            long t2 = (long) block[base + 3] + (((long) block[base + 3] * CONST1) >> 16);
            long c1 = t1 - t2;

            t1 = (long) block[base + 1] + (((long) block[base + 1] * CONST1) >> 16);
            t2 = ((long) block[base + 3] * CONST2) >> 16;
            long d1 = t1 + t2;

            block[base] = (int) ((a1 + d1 + 4) >> 3);
            block[base + 3] = (int) ((a1 - d1 + 4) >> 3);
            block[base + 1] = (int) ((b1 + c1 + 4) >> 3);
            block[base + 2] = (int) ((b1 - c1 + 4) >> 3);
        }
    }

    static void iwht4x4(int[] block) {
        for (int i = 0; i < 4; i++) {
            int a1 = block[i] + block[12 + i];
            int b1 = block[4 + i] + block[8 + i];
            int c1 = block[4 + i] - block[8 + i];
            int d1 = block[i] - block[12 + i];

            block[i] = a1 + b1;
            block[4 + i] = c1 + d1;
            block[8 + i] = a1 - b1;
            block[12 + i] = d1 - c1;
        }

        for (int i = 0; i < 4; i++) {
            int base = 4 * i;
            int a1 = block[base] + block[base + 3];
            int b1 = block[base + 1] + block[base + 2];
            int c1 = block[base + 1] - block[base + 2];
            int d1 = block[base] - block[base + 3];

            int a2 = a1 + b1;
            int b2 = c1 + d1;
            int c2 = a1 - b1;
            int d2 = d1 - c1;

            block[base] = (a2 + 3) >> 3;
            block[base + 1] = (b2 + 3) >> 3;
            block[base + 2] = (c2 + 3) >> 3;
            block[base + 3] = (d2 + 3) >> 3;
        }
    }
}
