package io.github.imagewebp.decoder;

import java.nio.ByteBuffer;

/** Lossless VP8L transforms (predictor/color/subtract-green/color-indexing). */
final class Vp8LTransforms {
    private Vp8LTransforms() {}

    static int subsampleSize(int size, int bits) {
        return (size + (1 << bits) - 1) >> bits;
    }

    static void applyPredictorTransform(
            ByteBuffer imageData,
            int width,
            int height,
            int sizeBits,
            ByteBuffer predictorData
    ) throws WebPDecodeException {
        int blockXsize = subsampleSize(width, sizeBits);

        // top-left pixel: A += 255
        if (imageData.remaining() < 4) {
            return;
        }
        imageData.put(3, (byte) (imageData.get(3) + (byte) 0xFF));

        // top row: use predictor 1 (left)
        applyPredictor1(imageData, 4, width * 4, width);

        // left column: add top pixel
        for (int y = 1; y < height; y++) {
            int row = y * width * 4;
            int prev = (y - 1) * width * 4;
            for (int c = 0; c < 4; c++) {
                int idx = row + c;
                imageData.put(idx, (byte) (imageData.get(idx) + imageData.get(prev + c)));
            }
        }

        for (int y = 1; y < height; y++) {
            for (int blockX = 0; blockX < blockXsize; blockX++) {
                int blockIndex = (y >> sizeBits) * blockXsize + blockX;
                int predictor = predictorData.get(blockIndex * 4 + 1) & 0xFF;

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

    private static void applyPredictor0(ByteBuffer d, int start, int end) {
        for (int i = start + 3; i < end; i += 4) {
            d.put(i, (byte) (d.get(i) + (byte) 0xFF));
        }
    }

    private static void applyPredictor1(ByteBuffer d, int start, int end, int width) {
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + d.get(i - 4)));
        }
    }

    private static void applyPredictor2(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + d.get(i - stride)));
        }
    }

    private static void applyPredictor3(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + d.get(i - stride + 4)));
        }
    }

    private static void applyPredictor4(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + d.get(i - stride - 4)));
        }
    }

    private static void applyPredictor5(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        byte pr = d.get(start - 4);
        byte pg = d.get(start - 3);
        byte pb = d.get(start - 2);
        byte pa = d.get(start - 1);

        for (int i = start; i < end; i += 4) {
            int trOff = i - stride + 4;
            int tOff = i - stride;

            pr = (byte) (d.get(i) + average2Auto(average2Auto(pr, d.get(trOff)), d.get(tOff)));
            pg = (byte) (d.get(i + 1) + average2Auto(average2Auto(pg, d.get(trOff + 1)), d.get(tOff + 1)));
            pb = (byte) (d.get(i + 2) + average2Auto(average2Auto(pb, d.get(trOff + 2)), d.get(tOff + 2)));
            pa = (byte) (d.get(i + 3) + average2Auto(average2Auto(pa, d.get(trOff + 3)), d.get(tOff + 3)));

            d.put(i, pr);
            d.put(i + 1, pg);
            d.put(i + 2, pb);
            d.put(i + 3, pa);
        }
    }

    private static void applyPredictor6(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + average2(d.get(i - 4), d.get(i - stride - 4))));
        }
    }

    private static void applyPredictor7(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d.get(start - 4);
        byte pg = d.get(start - 3);
        byte pb = d.get(start - 2);
        byte pa = d.get(start - 1);

        for (int i = start; i < end; i += 4) {
            int tOff = i - stride;

            pr = (byte) (d.get(i) + average2Auto(pr, d.get(tOff)));
            pg = (byte) (d.get(i + 1) + average2Auto(pg, d.get(tOff + 1)));
            pb = (byte) (d.get(i + 2) + average2Auto(pb, d.get(tOff + 2)));
            pa = (byte) (d.get(i + 3) + average2Auto(pa, d.get(tOff + 3)));

            d.put(i, pr);
            d.put(i + 1, pg);
            d.put(i + 2, pb);
            d.put(i + 3, pa);
        }
    }

    private static void applyPredictor8(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + average2(d.get(i - stride - 4), d.get(i - stride))));
        }
    }

    private static void applyPredictor9(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;
        for (int i = start; i < end; i++) {
            d.put(i, (byte) (d.get(i) + average2(d.get(i - stride), d.get(i - stride + 4))));
        }
    }

    private static void applyPredictor10(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d.get(start - 4);
        byte pg = d.get(start - 3);
        byte pb = d.get(start - 2);
        byte pa = d.get(start - 1);

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;
            int trOff = i - stride + 4;

            pr = (byte) (d.get(i) + average2(average2(pr, d.get(tlOff)), average2(d.get(tOff), d.get(trOff))));
            pg = (byte) (d.get(i + 1) + average2(average2(pg, d.get(tlOff + 1)), average2(d.get(tOff + 1), d.get(trOff + 1))));
            pb = (byte) (d.get(i + 2) + average2(average2(pb, d.get(tlOff + 2)), average2(d.get(tOff + 2), d.get(trOff + 2))));
            pa = (byte) (d.get(i + 3) + average2(average2(pa, d.get(tlOff + 3)), average2(d.get(tOff + 3), d.get(trOff + 3))));

            d.put(i, pr);
            d.put(i + 1, pg);
            d.put(i + 2, pb);
            d.put(i + 3, pa);
        }
    }

    private static void applyPredictor11(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;

        int l0 = d.get(start - 4) & 0xFF;
        int l1 = d.get(start - 3) & 0xFF;
        int l2 = d.get(start - 2) & 0xFF;
        int l3 = d.get(start - 1) & 0xFF;

        int tl0 = d.get(start - stride - 4) & 0xFF;
        int tl1 = d.get(start - stride - 3) & 0xFF;
        int tl2 = d.get(start - stride - 2) & 0xFF;
        int tl3 = d.get(start - stride - 1) & 0xFF;

        for (int i = start; i < end; i += 4) {
            int t0 = d.get(i - stride) & 0xFF;
            int t1 = d.get(i - stride + 1) & 0xFF;
            int t2 = d.get(i - stride + 2) & 0xFF;
            int t3 = d.get(i - stride + 3) & 0xFF;

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
                d.put(i, (byte) (d.get(i) + (byte) l0));
                d.put(i + 1, (byte) (d.get(i + 1) + (byte) l1));
                d.put(i + 2, (byte) (d.get(i + 2) + (byte) l2));
                d.put(i + 3, (byte) (d.get(i + 3) + (byte) l3));
            } else {
                d.put(i, (byte) (d.get(i) + (byte) t0));
                d.put(i + 1, (byte) (d.get(i + 1) + (byte) t1));
                d.put(i + 2, (byte) (d.get(i + 2) + (byte) t2));
                d.put(i + 3, (byte) (d.get(i + 3) + (byte) t3));
            }

            tl0 = t0;
            tl1 = t1;
            tl2 = t2;
            tl3 = t3;

            l0 = d.get(i) & 0xFF;
            l1 = d.get(i + 1) & 0xFF;
            l2 = d.get(i + 2) & 0xFF;
            l3 = d.get(i + 3) & 0xFF;
        }
    }

    private static void applyPredictor12(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d.get(start - 4);
        byte pg = d.get(start - 3);
        byte pb = d.get(start - 2);
        byte pa = d.get(start - 1);

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;

            pr = (byte) (d.get(i) + clampAddSubtractFull(pr, d.get(tOff), d.get(tlOff)));
            pg = (byte) (d.get(i + 1) + clampAddSubtractFull(pg, d.get(tOff + 1), d.get(tlOff + 1)));
            pb = (byte) (d.get(i + 2) + clampAddSubtractFull(pb, d.get(tOff + 2), d.get(tlOff + 2)));
            pa = (byte) (d.get(i + 3) + clampAddSubtractFull(pa, d.get(tOff + 3), d.get(tlOff + 3)));

            d.put(i, pr);
            d.put(i + 1, pg);
            d.put(i + 2, pb);
            d.put(i + 3, pa);
        }
    }

    private static void applyPredictor13(ByteBuffer d, int start, int end, int width) {
        int stride = width * 4;

        byte pr = d.get(start - 4);
        byte pg = d.get(start - 3);
        byte pb = d.get(start - 2);
        byte pa = d.get(start - 1);

        for (int i = start; i < end; i += 4) {
            int tlOff = i - stride - 4;
            int tOff = i - stride;

            pr = (byte) (d.get(i) + clampAddSubtractHalf(((pr & 0xFF) + (d.get(tOff) & 0xFF)) / 2, d.get(tlOff)));
            pg = (byte) (d.get(i + 1) + clampAddSubtractHalf(((pg & 0xFF) + (d.get(tOff + 1) & 0xFF)) / 2, d.get(tlOff + 1)));
            pb = (byte) (d.get(i + 2) + clampAddSubtractHalf(((pb & 0xFF) + (d.get(tOff + 2) & 0xFF)) / 2, d.get(tlOff + 2)));
            pa = (byte) (d.get(i + 3) + clampAddSubtractHalf(((pa & 0xFF) + (d.get(tOff + 3) & 0xFF)) / 2, d.get(tlOff + 3)));

            d.put(i, pr);
            d.put(i + 1, pg);
            d.put(i + 2, pb);
            d.put(i + 3, pa);
        }
    }

    static void applyColorTransform(ByteBuffer imageData, int width, int height, int sizeBits, ByteBuffer transformData) {
        int blockXsize = subsampleSize(width, sizeBits);

        for (int y = 0; y < height; y++) {
            int rowOff = y * width * 4;
            int tfRowOff = (y >> sizeBits) * blockXsize * 4;

            for (int blockX = 0; blockX < blockXsize; blockX++) {
                int tfOff = tfRowOff + blockX * 4;
                byte redToBlue = transformData.get(tfOff);
                byte greenToBlue = transformData.get(tfOff + 1);
                byte greenToRed = transformData.get(tfOff + 2);

                int startX = blockX << sizeBits;
                int endX = Math.min((blockX + 1) << sizeBits, width);

                for (int x = startX; x < endX; x++) {
                    int p = rowOff + x * 4;
                    int green = imageData.get(p + 1) & 0xFF;
                    int tempRed = imageData.get(p) & 0xFF;
                    int tempBlue = imageData.get(p + 2) & 0xFF;

                    tempRed = (tempRed + colorTransformDelta(greenToRed, (byte) green)) & 0xFF;
                    tempBlue = (tempBlue + colorTransformDelta(greenToBlue, (byte) green)) & 0xFF;
                    tempBlue = (tempBlue + colorTransformDelta(redToBlue, (byte) tempRed)) & 0xFF;

                    imageData.put(p, (byte) tempRed);
                    imageData.put(p + 2, (byte) tempBlue);
                }
            }
        }
    }

    static void applySubtractGreenTransform(ByteBuffer imageData, int imageSize) {
        for (int i = 0; i < imageSize; i += 4) {
            byte g = imageData.get(i + 1);
            imageData.put(i, (byte) (imageData.get(i) + g));
            imageData.put(i + 2, (byte) (imageData.get(i + 2) + g));
        }
    }

    static void applyColorIndexingTransform(
            ByteBuffer imageData,
            int width,
            int height,
            int tableSize,
            ByteBuffer tableData
    ) {
        if (tableSize <= 0) {
            return;
        }

        if (tableSize > 16) {
            // index is in G channel
            for (int p = 0; p < width * height; p++) {
                int off = p * 4;
                int idx = imageData.get(off + 1) & 0xFF;
                if (idx < tableSize) {
                    int t = idx * 4;
                    imageData.put(off, tableData.get(t));
                    imageData.put(off + 1, tableData.get(t + 1));
                    imageData.put(off + 2, tableData.get(t + 2));
                    imageData.put(off + 3, tableData.get(t + 3));
                } else {
                    imageData.put(off, (byte) 0);
                    imageData.put(off + 1, (byte) 0);
                    imageData.put(off + 2, (byte) 0);
                    imageData.put(off + 3, (byte) 0);
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
                packedRow[bx] = imageData.get(packedRowOff + bx * 4 + 1);
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
                        imageData.put(outOff, tableData.get(t));
                        imageData.put(outOff + 1, tableData.get(t + 1));
                        imageData.put(outOff + 2, tableData.get(t + 2));
                        imageData.put(outOff + 3, tableData.get(t + 3));
                    } else {
                        imageData.put(outOff, (byte) 0);
                        imageData.put(outOff + 1, (byte) 0);
                        imageData.put(outOff + 2, (byte) 0);
                        imageData.put(outOff + 3, (byte) 0);
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
