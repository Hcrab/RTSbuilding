package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露原版键位当前绑定和点击计数，用于旧绑定迁移及 Jade 快捷键桥。
 * 本接口只读写原版已有状态，不改变普通游戏界面的键位处理。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key getBoundKey();

    @Accessor("clickCount")
    int getClickCount();

    @Accessor("clickCount")
    void setClickCount(int value);
}
