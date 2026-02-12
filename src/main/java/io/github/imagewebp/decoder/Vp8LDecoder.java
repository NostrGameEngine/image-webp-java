package io.github.imagewebp.decoder;

/** VP8L (lossless) decoder. */
final class Vp8LDecoder {
    private Vp8LDecoder() {}

    private static final int CODE_LENGTH_CODES = 19;
    private static final int[] CODE_LENGTH_CODE_ORDER = {
            17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    // Distance map for backward references (spec-defined).
    private static final int[] DIST_X = {
            0, 1, 1, -1, 0, 2, 1, -1, 2, -2, 2, -2, 0, 3, 1, -1,
            3, -3, 2, -2, 3, -3, 0, 4, 1, -1, 4, -4, 3, -3, 2, -2,
            4, -4, 0, 3, -3, 4, -4, 5, 1, -1, 5, -5, 2, -2, 5, -5,
            4, -4, 3, -3, 5, -5, 0, 6, 1, -1, 6, -6, 2, -2, 6, -6,
            4, -4, 5, -5, 3, -3, 6, -6, 0, 7, 1, -1, 5, -5, 7, -7,
            4, -4, 6, -6, 2, -2, 7, -7, 3, -3, 7, -7, 5, -5, 6, -6,
            8, 4, -4, 7, -7, 8, 8, 6, -6, 8, 5, -5, 7, -7, 8, 6,
            -6, 7, -7, 8, 7, -7, 8, 8
    };

    private static final int[] DIST_Y = {
            1, 0, 1, 1, 2, 0, 2, 2, 1, 1, 2, 2, 3, 0, 3, 3,
            1, 1, 3, 3, 2, 2, 4, 0, 4, 4, 1, 1, 3, 3, 4, 4,
            2, 2, 5, 4, 4, 3, 3, 0, 5, 5, 1, 1, 5, 5, 2, 2,
            4, 4, 5, 5, 3, 3, 6, 0, 6, 6, 1, 1, 6, 6, 2, 2,
            5, 5, 4, 4, 6, 6, 3, 3, 7, 0, 7, 7, 5, 5, 1, 1,
            6, 6, 4, 4, 7, 7, 2, 2, 7, 7, 3, 3, 6, 6, 5, 5,
            0, 7, 7, 4, 4, 1, 2, 6, 6, 3, 7, 7, 5, 5, 4, 7,
            7, 6, 6, 5, 7, 7, 6, 7
    };

    private static final int GREEN = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int ALPHA = 3;
    private static final int DIST = 4;

    private static final int HUFFMAN_CODES_PER_META_CODE = 5;
    private static final int[] ALPHABET_SIZE = {256 + 24, 256, 256, 256, 40};

    /**
     * Decode VP8L bitstream into RGBA.
     *
     * @param implicitDimensions if true, the VP8L signature/header are not present (ALPH chunk payload)
     */
    static void decodeToRgba(
            byte[] webp,
            int off,
            int len,
            int width,
            int height,
            boolean implicitDimensions,
            byte[] outRgba
    ) throws WebPDecodeException {
        if (width <= 0 || height <= 0) {
            throw new WebPDecodeException("Invalid dimensions");
        }
        if (outRgba.length < width * height * 4) {
            throw new WebPDecodeException("Output buffer too small");
        }

        Vp8LBitReader br = new Vp8LBitReader(webp, off, len);

        if (!implicitDimensions) {
            int signature = br.readBits(8);
            if (signature != 0x2F) {
                throw new WebPDecodeException("Invalid VP8L signature");
            }

            int w = br.readBits(14) + 1;
            int h = br.readBits(14) + 1;
            if (w != width || h != height) {
                throw new WebPDecodeException("Inconsistent image sizes");
            }

            br.readBits(1); // alpha used flag (informational)
            int version = br.readBits(3);
            if (version != 0) {
                throw new WebPDecodeException("Unsupported VP8L version: " + version);
            }
        }

        Transform[] transforms = new Transform[4];
        int[] order = new int[4];
        int orderLen = 0;

        int transformedWidth = width;
        while (br.readBits(1) == 1) {
            int transformType = br.readBits(2);
            if (transformType < 0 || transformType >= transforms.length) {
                throw new WebPDecodeException("Transform error");
            }
            if (transforms[transformType] != null) {
                throw new WebPDecodeException("Transform error");
            }
            order[orderLen++] = transformType;

            switch (transformType) {
                case 0:
                {
                    int sizeBits = br.readBits(3) + 2;
                    int blockXsize = Vp8LTransforms.subsampleSize(transformedWidth, sizeBits);
                    int blockYsize = Vp8LTransforms.subsampleSize(height, sizeBits);
                    byte[] predictorData = new byte[blockXsize * blockYsize * 4];
                    decodeImageStream(br, blockXsize, blockYsize, false, predictorData);
                    transforms[transformType] = new PredictorTransform(sizeBits, predictorData);
                    break;
                }
                case 1:
                {
                    int sizeBits = br.readBits(3) + 2;
                    int blockXsize = Vp8LTransforms.subsampleSize(transformedWidth, sizeBits);
                    int blockYsize = Vp8LTransforms.subsampleSize(height, sizeBits);
                    byte[] transformData = new byte[blockXsize * blockYsize * 4];
                    decodeImageStream(br, blockXsize, blockYsize, false, transformData);
                    transforms[transformType] = new ColorTransform(sizeBits, transformData);
                    break;
                }
                case 2:
                    transforms[transformType] = new SubtractGreenTransform();
                    break;
                case 3:
                {
                    int tableSize = br.readBits(8) + 1;
                    byte[] colorMap = new byte[tableSize * 4];
                    decodeImageStream(br, tableSize, 1, false, colorMap);

                    int bits;
                    if (tableSize <= 2) {
                        bits = 3;
                    } else if (tableSize <= 4) {
                        bits = 2;
                    } else if (tableSize <= 16) {
                        bits = 1;
                    } else {
                        bits = 0;
                    }
                    transformedWidth = Vp8LTransforms.subsampleSize(transformedWidth, bits);
                    adjustColorMap(colorMap);
                    transforms[transformType] = new ColorIndexingTransform(tableSize, colorMap);
                    break;
                }
                default:
                    throw new WebPDecodeException("Transform error");
            }
        }

        int transformedSize = transformedWidth * height * 4;
        decodeImageStream(br, transformedWidth, height, true, outRgba, transformedSize);

        int imageSize = transformedSize;
        int curWidth = transformedWidth;
        for (int i = orderLen - 1; i >= 0; i--) {
            Transform t = transforms[order[i]];
            if (t instanceof PredictorTransform) {
                PredictorTransform pt = (PredictorTransform) t;
                Vp8LTransforms.applyPredictorTransform(outRgba, curWidth, height, pt.sizeBits, pt.data);
            } else if (t instanceof ColorTransform) {
                ColorTransform ct = (ColorTransform) t;
                Vp8LTransforms.applyColorTransform(outRgba, curWidth, height, ct.sizeBits, ct.data);
            } else if (t instanceof SubtractGreenTransform) {
                Vp8LTransforms.applySubtractGreenTransform(outRgba, imageSize);
            } else if (t instanceof ColorIndexingTransform) {
                ColorIndexingTransform cit = (ColorIndexingTransform) t;
                curWidth = width;
                imageSize = width * height * 4;
                Vp8LTransforms.applyColorIndexingTransform(outRgba, curWidth, height, cit.tableSize, cit.tableData);
            }
        }
    }

    private static void decodeImageStream(
            Vp8LBitReader br,
            int width,
            int height,
            boolean isArgbImg,
            byte[] data
    ) throws WebPDecodeException {
        decodeImageStream(br, width, height, isArgbImg, data, data.length);
    }

    private static void decodeImageStream(
            Vp8LBitReader br,
            int width,
            int height,
            boolean isArgbImg,
            byte[] data,
            int dataSize
    ) throws WebPDecodeException {
        Integer cacheBits = readColorCache(br);
        ColorCache cache = cacheBits != null ? new ColorCache(cacheBits) : null;

        HuffmanInfo info = readHuffmanCodes(br, isArgbImg, width, height, cache);
        decodeImageData(br, width, height, info, data, dataSize);
    }

    private static void adjustColorMap(byte[] colorMap) {
        for (int i = 4; i < colorMap.length; i++) {
            colorMap[i] = (byte) (colorMap[i] + colorMap[i - 4]);
        }
    }

    private static Integer readColorCache(Vp8LBitReader br) throws WebPDecodeException {
        if (br.readBits(1) == 1) {
            int bits = br.readBits(4);
            if (bits < 1 || bits > 11) {
                throw new WebPDecodeException("Invalid color cache bits: " + bits);
            }
            return bits;
        }
        return null;
    }

    private static HuffmanInfo readHuffmanCodes(
            Vp8LBitReader br,
            boolean readMeta,
            int xsize,
            int ysize,
            ColorCache cache
    ) throws WebPDecodeException {
        int numHuffGroups = 1;

        int huffmanBits = 0;
        int huffmanXsize = 1;
        int huffmanYsize = 1;
        int[] entropyImage = null;

        if (readMeta && br.readBits(1) == 1) {
            huffmanBits = br.readBits(3) + 2;
            huffmanXsize = Vp8LTransforms.subsampleSize(xsize, huffmanBits);
            huffmanYsize = Vp8LTransforms.subsampleSize(ysize, huffmanBits);

            byte[] tmp = new byte[huffmanXsize * huffmanYsize * 4];
            decodeImageStream(br, huffmanXsize, huffmanYsize, false, tmp);

            entropyImage = new int[huffmanXsize * huffmanYsize];
            for (int i = 0; i < entropyImage.length; i++) {
                int r = tmp[i * 4] & 0xFF;
                int g = tmp[i * 4 + 1] & 0xFF;
                int meta = (r << 8) | g;
                entropyImage[i] = meta;
                if (meta >= numHuffGroups) {
                    numHuffGroups = meta + 1;
                }
            }
        }

        Vp8LHuffmanTree[][] groups = new Vp8LHuffmanTree[numHuffGroups][HUFFMAN_CODES_PER_META_CODE];
        for (int i = 0; i < numHuffGroups; i++) {
            for (int j = 0; j < HUFFMAN_CODES_PER_META_CODE; j++) {
                int alphabetSize = ALPHABET_SIZE[j];
                if (j == 0 && cache != null) {
                    alphabetSize += 1 << cache.bits;
                }
                groups[i][j] = readHuffmanCode(br, alphabetSize);
            }
        }

        int mask = huffmanBits == 0 ? 0xFFFF : (1 << huffmanBits) - 1;
        return new HuffmanInfo(huffmanXsize, entropyImage, huffmanBits, mask, cache, groups);
    }

    private static Vp8LHuffmanTree readHuffmanCode(Vp8LBitReader br, int alphabetSize) throws WebPDecodeException {
        boolean simple = br.readBits(1) == 1;

        if (simple) {
            int numSymbols = br.readBits(1) + 1;
            int isFirst8bits = br.readBits(1);
            int zeroSymbol = br.readBits(1 + 7 * isFirst8bits);
            if (zeroSymbol >= alphabetSize) {
                throw new WebPDecodeException("Corrupt bitstream");
            }
            if (numSymbols == 1) {
                return Vp8LHuffmanTree.buildSingleNode(zeroSymbol);
            }

            int oneSymbol = br.readBits(8);
            if (oneSymbol >= alphabetSize) {
                throw new WebPDecodeException("Corrupt bitstream");
            }
            return Vp8LHuffmanTree.buildTwoNode(zeroSymbol, oneSymbol);
        }

        int[] codeLengthCodeLengths = new int[CODE_LENGTH_CODES];
        int numCodeLengths = 4 + br.readBits(4);
        for (int i = 0; i < numCodeLengths; i++) {
            codeLengthCodeLengths[CODE_LENGTH_CODE_ORDER[i]] = br.readBits(3);
        }

        int[] newCodeLengths = readHuffmanCodeLengths(br, codeLengthCodeLengths, alphabetSize);
        return Vp8LHuffmanTree.buildImplicit(newCodeLengths);
    }

    private static int[] readHuffmanCodeLengths(
            Vp8LBitReader br,
            int[] codeLengthCodeLengths,
            int numSymbols
    ) throws WebPDecodeException {
        Vp8LHuffmanTree table = Vp8LHuffmanTree.buildImplicit(codeLengthCodeLengths);

        int maxSymbol;
        if (br.readBits(1) == 1) {
            int lengthNbits = 2 + 2 * br.readBits(3);
            int maxMinusTwo = br.readBits(lengthNbits);
            if (maxMinusTwo > numSymbols - 2) {
                throw new WebPDecodeException("Corrupt bitstream");
            }
            maxSymbol = 2 + maxMinusTwo;
        } else {
            maxSymbol = numSymbols;
        }

        int[] codeLengths = new int[numSymbols];
        int prevCodeLen = 8;

        int symbol = 0;
        while (symbol < numSymbols) {
            if (maxSymbol == 0) {
                break;
            }
            maxSymbol--;

            br.fill();
            int codeLen = table.readSymbol(br);

            if (codeLen < 16) {
                codeLengths[symbol++] = codeLen;
                if (codeLen != 0) {
                    prevCodeLen = codeLen;
                }
            } else {
                boolean usePrev = codeLen == 16;
                int slot = codeLen - 16;
                int extraBits;
                int repeatOffset;
                if (slot == 0) {
                    extraBits = 2;
                    repeatOffset = 3;
                } else if (slot == 1) {
                    extraBits = 3;
                    repeatOffset = 3;
                } else if (slot == 2) {
                    extraBits = 7;
                    repeatOffset = 11;
                } else {
                    throw new WebPDecodeException("Corrupt bitstream");
                }

                int repeat = br.readBits(extraBits) + repeatOffset;
                if (symbol + repeat > numSymbols) {
                    throw new WebPDecodeException("Corrupt bitstream");
                }
                int len = usePrev ? prevCodeLen : 0;
                for (int i = 0; i < repeat; i++) {
                    codeLengths[symbol++] = len;
                }
            }
        }

        return codeLengths;
    }

    private static void decodeImageData(
            Vp8LBitReader br,
            int width,
            int height,
            HuffmanInfo info,
            byte[] data,
            int dataSize
    ) throws WebPDecodeException {
        int numValues = width * height;
        if (dataSize < numValues * 4) {
            throw new WebPDecodeException("Output buffer too small");
        }

        int huffIndex = info.getHuffIndex(0, 0);
        Vp8LHuffmanTree[] tree = info.groups[huffIndex];

        int index = 0;
        int nextBlockStart = 0;

        while (index < numValues) {
            br.fill();

            if (index >= nextBlockStart) {
                int x = index % width;
                int y = index / width;
                nextBlockStart = Math.min((x | info.mask), width - 1) + y * width + 1;

                huffIndex = info.getHuffIndex(x, y);
                tree = info.groups[huffIndex];

                if (tree[0].isSingleNode() && tree[1].isSingleNode() && tree[2].isSingleNode() && tree[3].isSingleNode()) {
                    int code = tree[GREEN].readSymbol(br);
                    if (code < 256) {
                        int n = (info.bits == 0) ? numValues : (nextBlockStart - index);

                        byte red = (byte) tree[RED].readSymbol(br);
                        byte green = (byte) code;
                        byte blue = (byte) tree[BLUE].readSymbol(br);
                        byte alpha = (byte) tree[ALPHA].readSymbol(br);

                        for (int i = 0; i < n; i++) {
                            int p = (index + i) * 4;
                            data[p] = red;
                            data[p + 1] = green;
                            data[p + 2] = blue;
                            data[p + 3] = alpha;
                        }

                        if (info.cache != null) {
                            info.cache.insert(red, green, blue, alpha);
                        }

                        index += n;
                        continue;
                    }
                }
            }

            int code = tree[GREEN].readSymbol(br);

            if (code < 256) {
                byte green = (byte) code;
                byte red = (byte) tree[RED].readSymbol(br);
                byte blue = (byte) tree[BLUE].readSymbol(br);
                if (br.nbits < 15) {
                    br.fill();
                }
                byte alpha = (byte) tree[ALPHA].readSymbol(br);

                int p = index * 4;
                data[p] = red;
                data[p + 1] = green;
                data[p + 2] = blue;
                data[p + 3] = alpha;

                if (info.cache != null) {
                    info.cache.insert(red, green, blue, alpha);
                }
                index++;
            } else if (code < 256 + 24) {
                int lengthSymbol = code - 256;
                int length = getCopyDistance(br, lengthSymbol);

                int distSymbol = tree[DIST].readSymbol(br);
                int distCode = getCopyDistance(br, distSymbol);
                int dist = planeCodeToDistance(width, distCode);

                if (index < dist || numValues - index < length) {
                    throw new WebPDecodeException("Corrupt bitstream");
                }

                for (int i = 0; i < length * 4; i++) {
                    data[index * 4 + i] = data[index * 4 + i - dist * 4];
                }

                if (info.cache != null) {
                    for (int i = 0; i < length; i++) {
                        int p = (index + i) * 4;
                        info.cache.insert(data[p], data[p + 1], data[p + 2], data[p + 3]);
                    }
                }

                index += length;
            } else {
                if (info.cache == null) {
                    throw new WebPDecodeException("Corrupt bitstream");
                }
                int ccIndex = code - 280;
                info.cache.lookupInto(ccIndex, data, index * 4);
                index++;

                if (index < nextBlockStart) {
                    int[] peek = tree[GREEN].peekSymbol(br);
                    if (peek != null) {
                        int bits = peek[0];
                        int sym = peek[1];
                        if (sym >= 280) {
                            br.consume(bits);
                            info.cache.lookupInto(sym - 280, data, index * 4);
                            index++;
                        }
                    }
                }
            }
        }
    }

    private static int getCopyDistance(Vp8LBitReader br, int prefixCode) throws WebPDecodeException {
        if (prefixCode < 4) {
            return prefixCode + 1;
        }
        int extraBits = (prefixCode - 2) >> 1;
        int offset = (2 + (prefixCode & 1)) << extraBits;

        if (br.nbits < extraBits) {
            br.fill();
        }
        int bits = (int) br.peek(extraBits);
        br.consume(extraBits);

        return offset + bits + 1;
    }

    private static int planeCodeToDistance(int xsize, int planeCode) {
        if (planeCode > 120) {
            return planeCode - 120;
        }
        int xoffset = DIST_X[planeCode - 1];
        int yoffset = DIST_Y[planeCode - 1];
        int dist = xoffset + yoffset * xsize;
        return dist < 1 ? 1 : dist;
    }

    private interface Transform {}

    private static final class PredictorTransform implements Transform {
        final int sizeBits;
        final byte[] data;

        PredictorTransform(int sizeBits, byte[] data) {
            this.sizeBits = sizeBits;
            this.data = data;
        }
    }

    private static final class ColorTransform implements Transform {
        final int sizeBits;
        final byte[] data;

        ColorTransform(int sizeBits, byte[] data) {
            this.sizeBits = sizeBits;
            this.data = data;
        }
    }

    private static final class SubtractGreenTransform implements Transform {}

    private static final class ColorIndexingTransform implements Transform {
        final int tableSize;
        final byte[] tableData;

        ColorIndexingTransform(int tableSize, byte[] tableData) {
            this.tableSize = tableSize;
            this.tableData = tableData;
        }
    }

    private static final class HuffmanInfo {
        final int xsize;
        final int[] image; // may be null
        final int bits;
        final int mask;
        final ColorCache cache;
        final Vp8LHuffmanTree[][] groups;

        HuffmanInfo(int xsize, int[] image, int bits, int mask, ColorCache cache, Vp8LHuffmanTree[][] groups) {
            this.xsize = xsize;
            this.image = image;
            this.bits = bits;
            this.mask = mask;
            this.cache = cache;
            this.groups = groups;
        }

        int getHuffIndex(int x, int y) {
            if (bits == 0) {
                return 0;
            }
            int position = (y >> bits) * xsize + (x >> bits);
            return image[position];
        }
    }

    private static final class ColorCache {
        final int bits;
        final byte[] table;

        ColorCache(int bits) {
            this.bits = bits;
            this.table = new byte[(1 << bits) * 4];
        }

        void insert(byte r, byte g, byte b, byte a) {
            int color = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF) | ((a & 0xFF) << 24);
            int index = (0x1e35a7bd * color) >>> (32 - bits);
            int off = index * 4;
            table[off] = r;
            table[off + 1] = g;
            table[off + 2] = b;
            table[off + 3] = a;
        }

        void lookupInto(int index, byte[] out, int outOff) {
            int off = index * 4;
            out[outOff] = table[off];
            out[outOff + 1] = table[off + 1];
            out[outOff + 2] = table[off + 2];
            out[outOff + 3] = table[off + 3];
        }
    }
}
