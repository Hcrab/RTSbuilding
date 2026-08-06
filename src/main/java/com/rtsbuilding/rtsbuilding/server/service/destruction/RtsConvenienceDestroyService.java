package com.rtsbuilding.rtsbuilding.server.service.destruction;

import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 把声明式便捷破坏请求接入正式范围破坏任务。
 *
 * <p>客户端只提供模式、锚点和有界设置；服务端重新读取世界并规划目标。工具借用、
 * 领地权限、分 Tick、掉落、储存和撤销仍由 AREA_DESTROY 管道负责。</p>
 */
public final class RtsConvenienceDestroyService {
    public static final RtsConvenienceDestroyService INSTANCE = new RtsConvenienceDestroyService();

    private RtsConvenienceDestroyService() {
    }

    public RtsConvenienceDestroyPlanner.Plan submit(ServerPlayer player,
            RtsConvenienceDestroyMode mode, BlockPos anchor, Direction face,
            RtsConvenienceDestroySettings settings, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean toolProtectionEnabled) {
        if (player == null || anchor == null
                || !RtsProgressionManager.canUse(player, RtsFeature.AREA_DESTROY)
                || !RtsLinkedStorageResolver.canAccessWorldTarget(player, anchor)) {
            return rejected(player, RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, settings);
        }
        RtsConvenienceDestroyPlanner.Plan plan = RtsConvenienceDestroyPlanner.plan(
                player.getLevel(), mode, anchor, face, settings);
        if (!plan.ready()) {
            return rejected(player, plan.code(), settings);
        }
        ServiceRegistry.getInstance().mining().areaDestroy(
                player, plan.targets(), toolSlot,
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled);
        return plan;
    }

    private static RtsConvenienceDestroyPlanner.Plan rejected(ServerPlayer player,
            RtsConvenienceDestroyPlanner.ResultCode code,
            RtsConvenienceDestroySettings rawSettings) {
        if (player != null) {
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
        return new RtsConvenienceDestroyPlanner.Plan(code, java.util.List.of(), 0);
    }
}
