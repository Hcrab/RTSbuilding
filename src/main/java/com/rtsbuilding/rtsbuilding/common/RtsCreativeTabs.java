package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistryEntry;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsSimpleRegistry;

/**
 * 创造模式物品栏标签页注册器 —— RTSbuilding 的所有创造标签页在此集中注册。
 * <p>
 * 目前包含一个主标签页，
 * 自动收集所有标记为 creative 的物品和方块并显示在其中。
 */
public final class RtsCreativeTabs {

    /** 统一的创造标签页注册表实例 */
    public static final RtsSimpleRegistry<CreativeModeTab> CREATIVE_TABS =
            new RtsSimpleRegistry<>(BuiltInRegistries.CREATIVE_MODE_TAB, RtsbuildingMod.MODID);

    /** RTSbuilding 主标签页 —— 包含所有模组物品与方块 */
    @SuppressWarnings("unused")
    public static final RtsRegistryEntry<CreativeModeTab, CreativeModeTab> RTSBUILDING_TAB = CREATIVE_TABS.register(
            "rtsbuilding",
            () -> FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.rtsbuilding"))
                    .icon(() -> new ItemStack(RtsItems.RTS_CONTROL_CORE.get()))
                    .displayItems((parameters, output) -> {
                        for (var holder : RtsItems.getCreativeTabItems()) {
                            output.accept(holder.get());
                        }
                        for (var holder : RtsBlocks.getCreativeTabBlocks()) {
                            output.accept(holder.get());
                        }
                    })
                    .build());

    // ============================================================
    //  注册入口
    // ============================================================

    /**
     * 在模组总线上注册所有创造标签页。
     *
     * @param modEventBus 模组事件总线
     */
    public static void register() {
        // 访问本类即已按声明顺序注册；此方法明确表达入口初始化顺序。
    }

    private RtsCreativeTabs() {
    }
}
