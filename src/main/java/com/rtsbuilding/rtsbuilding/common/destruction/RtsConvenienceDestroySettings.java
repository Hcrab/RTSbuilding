package com.rtsbuilding.rtsbuilding.common.destruction;

/**
 * 一次便捷破坏请求的紧凑参数。
 *
 * <p>这里保存玩家选择的原始意图；真正执行前仍由规划器按硬上限和世界高度清洗。
 * 树木只有一个总方块上限，不把原木、树叶或单棵树拆成多个独立配额。</p>
 */
public record RtsConvenienceDestroySettings(
        int sizeX,
        int sizeY,
        int sizeZ,
        int chunkUp,
        int chunkDown,
        int treeMaxBlocks) {

    public static final RtsConvenienceDestroySettings DEFAULT =
            new RtsConvenienceDestroySettings(3, 3, 3, 0, 15, 256);
}

