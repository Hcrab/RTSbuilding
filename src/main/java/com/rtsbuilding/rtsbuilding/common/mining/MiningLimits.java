package com.rtsbuilding.rtsbuilding.common.mining;

/**
 * 挖掘操作共同的数量语义与实现支持范围，不读取配置、不访问世界或网络。
 *
 * <p>区域以含端点包围盒体积计费，长条和薄片没有额外轴向上限。配置决定新操作的
 * 实际容量；这里的实现上限同时供传输重组和任务存档使用，避免存档恢复被后来调低的
 * 配置截断。网络字节和历史 NBT 预算仍由各自模块负责，不能与方块数量混为一谈。</p>
 */
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
        return Math.clamp(volume, 1, MAX_VOLUME);
    }

    public static int clampChainLimit(int count) {
        return Math.clamp(count, 1, MAX_CHAIN_LIMIT);
    }

    /** 除法判断避免坐标跨度相乘溢出；单轴的唯一上限由体积本身推导。 */
    public static boolean fitsVolume(long width, long height, long depth, int maxVolume) {
        long budget = clampVolume(maxVolume);
        return width > 0 && height > 0 && depth > 0
                && width <= budget && height <= budget / width
                && depth <= budget / width / height;
    }

    /**
     * 保留合法尺寸；超限时均衡收缩最长方向。二分定位共同尺寸水位后最多调整三格，
     * 避免超长拖拽在每次预览时执行几十万次减一循环。
     */
    public static Dimensions clampDimensions(int width, int height, int depth, int maxVolume) {
        int budget = clampVolume(maxVolume);
        int w = Math.clamp(width, 1, budget), h = Math.clamp(height, 1, budget), d = Math.clamp(depth, 1, budget);
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
