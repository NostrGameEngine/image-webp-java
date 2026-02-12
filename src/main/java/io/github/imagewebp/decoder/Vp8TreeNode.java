package io.github.imagewebp.decoder;

/** VP8 probability tree node (ported from Rust src/vp8.rs). */
final class Vp8TreeNode {
    final int left;
    final int right;
    int prob;
    final int index;

    Vp8TreeNode(int left, int right, int prob, int index) {
        this.left = left;
        this.right = right;
        this.prob = prob;
        this.index = index;
    }

    static int prepareBranch(int t) {
        if (t > 0) {
            return t / 2;
        }
        int value = -t;
        return 0x80 | value;
    }

    static int valueFromBranch(int t) {
        return t & ~0x80;
    }
}
