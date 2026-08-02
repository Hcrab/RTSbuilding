package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 暴露原版键位当前绑定和点击计数，用于旧绑定迁移及 Jade 快捷键桥。
 * 本接口只读写原版已有状态，不改变普通游戏界面的键位处理。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    /**
     * Fabric 没有 NeoForge 的按键冲突上下文；原版映射表每个物理键只保留一个 owner。
     * 这里只把映射表暴露给 Fabric 的上下文恢复桥，不允许普通玩法代码直接改写。
     */
    @Accessor("MAP")
    static Map<InputConstants.Key, KeyMapping> rtsbuilding$getKeyMap() {
        throw new AssertionError();
    }

    @Accessor("key")
    InputConstants.Key getBoundKey();

    @Accessor("clickCount")
    int getClickCount();

    @Accessor("clickCount")
    void setClickCount(int value);
}
