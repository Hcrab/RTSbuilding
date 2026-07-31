package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 把声明式便捷破坏请求接入现有范围拆除任务。
 *
 * <p>本类只拥有“服务端重新规划并提交”这一条边界；工具借用、插件权限、分 Tick、掉落、
 * 储存和 Ctrl+Z 仍由正式 AREA_DESTROY 管道负责，避免出现第二套破坏实现。</p>
 */
public final class RtsConvenienceDestroyService {
    public static final RtsConvenienceDestroyService INSTANCE = new RtsConvenienceDestroyService();

    private RtsConvenienceDestroyService() {
    }

    public RtsConvenienceDestroyPlanner.Plan submit(ServerPlayer player,
            RtsConvenienceDestroyMode mode, BlockPos anchor, Direction face,
            RtsConvenienceDestroySettings settings, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        if (player == null) {
            return new RtsConvenienceDestroyPlanner.Plan(
                    RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, java.util.List.of(), 0);
        }
        // 在扫描盒体、区块或树群之前先做权限门禁，避免未解锁客户端借请求制造无意义的世界遍历。
        if (!RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)) {
            return new RtsConvenienceDestroyPlanner.Plan(
                    RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, java.util.List.of(), 0);
        }
        RtsConvenienceDestroyPlanner.Plan plan = RtsConvenienceDestroyPlanner.plan(
                player.serverLevel(), mode, anchor, face, settings);
        if (!plan.ready()) {
            notifyRejected(player, plan.code(), settings);
            return plan;
        }
        ServiceRegistry.getInstance().mining().areaDestroy(
                player, plan.targets(), toolSlot,
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled);
        return plan;
    }

    private static void notifyRejected(ServerPlayer player,
            RtsConvenienceDestroyPlanner.ResultCode code,
            RtsConvenienceDestroySettings rawSettings) {
        String key = switch (code) {
            case OVER_LIMIT -> "message.rtsbuilding.convenience_destroy.over_limit";
            case UNLOADED_CHUNK -> "message.rtsbuilding.convenience_destroy.unloaded";
            case EMPTY -> "message.rtsbuilding.convenience_destroy.empty";
            default -> "message.rtsbuilding.convenience_destroy.invalid";
        };
        Component message = code == RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT
                ? Component.translatable(key,
                        RtsConvenienceDestroyPlanner.sanitize(rawSettings).treeMaxBlocks())
                : Component.translatable(key);
        player.displayClientMessage(message, true);
    }
}
