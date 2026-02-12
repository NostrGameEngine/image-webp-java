package io.github.imagewebp.decoder;

/** Lossless VP8L transforms (predictor/color/subtract-green/color-indexing). */
final class Vp8LTransforms {
    private Vp8LTransforms() {}

    static int subsampleSize(int size, int bits) {
        return (size + (1 << bits) - 1) >> bits;
    }

    static void applyPredictorTransform(
            byte[] imageData,
            int width,
            int height,
            int sizeBits,
            byte[] predictorData
    ) throws WebPDecodeException {
        int blockXsize = subsampleSize(width, sizeBits);

        // top-left pixel: A += 255
        if (imageData.length < 4) {
            return;
        }
        imageData[3] = (byte) (imageData[3] + (byte) 0xFF);

        // top row: use predictor 1 (left)
        applyPredictor1(imageData, 4, width * 4, width);

        // left column: add top pixel
        for (int y = 1; y < height; y++) {
            int row = y * width * 4;
            int prev = (y - 1) * width * 4;
            for (int c = 0; c < 4; c++) {
                imageData[row + c] = (byte) (imageData[row + c] + imageData[prev + c]);
            }
        }

        for (int y = 1; y < height; y++) {
            for (int blockX = 0; blockX < blockXsize; blockX++) {
                int blockIndex = (y >> sizeBits) * blockXsize + blockX;
                int predictor = predictorData[blockIndex * 4 + 1] & 0xFF;

                int startX = Math.max(blockX << sizeBits, 1);
                int endX = Math.min((blockX + 1) << sizeBits, width);
                int start = (y * width + startX) * 4;
                int end = (y * width + endX) * 4;

                switch (predictor) {
                    case 0:
                        applyPredictor0(imageData, start, end);
                        break;
                    case 1:
                        applyPredictor1(imageData, start, end, width);
                        break;
                    case 2:
                        applyPredictor2(imageData, start, end, width);
                        break;
                    case 3:
                        applyPredictor3(imageData, start, end, width);
                        break;
                    case 4:
                        applyPredictor4(imageData, start, end, width);
                        break;
                    case 5:
                        applyPredictor5(imageData, start, end, width);
                        break;
                    case 6:
                        applyPredictor6(imageData, start, end, width);
                        break;
                    case 7:
                        applyPredictor7(imageData, start, end, width);
                        break;
                    case 8:
                        applyPredictor8(imageData, start, end, width);
                        break;
                    case 9:
                        applyPredictor9(imageData, start, end, width);
                        break;
                    case 10:
                        applyPredictor10(imageData, start, end, width);
                        break;
                    case 11:
                        applyPredictor11(imageData, start, end, width);
                        break;
                    case 12:
                        applyPredictor12(imageData, start, end, width);
                        break;
                    case 13:
                        applyPredictor13(imageData, start, end, width);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private static void applyPredictor0(byte[] d, int start, int end) {
        for (int i = start + 3; i < end; i += 4) {
            d[i] = (byte) (d[i] + (byte) 0xFF);
        }
    }

    private static void applyPredictor1(byte[] d, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + d[i - 4]);
        }
    }

    private static void applyPredictor2(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + d[i - stride]);
        }
    }

    private static void applyPredictor3(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + d[i - stride + 4]);
        }
    }

    private static void applyPredictor4(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + d[i - stride - 4]);
        }
    }

    private static void applyPredictor5(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        byte pr = d[start - 4];
        byte pg = d[start - 3];
        byte pb = d[start - 2];
        byte pa = d[start - 1];

        for (int i = start; i < end; i += 4) {
            int trOff = i - stride + 4;
            int tOff = i - stride;

            pr = (byte) (d[i] + average2Auto(average2Auto(pr, d[trOff]), d[tOff]));
            pg = (byte) (d[i + 1] + average2Auto(average2Auto(pg, d[trOff + 1]), d[tOff + 1]));
            pb = (byte) (d[i + 2] + average2Auto(average2Auto(pb, d[trOff + 2]), d[tOff + 2]));
            pa = (byte) (d[i + 3] + average2Auto(average2Auto(pa, d[trOff + 3]), d[tOff + 3]));

            d[i] = pr;
            d[i + 1] = pg;
            d[i + 2] = pb;
            d[i + 3] = pa;
        }
    }

    private static void applyPredictor6(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + average2(d[i - 4], d[i - stride - 4]));
        }
    }

    private static void applyPredictor7(byte[] d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d[start - 4];
        byte pg = d[start - 3];
        byte pb = d[start - 2];
        byte pa = d[start - 1];

        for (int i = start; i < end; i += 4) {
            int tOff = i - stride;

            pr = (byte) (d[i] + average2Auto(pr, d[tOff]));
            pg = (byte) (d[i + 1] + average2Auto(pg, d[tOff + 1]));
            pb = (byte) (d[i + 2] + average2Auto(pb, d[tOff + 2]));
            pa = (byte) (d[i + 3] + average2Auto(pa, d[tOff + 3]));

            d[i] = pr;
            d[i + 1] = pg;
            d[i + 2] = pb;
            d[i + 3] = pa;
        }
    }

    private static void applyPredictor8(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + average2(d[i - stride - 4], d[i - stride]));
        }
    }

    private static void applyPredictor9(byte[] d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d[i] = (byte) (d[i] + average2(d[i - stride], d[i - stride + 4]));
        }
    }

    private static void applyPredictor10(byte[] d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d[start - 4];
        byte pg = d[start - 3];
        byte pb = d[start - 2];
        byte pa = d[start - 1];

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;
            int trOff = i - stride + 4;

            pr = (byte) (d[i] + average2(average2(pr, d[tlOff]), average2(d[tOff], d[trOff])));
            pg = (byte) (d[i + 1] + average2(average2(pg, d[tlOff + 1]), average2(d[tOff + 1], d[trOff + 1])));
            pb = (byte) (d[i + 2] + average2(average2(pb, d[tlOff + 2]), average2(d[tOff + 2], d[trOff + 2])));
            pa = (byte) (d[i + 3] + average2(average2(pa, d[tlOff + 3]), average2(d[tOff + 3], d[trOff + 3])));

            d[i] = pr;
            d[i + 1] = pg;
            d[i + 2] = pb;
            d[i + 3] = pa;
        }
    }

    private static void applyPredictor11(byte[] d, int start, int end, int width) {
        int stride = width * 4;

        int l0 = d[start - 4] & 0xFF;
        int l1 = d[start - 3] & 0xFF;
        int l2 = d[start - 2] & 0xFF;
        int l3 = d[start - 1] & 0xFF;

        int tl0 = d[start - stride - 4] & 0xFF;
        int tl1 = d[start - stride - 3] & 0xFF;
        int tl2 = d[start - stride - 2] & 0xFF;
        int tl3 = d[start - stride - 1] & 0xFF;

        for (int i = start; i < end; i += 4) {
            int t0 = d[i - stride] & 0xFF;
            int t1 = d[i - stride + 1] & 0xFF;
            int t2 = d[i - stride + 2] & 0xFF;
            int t3 = d[i - stride + 3] & 0xFF;

            int predictLeft = 0;
            int predictTop = 0;
            int p;

            p = l0 + t0 - tl0;
            predictLeft += Math.abs(p - l0);
            predictTop += Math.abs(p - t0);

            p = l1 + t1 - tl1;
            predictLeft += Math.abs(p - l1);
            predictTop += Math.abs(p - t1);

            p = l2 + t2 - tl2;
            predictLeft += Math.abs(p - l2);
            predictTop += Math.abs(p - t2);

            p = l3 + t3 - tl3;
            predictLeft += Math.abs(p - l3);
            predictTop += Math.abs(p - t3);

            if (predictLeft < predictTop) {
                d[i] = (byte) (d[i] + (byte) l0);
                d[i + 1] = (byte) (d[i + 1] + (byte) l1);
                d[i + 2] = (byte) (d[i + 2] + (byte) l2);
                d[i + 3] = (byte) (d[i + 3] + (byte) l3);
            } else {
                d[i] = (byte) (d[i] + (byte) t0);
                d[i + 1] = (byte) (d[i + 1] + (byte) t1);
                d[i + 2] = (byte) (d[i + 2] + (byte) t2);
                d[i + 3] = (byte) (d[i + 3] + (byte) t3);
            }

            tl0 = t0;
            tl1 = t1;
            tl2 = t2;
            tl3 = t3;

            l0 = d[i] & 0xFF;
            l1 = d[i + 1] & 0xFF;
            l2 = d[i + 2] & 0xFF;
            l3 = d[i + 3] & 0xFF;
        }
    }

    private static void applyPredictor12(byte[] d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d[start - 4];
        byte pg = d[start - 3];
        byte pb = d[start - 2];
        byte pa = d[start - 1];

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;

            pr = (byte) (d[i] + clampAddSubtractFull(pr, d[tOff], d[tlOff]));
            pg = (byte) (d[i + 1] + clampAddSubtractFull(pg, d[tOff + 1], d[tlOff + 1]));
            pb = (byte) (d[i + 2] + clampAddSubtractFull(pb, d[tOff + 2], d[tlOff + 2]));
            pa = (byte) (d[i + 3] + clampAddSubtractFull(pa, d[tOff + 3], d[tlOff + 3]));

            d[i] = pr;
            d[i + 1] = pg;
            d[i + 2] = pb;
            d[i + 3] = pa;
        }
    }

    private static void applyPredictor13(byte[] d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d[start - 4];
        byte pg = d[start - 3];
        byte pb = d[start - 2];
        byte pa = d[start - 1];

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;

            pr = (byte) (d[i] + clampAddSubtractHalf(((pr & 0xFF) + (d[tOff] & 0xFF)) / 2, d[tlOff]));
            pg = (byte) (d[i + 1] + clampAddSubtractHalf(((pg & 0xFF) + (d[tOff + 1] & 0xFF)) / 2, d[tlOff + 1]));
            pb = (byte) (d[i + 2] + clampAddSubtractHalf(((pb & 0xFF) + (d[tOff + 2] & 0xFF)) / 2, d[tlOff + 2]));
            pa = (byte) (d[i + 3] + clampAddSubtractHalf(((pa & 0xFF) + (d[tOff + 3] & 0xFF)) / 2, d[tlOff + 3]));

            d[i] = pr;
            d[i + 1] = pg;
            d[i + 2] = pb;
            d[i + 3] = pa;
        }
    }

    static void applyColorTransform(byte[] imageData, int width, int height, int sizeBits, byte[] transformData) {
        int blockXsize = subsampleSize(width, sizeBits);

        for (int y = 0; y < height; y++) {
            int rowOff = y * width * 4;
            int tfRowOff = (y >> sizeBits) * blockXsize * 4;

            for (int blockX = 0; blockX < blockXsize; blockX++) {
                int tfOff = tfRowOff + blockX * 4;
                byte redToBlue = transformData[tfOff];
                byte greenToBlue = transformData[tfOff + 1];
                byte greenToRed = transformData[tfOff + 2];

                int startX = blockX << sizeBits;
                int endX = Math.min((blockX + 1) << sizeBits, width);

                for (int x = startX; x < endX; x++) {
                    int p = rowOff + x * 4;
                    int green = imageData[p + 1] & 0xFF;
                    int tempRed = imageData[p] & 0xFF;
                    int tempBlue = imageData[p + 2] & 0xFF;

                    tempRed = (tempRed + colorTransformDelta(greenToRed, (byte) green)) & 0xFF;
                    tempBlue = (tempBlue + colorTransformDelta(greenToBlue, (byte) green)) & 0xFF;
                    tempBlue = (tempBlue + colorTransformDelta(redToBlue, (byte) tempRed)) & 0xFF;

                    imageData[p] = (byte) tempRed;
                    imageData[p + 2] = (byte) tempBlue;
                }
            }
        }
    }

    static void applySubtractGreenTransform(byte[] imageData, int imageSize) {
        for (int i = 0; i < imageSize; i += 4) {
            imageData[i] = (byte) (imageData[i] + imageData[i + 1]);
            imageData[i + 2] = (byte) (imageData[i + 2] + imageData[i + 1]);
        }
    }

    static void applyColorIndexingTransform(
            byte[] imageData,
            int width,
            int height,
            int tableSize,
            byte[] tableData
    ) {
        if (tableSize <= 0) {
            return;
        }

        if (tableSize > 16) {
            // index is in G channel
            for (int p = 0; p < width * height; p++) {
                int off = p * 4;
                int idx = imageData[off + 1] & 0xFF;
                if (idx < tableSize) {
                    int t = idx * 4;
                    imageData[off] = tableData[t];
                    imageData[off + 1] = tableData[t + 1];
                    imageData[off + 2] = tableData[t + 2];
                    imageData[off + 3] = tableData[t + 3];
                } else {
                    imageData[off] = 0;
                    imageData[off + 1] = 0;
                    imageData[off + 2] = 0;
                    imageData[off + 3] = 0;
                }
            }
            return;
        }

        int bits;
        if (tableSize <= 2) {
            bits = 3; // 1 bit per pixel, 8 pixels per packed byte
        } else if (tableSize <= 4) {
            bits = 2; // 2 bits per pixel, 4 pixels per packed byte
        } else {
            bits = 1; // 4 bits per pixel, 2 pixels per packed byte
        }

        int pixelsPerPackedByte = 1 << bits;
        int bitsPerEntry = 8 / pixelsPerPackedByte;
        int mask = (1 << bitsPerEntry) - 1;

        int packedWidth = (width + pixelsPerPackedByte - 1) / pixelsPerPackedByte;
        byte[] packedRow = new byte[packedWidth];

        for (int y = height - 1; y >= 0; y--) {
            int packedRowOff = y * packedWidth * 4;
            for (int bx = 0; bx < packedWidth; bx++) {
                packedRow[bx] = imageData[packedRowOff + bx * 4 + 1];
            }

            int outRowOff = y * width * 4;
            int outX = 0;
            for (int bx = 0; bx < packedWidth; bx++) {
                int packed = packedRow[bx] & 0xFF;
                for (int sub = 0; sub < pixelsPerPackedByte && outX < width; sub++) {
                    int idx = (packed >> (sub * bitsPerEntry)) & mask;
                    int outOff = outRowOff + outX * 4;
                    if (idx < tableSize) {
                        int t = idx * 4;
                        imageData[outOff] = tableData[t];
                        imageData[outOff + 1] = tableData[t + 1];
                        imageData[outOff + 2] = tableData[t + 2];
                        imageData[outOff + 3] = tableData[t + 3];
                    } else {
                        imageData[outOff] = 0;
                        imageData[outOff + 1] = 0;
                        imageData[outOff + 2] = 0;
                        imageData[outOff + 3] = 0;
                    }
                    outX++;
                }
            }
        }
    }

    private static byte average2(byte a, byte b) {
        int v = ((a & 0xFF) + (b & 0xFF)) >>> 1;
        return (byte) v;
    }

    private static byte average2Auto(byte a, byte b) {
        int aa = a & 0xFF;
        int bb = b & 0xFF;
        return (byte) ((aa & bb) + ((aa ^ bb) >>> 1));
    }

    private static byte clampAddSubtractFull(byte a, byte b, byte c) {
        int v = (a & 0xFF) + (b & 0xFF) - (c & 0xFF);
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        return (byte) v;
    }

    private static byte clampAddSubtractHalf(int a, byte b) {
        int v = a + (a - (b & 0xFF)) / 2;
        if (v < 0) v = 0;
        if (v > 255) v = 255;
        return (byte) v;
    }

    private static int colorTransformDelta(byte t, byte c) {
        int ti = (byte) t;
        int ci = (byte) c;
        return (ti * ci) >> 5;
    }
}
