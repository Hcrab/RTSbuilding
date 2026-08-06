package com.rtsbuilding.rtsbuilding.common.destruction;

/** 一次便利破坏请求的紧凑参数；服务端会再次清洗。 */
public final class RtsConvenienceDestroySettings {
    public static final RtsConvenienceDestroySettings DEFAULT =
            new RtsConvenienceDestroySettings(3, 3, 3, 0, 15, 256);

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int chunkUp;
    private final int chunkDown;
    private final int treeMaxBlocks;

    public RtsConvenienceDestroySettings(int sizeX, int sizeY, int sizeZ,
                                         int chunkUp, int chunkDown, int treeMaxBlocks) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.chunkUp = chunkUp;
        this.chunkDown = chunkDown;
        this.treeMaxBlocks = treeMaxBlocks;
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int chunkUp() { return chunkUp; }
    public int chunkDown() { return chunkDown; }
    public int treeMaxBlocks() { return treeMaxBlocks; }
}
