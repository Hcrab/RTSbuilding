package com.rtsbuilding.rtsbuilding.platform.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.ServerConfigurationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 后续版本 PlayerList 的 1.7.10 只读/同步视图。 */
public final class PlayerListCompat {
    private final ServerConfigurationManager delegate;

    PlayerListCompat(ServerConfigurationManager delegate) {
        this.delegate = delegate;
    }

    public List<EntityPlayerMP> getPlayers() {
        ArrayList<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
        if (delegate == null) return players;
        for (Object value : delegate.playerEntityList) {
            if (value instanceof EntityPlayerMP) players.add((EntityPlayerMP) value);
        }
        return players;
    }

    public EntityPlayerMP getPlayerByUUID(UUID playerId) {
        if (playerId == null) return null;
        for (EntityPlayerMP player : getPlayers()) {
            if (playerId.equals(player.getUniqueID())) return player;
        }
        return null;
    }

    public void saveAllPlayerData() {
        if (delegate != null) delegate.saveAllPlayerData();
    }

    public void syncPlayerInventory(EntityPlayerMP player) {
        if (delegate != null && player != null) delegate.syncPlayerInventory(player);
    }
}
