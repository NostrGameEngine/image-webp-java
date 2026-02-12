package io.github.imagewebp.decoder;

/** Predictor functions used by ALPH filtering. */
final class AlphaPredictor {
    private AlphaPredictor() {}

    static int predict(int x, int y, int width, int filteringMethod, byte[] rgba) {
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

    private static int horizontal(int x, int y, int width, byte[] rgba) {
        if (x == 0 && y == 0) return 0;
        if (x == 0) {
            int above = (y - 1) * width + x;
            return rgba[above * 4 + 3] & 0xFF;
        }
        int left = y * width + (x - 1);
        return rgba[left * 4 + 3] & 0xFF;
    }

    private static int vertical(int x, int y, int width, byte[] rgba) {
        if (x == 0 && y == 0) return 0;
        if (y == 0) {
            int left = y * width + (x - 1);
            return rgba[left * 4 + 3] & 0xFF;
        }
        int above = (y - 1) * width + x;
        return rgba[above * 4 + 3] & 0xFF;
    }

    private static int gradient(int x, int y, int width, byte[] rgba) {
        int left, top, topLeft;
        if (x == 0 && y == 0) {
            left = top = topLeft = 0;
        } else if (x == 0) {
            int above = (y - 1) * width + x;
            left = top = topLeft = rgba[above * 4 + 3] & 0xFF;
        } else if (y == 0) {
            int l = y * width + (x - 1);
            left = top = topLeft = rgba[l * 4 + 3] & 0xFF;
        } else {
            int l = y * width + (x - 1);
            int t = (y - 1) * width + x;
            int tl = (y - 1) * width + (x - 1);
            left = rgba[l * 4 + 3] & 0xFF;
            top = rgba[t * 4 + 3] & 0xFF;
            topLeft = rgba[tl * 4 + 3] & 0xFF;
        }

        int comb = left + top - topLeft;
        if (comb < 0) comb = 0;
        if (comb > 255) comb = 255;
        return comb;
    }
}
