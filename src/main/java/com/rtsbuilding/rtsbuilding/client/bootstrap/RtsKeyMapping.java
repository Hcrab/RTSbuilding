package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 26.1 强类型输入事件与现有 RTS 面板整数回调之间的窄适配器。
 *
 * <p>它保留原版 {@link KeyMapping} 的冲突和重绑定语义，只把旧的
 * keyCode/scanCode 或 mouse button 参数包装成新版事件对象。
 */
public final class RtsKeyMapping extends KeyMapping {
    public RtsKeyMapping(String name, int keysym, Category category) {
        super(name, keysym, category);
    }

    public RtsKeyMapping(String name, InputConstants.Type type, int value, Category category) {
        super(name, type, value, category);
    }

    public RtsKeyMapping(String name, IKeyConflictContext context, KeyModifier modifier,
            InputConstants.Type type, int keyCode, Category category) {
        super(name, context, modifier, type, keyCode, category);
    }

    public boolean matches(int keyCode, int scanCode) {
        return this.matches(keyCode, scanCode, 0);
    }

    public boolean matches(int keyCode, int scanCode, int modifiers) {
        return this.matches(new KeyEvent(keyCode, scanCode, modifiers));
    }

    public boolean matchesMouse(int button) {
        return this.matchesMouse(new MouseButtonEvent(
                0.0D, 0.0D, new MouseButtonInfo(button, 0)));
    }
}
