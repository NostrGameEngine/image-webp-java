package io.github.imagewebp.decoder;

import java.nio.ByteBuffer;

/** Predictor functions used by ALPH filtering. */
final class AlphaPredictor {
    private AlphaPredictor() {}

    static int predict(int x, int y, int width, int filteringMethod, ByteBuffer rgba) {
        // filteringMethod: 0 None, 1 Horizontal, 2 Vertical, 3 Gradient
        switch (filteringMethod) {
            case 0:
                return 0;
            case 1:
                return horizontal(x, y, width, rgba);
            case 2:
                return vertical(x, y, width, rgba);
            case 3:
                return gradient(x, y, width, rgba);
            default:
                return 0;
        }
    }

    private static int alphaAt(ByteBuffer rgba, int pixelIndex) {
        return rgba.get(pixelIndex * 4 + 3) & 0xFF;
    }

    private static int horizontal(int x, int y, int width, ByteBuffer rgba) {
        if (x == 0 && y == 0) return 0;
        if (x == 0) {
            int above = (y - 1) * width + x;
            return alphaAt(rgba, above);
        }
        int left = y * width + (x - 1);
        return alphaAt(rgba, left);
    }

    private static int vertical(int x, int y, int width, ByteBuffer rgba) {
        if (x == 0 && y == 0) return 0;
        if (y == 0) {
            int left = y * width + (x - 1);
            return alphaAt(rgba, left);
        }
        int above = (y - 1) * width + x;
        return alphaAt(rgba, above);
    }

    private static int gradient(int x, int y, int width, ByteBuffer rgba) {
        int left, top, topLeft;
        if (x == 0 && y == 0) {
            left = top = topLeft = 0;
        } else if (x == 0) {
            int above = (y - 1) * width + x;
            left = top = topLeft = alphaAt(rgba, above);
        } else if (y == 0) {
            int l = y * width + (x - 1);
            left = top = topLeft = alphaAt(rgba, l);
        } else {
            int l = y * width + (x - 1);
            int t = (y - 1) * width + x;
            int tl = (y - 1) * width + (x - 1);
            left = alphaAt(rgba, l);
            top = alphaAt(rgba, t);
            topLeft = alphaAt(rgba, tl);
        }

        int comb = left + top - topLeft;
        if (comb < 0) comb = 0;
        if (comb > 255) comb = 255;
        return comb;
    }
}
