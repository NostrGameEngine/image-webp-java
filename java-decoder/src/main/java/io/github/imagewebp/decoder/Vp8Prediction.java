package io.github.imagewebp.decoder;

/** VP8 intra prediction helpers (ported from Rust src/vp8_prediction.rs). */
final class Vp8Prediction {
    static final int LUMA_STRIDE = 1 + 16 + 4;
    static final int LUMA_BLOCK_SIZE = LUMA_STRIDE * (1 + 16);

    static final int CHROMA_STRIDE = 8 + 1;
    static final int CHROMA_BLOCK_SIZE = CHROMA_STRIDE * (8 + 1);

    private Vp8Prediction() {}

    static byte[] createBorderLuma(int mbx, int mby, int mbw, byte[] top, byte[] left) {
        int stride = LUMA_STRIDE;
        byte[] ws = new byte[LUMA_BLOCK_SIZE];

        // above (A)
        int aboveOff = 1;
        if (mby == 0) {
            for (int i = 0; i < stride - 1; i++) ws[aboveOff + i] = 127;
        } else {
            // first 16
            int topOff = mbx * 16;
            for (int i = 0; i < 16; i++) ws[aboveOff + i] = top[topOff + i];

            if (mbx == mbw - 1) {
                byte last = top[topOff + 15];
                for (int i = 16; i < stride - 1; i++) ws[aboveOff + i] = last;
            } else {
                for (int i = 0; i < 4; i++) ws[aboveOff + 16 + i] = top[topOff + 16 + i];
            }
        }

        // replicate top-right into the block rows (matching Rust behavior)
        for (int i = 17; i < stride; i++) {
            ws[4 * stride + i] = ws[i];
            ws[8 * stride + i] = ws[i];
            ws[12 * stride + i] = ws[i];
        }

        // left (L)
        if (mbx == 0) {
            for (int i = 0; i < 16; i++) ws[(i + 1) * stride] = (byte) 129;
        } else {
            for (int i = 0; i < 16; i++) ws[(i + 1) * stride] = left[1 + i];
        }

        // top-left (P)
        ws[0] = (byte) (mby == 0 ? 127 : (mbx == 0 ? 129 : (left[0] & 0xFF)));
        return ws;
    }

    static byte[] createBorderChroma(int mbx, int mby, byte[] top, byte[] left) {
        int stride = CHROMA_STRIDE;
        byte[] chroma = new byte[CHROMA_BLOCK_SIZE];

        // above
        if (mby == 0) {
            for (int i = 0; i < 8; i++) chroma[1 + i] = 127;
        } else {
            int topOff = mbx * 8;
            for (int i = 0; i < 8; i++) chroma[1 + i] = top[topOff + i];
        }

        // left
        if (mbx == 0) {
            for (int y = 0; y < 8; y++) chroma[(y + 1) * stride] = (byte) 129;
        } else {
            for (int y = 0; y < 8; y++) chroma[(y + 1) * stride] = left[1 + y];
        }

        chroma[0] = (byte) (mby == 0 ? 127 : (mbx == 0 ? 129 : (left[0] & 0xFF)));
        return chroma;
    }

    static void addResidue(byte[] pblock, int[] rblock, int y0, int x0, int stride) {
        addResidue(pblock, rblock, 0, y0, x0, stride);
    }

    static void addResidue(byte[] pblock, int[] rblock, int rOff, int y0, int x0, int stride) {
        int pos = y0 * stride + x0;
        int k = rOff;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int v = rblock[k++] + (pblock[pos + x] & 0xFF);
                if (v < 0) v = 0;
                else if (v > 255) v = 255;
                pblock[pos + x] = (byte) v;
            }
            pos += stride;
        }
    }

    private static int avg3(int left, int cur, int right) {
        return (left + 2 * cur + right + 2) >> 2;
    }

    private static int avg2(int cur, int right) {
        return (cur + right + 1) >> 1;
    }

    static void predict4x4(byte[] ws, int stride, byte[] modes, int[] resdata) {
        for (int sby = 0; sby < 4; sby++) {
            for (int sbx = 0; sbx < 4; sbx++) {
                int i = sbx + sby * 4;
                int y0 = sby * 4 + 1;
                int x0 = sbx * 4 + 1;

                int mode = modes[i] & 0xFF;
                switch (mode) {
                    case Vp8Common.B_TM_PRED -> predictTmpred(ws, 4, x0, y0, stride);
                    case Vp8Common.B_VE_PRED -> predictBvepred(ws, x0, y0, stride);
                    case Vp8Common.B_HE_PRED -> predictBhepred(ws, x0, y0, stride);
                    case Vp8Common.B_DC_PRED -> predictBdcpred(ws, x0, y0, stride);
                    case Vp8Common.B_LD_PRED -> predictBldpred(ws, x0, y0, stride);
                    case Vp8Common.B_RD_PRED -> predictBrdpred(ws, x0, y0, stride);
                    case Vp8Common.B_VR_PRED -> predictBvrpred(ws, x0, y0, stride);
                    case Vp8Common.B_VL_PRED -> predictBvlpred(ws, x0, y0, stride);
                    case Vp8Common.B_HD_PRED -> predictBhdpred(ws, x0, y0, stride);
                    case Vp8Common.B_HU_PRED -> predictBhupred(ws, x0, y0, stride);
                    default -> throw new IllegalArgumentException("Invalid intra mode: " + mode);
                }

                addResidue(ws, resdata, i * 16, y0, x0, stride);
            }
        }
    }

    static void predictVpred(byte[] a, int size, int x0, int y0, int stride) {
        int abovePos = (y0 - 1) * stride + x0;
        for (int y = 0; y < size; y++) {
            int rowPos = (y0 + y) * stride + x0;
            System.arraycopy(a, abovePos, a, rowPos, size);
        }
    }

    static void predictHpred(byte[] a, int size, int x0, int y0, int stride) {
        for (int y = 0; y < size; y++) {
            int rowPos = (y0 + y) * stride + x0;
            int left = a[(y0 + y) * stride + x0 - 1] & 0xFF;
            for (int x = 0; x < size; x++) a[rowPos + x] = (byte) left;
        }
    }

    static void predictDcpred(byte[] a, int size, int stride, boolean above, boolean left) {
        int sum = 0;
        int shf = (size == 8) ? 2 : 3;

        if (left) {
            for (int y = 0; y < size; y++) sum += a[(y + 1) * stride] & 0xFF;
            shf += 1;
        }
        if (above) {
            for (int x = 0; x < size; x++) sum += a[1 + x] & 0xFF;
            shf += 1;
        }

        int dcval = (!left && !above) ? 128 : (sum + (1 << (shf - 1))) >> shf;
        for (int y = 0; y < size; y++) {
            int rowPos = (y + 1) * stride + 1;
            for (int x = 0; x < size; x++) a[rowPos + x] = (byte) dcval;
        }
    }

    static void predictTmpred(byte[] a, int size, int x0, int y0, int stride) {
        int p = a[(y0 - 1) * stride + x0 - 1] & 0xFF;
        int abovePos = (y0 - 1) * stride + x0;
        int leftPos = y0 * stride + x0 - 1;
        for (int y = 0; y < size; y++) {
            int leftMinusP = (a[leftPos + y * stride] & 0xFF) - p;
            int rowPos = (y0 + y) * stride + x0;
            for (int x = 0; x < size; x++) {
                int v = leftMinusP + (a[abovePos + x] & 0xFF);
                if (v < 0) v = 0;
                else if (v > 255) v = 255;
                a[rowPos + x] = (byte) v;
            }
        }
    }

    static void predictBdcpred(byte[] a, int x0, int y0, int stride) {
        int v = 4;
        int topPos = (y0 - 1) * stride + x0;
        for (int i = 0; i < 4; i++) v += a[topPos + i] & 0xFF;
        for (int i = 0; i < 4; i++) v += a[(y0 + i) * stride + x0 - 1] & 0xFF;
        v >>= 3;
        for (int y = 0; y < 4; y++) {
            int rowPos = (y0 + y) * stride + x0;
            for (int x = 0; x < 4; x++) a[rowPos + x] = (byte) v;
        }
    }

    private static int topleftPixel(byte[] a, int x0, int y0, int stride) {
        return a[(y0 - 1) * stride + x0 - 1] & 0xFF;
    }

    private static void topPixels(byte[] a, int x0, int y0, int stride, int[] out8) {
        int pos = (y0 - 1) * stride + x0;
        for (int i = 0; i < 8; i++) out8[i] = a[pos + i] & 0xFF;
    }

    private static void leftPixels(byte[] a, int x0, int y0, int stride, int[] out4) {
        out4[0] = a[y0 * stride + x0 - 1] & 0xFF;
        out4[1] = a[(y0 + 1) * stride + x0 - 1] & 0xFF;
        out4[2] = a[(y0 + 2) * stride + x0 - 1] & 0xFF;
        out4[3] = a[(y0 + 3) * stride + x0 - 1] & 0xFF;
    }

    private static void edgePixels(byte[] a, int x0, int y0, int stride, int[] out9) {
        int pos = (y0 - 1) * stride + x0 - 1;
        out9[0] = a[pos + 4 * stride] & 0xFF;
        out9[1] = a[pos + 3 * stride] & 0xFF;
        out9[2] = a[pos + 2 * stride] & 0xFF;
        out9[3] = a[pos + stride] & 0xFF;
        for (int i = 0; i < 5; i++) out9[4 + i] = a[pos + i] & 0xFF;
    }

    static void predictBvepred(byte[] a, int x0, int y0, int stride) {
        int p = topleftPixel(a, x0, y0, stride);
        int[] t = new int[8];
        topPixels(a, x0, y0, stride, t);
        int[] avg = new int[] {
                avg3(p, t[0], t[1]),
                avg3(t[0], t[1], t[2]),
                avg3(t[1], t[2], t[3]),
                avg3(t[2], t[3], t[4]),
        };
        int pos = y0 * stride + x0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) a[pos + x] = (byte) avg[x];
            pos += stride;
        }
    }

    static void predictBhepred(byte[] a, int x0, int y0, int stride) {
        int p = topleftPixel(a, x0, y0, stride);
        int[] l = new int[4];
        leftPixels(a, x0, y0, stride, l);
        int[] avgs = new int[] {
                avg3(p, l[0], l[1]),
                avg3(l[0], l[1], l[2]),
                avg3(l[1], l[2], l[3]),
                avg3(l[2], l[3], l[3]),
        };
        int pos = y0 * stride + x0;
        for (int y = 0; y < 4; y++) {
            int v = avgs[y];
            for (int x = 0; x < 4; x++) a[pos + x] = (byte) v;
            pos += stride;
        }
    }

    static void predictBldpred(byte[] a, int x0, int y0, int stride) {
        int[] t = new int[8];
        topPixels(a, x0, y0, stride, t);
        int[] avgs = new int[] {
                avg3(t[0], t[1], t[2]),
                avg3(t[1], t[2], t[3]),
                avg3(t[2], t[3], t[4]),
                avg3(t[3], t[4], t[5]),
                avg3(t[4], t[5], t[6]),
                avg3(t[5], t[6], t[7]),
                avg3(t[6], t[7], t[7]),
        };
        int pos = y0 * stride + x0;
        for (int i = 0; i < 4; i++) {
            for (int x = 0; x < 4; x++) a[pos + x] = (byte) avgs[i + x];
            pos += stride;
        }
    }

    static void predictBrdpred(byte[] a, int x0, int y0, int stride) {
        int[] e = new int[9];
        edgePixels(a, x0, y0, stride, e);
        int[] avgs = new int[] {
                avg3(e[0], e[1], e[2]),
                avg3(e[1], e[2], e[3]),
                avg3(e[2], e[3], e[4]),
                avg3(e[3], e[4], e[5]),
                avg3(e[4], e[5], e[6]),
                avg3(e[5], e[6], e[7]),
                avg3(e[6], e[7], e[8]),
        };
        int pos = y0 * stride + x0;
        for (int i = 0; i < 4; i++) {
            int start = 3 - i;
            for (int x = 0; x < 4; x++) a[pos + x] = (byte) avgs[start + x];
            pos += stride;
        }
    }

    static void predictBvrpred(byte[] a, int x0, int y0, int stride) {
        int[] e = new int[9];
        edgePixels(a, x0, y0, stride, e);
        int e1 = e[1], e2 = e[2], e3 = e[3], e4 = e[4], e5 = e[5], e6 = e[6], e7 = e[7], e8 = e[8];

        a[(y0 + 3) * stride + x0] = (byte) avg3(e1, e2, e3);
        a[(y0 + 2) * stride + x0] = (byte) avg3(e2, e3, e4);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e3, e4, e5);
        a[(y0 + 1) * stride + x0] = (byte) avg3(e3, e4, e5);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(e4, e5);
        a[y0 * stride + x0] = (byte) avg2(e4, e5);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(e4, e5, e6);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e4, e5, e6);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e5, e6);
        a[y0 * stride + x0 + 1] = (byte) avg2(e5, e6);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e5, e6, e7);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(e5, e6, e7);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg2(e6, e7);
        a[y0 * stride + x0 + 2] = (byte) avg2(e6, e7);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e6, e7, e8);
        a[y0 * stride + x0 + 3] = (byte) avg2(e7, e8);
    }

    static void predictBvlpred(byte[] a, int x0, int y0, int stride) {
        int[] t = new int[8];
        topPixels(a, x0, y0, stride, t);
        int a0=t[0],a1=t[1],a2=t[2],a3=t[3],a4=t[4],a5=t[5],a6=t[6],a7=t[7];

        a[y0 * stride + x0] = (byte) avg2(a0, a1);
        a[(y0 + 1) * stride + x0] = (byte) avg3(a0, a1, a2);
        a[(y0 + 2) * stride + x0] = (byte) avg2(a1, a2);
        a[y0 * stride + x0 + 1] = (byte) avg2(a1, a2);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(a1, a2, a3);
        a[(y0 + 3) * stride + x0] = (byte) avg3(a1, a2, a3);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(a2, a3);
        a[y0 * stride + x0 + 2] = (byte) avg2(a2, a3);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(a2, a3, a4);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(a2, a3, a4);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(a3, a4);
        a[y0 * stride + x0 + 3] = (byte) avg2(a3, a4);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(a3, a4, a5);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(a3, a4, a5);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(a4, a5, a6);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(a5, a6, a7);
    }

    static void predictBhdpred(byte[] a, int x0, int y0, int stride) {
        int[] e = new int[9];
        edgePixels(a, x0, y0, stride, e);
        int e0=e[0],e1=e[1],e2=e[2],e3=e[3],e4=e[4],e5=e[5],e6=e[6],e7=e[7];

        a[(y0 + 3) * stride + x0] = (byte) avg2(e0, e1);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e0, e1, e2);
        a[(y0 + 2) * stride + x0] = (byte) avg2(e1, e2);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg2(e1, e2);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(e1, e2, e3);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e1, e2, e3);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e2, e3);
        a[(y0 + 1) * stride + x0] = (byte) avg2(e2, e3);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(e2, e3, e4);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e2, e3, e4);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(e3, e4);
        a[y0 * stride + x0] = (byte) avg2(e3, e4);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e3, e4, e5);
        a[y0 * stride + x0 + 1] = (byte) avg3(e3, e4, e5);
        a[y0 * stride + x0 + 2] = (byte) avg3(e4, e5, e6);
        a[y0 * stride + x0 + 3] = (byte) avg3(e5, e6, e7);
    }

    static void predictBhupred(byte[] a, int x0, int y0, int stride) {
        int[] l = new int[4];
        leftPixels(a, x0, y0, stride, l);
        int l0=l[0],l1=l[1],l2=l[2],l3=l[3];

        a[y0 * stride + x0] = (byte) avg2(l0, l1);
        a[y0 * stride + x0 + 1] = (byte) avg3(l0, l1, l2);
        a[y0 * stride + x0 + 2] = (byte) avg2(l1, l2);
        a[(y0 + 1) * stride + x0] = (byte) avg2(l1, l2);
        a[y0 * stride + x0 + 3] = (byte) avg3(l1, l2, l3);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(l1, l2, l3);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(l2, l3);
        a[(y0 + 2) * stride + x0] = (byte) avg2(l2, l3);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(l2, l3, l3);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(l2, l3, l3);
        a[(y0 + 2) * stride + x0 + 2] = (byte) l3;
        a[(y0 + 2) * stride + x0 + 3] = (byte) l3;
        a[(y0 + 3) * stride + x0] = (byte) l3;
        a[(y0 + 3) * stride + x0 + 1] = (byte) l3;
        a[(y0 + 3) * stride + x0 + 2] = (byte) l3;
        a[(y0 + 3) * stride + x0 + 3] = (byte) l3;
    }
}
