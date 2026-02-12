package io.github.imagewebp.decoder;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** VP8 (lossy) keyframe decoder (ported from Rust src/vp8.rs; no AWT). */
final class Vp8Decoder {
    private Vp8Decoder() {}

    static void decodeToRgba(
            byte[] webp,
            int off,
            int len,
            int width,
            int height,
            ByteBuffer outRgba
    ) throws WebPDecodeException {
        Decoder d = new Decoder(webp, off, len);
        Frame f = d.decodeFrame();
        if (f.width != width || f.height != height) {
            throw new WebPDecodeException("VP8 decoded size mismatch");
        }
        Yuv.fillRgbaBufferFancy(outRgba, f.ybuf, f.ubuf, f.vbuf, width, height, f.bufferWidth);
    }

    private static final class Frame {
        int width;
        int height;
        int bufferWidth;
        byte[] ybuf;
        byte[] ubuf;
        byte[] vbuf;

        int version;
        boolean forDisplay;
        int pixelType;

        boolean filterType; // true=simple
        int filterLevel;
        int sharpnessLevel;
    }

    private static final class Segment {
        short ydc;
        short yac;
        short y2dc;
        short y2ac;
        short uvdc;
        short uvac;

        boolean deltaValues;
        byte quantizerLevel;
        byte loopfilterLevel;
    }

    private static final class MacroBlock {
        final byte[] bpred = new byte[16];
        int lumaMode;
        int chromaMode;
        int segmentId;
        boolean coeffsSkipped;
        boolean nonZeroDct;
    }

    private static final class PreviousMacroBlock {
        final byte[] bpred = new byte[4];
        // complexity layout: y2,y,y,y,y,u,u,v,v
        final byte[] complexity = new byte[9];
    }

    private static final class Decoder {
        private static final int PLANE_YCOEFF1 = 0;
        private static final int PLANE_Y2 = 1;
        private static final int PLANE_CHROMA = 2;
        private static final int PLANE_YCOEFF0 = 3;

        private final Vp8Reader r;
        private final Vp8ArithmeticDecoder b = new Vp8ArithmeticDecoder();

        private int mbWidth;
        private int mbHeight;
        private MacroBlock[] macroblocks;
        private final Frame frame = new Frame();

        private boolean segmentsEnabled;
        private boolean segmentsUpdateMap;
        private final Segment[] segment = new Segment[Vp8Common.MAX_SEGMENTS];

        private boolean loopFilterAdjustmentsEnabled;
        private final int[] refDelta = new int[4];
        private final int[] modeDelta = new int[4];

        private final Vp8ArithmeticDecoder[] partitions = {
                new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder(),
                new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder(), new Vp8ArithmeticDecoder()
        };
        private int numPartitions = 1;

        private final Vp8TreeNode[] segmentTreeNodes;
        private final Vp8TreeNode[][][][] tokenProbs;

        private int probSkipFalse = -1;

        private PreviousMacroBlock[] top;
        private final PreviousMacroBlock left = new PreviousMacroBlock();

        private byte[] topBorderY;
        private byte[] leftBorderY;
        private byte[] topBorderU;
        private byte[] leftBorderU;
        private byte[] topBorderV;
        private byte[] leftBorderV;

        Decoder(byte[] webp, int off, int len) throws WebPDecodeException {
            this.r = new Vp8Reader(webp, off, len);
            for (int i = 0; i < segment.length; i++) {
                segment[i] = new Segment();
            }
            this.segmentTreeNodes = new Vp8TreeNode[3];
            for (int i = 0; i < 3; i++) {
                Vp8TreeNode n = Vp8Common.SEGMENT_TREE_NODE_DEFAULTS[i];
                this.segmentTreeNodes[i] = new Vp8TreeNode(n.left, n.right, n.prob, n.index);
            }
            this.tokenProbs = Vp8Common.newDefaultTokenProbNodes();
        }

        Frame decodeFrame() throws WebPDecodeException {
            readFrameHeader();

            macroblocks = new MacroBlock[mbWidth * mbHeight];

            for (int mby = 0; mby < mbHeight; mby++) {
                int p = mby % numPartitions;
                Arrays.fill(left.bpred, (byte) 0);
                Arrays.fill(left.complexity, (byte) 0);

                for (int mbx = 0; mbx < mbWidth; mbx++) {
                    MacroBlock mb = readMacroblockHeader(mbx);
                    int[] blocks;
                    if (!mb.coeffsSkipped) {
                        blocks = readResidualData(mb, mbx, p);
                    } else {
                        if (mb.lumaMode != Vp8Common.B_PRED) {
                            left.complexity[0] = 0;
                            top[mbx].complexity[0] = 0;
                        }
                        for (int i = 1; i < 9; i++) {
                            left.complexity[i] = 0;
                            top[mbx].complexity[i] = 0;
                        }
                        blocks = new int[384];
                    }

                    intraPredictLuma(mbx, mby, mb, blocks);
                    intraPredictChroma(mbx, mby, mb, blocks);

                    macroblocks[mby * mbWidth + mbx] = mb;
                }

                leftBorderY = new byte[1 + 16];
                Arrays.fill(leftBorderY, (byte) 129);
                leftBorderU = new byte[1 + 8];
                Arrays.fill(leftBorderU, (byte) 129);
                leftBorderV = new byte[1 + 8];
                Arrays.fill(leftBorderV, (byte) 129);
            }

            for (int mby = 0; mby < mbHeight; mby++) {
                for (int mbx = 0; mbx < mbWidth; mbx++) {
                    loopFilter(mbx, mby, macroblocks[mby * mbWidth + mbx]);
                }
            }

            return frame;
        }

        private void readFrameHeader() throws WebPDecodeException {
            int tag = r.readU24LE();
            boolean keyframe = (tag & 1) == 0;
            if (!keyframe) {
                throw new WebPDecodeException("Non-keyframe VP8 not supported");
            }

            frame.version = (tag >> 1) & 7;
            frame.forDisplay = ((tag >> 4) & 1) != 0;
            int firstPartitionSize = tag >> 5;

            byte[] magic = r.readBytes(3);
            if ((magic[0] & 0xFF) != 0x9D || (magic[1] & 0xFF) != 0x01 || (magic[2] & 0xFF) != 0x2A) {
                throw new WebPDecodeException("Invalid VP8 magic");
            }

            frame.width = r.readU16LE() & 0x3FFF;
            frame.height = r.readU16LE() & 0x3FFF;

            mbWidth = (frame.width + 15) / 16;
            mbHeight = (frame.height + 15) / 16;

            top = new PreviousMacroBlock[mbWidth];
            for (int i = 0; i < top.length; i++) top[i] = new PreviousMacroBlock();

            frame.bufferWidth = mbWidth * 16;
            frame.ybuf = new byte[mbWidth * 16 * mbHeight * 16];
            frame.ubuf = new byte[mbWidth * 8 * mbHeight * 8];
            frame.vbuf = new byte[mbWidth * 8 * mbHeight * 8];

            topBorderY = new byte[frame.width + 4 + 16];
            Arrays.fill(topBorderY, (byte) 127);
            leftBorderY = new byte[1 + 16];
            Arrays.fill(leftBorderY, (byte) 129);

            topBorderU = new byte[8 * mbWidth];
            Arrays.fill(topBorderU, (byte) 127);
            leftBorderU = new byte[1 + 8];
            Arrays.fill(leftBorderU, (byte) 129);

            topBorderV = new byte[8 * mbWidth];
            Arrays.fill(topBorderV, (byte) 127);
            leftBorderV = new byte[1 + 8];
            Arrays.fill(leftBorderV, (byte) 129);

            byte[] part0 = r.readBytes(firstPartitionSize);
            b.init(part0, firstPartitionSize);

            int colorSpace = b.readLiteral(1);
            frame.pixelType = b.readLiteral(1);
            if (colorSpace != 0) {
                throw new WebPDecodeException("Invalid VP8 colorspace: " + colorSpace);
            }

            segmentsEnabled = b.readFlag();
            if (segmentsEnabled) {
                readSegmentUpdates();
            }

            frame.filterType = b.readFlag();
            frame.filterLevel = b.readLiteral(6);
            frame.sharpnessLevel = b.readLiteral(3);

            loopFilterAdjustmentsEnabled = b.readFlag();
            if (loopFilterAdjustmentsEnabled) {
                readLoopFilterAdjustments();
            }

            numPartitions = 1 << b.readLiteral(2);
            initPartitions(numPartitions);

            readQuantizationIndices();

            // refresh entropy probs (ignored)
            b.readLiteral(1);

            updateTokenProbabilities();

            int mbNoSkipCoeff = b.readLiteral(1);
            if (mbNoSkipCoeff == 1) {
                probSkipFalse = b.readLiteral(8);
            } else {
                probSkipFalse = -1;
            }
        }

        private void updateTokenProbabilities() throws WebPDecodeException {
            for (int i = 0; i < Vp8Common.COEFF_UPDATE_PROBS.length; i++) {
                for (int j = 0; j < Vp8Common.COEFF_UPDATE_PROBS[i].length; j++) {
                    for (int k = 0; k < Vp8Common.COEFF_UPDATE_PROBS[i][j].length; k++) {
                        for (int t = 0; t < Vp8Common.NUM_DCT_TOKENS - 1; t++) {
                            int prob = Vp8Common.COEFF_UPDATE_PROBS[i][j][k][t] & 0xFF;
                            if (b.readBool(prob)) {
                                tokenProbs[i][j][k][t].prob = b.readLiteral(8);
                            }
                        }
                    }
                }
            }
        }

        private void initPartitions(int n) throws WebPDecodeException {
            if (n > 1) {
                byte[] sizes = r.readBytes(3 * n - 3);
                for (int i = 0; i < n - 1; i++) {
                    int off = 3 * i;
                    int size = (sizes[off] & 0xFF) | ((sizes[off + 1] & 0xFF) << 8) | ((sizes[off + 2] & 0xFF) << 16);
                    byte[] part = r.readBytes(size);
                    partitions[i].init(part, size);
                }
            }
            byte[] last = r.readBytes(r.remaining());
            partitions[n - 1].init(last, last.length);
        }

        private static short dcQuant(int index) {
            int idx = index;
            if (idx < 0) idx = 0;
            if (idx > 127) idx = 127;
            return Vp8Common.DC_QUANT[idx];
        }

        private static short acQuant(int index) {
            int idx = index;
            if (idx < 0) idx = 0;
            if (idx > 127) idx = 127;
            return Vp8Common.AC_QUANT[idx];
        }

        private void readQuantizationIndices() throws WebPDecodeException {
            int yacAbs = b.readLiteral(7);
            int ydcDelta = b.readOptionalSignedValue(4);
            int y2dcDelta = b.readOptionalSignedValue(4);
            int y2acDelta = b.readOptionalSignedValue(4);
            int uvdcDelta = b.readOptionalSignedValue(4);
            int uvacDelta = b.readOptionalSignedValue(4);

            int n = segmentsEnabled ? Vp8Common.MAX_SEGMENTS : 1;
            for (int i = 0; i < n; i++) {
                int base;
                if (segmentsEnabled) {
                    if (segment[i].deltaValues) {
                        base = segment[i].quantizerLevel + yacAbs;
                    } else {
                        base = segment[i].quantizerLevel;
                    }
                } else {
                    base = yacAbs;
                }

                segment[i].ydc = dcQuant(base + ydcDelta);
                segment[i].yac = acQuant(base);

                segment[i].y2dc = (short) (dcQuant(base + y2dcDelta) * 2);
                segment[i].y2ac = (short) ((acQuant(base + y2acDelta) * 155) / 100);

                segment[i].uvdc = dcQuant(base + uvdcDelta);
                segment[i].uvac = acQuant(base + uvacDelta);

                if (segment[i].y2ac < 8) segment[i].y2ac = 8;
                if (segment[i].uvdc > 132) segment[i].uvdc = 132;
            }
        }

        private void readLoopFilterAdjustments() throws WebPDecodeException {
            if (b.readFlag()) {
                for (int i = 0; i < 4; i++) {
                    refDelta[i] = b.readOptionalSignedValue(6);
                }
                for (int i = 0; i < 4; i++) {
                    modeDelta[i] = b.readOptionalSignedValue(6);
                }
            }
        }

        private void readSegmentUpdates() throws WebPDecodeException {
            segmentsUpdateMap = b.readFlag();
            boolean updateSegmentFeatureData = b.readFlag();

            if (updateSegmentFeatureData) {
                boolean segmentFeatureMode = b.readFlag();
                for (int i = 0; i < Vp8Common.MAX_SEGMENTS; i++) {
                    segment[i].deltaValues = !segmentFeatureMode;
                }
                for (int i = 0; i < Vp8Common.MAX_SEGMENTS; i++) {
                    segment[i].quantizerLevel = (byte) b.readOptionalSignedValue(7);
                }
                for (int i = 0; i < Vp8Common.MAX_SEGMENTS; i++) {
                    segment[i].loopfilterLevel = (byte) b.readOptionalSignedValue(6);
                }
            }

            if (segmentsUpdateMap) {
                for (int i = 0; i < 3; i++) {
                    boolean update = b.readFlag();
                    int prob = update ? b.readLiteral(8) : 255;
                    segmentTreeNodes[i].prob = prob;
                }
            }
        }

        private static int intraModeFromLumaMode(int lumaMode) {
            switch (lumaMode) {
                case Vp8Common.DC_PRED:
                    return Vp8Common.B_DC_PRED;
                case Vp8Common.V_PRED:
                    return Vp8Common.B_VE_PRED;
                case Vp8Common.H_PRED:
                    return Vp8Common.B_HE_PRED;
                case Vp8Common.TM_PRED:
                    return Vp8Common.B_TM_PRED;
                default:
                    throw new IllegalArgumentException("invalid luma mode");
            }
        }

        private MacroBlock readMacroblockHeader(int mbx) throws WebPDecodeException {
            MacroBlock mb = new MacroBlock();

            if (segmentsEnabled && segmentsUpdateMap) {
                mb.segmentId = b.readWithTree(segmentTreeNodes);
            }

            mb.coeffsSkipped = probSkipFalse >= 0 && b.readBool(probSkipFalse);

            int luma = b.readWithTree(Vp8Common.KEYFRAME_YMODE_NODES);
            if (luma < 0 || luma > Vp8Common.B_PRED) {
                throw new WebPDecodeException("Invalid VP8 luma prediction mode: " + luma);
            }
            mb.lumaMode = luma;

            if (mb.lumaMode == Vp8Common.B_PRED) {
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int topMode = top[mbx].bpred[x] & 0xFF;
                        int leftMode = left.bpred[y] & 0xFF;
                        Vp8TreeNode[] tree = Vp8Common.KEYFRAME_BPRED_MODE_NODES[topMode][leftMode];
                        int intra = b.readWithTree(tree);
                        if (intra < 0 || intra > 9) {
                            throw new WebPDecodeException("Invalid VP8 intra prediction mode: " + intra);
                        }
                        mb.bpred[x + y * 4] = (byte) intra;
                        top[mbx].bpred[x] = (byte) intra;
                        left.bpred[y] = (byte) intra;
                    }
                }
            } else {
                int mode = intraModeFromLumaMode(mb.lumaMode);
                for (int i = 0; i < 4; i++) {
                    mb.bpred[12 + i] = (byte) mode;
                    left.bpred[i] = (byte) mode;
                }
            }

            int chroma = b.readWithTree(Vp8Common.KEYFRAME_UV_MODE_NODES);
            if (chroma < 0 || chroma > Vp8Common.TM_PRED) {
                throw new WebPDecodeException("Invalid VP8 chroma prediction mode: " + chroma);
            }
            mb.chromaMode = chroma;

            for (int i = 0; i < 4; i++) {
                top[mbx].bpred[i] = mb.bpred[12 + i];
            }

            return mb;
        }

        private boolean readCoefficients(int[] block, int p, int plane, int complexity, short dcq, short acq) throws WebPDecodeException {
            int firstCoeff = (plane == PLANE_YCOEFF1) ? 1 : 0;
            Vp8ArithmeticDecoder dec = partitions[p];
            boolean hasCoeffs = false;
            boolean skip = false;

            int cplx = complexity;

            for (int i = firstCoeff; i < 16; i++) {
                int band = Vp8Common.COEFF_BANDS[i];
                Vp8TreeNode[] tree = tokenProbs[plane][band][cplx];

                Vp8TreeNode firstNode = tree[skip ? 1 : 0];
                int token = dec.readWithTreeWithFirstNode(tree, firstNode);

                int absValue;
                if (token == Vp8Common.DCT_EOB) {
                    break;
                } else if (token == Vp8Common.DCT_0) {
                    skip = true;
                    hasCoeffs = true;
                    cplx = 0;
                    continue;
                } else if (token >= Vp8Common.DCT_1 && token <= Vp8Common.DCT_4) {
                    absValue = token;
                } else if (token >= Vp8Common.DCT_CAT1 && token <= Vp8Common.DCT_CAT6) {
                    short[] probs = Vp8Common.PROB_DCT_CAT[token - Vp8Common.DCT_CAT1];
                    int extra = 0;
                    for (short pr : probs) {
                        int t = pr & 0xFF;
                        if (t == 0) break;
                        boolean bit = dec.readBool(t);
                        extra = extra + extra + (bit ? 1 : 0);
                    }
                    absValue = Vp8Common.DCT_CAT_BASE[token - Vp8Common.DCT_CAT1] + extra;
                } else {
                    throw new WebPDecodeException("Unknown DCT token: " + token);
                }

                skip = false;

                cplx = (absValue == 0) ? 0 : (absValue == 1 ? 1 : 2);

                if (dec.readSign()) {
                    absValue = -absValue;
                }

                int zigzag = Vp8Common.ZIGZAG[i];
                int q = zigzag > 0 ? acq : dcq;
                block[zigzag] = absValue * q;
                hasCoeffs = true;
            }

            return hasCoeffs;
        }

        private int[] readResidualData(MacroBlock mb, int mbx, int p) throws WebPDecodeException {
            int sindex = mb.segmentId;
            int[] blocks = new int[384];

            int plane = (mb.lumaMode == Vp8Common.B_PRED) ? PLANE_YCOEFF0 : PLANE_Y2;

            int[] block16 = new int[16];

            if (plane == PLANE_Y2) {
                int complexity = (top[mbx].complexity[0] & 0xFF) + (left.complexity[0] & 0xFF);
                Arrays.fill(block16, 0);
                boolean n = readCoefficients(block16, p, plane, complexity, segment[sindex].y2dc, segment[sindex].y2ac);

                left.complexity[0] = (byte) (n ? 1 : 0);
                top[mbx].complexity[0] = (byte) (n ? 1 : 0);

                Vp8Transform.iwht4x4(block16);
                for (int k = 0; k < 16; k++) {
                    blocks[16 * k] = block16[k];
                }

                plane = PLANE_YCOEFF1;
            }

            for (int y = 0; y < 4; y++) {
                byte leftC = left.complexity[y + 1];
                for (int x = 0; x < 4; x++) {
                    int i = x + y * 4;
                    int off = i * 16;

                    Arrays.fill(block16, 0);
                    if (plane == PLANE_YCOEFF1) {
                        block16[0] = blocks[off];
                    }

                    int complexity = (top[mbx].complexity[x + 1] & 0xFF) + (leftC & 0xFF);
                    boolean n = readCoefficients(block16, p, plane, complexity, segment[sindex].ydc, segment[sindex].yac);

                    if (block16[0] != 0 || n) {
                        mb.nonZeroDct = true;
                        Vp8Transform.idct4x4(block16);
                    }

                    System.arraycopy(block16, 0, blocks, off, 16);

                    leftC = (byte) (n ? 1 : 0);
                    top[mbx].complexity[x + 1] = (byte) (n ? 1 : 0);
                }
                left.complexity[y + 1] = leftC;
            }

            plane = PLANE_CHROMA;
            for (int j : new int[] {5, 7}) {
                for (int y = 0; y < 2; y++) {
                    byte leftC = left.complexity[y + j];
                    for (int x = 0; x < 2; x++) {
                        int i = x + y * 2 + (j == 5 ? 16 : 20);
                        int off = i * 16;

                        Arrays.fill(block16, 0);
                        int complexity = (top[mbx].complexity[x + j] & 0xFF) + (leftC & 0xFF);
                        boolean n = readCoefficients(block16, p, plane, complexity, segment[sindex].uvdc, segment[sindex].uvac);

                        if (block16[0] != 0 || n) {
                            mb.nonZeroDct = true;
                            Vp8Transform.idct4x4(block16);
                        }

                        System.arraycopy(block16, 0, blocks, off, 16);

                        leftC = (byte) (n ? 1 : 0);
                        top[mbx].complexity[x + j] = (byte) (n ? 1 : 0);
                    }
                    left.complexity[y + j] = leftC;
                }
            }

            return blocks;
        }

        private void intraPredictLuma(int mbx, int mby, MacroBlock mb, int[] resdata) {
            int stride = Vp8Prediction.LUMA_STRIDE;
            byte[] ws = Vp8Prediction.createBorderLuma(mbx, mby, mbWidth, topBorderY, leftBorderY);

            switch (mb.lumaMode) {
                case Vp8Common.V_PRED:
                    Vp8Prediction.predictVpred(ws, 16, 1, 1, stride);
                    break;
                case Vp8Common.H_PRED:
                    Vp8Prediction.predictHpred(ws, 16, 1, 1, stride);
                    break;
                case Vp8Common.TM_PRED:
                    Vp8Prediction.predictTmpred(ws, 16, 1, 1, stride);
                    break;
                case Vp8Common.DC_PRED:
                    Vp8Prediction.predictDcpred(ws, 16, stride, mby != 0, mbx != 0);
                    break;
                case Vp8Common.B_PRED:
                    Vp8Prediction.predict4x4(ws, stride, mb.bpred, resdata);
                    break;
                default:
                    throw new IllegalStateException("Unexpected luma mode");
            }

            if (mb.lumaMode != Vp8Common.B_PRED) {
                for (int y = 0; y < 4; y++) {
                    for (int x = 0; x < 4; x++) {
                        int i = x + y * 4;
                        int y0 = 1 + y * 4;
                        int x0 = 1 + x * 4;
                        Vp8Prediction.addResidue(ws, resdata, i * 16, y0, x0, stride);
                    }
                }
            }

            leftBorderY[0] = ws[16];
            for (int i = 0; i < 16; i++) {
                leftBorderY[1 + i] = ws[(i + 1) * stride + 16];
            }

            System.arraycopy(ws, 16 * stride + 1, topBorderY, mbx * 16, 16);

            int lumaW = mbWidth * 16;
            for (int y = 0; y < 16; y++) {
                int dst = (mby * 16 + y) * lumaW + mbx * 16;
                int src = (1 + y) * stride + 1;
                System.arraycopy(ws, src, frame.ybuf, dst, 16);
            }
        }

        private void intraPredictChroma(int mbx, int mby, MacroBlock mb, int[] resdata) {
            int stride = Vp8Prediction.CHROMA_STRIDE;
            byte[] uws = Vp8Prediction.createBorderChroma(mbx, mby, topBorderU, leftBorderU);
            byte[] vws = Vp8Prediction.createBorderChroma(mbx, mby, topBorderV, leftBorderV);

            switch (mb.chromaMode) {
                case Vp8Common.DC_PRED:
                    Vp8Prediction.predictDcpred(uws, 8, stride, mby != 0, mbx != 0);
                    Vp8Prediction.predictDcpred(vws, 8, stride, mby != 0, mbx != 0);
                    break;
                case Vp8Common.V_PRED:
                    Vp8Prediction.predictVpred(uws, 8, 1, 1, stride);
                    Vp8Prediction.predictVpred(vws, 8, 1, 1, stride);
                    break;
                case Vp8Common.H_PRED:
                    Vp8Prediction.predictHpred(uws, 8, 1, 1, stride);
                    Vp8Prediction.predictHpred(vws, 8, 1, 1, stride);
                    break;
                case Vp8Common.TM_PRED:
                    Vp8Prediction.predictTmpred(uws, 8, 1, 1, stride);
                    Vp8Prediction.predictTmpred(vws, 8, 1, 1, stride);
                    break;
                default:
                    throw new IllegalStateException("Unexpected chroma mode");
            }

            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    int i = x + y * 2;
                    int y0 = 1 + y * 4;
                    int x0 = 1 + x * 4;

                    Vp8Prediction.addResidue(uws, resdata, 16 * 16 + i * 16, y0, x0, stride);
                    Vp8Prediction.addResidue(vws, resdata, 20 * 16 + i * 16, y0, x0, stride);
                }
            }

            setChromaBorder(leftBorderU, topBorderU, uws, mbx);
            setChromaBorder(leftBorderV, topBorderV, vws, mbx);

            int chromaW = mbWidth * 8;
            for (int y = 0; y < 8; y++) {
                int dst = (mby * 8 + y) * chromaW + mbx * 8;
                int src = (1 + y) * stride + 1;
                System.arraycopy(uws, src, frame.ubuf, dst, 8);
                System.arraycopy(vws, src, frame.vbuf, dst, 8);
            }
        }

        private static void setChromaBorder(byte[] leftBorder, byte[] topBorder, byte[] chromaBlock, int mbx) {
            int stride = Vp8Prediction.CHROMA_STRIDE;
            leftBorder[0] = chromaBlock[8];
            for (int i = 0; i < 8; i++) {
                leftBorder[1 + i] = chromaBlock[(i + 1) * stride + 8];
            }
            System.arraycopy(chromaBlock, 8 * stride + 1, topBorder, mbx * 8, 8);
        }

        private void loopFilter(int mbx, int mby, MacroBlock mb) {
            int lumaW = mbWidth * 16;
            int chromaW = mbWidth * 8;

            int[] params = calculateFilterParameters(mb);
            int filterLevel = params[0];
            int interiorLimit = params[1];
            int hevThreshold = params[2];

            if (filterLevel <= 0) return;

            int mbEdgeLimit = (filterLevel + 2) * 2 + interiorLimit;
            int subBEdgeLimit = (filterLevel * 2) + interiorLimit;

            boolean doSubblockFiltering = mb.lumaMode == Vp8Common.B_PRED || (!mb.coeffsSkipped && mb.nonZeroDct);

            if (mbx > 0) {
                if (frame.filterType) {
                    for (int y = 0; y < 16; y++) {
                        int y0 = mby * 16 + y;
                        int x0 = mbx * 16;
                        int off = y0 * lumaW + x0 - 4;
                        Vp8LoopFilter.simpleSegmentHorizontal(mbEdgeLimit, frame.ybuf, off);
                    }
                } else {
                    for (int y = 0; y < 16; y++) {
                        int y0 = mby * 16 + y;
                        int x0 = mbx * 16;
                        int off = y0 * lumaW + x0 - 4;
                        Vp8LoopFilter.macroblockFilterHorizontal(hevThreshold, interiorLimit, mbEdgeLimit, frame.ybuf, off);
                    }
                    for (int y = 0; y < 8; y++) {
                        int y0 = mby * 8 + y;
                        int x0 = mbx * 8;
                        int off = y0 * chromaW + x0 - 4;
                        Vp8LoopFilter.macroblockFilterHorizontal(hevThreshold, interiorLimit, mbEdgeLimit, frame.ubuf, off);
                        Vp8LoopFilter.macroblockFilterHorizontal(hevThreshold, interiorLimit, mbEdgeLimit, frame.vbuf, off);
                    }
                }
            }

            if (doSubblockFiltering) {
                if (frame.filterType) {
                    for (int x = 4; x < 15; x += 4) {
                        for (int y = 0; y < 16; y++) {
                            int y0 = mby * 16 + y;
                            int x0 = mbx * 16 + x;
                            int off = y0 * lumaW + x0 - 4;
                            Vp8LoopFilter.simpleSegmentHorizontal(subBEdgeLimit, frame.ybuf, off);
                        }
                    }
                } else {
                    for (int x = 4; x < 13; x += 4) {
                        for (int y = 0; y < 16; y++) {
                            int y0 = mby * 16 + y;
                            int x0 = mbx * 16 + x;
                            int off = y0 * lumaW + x0 - 4;
                            Vp8LoopFilter.subblockFilterHorizontal(hevThreshold, interiorLimit, subBEdgeLimit, frame.ybuf, off);
                        }
                    }
                    for (int y = 0; y < 8; y++) {
                        int y0 = mby * 8 + y;
                        int x0 = mbx * 8 + 4;
                        int off = y0 * chromaW + x0 - 4;
                        Vp8LoopFilter.subblockFilterHorizontal(hevThreshold, interiorLimit, subBEdgeLimit, frame.ubuf, off);
                        Vp8LoopFilter.subblockFilterHorizontal(hevThreshold, interiorLimit, subBEdgeLimit, frame.vbuf, off);
                    }
                }
            }

            if (mby > 0) {
                if (frame.filterType) {
                    for (int x = 0; x < 16; x++) {
                        int y0 = mby * 16;
                        int x0 = mbx * 16 + x;
                        Vp8LoopFilter.simpleSegmentVertical(mbEdgeLimit, frame.ybuf, y0 * lumaW + x0, lumaW);
                    }
                } else {
                    for (int x = 0; x < 16; x++) {
                        int y0 = mby * 16;
                        int x0 = mbx * 16 + x;
                        Vp8LoopFilter.macroblockFilterVertical(hevThreshold, interiorLimit, mbEdgeLimit, frame.ybuf, y0 * lumaW + x0, lumaW);
                    }
                    for (int x = 0; x < 8; x++) {
                        int y0 = mby * 8;
                        int x0 = mbx * 8 + x;
                        Vp8LoopFilter.macroblockFilterVertical(hevThreshold, interiorLimit, mbEdgeLimit, frame.ubuf, y0 * chromaW + x0, chromaW);
                        Vp8LoopFilter.macroblockFilterVertical(hevThreshold, interiorLimit, mbEdgeLimit, frame.vbuf, y0 * chromaW + x0, chromaW);
                    }
                }
            }

            if (doSubblockFiltering) {
                if (frame.filterType) {
                    for (int y = 4; y < 15; y += 4) {
                        for (int x = 0; x < 16; x++) {
                            int y0 = mby * 16 + y;
                            int x0 = mbx * 16 + x;
                            Vp8LoopFilter.simpleSegmentVertical(subBEdgeLimit, frame.ybuf, y0 * lumaW + x0, lumaW);
                        }
                    }
                } else {
                    for (int y = 4; y < 13; y += 4) {
                        for (int x = 0; x < 16; x++) {
                            int y0 = mby * 16 + y;
                            int x0 = mbx * 16 + x;
                            Vp8LoopFilter.subblockFilterVertical(hevThreshold, interiorLimit, subBEdgeLimit, frame.ybuf, y0 * lumaW + x0, lumaW);
                        }
                    }
                    for (int x = 0; x < 8; x++) {
                        int y0 = mby * 8 + 4;
                        int x0 = mbx * 8 + x;
                        Vp8LoopFilter.subblockFilterVertical(hevThreshold, interiorLimit, subBEdgeLimit, frame.ubuf, y0 * chromaW + x0, chromaW);
                        Vp8LoopFilter.subblockFilterVertical(hevThreshold, interiorLimit, subBEdgeLimit, frame.vbuf, y0 * chromaW + x0, chromaW);
                    }
                }
            }
        }

        private int[] calculateFilterParameters(MacroBlock mb) {
            Segment seg = segment[mb.segmentId];
            int filterLevel = frame.filterLevel;
            if (filterLevel == 0) return new int[] {0, 0, 0};

            if (segmentsEnabled) {
                if (seg.deltaValues) {
                    filterLevel += seg.loopfilterLevel;
                } else {
                    filterLevel = seg.loopfilterLevel;
                }
            }

            if (filterLevel < 0) filterLevel = 0;
            if (filterLevel > 63) filterLevel = 63;

            if (loopFilterAdjustmentsEnabled) {
                filterLevel += refDelta[0];
                if (mb.lumaMode == Vp8Common.B_PRED) {
                    filterLevel += modeDelta[0];
                }
            }

            if (filterLevel < 0) filterLevel = 0;
            if (filterLevel > 63) filterLevel = 63;

            int interiorLimit = filterLevel;
            if (frame.sharpnessLevel > 0) {
                interiorLimit >>= (frame.sharpnessLevel > 4) ? 2 : 1;
                int cap = 9 - frame.sharpnessLevel;
                if (interiorLimit > cap) interiorLimit = cap;
            }
            if (interiorLimit == 0) interiorLimit = 1;

            int hevThreshold = (filterLevel >= 40) ? 2 : (filterLevel >= 15 ? 1 : 0);
            return new int[] {filterLevel, interiorLimit, hevThreshold};
        }
    }
}
