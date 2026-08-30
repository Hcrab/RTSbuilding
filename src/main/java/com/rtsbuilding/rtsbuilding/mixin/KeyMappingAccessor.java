package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 仅为 RTS 界面补回 Jade 快捷键点击计数，不改变普通游戏界面的键位处理。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int getClickCount();

    @Accessor("clickCount")
    void setClickCount(int value);
}
