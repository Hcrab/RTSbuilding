package com.rtsbuilding.rtsbuilding.compat.ftb;


import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RtsFtbTeamsCompatImpl {
    private final FtbTeamReflection teamReflection;

    RtsFtbTeamsCompatImpl() throws ReflectiveOperationException {
        this.teamReflection = FtbTeamReflection.create();
    }

    String teamKey(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        try {
            Object team = this.teamReflection.resolveTeam(player);
            if (team == null) {
                return "";
            }
            String stableId = resolveStableTeamId(team);
            return stableId.isBlank() ? "" : "ftb:" + stableId;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    String teamLabel(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        try {
            Object team = this.teamReflection.resolveTeam(player);
            return team == null ? "" : resolveTeamLabel(team);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    private static String resolveStableTeamId(Object team) {
        for (String methodName : new String[] { "getId", "getTeamId", "getTeamID", "getUUID", "getUuid" }) {
            try {
                Method method = team.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                Object value = method.invoke(team);
                if (value instanceof UUID uuid) {
                    return uuid.toString();
                }
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // Try the next known id method name.
            }
        }
        return team.toString();
    }

    private static String resolveTeamLabel(Object team) {
        for (String methodName : new String[] { "getName", "getDisplayName", "getShortName" }) {
            try {
                Method method = team.getClass().getMethod(methodName);
                if (method.getParameterCount() != 0) {
                    continue;
                }
                String label = plainTeamLabel(method.invoke(team));
                if (!label.isBlank()) {
                    return label;
                }
            } catch (ReflectiveOperationException ignored) {
                // 尝试下一个常见队伍显示名方法。
            }
        }
        return "";
    }

    /**
     * 将 FTB Teams 的显示名转换为真正给玩家看的纯文本。
     *
     * <p>FTB 的 {@code Team#getName()} 可能返回 {@link Component}。不能调用
     * {@code toString()}，否则会把样式和内部组件结构展开成可能超过网络上限的调试文本。</p>
     */
    static String plainTeamLabel(Object value) {
        Object unwrapped = unwrapOptional(value);
        String text = unwrapped instanceof Component component
                ? component.getString()
                : unwrapped == null ? "" : unwrapped.toString();
        return text.trim();
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }
}
