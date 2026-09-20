package com.rtsbuilding.rtsbuilding.common.mining;

/**
 * 一次范围操作使用的不可变有效限制。
 *
 * <p>体积和三个世界轴是四个独立的服务端规则。这个类型不读取配置，也不把连锁
 * 或树木扫描的目标数误套成范围轴长；loader 配置只需要在入口构造一次即可。</p>
 */
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
