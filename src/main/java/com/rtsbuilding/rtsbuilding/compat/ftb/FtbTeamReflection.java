package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.entity.player.EntityPlayerMP;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * FTB Library Legacy（1.12）的队伍反射入口。
 *
 * <p>1.12 没有后来的 FTB Teams API。玩家和队伍由
 * {@code com.feed_the_beast.ftblib.lib.data.Universe} 管理，
 * {@code ForgePlayer#team} 是该版本公开且持久化的队伍引用。</p>
 */
final class FtbTeamReflection {
    private final Method universeLoadedMethod;
    private final Method universeGetMethod;
    private final Method universeGetPlayerMethod;
    private final Method playerHasTeamMethod;
    private final Field playerTeamField;
    private final Method teamGetIdMethod;
    private final Method teamGetTitleMethod;

    private FtbTeamReflection(Method universeLoadedMethod, Method universeGetMethod,
            Method universeGetPlayerMethod, Method playerHasTeamMethod, Field playerTeamField,
            Method teamGetIdMethod, Method teamGetTitleMethod) {
        this.universeLoadedMethod = universeLoadedMethod;
        this.universeGetMethod = universeGetMethod;
        this.universeGetPlayerMethod = universeGetPlayerMethod;
        this.playerHasTeamMethod = playerHasTeamMethod;
        this.playerTeamField = playerTeamField;
        this.teamGetIdMethod = teamGetIdMethod;
        this.teamGetTitleMethod = teamGetTitleMethod;
    }

    static FtbTeamReflection create() throws ReflectiveOperationException {
        Class<?> universeClass = Class.forName("com.feed_the_beast.ftblib.lib.data.Universe");
        Class<?> forgePlayerClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgePlayer");
        Class<?> forgeTeamClass = Class.forName("com.feed_the_beast.ftblib.lib.data.ForgeTeam");
        return new FtbTeamReflection(
                universeClass.getMethod("loaded"),
                universeClass.getMethod("get"),
                universeClass.getMethod("getPlayer", UUID.class),
                forgePlayerClass.getMethod("hasTeam"),
                forgePlayerClass.getField("team"),
                forgeTeamClass.getMethod("getId"),
                forgeTeamClass.getMethod("getTitle"));
    }

    Object resolveTeam(EntityPlayerMP player) throws ReflectiveOperationException {
        if (player == null || !Boolean.TRUE.equals(this.universeLoadedMethod.invoke(null))) {
            return null;
        }
        Object universe = this.universeGetMethod.invoke(null);
        Object forgePlayer = this.universeGetPlayerMethod.invoke(universe, player.getUniqueID());
        if (forgePlayer == null || !Boolean.TRUE.equals(this.playerHasTeamMethod.invoke(forgePlayer))) {
            return null;
        }
        return this.playerTeamField.get(forgePlayer);
    }

    String teamId(Object team) throws ReflectiveOperationException {
        Object value = team == null ? null : this.teamGetIdMethod.invoke(team);
        return value == null ? "" : value.toString().trim();
    }

    String teamLabel(Object team) throws ReflectiveOperationException {
        Object title = team == null ? null : this.teamGetTitleMethod.invoke(team);
        if (title == null) {
            return "";
        }
        try {
            Object text = title.getClass().getMethod("getUnformattedText").invoke(title);
            return text == null ? "" : text.toString().trim();
        } catch (NoSuchMethodException ignored) {
            return title.toString().trim();
        }
    }
}
