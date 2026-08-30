package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

/** 便捷破坏设置的 Java 8 纯值快照。 */
public final class QuickBuildUiConvenienceSettings {
    public static final int BOX_MIN = 1;
    public static final int BOX_MAX = 64;
    public static final int HEIGHT_MAX = 128;
    public static final int TREE_MIN = 1;
    public static final int TREE_MAX = 8_192;
    public static final QuickBuildUiConvenienceSettings DEFAULT =
            new QuickBuildUiConvenienceSettings(3, 3, 3, 0, 15, 256);

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int chunkUp;
    private final int chunkDown;
    private final int treeMaxBlocks;

    public QuickBuildUiConvenienceSettings(
            int sizeX, int sizeY, int sizeZ,
            int chunkUp, int chunkDown, int treeMaxBlocks) {
        this.sizeX = clamp(sizeX, BOX_MIN, BOX_MAX);
        this.sizeY = clamp(sizeY, BOX_MIN, HEIGHT_MAX);
        this.sizeZ = clamp(sizeZ, BOX_MIN, BOX_MAX);
        this.chunkUp = clamp(chunkUp, 0, HEIGHT_MAX);
        this.chunkDown = clamp(chunkDown, 0, HEIGHT_MAX);
        this.treeMaxBlocks = clamp(treeMaxBlocks, TREE_MIN, TREE_MAX);
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int chunkUp() { return chunkUp; }
    public int chunkDown() { return chunkDown; }
    public int treeMaxBlocks() { return treeMaxBlocks; }

    public int value(QuickBuildUiConvenienceParameter parameter) {
        switch (parameter) {
            case SIZE_X: return sizeX;
            case SIZE_Y: return sizeY;
            case SIZE_Z: return sizeZ;
            case CHUNK_UP: return chunkUp;
            case CHUNK_DOWN: return chunkDown;
            case TREE_MAX_BLOCKS: return treeMaxBlocks;
            default: throw new IllegalArgumentException("parameter");
        }
    }

    public QuickBuildUiConvenienceSettings with(
            QuickBuildUiConvenienceParameter parameter, int value) {
        switch (parameter) {
            case SIZE_X:
                return new QuickBuildUiConvenienceSettings(
                        value, sizeY, sizeZ, chunkUp, chunkDown, treeMaxBlocks);
            case SIZE_Y:
                return new QuickBuildUiConvenienceSettings(
                        sizeX, value, sizeZ, chunkUp, chunkDown, treeMaxBlocks);
            case SIZE_Z:
                return new QuickBuildUiConvenienceSettings(
                        sizeX, sizeY, value, chunkUp, chunkDown, treeMaxBlocks);
            case CHUNK_UP:
                return new QuickBuildUiConvenienceSettings(
                        sizeX, sizeY, sizeZ, value, chunkDown, treeMaxBlocks);
            case CHUNK_DOWN:
                return new QuickBuildUiConvenienceSettings(
                        sizeX, sizeY, sizeZ, chunkUp, value, treeMaxBlocks);
            case TREE_MAX_BLOCKS:
                return new QuickBuildUiConvenienceSettings(
                        sizeX, sizeY, sizeZ, chunkUp, chunkDown, value);
            default:
                throw new IllegalArgumentException("parameter");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof QuickBuildUiConvenienceSettings)) return false;
        QuickBuildUiConvenienceSettings value = (QuickBuildUiConvenienceSettings) other;
        return sizeX == value.sizeX && sizeY == value.sizeY && sizeZ == value.sizeZ
                && chunkUp == value.chunkUp && chunkDown == value.chunkDown
                && treeMaxBlocks == value.treeMaxBlocks;
    }

    @Override
    public int hashCode() {
        int result = sizeX;
        result = 31 * result + sizeY;
        result = 31 * result + sizeZ;
        result = 31 * result + chunkUp;
        result = 31 * result + chunkDown;
        result = 31 * result + treeMaxBlocks;
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
