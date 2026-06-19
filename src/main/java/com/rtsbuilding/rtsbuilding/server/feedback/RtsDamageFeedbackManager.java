package com.rtsbuilding.rtsbuilding.server.feedback;

import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 伤害反馈管理器。负责追踪服务器端玩家的血量变化，
 * 当检测到玩家受到伤害时，向客户端发送伤害反馈数据包，
 * 用于在 HUD 上显示受伤提示效果。
 */
public final class RtsDamageFeedbackManager {
    /** 血量变化的最小阈值，低于此值忽略，避免微小波动触发反馈 */
    private static final float HEALTH_EPSILON = 0.001F;

    /** 玩家 UUID → 上一次记录的生命值，用于比对血量变化 */
    private static final Map<UUID, Float> LAST_HEALTH = new ConcurrentHashMap<>();

    private RtsDamageFeedbackManager() {
    }

    /**
     * 记录指定玩家的当前生命值，作为后续比对基准。
     * 通常在玩家刚进入 RTS 相机模式时调用。
     *
     * @param player 要记录的服务端玩家
     */
    public static void remember(ServerPlayer player) {
        if (player != null) {
            LAST_HEALTH.put(player.getUUID(), player.getHealth());
        }
    }

    /**
     * 移除指定玩家的生命值记录，停止对其的伤害追踪。
     * 通常在玩家退出 RTS 相机模式时调用。
     *
     * @param player 要停止追踪的服务端玩家
     */
    public static void forget(ServerPlayer player) {
        if (player != null) {
            LAST_HEALTH.remove(player.getUUID());
        }
    }

    /**
     * 每 tick 检查一次玩家的血量变化。若掉血量超过阈值且玩家处于
     * RTS 相机模式下，则构造伤害反馈数据包发送给客户端。
     *
     * @param player 要检查的服务端玩家
     */
    public static void tick(ServerPlayer player) {
        if (player == null) {
            return;
        }

        float currentHealth = player.getHealth();
        // 更新记录并获取上一次的生命值
        Float previousHealth = LAST_HEALTH.put(player.getUUID(), currentHealth);
        if (previousHealth == null) {
            // 首次记录，尚无上次值可供比对，跳过
            return;
        }

        float lostHealth = previousHealth - currentHealth;
        // 掉血量不足阈值，或者玩家不在 RTS 相机模式下，不发送反馈
        if (lostHealth <= HEALTH_EPSILON || !RtsCameraManager.isActive(player)) {
            return;
        }

        // 发送伤害反馈数据包，同时附带「是否处于低血量（≤50%）」的标志
        PacketDistributor.sendToPlayer(player, new S2CRtsDamageFeedbackPayload(lostHealth, currentHealth <= player.getMaxHealth() * 0.5F));
    }
}
