package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 服务端 Tick 末副作用提交器。
 *
 * <p>业务代码只标记页面、工作流或持久化为脏；这里保证同一玩家每 Tick 每类最多提交一次。
 * 当前页面构建会顺带保存浏览状态，因此 PAGE 已包含 PERSISTENCE，不再二次保存。</p>
 */
public final class RtsEffectAccumulator {
    public static final RtsEffectAccumulator INSTANCE = new RtsEffectAccumulator();

    private final CoalescingEffectQueue<PlayerEffectKey> queue = new CoalescingEffectQueue<>();

    private RtsEffectAccumulator() {
    }

    public void markStoragePage(UUID playerId, ResourceKey<Level> dimension) {
        queue.mark(new PlayerEffectKey(playerId, dimension), CoalescingEffectQueue.Kind.STORAGE_PAGE);
    }

    public void markWorkflow(UUID playerId, ResourceKey<Level> dimension) {
        queue.mark(new PlayerEffectKey(playerId, dimension), CoalescingEffectQueue.Kind.WORKFLOW);
    }

    public void markPersistence(UUID playerId, ResourceKey<Level> dimension) {
        queue.mark(new PlayerEffectKey(playerId, dimension), CoalescingEffectQueue.Kind.PERSISTENCE);
    }

    public void flush(MinecraftServer server) {
        for (var pending : queue.drain()) {
            var key = pending.key();
            var player = server.getPlayerList().getPlayer(key.playerId());
            if (player == null || !player.level().dimension().equals(key.dimension())) continue;
            var session = RtsSessionService.getIfPresent(player);
            if (session == null) continue;

            boolean page = pending.kinds().contains(CoalescingEffectQueue.Kind.STORAGE_PAGE);
            if (page && RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
                RtsPageService.requestPage(player, session.browser.page, session.browser.search,
                        session.browser.category, session.browser.sort, session.browser.ascending);
            } else if (pending.kinds().contains(CoalescingEffectQueue.Kind.PERSISTENCE)) {
                RtsSessionService.saveToPlayerNbt(player, session);
            }
            if (pending.kinds().contains(CoalescingEffectQueue.Kind.WORKFLOW)) {
                RtsWorkflowEngine.getInstance().flushPlayerNow(key.playerId(), key.dimension());
            }
        }
    }

    public record PlayerEffectKey(UUID playerId, ResourceKey<Level> dimension) {
    }
}
