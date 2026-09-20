package com.rtsbuilding.rtsbuilding.common.mining;

/** 范围/连锁数量的共同实现边界；不读取 Forge 配置。 */
public final class MiningLimits {
    public static final int DEFAULT_VOLUME = 46_656;
    public static final int MAX_VOLUME = 262_144;
    public static final int DEFAULT_CHAIN_LIMIT = 256;
    public static final int MAX_CHAIN_LIMIT = 4_096;
    /** 树木默认完整目标表示上限；可调上限与统一目标容量保持一致。 */
    public static final int DEFAULT_TREE_BLOCKS = 8_192;
    public static final int MAX_TREE_BLOCKS = MAX_VOLUME;

    private MiningLimits() {
    }

    public static int clampVolume(int volume) {
        return Math.max(1, Math.min(MAX_VOLUME, volume));
    }

    public static int clampChainLimit(int count) {
        return Math.max(1, Math.min(MAX_CHAIN_LIMIT, count));
    }

    public static boolean fitsVolume(long width, long height, long depth, int maxVolume) {
        long budget = clampVolume(maxVolume);
        return width > 0 && height > 0 && depth > 0
                && width <= budget && height <= budget / width
                && depth <= budget / width / height;
    }

    public static Dimensions clampDimensions(int width, int height, int depth, int maxVolume) {
        int budget = clampVolume(maxVolume);
        int w = Math.max(1, Math.min(budget, width));
        int h = Math.max(1, Math.min(budget, height));
        int d = Math.max(1, Math.min(budget, depth));
        if (fitsVolume(w, h, d, budget)) return new Dimensions(w, h, d);
        int low = 1, high = Math.max(w, Math.max(h, d));
        while (low < high) {
            int mid = low + (high - low + 1) / 2;
            if (fitsVolume(Math.min(w, mid), Math.min(h, mid), Math.min(d, mid), budget)) low = mid;
            else high = mid - 1;
        }
        w = Math.min(w, low + 1);
        h = Math.min(h, low + 1);
        d = Math.min(d, low + 1);
        while (!fitsVolume(w, h, d, budget)) {
            if (h >= w && h >= d && h > 1) h--;
            else if (w >= d && w > 1) w--;
            else d--;
        }
        return new Dimensions(w, h, d);
    }

    public record Dimensions(int width, int height, int depth) {
    }
}
