package org.ngengine.webp.decoder;

import java.util.Arrays;

/** Huffman tree used in VP8L lossless decoding. */
final class Vp8LHuffmanTree {
    private static final int MAX_ALLOWED_CODE_LENGTH = 15;
    private static final int MAX_TABLE_BITS = 10;

    private final boolean single;
    private final int singleSymbol;

    private final int tableMask;
    private final int[] primaryTable;
    private final int[] secondaryTable;

    private Vp8LHuffmanTree(int symbol) {
        this.single = true;
        this.singleSymbol = symbol;
        this.tableMask = 0;
        this.primaryTable = null;
        this.secondaryTable = null;
    }

    private Vp8LHuffmanTree(int tableMask, int[] primaryTable, int[] secondaryTable) {
        this.single = false;
        this.singleSymbol = 0;
        this.tableMask = tableMask;
        this.primaryTable = primaryTable;
        this.secondaryTable = secondaryTable;
    }

    /** Creates a degenerate tree that always returns {@code symbol}. */
    static Vp8LHuffmanTree buildSingleNode(int symbol) {
        return new Vp8LHuffmanTree(symbol);
    }

    /** Creates a two-symbol tree with one-bit codewords for zero/one branches. */
    static Vp8LHuffmanTree buildTwoNode(int zero, int one) {
        return new Vp8LHuffmanTree(0x1, new int[] { (1 << 12) | zero, (1 << 12) | one }, new int[0]);
    }

    /** Returns whether this instance is a degenerate single-symbol Huffman tree. */
    boolean isSingleNode() {
        return single;
    }

    /** Builds canonical Huffman decode tables from implicit code lengths. */
    static Vp8LHuffmanTree buildImplicit(int[] codeLengths) throws WebPDecodeException {
        int[] histogram = new int[MAX_ALLOWED_CODE_LENGTH + 1];
        int numSymbols = 0;
        for (int len : codeLengths) {
            if (len < 0 || len > MAX_ALLOWED_CODE_LENGTH) {
                throw new WebPDecodeException("Invalid Huffman code length");
            }
            histogram[len]++;
            if (len != 0) numSymbols++;
        }

        if (numSymbols == 0) {
            throw new WebPDecodeException("Invalid Huffman code");
        } else if (numSymbols == 1) {
            int sym = -1;
            for (int i = 0; i < codeLengths.length; i++) {
                if (codeLengths[i] != 0) {
                    sym = i;
                    break;
                }
            }
            return buildSingleNode(sym);
        }

        int maxLength = MAX_ALLOWED_CODE_LENGTH;
        while (maxLength > 1 && histogram[maxLength] == 0) {
            maxLength--;
        }

        int[] offsets = new int[16];
        int codespaceUsed = 0;
        offsets[1] = histogram[0];
        for (int i = 1; i < maxLength; i++) {
            offsets[i + 1] = offsets[i] + histogram[i];
            codespaceUsed = (codespaceUsed << 1) + histogram[i];
        }
        codespaceUsed = (codespaceUsed << 1) + histogram[maxLength];
        if (codespaceUsed != (1 << maxLength)) {
            throw new WebPDecodeException("Invalid Huffman code");
        }

        int tableBits = Math.min(maxLength, MAX_TABLE_BITS);
        int tableSize = 1 << tableBits;
        int tableMask = tableSize - 1;
        int[] primary = new int[tableSize];

        int[] nextIndex = Arrays.copyOf(offsets, offsets.length);
        int[] sortedSymbols = new int[codeLengths.length];
        for (int symbol = 0; symbol < codeLengths.length; symbol++) {
            int len = codeLengths[symbol];
            sortedSymbols[nextIndex[len]] = symbol;
            nextIndex[len]++;
        }

        int codeword = 0;
        int idx = histogram[0];
        int primaryTableBits = Integer.numberOfTrailingZeros(primary.length);
        int primaryTableMask = (1 << primaryTableBits) - 1;

        for (int length = 1; length <= primaryTableBits; length++) {
            int currentTableEnd = 1 << length;
            for (int j = 0; j < histogram[length]; j++) {
                int symbol = sortedSymbols[idx++];
                int entry = (length << 12) | symbol;
                primary[codeword] = entry;
                codeword = nextCodeword(codeword, currentTableEnd);
            }
            if (length < primaryTableBits) {
                System.arraycopy(primary, 0, primary, currentTableEnd, currentTableEnd);
            }
        }

        int[] secondary = new int[0];
        if (maxLength > primaryTableBits) {
            int subtableStart = 0;
            int subtablePrefix = ~0;
            for (int length = primaryTableBits + 1; length <= maxLength; length++) {
                int subtableSize = 1 << (length - primaryTableBits);
                for (int j = 0; j < histogram[length]; j++) {
                    if ( (codeword & primaryTableMask) != subtablePrefix) {
                        subtablePrefix = codeword & primaryTableMask;
                        subtableStart = secondary.length;
                        primary[subtablePrefix] = (length << 12) | subtableStart;
                        secondary = Arrays.copyOf(secondary, subtableStart + subtableSize);
                    }

                    int symbol = sortedSymbols[idx++];
                    secondary[subtableStart + (codeword >> primaryTableBits)] = (symbol << 4) | length;
                    codeword = nextCodeword(codeword, 1 << length);
                }

                if (length < maxLength && (codeword & primaryTableMask) == subtablePrefix) {
                    int oldLen = secondary.length;
                    secondary = Arrays.copyOf(secondary, oldLen + (oldLen - subtableStart));
                    System.arraycopy(secondary, subtableStart, secondary, oldLen, oldLen - subtableStart);
                    primary[subtablePrefix] = ((length + 1) << 12) | subtableStart;
                }
            }
        }

        if (secondary.length > 4096) {
            throw new WebPDecodeException("Invalid Huffman code");
        }

        return new Vp8LHuffmanTree(tableMask, primary, secondary);
    }

    private static int nextCodeword(int codeword, int tableSize) {
        if (codeword == tableSize - 1) {
            return codeword;
        }
        int adv = 31 - Integer.numberOfLeadingZeros(codeword ^ (tableSize - 1));
        int bit = 1 << adv;
        codeword &= bit - 1;
        codeword |= bit;
        return codeword;
    }

    /** Reads one symbol from the bitstream using this Huffman table. */
    int readSymbol(Vp8LBitReader br) throws WebPDecodeException {
        if (single) {
            return singleSymbol;
        }
        int v = (int) br.peekFull() & 0xFFFF;
        int entry = primaryTable[v & tableMask];
        int len = entry >>> 12;
        if (len <= MAX_TABLE_BITS) {
            br.consume(len);
            return entry & 0xFFF;
        }

        int mask = (1 << (len - MAX_TABLE_BITS)) - 1;
        int secondaryIndex = (entry & 0xFFF) + ((v >>> MAX_TABLE_BITS) & mask);
        int secondaryEntry = secondaryTable[secondaryIndex];
        br.consume(secondaryEntry & 0xF);
        return secondaryEntry >>> 4;
    }

    /** Returns {bits, symbol} if readable with primary table only, else null. */
    int[] peekSymbol(Vp8LBitReader br) {
        if (single) {
            return new int[] {0, singleSymbol};
        }
        int v = (int) br.peekFull() & 0xFFFF;
        int entry = primaryTable[v & tableMask];
        int len = entry >>> 12;
        if (len <= MAX_TABLE_BITS) {
            return new int[] {len, entry & 0xFFF};
        }
        return null;
    }
}
