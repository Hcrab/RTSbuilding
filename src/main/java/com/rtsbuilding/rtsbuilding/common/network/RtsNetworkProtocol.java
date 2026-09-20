package com.rtsbuilding.rtsbuilding.common.network;

/**
 * 本 beta 线的 wire 协议身份。
 *
 * <p>它是 payload 注册器的共同纯 common 常量，不等同于 mod 版本，也不接受旧代次
 * 按新字段顺序解码。</p>
 */
public final class RtsNetworkProtocol {
    public static final String VERSION = "1.1.8-beta";

    private RtsNetworkProtocol() {
    }
}
