package com.rtsbuilding.rtsbuilding.common.mining;

/** 一次范围操作的服务端体积与世界 X/Y/Z 轴长上限；不读取配置。 */
public record SelectionVolumeLimit(int maxVolume, int maxSizeX, int maxSizeY, int maxSizeZ) {
    public SelectionVolumeLimit {
        if (maxVolume < 1 || maxVolume > MiningLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("maxVolume out of range: " + maxVolume);
        }
        if (maxSizeX < 1 || maxSizeY < 1 || maxSizeZ < 1) {
            throw new IllegalArgumentException("selection axes must be positive");
        }
    }

    public static SelectionVolumeLimit defaults() {
        return new SelectionVolumeLimit(MiningLimits.DEFAULT_VOLUME, 64, 64, 64);
    }

    public boolean fits(long spanX, long spanY, long spanZ) {
        return spanX > 0 && spanY > 0 && spanZ > 0
                && spanX <= maxSizeX && spanY <= maxSizeY && spanZ <= maxSizeZ
                && MiningLimits.fitsVolume(spanX, spanY, spanZ, maxVolume);
    }

    public boolean fits(int spanX, int spanY, int spanZ) {
        return fits((long) spanX, (long) spanY, (long) spanZ);
    }
}
