package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露原版容器屏幕用于命中检测的只读布局状态。
 *
 * <p>本接口不改变容器点击语义；它只替代 NeoForge 曾提供的公开便捷 getter，
 * 让 Fabric 客户端继续按原版槽位坐标识别鼠标下方的物品。
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("hoveredSlot")
    Slot getHoveredSlot();

    @Accessor("leftPos")
    int getLeftPos();

    @Accessor("topPos")
    int getTopPos();
}
