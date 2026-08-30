package com.rtsbuilding.rtsbuilding.server.task.effect;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/**
 * Effect Ledger 到现有 Minecraft 服务的唯一生产适配器。
 *
 * <p>每一类投影独立提交和确认：某个网络快照失败不会迫使已经成功的 Session staging 重做，
 * 也不会吞掉同目标尚未提交的其它类型。这里不做物品、世界或 Capability 写入。</p>
 */
public final class RtsProductionEffectCommitter
        implements RtsEffectCommitter<RtsPlayerEffectTarget> {
    private final MinecraftServer server;

    public RtsProductionEffectCommitter(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public RtsEffectCommitResult commit(RtsPlayerEffectTarget target, RtsEffectSet effects) {
        ServerPlayer player = server.getPlayerList().getPlayer(target.playerId());
        if (player == null || player.isRemoved()) {
            // 登出路径会直接完成最终 Session/Workflow 保存；离线网络投影已经失去接收者。
            return RtsEffectCommitResult.all(effects);
        }
        if (!target.isGlobal()
                && !player.level().dimension().location().toString().equals(target.dimensionId())) {
            // 旧维度页面和 Workflow 投影不能发送到新维度，也不应永久占据重试队列。
            return RtsEffectCommitResult.all(effects);
        }

        RtsEffectSet acknowledged = RtsEffectSet.empty();
        for (RtsEffectKind kind : RtsEffectKind.values()) {
            if (!effects.contains(kind)) continue;
            try {
                commitOne(player, kind);
                acknowledged = acknowledged.union(RtsEffectSet.of(kind));
            } catch (RuntimeException failure) {
                RtsbuildingMod.LOGGER.error(
                        "提交玩家 {} 的副作用 {} 失败，已保留到后续 tick 重试",
                        player.getUUID(), kind, failure);
            }
        }
        return new RtsEffectCommitResult(acknowledged);
    }

    private static void commitOne(ServerPlayer player, RtsEffectKind kind) {
        var registry = ServiceRegistry.getInstance();
        var session = registry.session().getIfPresent(player);
        switch (kind) {
            case SESSION_PERSISTENCE -> {
                if (session != null) {
                    registry.session().saveToPlayerNbt(player, session);
                    RtsDeveloperMetrics.recordSessionSnapshot(player);
                }
            }
            case STORAGE_VIEW_DIRTY -> {
                if (session != null && RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
                    registry.page().markStorageViewDirty(player, session);
                }
            }
            case WORKFLOW_SNAPSHOT -> {
                RtsWorkflowEngine.getInstance().flushPlayerNow(
                        player.getUUID(), player.level().dimension());
                RtsDeveloperMetrics.recordWorkflowSnapshot(player);
            }
            case HISTORY_SNAPSHOT -> {
                ServerHistoryManager.sendSyncNow(player);
                RtsDeveloperMetrics.recordHistorySnapshot(player);
            }
            case PLUGIN_STATE_SNAPSHOT -> {
                RtsPluginService.syncToPlayerNow(player);
                RtsDeveloperMetrics.recordPluginSnapshot(player);
            }
            case PROGRESSION_STATE_SNAPSHOT -> {
                RtsProgressionManager.syncToPlayerNow(player);
                RtsDeveloperMetrics.recordProgressionSnapshot(player);
            }
        }
    }
}
