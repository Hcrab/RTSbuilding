package com.rtsbuilding.rtsbuilding.server.feedback;

import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsDamageFeedbackManager {
    private static final float HEALTH_EPSILON = 0.001F;
    private static final Map<UUID, Float> LAST_HEALTH = new ConcurrentHashMap<>();

    private RtsDamageFeedbackManager() {
    }

    public static void remember(ServerPlayer player) {
        if (player != null) {
            LAST_HEALTH.put(player.getUUID(), player.getHealth());
        }
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            LAST_HEALTH.remove(player.getUUID());
        }
    }

    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }
        float currentHealth = player.getHealth();
        Float previousHealth = LAST_HEALTH.put(player.getUUID(), currentHealth);
        if (previousHealth == null) {
            return;
        }
        float lostHealth = previousHealth - currentHealth;
        if (lostHealth <= HEALTH_EPSILON || !RtsCameraManager.isActive(player)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsDamageFeedbackPayload(lostHealth, currentHealth <= player.getMaxHealth() * 0.5F));
    }
}
