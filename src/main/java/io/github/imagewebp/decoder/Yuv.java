package io.github.imagewebp.decoder;

/** YUV->RGBA conversion used by the VP8 decoder (ported from Rust src/yuv.rs). */
final class Yuv {
    private static final int BPP = 4;

    private Yuv() {}

    static void fillRgbaBufferFancy(
            byte[] outRgba,
            byte[] yBuffer,
            byte[] uBuffer,
            byte[] vBuffer,
            int width,
            int height,
            int bufferWidth
    ) throws WebPDecodeException {
        if (width < 0 || height < 0 || bufferWidth < 0) {
            throw new WebPDecodeException("Invalid dimensions");
        }
        if (outRgba.length < width * height * BPP) {
            throw new WebPDecodeException("Output buffer too small");
        }

        int chromaBufferWidth = bufferWidth / 2;
        int chromaWidth = (width + 1) / 2;

        // top row
        fillRowFancyWith1UvRow(outRgba, 0, yBuffer, 0, uBuffer, 0, vBuffer, 0, width, chromaWidth);

        int outRow = 1;
        int yIndex = bufferWidth;
        int uRowIndex = 0;

        int mainPairs = (height - 1) / 2;
        for (int i = 0; i < mainPairs; i++) {
            int outOff1 = outRow * width * BPP;
            int outOff2 = outOff1 + width * BPP;

            int yOff1 = yIndex;
            int yOff2 = yOff1 + bufferWidth;

            int uOff1 = uRowIndex * chromaBufferWidth;
            int uOff2 = uOff1 + chromaBufferWidth;

            int vOff1 = uOff1;
            int vOff2 = uOff2;

            fillRowFancyWith2UvRows(outRgba, outOff1, yBuffer, yOff1, uBuffer, uOff1, uBuffer, uOff2,
                    vBuffer, vOff1, vBuffer, vOff2, width, chromaWidth);
            fillRowFancyWith2UvRows(outRgba, outOff2, yBuffer, yOff2, uBuffer, uOff2, uBuffer, uOff1,
                    vBuffer, vOff2, vBuffer, vOff1, width, chromaWidth);

            outRow += 2;
            yIndex += bufferWidth * 2;
            uRowIndex += 1;
        }

        if (outRow < height) {
            int chromaHeight = (height + 1) / 2;
            int lastUOff = (chromaHeight - 1) * chromaBufferWidth;
            int outOff = outRow * width * BPP;
            fillRowFancyWith1UvRow(outRgba, outOff, yBuffer, yIndex, uBuffer, lastUOff, vBuffer, lastUOff,
                    width, chromaWidth);
        }
    }

    private static void fillRowFancyWith2UvRows(
            byte[] outRgba,
            int outOff,
            byte[] yRow,
            int yOff,
            byte[] uRow1,
            int u1Off,
            byte[] uRow2,
            int u2Off,
            byte[] vRow1,
            int v1Off,
            byte[] vRow2,
            int v2Off,
            int width,
            int chromaWidth
    ) {
        // left pixel
        {
            int y = yRow[yOff] & 0xFF;
            int u = getFancyChromaValue(uRow1[u1Off] & 0xFF, uRow1[u1Off] & 0xFF, uRow2[u2Off] & 0xFF, uRow2[u2Off] & 0xFF);
            int v = getFancyChromaValue(vRow1[v1Off] & 0xFF, vRow1[v1Off] & 0xFF, vRow2[v2Off] & 0xFF, vRow2[v2Off] & 0xFF);
            setPixel(outRgba, outOff, y, u, v);
        }

        int out = outOff + BPP;
        int yIdx = yOff + 1;
        int win = 0;

        int pairs = (width - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            int y0 = yRow[yIdx++] & 0xFF;
            int y1 = yRow[yIdx++] & 0xFF;

            int u10 = uRow1[u1Off + win] & 0xFF;
            int u11 = uRow1[u1Off + win + 1] & 0xFF;
            int u20 = uRow2[u2Off + win] & 0xFF;
            int u21 = uRow2[u2Off + win + 1] & 0xFF;

            int v10 = vRow1[v1Off + win] & 0xFF;
            int v11 = vRow1[v1Off + win + 1] & 0xFF;
            int v20 = vRow2[v2Off + win] & 0xFF;
            int v21 = vRow2[v2Off + win + 1] & 0xFF;

            int uA = getFancyChromaValue(u10, u11, u20, u21);
            int vA = getFancyChromaValue(v10, v11, v20, v21);
            setPixel(outRgba, out, y0, uA, vA);

            int uB = getFancyChromaValue(u11, u10, u21, u20);
            int vB = getFancyChromaValue(v11, v10, v21, v20);
            setPixel(outRgba, out + BPP, y1, uB, vB);

            out += 2 * BPP;
            win += 1;
        }

        if (((width - 1) & 1) != 0) {
            int y = yRow[yIdx] & 0xFF;
            int lastU1 = uRow1[u1Off + chromaWidth - 1] & 0xFF;
            int lastU2 = uRow2[u2Off + chromaWidth - 1] & 0xFF;
            int lastV1 = vRow1[v1Off + chromaWidth - 1] & 0xFF;
            int lastV2 = vRow2[v2Off + chromaWidth - 1] & 0xFF;
            int u = getFancyChromaValue(lastU1, lastU1, lastU2, lastU2);
            int v = getFancyChromaValue(lastV1, lastV1, lastV2, lastV2);
            setPixel(outRgba, out, y, u, v);
        }
    }

    private static void fillRowFancyWith1UvRow(
            byte[] outRgba,
            int outOff,
            byte[] yRow,
            int yOff,
            byte[] uRow,
            int uOff,
            byte[] vRow,
            int vOff,
            int width,
            int chromaWidth
    ) {
        // left pixel
        {
            int y = yRow[yOff] & 0xFF;
            int u = uRow[uOff] & 0xFF;
            int v = vRow[vOff] & 0xFF;
            setPixel(outRgba, outOff, y, u, v);
        }

        int out = outOff + BPP;
        int yIdx = yOff + 1;
        int win = 0;

        int pairs = (width - 1) / 2;
        for (int i = 0; i < pairs; i++) {
            int y0 = yRow[yIdx++] & 0xFF;
            int y1 = yRow[yIdx++] & 0xFF;

            int u0 = uRow[uOff + win] & 0xFF;
            int u1 = uRow[uOff + win + 1] & 0xFF;
            int v0 = vRow[vOff + win] & 0xFF;
            int v1 = vRow[vOff + win + 1] & 0xFF;

            int uA = getFancyChromaValue(u0, u1, u0, u1);
            int vA = getFancyChromaValue(v0, v1, v0, v1);
            setPixel(outRgba, out, y0, uA, vA);

            int uB = getFancyChromaValue(u1, u0, u1, u0);
            int vB = getFancyChromaValue(v1, v0, v1, v0);
            setPixel(outRgba, out + BPP, y1, uB, vB);

            out += 2 * BPP;
            win += 1;
        }

        if (((width - 1) & 1) != 0) {
            int y = yRow[yIdx] & 0xFF;
            int u = uRow[uOff + chromaWidth - 1] & 0xFF;
            int v = vRow[vOff + chromaWidth - 1] & 0xFF;
            setPixel(outRgba, out, y, u, v);
        }
    }

    private static int getFancyChromaValue(int main, int secondary1, int secondary2, int tertiary) {
        return (9 * main + 3 * secondary1 + 3 * secondary2 + tertiary + 8) / 16;
    }

    private static void setPixel(byte[] out, int off, int y, int u, int v) {
        out[off] = (byte) yuvToR(y, v);
        out[off + 1] = (byte) yuvToG(y, u, v);
        out[off + 2] = (byte) yuvToB(y, u);
        out[off + 3] = (byte) 0xFF;
    }

    private static int mulhi(int v, int coeff) {
        return (v * coeff) >> 8;
    }

    private static int clip(int v) {
        // YUV_FIX2 = 6
        int x = v >> 6;
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
}
