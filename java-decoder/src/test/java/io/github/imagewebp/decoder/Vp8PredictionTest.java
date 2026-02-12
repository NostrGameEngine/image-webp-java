package io.github.imagewebp.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Port of selected Rust unit tests in src/vp8_prediction.rs. */
final class Vp8PredictionTest {
    @Test
    void addResidueClipsLikeRust() {
        byte[] pblock = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        int[] rblock = new int[] {-1, -2, -3, -4, 250, 249, 248, 250, -10, -18, -192, -17, -3, 15, 18, 9};
        byte[] expected = new byte[] {0, 0, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255, 0, 0, 0, 0, 10, 29, 33, 25};

        Vp8Prediction.addResidue(pblock, rblock, 0, 0, 4);
        assertArrayEquals(expected, pblock);
    }

    @Test
    void predictBhepredMatchesRust() {
        byte[] im = new byte[] {
                5, 0, 0, 0, 0,
                4, 0, 0, 0, 0,
                3, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                1, 0, 0, 0, 0,
        };

        byte[] expected = new byte[] {
                5, 0, 0, 0, 0,
                4, 4, 4, 4, 4,
                3, 3, 3, 3, 3,
                2, 2, 2, 2, 2,
                1, 1, 1, 1, 1,
        };

        Vp8Prediction.predictBhepred(im, 1, 1, 5);
        assertArrayEquals(expected, im);
    }

    @Test
    void predictBrdpredMatchesRust() {
        byte[] im = new byte[] {
                5, 6, 7, 8, 9,
                4, 0, 0, 0, 0,
                3, 0, 0, 0, 0,
                2, 0, 0, 0, 0,
                1, 0, 0, 0, 0,
        };

        byte[] expected = new byte[] {
                5, 6, 7, 8, 9,
                4, 5, 6, 7, 8,
                3, 4, 5, 6, 7,
                2, 3, 4, 5, 6,
                1, 2, 3, 4, 5,
        };

        Vp8Prediction.predictBrdpred(im, 1, 1, 5);
        assertArrayEquals(expected, im);
    }

    @Test
    void predictBldpredMatchesRust() {
        byte[] im = new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
        };

        Vp8Prediction.predictBldpred(im, 0, 1, 8);

        assertEquals(2, im[8] & 0xFF);
        assertEquals(3, im[9] & 0xFF);
        assertEquals(4, im[10] & 0xFF);
        assertEquals(5, im[11] & 0xFF);
        assertEquals(3, im[16] & 0xFF);
        assertEquals(4, im[17] & 0xFF);
        assertEquals(5, im[18] & 0xFF);
        assertEquals(6, im[19] & 0xFF);
        assertEquals(4, im[24] & 0xFF);
        assertEquals(5, im[25] & 0xFF);
        assertEquals(6, im[26] & 0xFF);
        assertEquals(7, im[27] & 0xFF);
        assertEquals(5, im[32] & 0xFF);
        assertEquals(6, im[33] & 0xFF);
        assertEquals(7, im[34] & 0xFF);
        assertEquals(8, im[35] & 0xFF);
    }

    @Test
    void predictBvepredMatchesRust() {
        byte[] im = new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
        };

        Vp8Prediction.predictBvepred(im, 1, 1, 9);

        assertEquals(2, im[10] & 0xFF);
        assertEquals(3, im[11] & 0xFF);
        assertEquals(4, im[12] & 0xFF);
        assertEquals(5, im[13] & 0xFF);
        assertEquals(2, im[19] & 0xFF);
        assertEquals(3, im[20] & 0xFF);
        assertEquals(4, im[21] & 0xFF);
        assertEquals(5, im[22] & 0xFF);
        assertEquals(2, im[28] & 0xFF);
        assertEquals(3, im[29] & 0xFF);
        assertEquals(4, im[30] & 0xFF);
        assertEquals(5, im[31] & 0xFF);
        assertEquals(2, im[37] & 0xFF);
        assertEquals(3, im[38] & 0xFF);
        assertEquals(4, im[39] & 0xFF);
        assertEquals(5, im[40] & 0xFF);
    }
}
