package com.rtsbuilding.rtsbuilding.fabric.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Map;

/**
 * 恢复非 RTS 世界中的原版鼠标路由所有权。
 *
 * <p>Fabric 的普通 {@link KeyMapping} 没有 NeoForge 的 GUI/上下文冲突域，而 Minecraft
 * 的静态映射表对每个物理键只保存一个 owner。RTS 的建造、破坏、旋转和拾取默认复用
 * 左右中键；如果让注册顺序决定 owner，关闭 BuilderScreen 后原版 use/attack/pick 就可能
 * 永远收不到点击。</p>
 *
 * <p>本类只在 RTS 未启用时把三个原版鼠标动作重新放回映射表。RTS Screen 内仍由现有
 * Screen 事件与 {@code matchesMouse} 读取玩家配置，所以不会失去可改键能力。本类不广播
 * 所有冲突按键，也不改变第三方模组的键位状态。</p>
 */
public final class FabricVanillaMouseRouting {
    private FabricVanillaMouseRouting() {
    }

    /** 每个客户端 tick 在输入采样前执行；重复写入同一对象是幂等的。 */
    public static void restoreOutsideRts() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null || ClientRtsController.get().isEnabled()) {
            return;
        }

        Map<InputConstants.Key, KeyMapping> keyMap = KeyMappingAccessor.rtsbuilding$getKeyMap();
        restoreMouseOwner(keyMap, minecraft.options.keyAttack);
        restoreMouseOwner(keyMap, minecraft.options.keyUse);
        restoreMouseOwner(keyMap, minecraft.options.keyPickItem);
    }

    private static void restoreMouseOwner(Map<InputConstants.Key, KeyMapping> keyMap, KeyMapping mapping) {
        if (keyMap == null || mapping == null) {
            return;
        }
        InputConstants.Key key = ((KeyMappingAccessor) mapping).getBoundKey();
        if (key != null
                && key.getType() == InputConstants.Type.MOUSE
                && !InputConstants.UNKNOWN.equals(key)) {
            keyMap.put(key, mapping);
        }
    }
}
