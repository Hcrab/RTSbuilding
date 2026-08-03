package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * RTSBuilding 自己的创造栏，保证插件物品在创造模式和测试世界里可直接拿到。
 */
public final class RtsCreativeTabs {
    /**
     * 1.19.2 的创造栏不是注册表对象，因此必须使用旧式静态页签。
     * 页签仍只展示主线明确列入创造栏的 RTS 物品，避免改变玩家可见内容边界。
     */
    public static final CreativeModeTab RTSBUILDING_TAB = new CreativeModeTab(RtsbuildingMod.MODID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(RtsItems.RTS_CONTROL_CORE.get());
        }

        @Override
        public void fillItemList(NonNullList<ItemStack> items) {
            for (var holder : RtsItems.getCreativeTabItems()) {
                items.add(new ItemStack(holder.get()));
            }
        }
    };

    private RtsCreativeTabs() {
    }

    /** 强制初始化旧式创造栏；1.19.2 不需要向模组事件总线注册。 */
    public static void initialize() {
        // 访问静态字段即可完成页签创建。
        RTSBUILDING_TAB.getId();
    }
}
