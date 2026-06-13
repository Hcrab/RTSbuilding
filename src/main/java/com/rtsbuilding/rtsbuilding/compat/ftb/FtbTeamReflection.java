package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared FTB Teams reflection for team lookup.
 * <p>
 * Eliminates the duplicated {@code resolveTeam()} logic between
 * {@link RtsFtbCompatImpl} and {@link RtsFtbTeamsCompatImpl}.
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
     * Loads all required FTB Teams reflection methods. Throws on failure so
     * callers can catch and disable the feature gracefully.
     */
    static FtbTeamReflection create() throws ReflectiveOperationException {
        Class<?> ftbTeamsApiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
        Method teamsApiMethod = ftbTeamsApiClass.getMethod("api");
        Method getTeamManagerMethod = teamsApiMethod.getReturnType().getMethod("getManager");
        Method getTeamForPlayerMethod = resolveTeamLookupMethod(getTeamManagerMethod.getReturnType());
        boolean teamLookupUsesServerPlayer =
                getTeamForPlayerMethod.getParameterTypes()[0].isAssignableFrom(ServerPlayer.class);
        return new FtbTeamReflection(teamsApiMethod, getTeamManagerMethod, getTeamForPlayerMethod,
                teamLookupUsesServerPlayer);
    }

    /**
     * Resolves the FTB Team for the given player, or {@code null} if the
     * player has no team or an error occurs.
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
        for (String name : new String[]{"getTeamForPlayerID", "getTeamForPlayer"}) {
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
