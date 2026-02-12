package org.ngengine.webp.decoder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Port of Rust unit tests in src/yuv.rs (no AWT; checks pure math + fancy upsampling). */
final class YuvTest {
    private static int mulhi(int v, int coeff) {
        return (v * coeff) >> 8;
    }

    private static int clip(int v) {
        int x = v >> 6; // YUV_FIX = 6
        if (x < 0) return 0;
        if (x > 255) return 255;
        return x;
    }

    private static int yuvToR(int y, int v) {
        return clip(mulhi(y, 19077) + mulhi(v, 26149) - 14234);
    }

    private static int yuvToG(int y, int u, int v) {
        return clip(mulhi(y, 19077) - mulhi(u, 6419) - mulhi(v, 13320) + 8708);
    }

    private static int yuvToB(int y, int u) {
        return clip(mulhi(y, 19077) + mulhi(u, 33050) - 17685);
    }

    @Test
    void yuvConversions() {
        int y = 203;
        int u = 40;
        int v = 42;
        assertEquals(80, yuvToR(y, v));
        assertEquals(255, yuvToG(y, u, v));
        assertEquals(40, yuvToB(y, u));
    }

    @Test
    void fancyGridMatchesExpectedUpsample() throws Exception {
        byte[] yBuffer = new byte[] {
                77, (byte) 162, (byte) 202, (byte) 185,
                28, 13, (byte) 199, (byte) 182,
                (byte) 135, (byte) 147, (byte) 164, (byte) 135,
                66, 27, (byte) 171, (byte) 130,
        };

        byte[] uBuffer = new byte[] {
                34, 101,
                123, (byte) 163,
        };

        byte[] vBuffer = new byte[] {
                97, (byte) 167,
                (byte) 149, 23,
        };

        byte[] outRgba = new byte[16 * 4];
        Yuv.fillRgbaBufferFancy(outRgba, yBuffer, uBuffer, vBuffer, 4, 4, 4);

        int[] upsampledU = new int[] {
                34, 51, 84, 101,
                56, 71, 101, 117,
                101, 112, 136, 148,
                123, 133, 153, 163,
        };

        int[] upsampledV = new int[] {
                97, 115, 150, 167,
                110, 115, 126, 131,
                136, 117, 78, 59,
                149, 118, 55, 23,
        };

        for (int i = 0; i < 16; i++) {
            int y = yBuffer[i] & 0xFF;
            int u = upsampledU[i];
            int v = upsampledV[i];

            assertEquals(yuvToR(y, v), outRgba[i * 4] & 0xFF, "r @" + i);
            assertEquals(yuvToG(y, u, v), outRgba[i * 4 + 1] & 0xFF, "g @" + i);
            assertEquals(yuvToB(y, u), outRgba[i * 4 + 2] & 0xFF, "b @" + i);
            assertEquals(0xFF, outRgba[i * 4 + 3] & 0xFF, "a @" + i);
        }
    }
}
