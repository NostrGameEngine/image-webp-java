package org.ngengine.webp.decoder;

/** VP8 loop filter (ported from Rust src/loop_filter.rs). */
final class Vp8LoopFilter {
    private Vp8LoopFilter() {}

    private static int c(int val) {
        if (val < -128) return -128;
        if (val > 127) return 127;
        return val;
    }

    private static int u2s(byte val) {
        return (val & 0xFF) - 128;
    }

    private static byte s2u(int val) {
        return (byte) (c(val) + 128);
    }

    private static int diff(byte[] p, int off, int i, int j) {
        int x = p[off + i] & 0xFF;
        int y = p[off + j] & 0xFF;
        return Math.abs(x - y);
    }

    private static int commonAdjustVertical(boolean useOuterTaps, byte[] pixels, int point, int stride) {
        int p1 = u2s(pixels[point - 2 * stride]);
        int p0 = u2s(pixels[point - stride]);
        int q0 = u2s(pixels[point]);
        int q1 = u2s(pixels[point + stride]);

        int outer = useOuterTaps ? c(p1 - q1) : 0;
        int a = c(outer + 3 * (q0 - p0));

        int b = c(a + 3) >> 3;
        a = c(a + 4) >> 3;

        pixels[point] = s2u(q0 - a);
        pixels[point - stride] = s2u(p0 + b);

        return a;
    }

    private static int commonAdjustHorizontal(boolean useOuterTaps, byte[] pixels, int off) {
        int p1 = u2s(pixels[off + 2]);
        int p0 = u2s(pixels[off + 3]);
        int q0 = u2s(pixels[off + 4]);
        int q1 = u2s(pixels[off + 5]);

        int outer = useOuterTaps ? c(p1 - q1) : 0;
        int a = c(outer + 3 * (q0 - p0));

        int b = c(a + 3) >> 3;
        a = c(a + 4) >> 3;

        pixels[off + 4] = s2u(q0 - a);
        pixels[off + 3] = s2u(p0 + b);
        return a;
    }

    private static boolean simpleThresholdVertical(int filterLimit, byte[] pixels, int point, int stride) {
        return Math.abs((pixels[point - stride] & 0xFF) - (pixels[point] & 0xFF)) * 2
                + Math.abs((pixels[point - 2 * stride] & 0xFF) - (pixels[point + stride] & 0xFF)) / 2
                <= filterLimit;
    }

    private static boolean simpleThresholdHorizontal(int filterLimit, byte[] pixels, int off) {
        return diff(pixels, off, 3, 4) * 2 + diff(pixels, off, 2, 5) / 2 <= filterLimit;
    }

    private static boolean shouldFilterVertical(int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        return simpleThresholdVertical(edgeLimit, pixels, point, stride)
                && Math.abs((pixels[point - 4 * stride] & 0xFF) - (pixels[point - 3 * stride] & 0xFF)) <= interiorLimit
                && Math.abs((pixels[point - 3 * stride] & 0xFF) - (pixels[point - 2 * stride] & 0xFF)) <= interiorLimit
                && Math.abs((pixels[point - 2 * stride] & 0xFF) - (pixels[point - stride] & 0xFF)) <= interiorLimit
                && Math.abs((pixels[point + 3 * stride] & 0xFF) - (pixels[point + 2 * stride] & 0xFF)) <= interiorLimit
                && Math.abs((pixels[point + 2 * stride] & 0xFF) - (pixels[point + stride] & 0xFF)) <= interiorLimit
                && Math.abs((pixels[point + stride] & 0xFF) - (pixels[point] & 0xFF)) <= interiorLimit;
    }

    private static boolean shouldFilterHorizontal(int interiorLimit, int edgeLimit, byte[] pixels, int off) {
        return simpleThresholdHorizontal(edgeLimit, pixels, off)
                && diff(pixels, off, 0, 1) <= interiorLimit
                && diff(pixels, off, 1, 2) <= interiorLimit
                && diff(pixels, off, 2, 3) <= interiorLimit
                && diff(pixels, off, 7, 6) <= interiorLimit
                && diff(pixels, off, 6, 5) <= interiorLimit
                && diff(pixels, off, 5, 4) <= interiorLimit;
    }

    private static boolean highEdgeVarianceVertical(int threshold, byte[] pixels, int point, int stride) {
        return Math.abs((pixels[point - 2 * stride] & 0xFF) - (pixels[point - stride] & 0xFF)) > threshold
                || Math.abs((pixels[point + stride] & 0xFF) - (pixels[point] & 0xFF)) > threshold;
    }

    private static boolean highEdgeVarianceHorizontal(int threshold, byte[] pixels, int off) {
        return diff(pixels, off, 2, 3) > threshold || diff(pixels, off, 5, 4) > threshold;
    }

    static void simpleSegmentVertical(int edgeLimit, byte[] pixels, int point, int stride) {
        if (simpleThresholdVertical(edgeLimit, pixels, point, stride)) {
            commonAdjustVertical(true, pixels, point, stride);
        }
    }

    static void simpleSegmentHorizontal(int edgeLimit, byte[] pixels, int off) {
        if (simpleThresholdHorizontal(edgeLimit, pixels, off)) {
            commonAdjustHorizontal(true, pixels, off);
        }
    }

    static void subblockFilterVertical(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        if (shouldFilterVertical(interiorLimit, edgeLimit, pixels, point, stride)) {
            boolean hv = highEdgeVarianceVertical(hevThreshold, pixels, point, stride);
            int a = (commonAdjustVertical(hv, pixels, point, stride) + 1) >> 1;
            if (!hv) {
                pixels[point + stride] = s2u(u2s(pixels[point + stride]) - a);
                pixels[point - 2 * stride] = s2u(u2s(pixels[point - 2 * stride]) + a);
            }
        }
    }

    static void subblockFilterHorizontal(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int off) {
        if (shouldFilterHorizontal(interiorLimit, edgeLimit, pixels, off)) {
            boolean hv = highEdgeVarianceHorizontal(hevThreshold, pixels, off);
            int a = (commonAdjustHorizontal(hv, pixels, off) + 1) >> 1;
            if (!hv) {
                pixels[off + 5] = s2u(u2s(pixels[off + 5]) - a);
                pixels[off + 2] = s2u(u2s(pixels[off + 2]) + a);
            }
        }
    }

    static void macroblockFilterVertical(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int point, int stride) {
        if (shouldFilterVertical(interiorLimit, edgeLimit, pixels, point, stride)) {
            if (!highEdgeVarianceVertical(hevThreshold, pixels, point, stride)) {
                int p2 = u2s(pixels[point - 3 * stride]);
                int p1 = u2s(pixels[point - 2 * stride]);
                int p0 = u2s(pixels[point - stride]);
                int q0 = u2s(pixels[point]);
                int q1 = u2s(pixels[point + stride]);
                int q2 = u2s(pixels[point + 2 * stride]);

                int w = c(c(p1 - q1) + 3 * (q0 - p0));

                int a = c((27 * w + 63) >> 7);
                pixels[point] = s2u(q0 - a);
                pixels[point - stride] = s2u(p0 + a);

                a = c((18 * w + 63) >> 7);
                pixels[point + stride] = s2u(q1 - a);
                pixels[point - 2 * stride] = s2u(p1 + a);

                a = c((9 * w + 63) >> 7);
                pixels[point + 2 * stride] = s2u(q2 - a);
                pixels[point - 3 * stride] = s2u(p2 + a);
            } else {
                commonAdjustVertical(true, pixels, point, stride);
            }
        }
    }

    static void macroblockFilterHorizontal(int hevThreshold, int interiorLimit, int edgeLimit, byte[] pixels, int off) {
        if (shouldFilterHorizontal(interiorLimit, edgeLimit, pixels, off)) {
            if (!highEdgeVarianceHorizontal(hevThreshold, pixels, off)) {
                int p2 = u2s(pixels[off + 1]);
                int p1 = u2s(pixels[off + 2]);
                int p0 = u2s(pixels[off + 3]);
                int q0 = u2s(pixels[off + 4]);
                int q1 = u2s(pixels[off + 5]);
                int q2 = u2s(pixels[off + 6]);

                int w = c(c(p1 - q1) + 3 * (q0 - p0));

                int a = c((27 * w + 63) >> 7);
                pixels[off + 4] = s2u(q0 - a);
                pixels[off + 3] = s2u(p0 + a);

                a = c((18 * w + 63) >> 7);
                pixels[off + 5] = s2u(q1 - a);
                pixels[off + 2] = s2u(p1 + a);

                a = c((9 * w + 63) >> 7);
                pixels[off + 6] = s2u(q2 - a);
                pixels[off + 1] = s2u(p2 + a);
            } else {
                commonAdjustHorizontal(true, pixels, off);
            }
        }
    }
}
