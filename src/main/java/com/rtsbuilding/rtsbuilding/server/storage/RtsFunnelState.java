package com.rtsbuilding.rtsbuilding.server.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物漏斗运行时状态。
 *
 * <p>这里只保存漏斗是否开启、目标位置、冷却和待插入缓冲区。真正的掉落物
 * 收集、插入和页面刷新仍由服务层处理。</p>
 */
public class RtsFunnelState {

    /** 漏斗模式是否激活。 */
    public boolean funnelEnabled;
    /** 漏斗输出目标坐标。 */
    public BlockPos funnelTarget;
    /** 漏斗冷却 tick。 */
    public int funnelTickCooldown;
    /** 漏斗临时缓冲区。 */
    public final List<ItemStack> funnelBuffer = new ArrayList<>();
}
