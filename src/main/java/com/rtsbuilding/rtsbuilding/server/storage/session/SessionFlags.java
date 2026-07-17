package com.rtsbuilding.rtsbuilding.server.storage.session;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话级别的布尔标志和虚拟流体存储，作用于单个 RtsStorageSession。
 *
 * <p>从 RtsStorageSession 提取，将开关标志和内部（虚拟）流体容量
 * 分组到单个值对象中。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #useBdNetwork}——BD 网络是否参与解析</li>
 *   <li>{@link #autoStoreMinedDrops}——挖掘掉落物是否自动存入链接存储</li>
 *   <li>{@link #internalFluidMb}——按流体注册名称键化的虚拟流体容量</li>
 * </ul>
 */
public final class SessionFlags {

    /** 是否将 BD 网络作为统一存储后端包含进来。 */
    public boolean useBdNetwork = true;

    /** 挖掘掉落物是否自动存入链接存储。 */
    public boolean autoStoreMinedDrops = true;

    /**
     * 虚拟流体容量，{@code 流体注册名 -> 容量(mB)}。
     * 当不存在真实流体处理器时，用于显示虚拟流体槽位。
     */
    public final Map<String, Long> internalFluidMb = new HashMap<>();

    /** 待处理的智能放置批量流体作业（非持久化，仅会话生命周期内存活）。 */
    public transient Object pendingFluidBatch;
}
