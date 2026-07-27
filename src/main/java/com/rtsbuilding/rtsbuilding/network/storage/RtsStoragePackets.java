package com.rtsbuilding.rtsbuilding.network.storage;

/**
 * 1.12.2 储存协议的聚合入口。
 *
 * <p>固定编号和方向分别由绑定、分页与传输三个窄 facade 拥有；这里仅维持原来的
 * 单一注册入口，避免调用方再次了解内部拆分。</p>
 */
public final class RtsStoragePackets {
    private RtsStoragePackets() {
    }

    public static void register() {
        RtsStorageBindingPackets.register();
        RtsStoragePagePackets1122.register();
        RtsStorageTransferPackets1122.register();
    }
}
