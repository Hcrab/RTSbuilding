package com.rtsbuilding.rtsbuilding.common;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

/** Forge 1.12.2 的 RTSBuilding 创造栏。创造栏不是注册表对象，由静态实例直接声明。 */
public final class RtsCreativeTabs {
    public static final CreativeTabs RTSBUILDING_TAB = new CreativeTabs("rtsbuilding") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(RtsItems.RTS_CONTROL_CORE.get());
        }
    };

    /** 保留统一注册入口；1.12.2 的 {@link CreativeTabs} 无需向注册表提交。 */
    public static void register() {
        // 类初始化即完成创建；此方法明确表达生命周期顺序。
    }

    private RtsCreativeTabs() {
    }
}
