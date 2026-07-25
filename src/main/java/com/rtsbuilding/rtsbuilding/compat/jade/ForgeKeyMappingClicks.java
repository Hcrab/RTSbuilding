package com.rtsbuilding.rtsbuilding.compat.jade;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

/**
 * Forge 1.20.1 的按键点击计数适配器。
 *
 * <p>RTS Screen 打开时，Minecraft 不会像普通游戏界面那样替第三方快捷键累加点击计数。
 * Jade 仍然需要读取这项计数来处理自己的配置与开关快捷键，因此这里只补回命中的 Jade
 * 按键。业务层不应直接依赖 1.20.1 的 SRG 字段名。</p>
 */
final class ForgeKeyMappingClicks {
    /**
     * 1.20.1 中 {@code KeyMapping.clickCount} 的稳定 SRG 名称。
     *
     * <p>{@link ObfuscationReflectionHelper} 会在开发环境和正式混淆环境中分别解析它，
     * 避免 Mixin accessor 因缺少字段映射而只在开发环境可用。</p>
     */
    private static final Field CLICK_COUNT =
            ObfuscationReflectionHelper.findField(KeyMapping.class, "f_90818_");

    private ForgeKeyMappingClicks() {
    }

    static void increment(KeyMapping keyMapping) {
        try {
            CLICK_COUNT.setInt(keyMapping, CLICK_COUNT.getInt(keyMapping) + 1);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("无法更新 Jade 快捷键点击计数", exception);
        }
    }
}
