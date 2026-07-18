package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link KeyMapping#clickCount} 字段，用于在 RTS 模式下
 * 手动递增 Jade 快捷键的点击计数，使 {@code consumeClick()} 正常工作。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("clickCount")
    int getClickCount();

    @Accessor("clickCount")
    void setClickCount(int value);
}
