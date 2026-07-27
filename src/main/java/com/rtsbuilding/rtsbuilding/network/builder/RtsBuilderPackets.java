package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;

/**
 * 建造域网络注册的兼容入口。
 *
 * <p>1.12.2 的消息按稳定 discriminator 拆在 placement、mining、workflow、action
 * 和 sound 注册器中；旧调用方仍可从本类启动统一网络注册，但不会重新排列协议编号。</p>
 */
public final class RtsBuilderPackets {
    private RtsBuilderPackets() {
    }

    public static void register() {
        RtsPayloadRegistrar.register();
    }
}
