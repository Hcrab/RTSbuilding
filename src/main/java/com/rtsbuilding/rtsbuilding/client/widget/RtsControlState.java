package com.rtsbuilding.rtsbuilding.client.widget;

import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * 统一描述按钮在当前一帧的交互状态。
 *
 * <p>角色回答“这个按钮是什么”，状态回答“现在能否执行、是否已选中、
 * 是否正在等待服务端、是否失败”。面板只产生状态，WindowButton 统一渲染，
 * 避免每个功能复制一套互相打架的颜色和禁用逻辑。</p>
 */
public record RtsControlState(
        RtsControlRole role,
        boolean enabled,
        boolean selected,
        boolean pending,
        boolean failed,
        Component disabledReason) {

    public RtsControlState {
        role = Objects.requireNonNull(role, "role");
        if (enabled) {
            disabledReason = null;
        }
        if (failed) {
            pending = false;
        }
    }

    public static RtsControlState enabled(RtsControlRole role) {
        return new RtsControlState(role, true, false, false, false, null);
    }

    public RtsControlState withEnabled(boolean value, Component reason) {
        return new RtsControlState(role, value, selected, pending, failed, reason);
    }

    public RtsControlState withRole(RtsControlRole value) {
        return new RtsControlState(value, enabled, selected, pending, failed, disabledReason);
    }

    public RtsControlState withSelected(boolean value) {
        return new RtsControlState(role, enabled, value, pending, failed, disabledReason);
    }

    public RtsControlState withPending(boolean value) {
        return new RtsControlState(role, enabled, selected, value, failed, disabledReason);
    }

    public RtsControlState withFailed(boolean value) {
        return new RtsControlState(role, enabled, selected, pending, value, disabledReason);
    }
}
