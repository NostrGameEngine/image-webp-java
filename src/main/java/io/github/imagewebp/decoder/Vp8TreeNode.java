package io.github.imagewebp.decoder;

/** VP8 probability tree node (ported from Rust src/vp8.rs). */
final class Vp8TreeNode {
    /** Left branch index or encoded terminal value branch. */
    final int left;
    /** Right branch index or encoded terminal value branch. */
    final int right;
    /** Probability used for boolean split at this node. */
    int prob;
    /** Node index in the tree array. */
    final int index;

    Vp8TreeNode(int left, int right, int prob, int index) {
        this.left = left;
        this.right = right;
        this.prob = prob;
        this.index = index;
    }

    /** Converts compact tree branch values into decoder branch representation. */
    static int prepareBranch(int t) {
        if (t > 0) {
            return t / 2;
        }
        int value = -t;
        return 0x80 | value;
    }

    /** Extracts symbol value from an encoded terminal branch. */
    static int valueFromBranch(int t) {
        return t & ~0x80;
    }
}
