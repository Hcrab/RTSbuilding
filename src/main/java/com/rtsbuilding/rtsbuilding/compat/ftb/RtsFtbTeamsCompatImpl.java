package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

final class RtsFtbTeamsCompatImpl {
    private final Method teamsApiMethod;
    private final Method getTeamManagerMethod;
    private final Method getTeamForPlayerMethod;
    private final boolean teamLookupUsesServerPlayer;

    RtsFtbTeamsCompatImpl() throws ReflectiveOperationException {
        Class<?> ftbTeamsApiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        this.teamsApiMethod = ftbTeamsApiClass.getMethod("api");
        this.getTeamManagerMethod = this.teamsApiMethod.getReturnType().getMethod("getManager");
        this.getTeamForPlayerMethod = resolveTeamLookupMethod(this.getTeamManagerMethod.getReturnType());
        this.teamLookupUsesServerPlayer = this.getTeamForPlayerMethod.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class);
    }

    String teamKey(ServerPlayer player) {
        if (player == null) {
            return "";
        }
        try {
            Object team = resolveTeam(player);
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
            Object team = resolveTeam(player);
            return team == null ? "" : resolveTeamLabel(team);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    private Object resolveTeam(ServerPlayer player) throws ReflectiveOperationException {
        Object api = this.teamsApiMethod.invoke(null);
        if (api == null) {
            return null;
        }
        Object manager = this.getTeamManagerMethod.invoke(api);
        if (manager == null) {
            return null;
        }
        Object rawTeam = this.teamLookupUsesServerPlayer
                ? this.getTeamForPlayerMethod.invoke(manager, player)
                : this.getTeamForPlayerMethod.invoke(manager, player.getUUID());
        return unwrapOptional(rawTeam);
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
                // Try the next known label method name.
            }
        }
        return "";
    }

    /**
     * 将 FTB Teams 的显示名转换为真正给玩家看的纯文本。
     *
     * <p>FTB 1.21 的 {@code Team#getName()} 返回 {@link Component}。不能调用
     * {@code toString()}，否则会把样式和内部组件结构展开成可能超过网络上限的调试文本。</p>
     */
    static String plainTeamLabel(Object value) {
        Object unwrapped = unwrapOptional(value);
        String text = unwrapped instanceof Component component
                ? component.getString()
                : unwrapped == null ? "" : unwrapped.toString();
        return text.trim();
    }

    private static Method resolveTeamLookupMethod(Class<?> managerClass) throws NoSuchMethodException {
        for (String name : new String[] { "getTeamForPlayerID", "getTeamForPlayer" }) {
            for (Method method : managerClass.getMethods()) {
                if (!name.equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (parameterType == UUID.class || parameterType.isAssignableFrom(ServerPlayer.class)) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodException("Missing team lookup method on " + managerClass.getName());
    }

    private static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }
}
