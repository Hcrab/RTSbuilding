package com.rtsbuilding.rtsbuilding.compat.ftb;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * 统一负责 FTB Teams 的队伍查询反射。
 *
 * <p>这个值对象只解析并调用 FTB Teams 的公共查询入口，不负责队伍显示名、任务进度或业务降级。
 * 两个 FTB 兼容实现共享同一套版本适配，避免它们分别维护方法名候选与参数类型判断。</p>
 */
record FtbTeamReflection(
        Method teamsApiMethod,
        Method getTeamManagerMethod,
        Method getTeamForPlayerMethod,
        boolean teamLookupUsesServerPlayer) {

    FtbTeamReflection {
        if (teamsApiMethod == null || getTeamManagerMethod == null || getTeamForPlayerMethod == null) {
            throw new IllegalArgumentException("All reflection methods must be non-null");
        }
    }

    /**
     * 一次性解析 Forge 1.20.1 上已知的 FTB Teams API 形态。
     *
     * <p>解析失败会直接抛出异常，由上层兼容入口决定是否关闭该可选集成。</p>
     */
    static FtbTeamReflection create() throws ReflectiveOperationException {
        Class<?> ftbTeamsApiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        Method teamsApiMethod = ftbTeamsApiClass.getMethod("api");
        Method getTeamManagerMethod = teamsApiMethod.getReturnType().getMethod("getManager");
        Method getTeamForPlayerMethod = resolveTeamLookupMethod(getTeamManagerMethod.getReturnType());
        boolean teamLookupUsesServerPlayer =
                getTeamForPlayerMethod.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class);
        return new FtbTeamReflection(
                teamsApiMethod,
                getTeamManagerMethod,
                getTeamForPlayerMethod,
                teamLookupUsesServerPlayer);
    }

    /**
     * 返回玩家所属的 FTB 队伍；没有队伍或 API 返回空值时返回 {@code null}。
     */
    Object resolveTeam(ServerPlayer player) throws ReflectiveOperationException {
        if (player == null) {
            return null;
        }
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
