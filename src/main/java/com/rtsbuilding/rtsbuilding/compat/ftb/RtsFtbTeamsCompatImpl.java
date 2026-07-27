package com.rtsbuilding.rtsbuilding.compat.ftb;

import net.minecraft.entity.player.EntityPlayerMP;

/** 读取 FTB Library Legacy 队伍身份，供 RTS 生存进度共享使用。 */
final class RtsFtbTeamsCompatImpl {
    private final FtbTeamReflection reflection;

    RtsFtbTeamsCompatImpl() throws ReflectiveOperationException {
        this.reflection = FtbTeamReflection.create();
    }

    String teamKey(EntityPlayerMP player) {
        try {
            String id = this.reflection.teamId(this.reflection.resolveTeam(player));
            return id.isEmpty() ? "" : "ftb:" + id;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    String teamLabel(EntityPlayerMP player) {
        try {
            return this.reflection.teamLabel(this.reflection.resolveTeam(player));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }
}
