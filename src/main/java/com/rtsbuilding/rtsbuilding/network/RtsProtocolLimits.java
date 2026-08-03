package com.rtsbuilding.rtsbuilding.network;

/**
 * 1.12.2 网络请求的固定硬上限。
 *
 * <p>这些值同时约束配置界面和数据包解码，避免管理员保存一个客户端可以选择、
 * 但协议会在序列化阶段拒绝的数值。它们不是每服可调的平衡参数。</p>
 */
public final class RtsProtocolLimits {
    public static final int AREA_MINE_MAX_VOLUME = 98_304;
    public static final int AREA_DESTROY_MAX_POSITIONS = 98_304;

    private RtsProtocolLimits() {
    }
}
