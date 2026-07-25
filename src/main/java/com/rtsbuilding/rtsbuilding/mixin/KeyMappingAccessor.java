package com.rtsbuilding.rtsbuilding.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 仅用于在 RTS Screen 中补回第三方快捷键的点击计数。
 *
 * <p>它不改变普通游戏界面的按键处理，也不拥有 Jade 逻辑。</p>
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("clickCount")
    int getClickCount();

    @Accessor("clickCount")
    void setClickCount(int value);
}
