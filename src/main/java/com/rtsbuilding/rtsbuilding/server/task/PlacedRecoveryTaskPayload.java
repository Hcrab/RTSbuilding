package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Objects;

/** 已放置方块回收任务载荷；实际物品仍由世界 ItemEntity 持有直到原子插入。 */
public final class PlacedRecoveryTaskPayload implements TaskPayload {
    private final EntityPlayerMP player;
    private final RtsStorageSession session;

    public PlacedRecoveryTaskPayload(EntityPlayerMP player, RtsStorageSession session) {
        this.player = player;
        this.session = session;
    }

    public EntityPlayerMP player() { return player; }
    public RtsStorageSession session() { return session; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof PlacedRecoveryTaskPayload)) return false;
        PlacedRecoveryTaskPayload other = (PlacedRecoveryTaskPayload) object;
        return Objects.equals(player, other.player) && Objects.equals(session, other.session);
    }

    @Override
    public int hashCode() { return Objects.hash(player, session); }

    @Override
    public String toString() {
        return "PlacedRecoveryTaskPayload{player=" + player + ", session=" + session + "}";
    }
}
