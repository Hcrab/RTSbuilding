package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Objects;

/** 漏斗任务只持有当前在线会话；世界实体 ownership 不跨调度片转移。 */
public final class FunnelTaskPayload implements TaskPayload {
    private final EntityPlayerMP player;
    private final RtsStorageSession session;

    public FunnelTaskPayload(EntityPlayerMP player, RtsStorageSession session) {
        this.player = player;
        this.session = session;
    }

    public EntityPlayerMP player() { return player; }
    public RtsStorageSession session() { return session; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof FunnelTaskPayload)) return false;
        FunnelTaskPayload other = (FunnelTaskPayload) object;
        return Objects.equals(player, other.player) && Objects.equals(session, other.session);
    }

    @Override
    public int hashCode() { return Objects.hash(player, session); }

    @Override
    public String toString() {
        return "FunnelTaskPayload{player=" + player + ", session=" + session + "}";
    }
}
