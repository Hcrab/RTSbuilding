package com.rtsbuilding.rtsbuilding.server.storage.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.UUID;

/**
 * 解析 {@link RtsStorageSession} 的链接存储边缘。
 *
 * <p>本类负责将会话的链接引用转换为物品/流体处理器、
 * 允许存入权限、显示名称和存储摘要。
 * 它刻意不构建页面、修改物品栏、合成、转移流体、
 * 执行远程挖掘、读写 NBT 或发送数据包。
 * 这些游戏玩法和传输流程仍由 {@link RtsStorageManager} 拥有。
 *
 * <p>解析器必须保留现有的 AE2 网络处理器行为、
 * 普通方块容器能力探测和 NeoForge 能力查询顺序。
 * 它也是未来 Transfer、Fluid 和 Craft 提取的依赖边界，
 * 这些模块应调用此解析器而非直接访问完整的存储管理器。
 *
 * <p>处理器解析和排序已提取到 {@link RtsLinkedHandlerResolutionService}。
 * 方块事件生命周期逻辑已提取到 {@link RtsLinkedStorageBlockEventHandler}。
 * 本类保留访问检查、摘要构建和链接模式规范化逻辑。
 */
public final class RtsLinkedStorageResolver {
    public static final byte LINK_MODE_BIDIRECTIONAL = C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;

    private RtsLinkedStorageResolver() {
    }

    /**
     * 链接显示标签是引用的缓存呈现，因此解析器拥有
     * 摘要和 UI 数据包使用的回退方块名称查询。
     */
    public static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    // ======================================================================
    //  处理器解析（委托给 RtsLinkedHandlerResolutionService）
    // ======================================================================

    /**
     * 将当前所有可访问的物品端点（包括 BD 网络回退）
     * 解析为已强制执行仅提取存储规则的处理器。
     */
    public static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedHandlers(player, session);
    }

    /**
     * 同时解析流体端点和物品端点，确保仅提取链接
     * 不能接受存储的流体，同时仍允许提取。
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedFluidHandlers(player, session);
    }

    // ======================================================================
    //  物品处理器提取辅助（面向高频调用者的外观）
    // ======================================================================

    /**
     * 便捷快捷方式：解析链接处理器并提取裸 {@link IItemHandler} 实例，
     * 按插入顺序排列（高优先级优先）。
     */
    public static List<IItemHandler> itemHandlersForInsert(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForInsert(handlers);
    }

    /**
     * 便捷快捷方式：解析链接处理器并提取裸 {@link IItemHandler} 实例，
     * 按提取顺序排列（低优先级优先）。
     */
    public static List<IItemHandler> itemHandlersForExtract(List<LinkedHandler> handlers) {
        return RtsLinkedHandlerResolutionService.itemHandlersForExtract(handlers);
    }

    // ======================================================================
    //  世界访问 / 可用性 / 摘要
    // ======================================================================

    /**
     * 链接引用是世界目标，因此解析器拥有在解析之前使用的
     * 共享相机、区块、交互和家园半径门控。
     * <p>
     * 同时强制基岩层边界：拒绝任何在世界最小建筑高度
     *（基岩层）或以下的坐标，防止在虚空中进行 RTS 操作。
     */
    public static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        ServerLevel level = player.level();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        // ── Bedrock-layer boundary: reject positions below the world floor ──
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY()) {
            return false;
        }
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!RtsCameraManager.isWithinActionRange(player, pos)) {
            return false;
        }
        return RtsProgressionManager.canAccessHomeRadius(player, pos);
    }

    /**
     * 存储可用性包括普通链接引用和 BD 网络回退，
     * 因为两者都通过此边界解析。
     */
    public static boolean hasAnyStorage(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return false;
        }
        if (!session.linkedStorageInfo.isEmpty()) {
            return true;
        }
        return session.sessionFlags.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player);
    }

    /**
     * UI 摘要描述当前可解析的链接存储源，
     * 因此它与可用性检查保持配对。
     */
    public static String buildAnyStorageSummary(ServerPlayer player, RtsStorageSession session) {
        if (session == null) {
            return "No Storage";
        }
        if (!session.linkedStorageInfo.isEmpty()) {
            return buildLinkedSummary(session);
        }
        if (session.sessionFlags.useBdNetwork && RtsBdCompat.hasPrimaryNetwork(player)) {
            return RtsBdCompat.getNetworkDisplayName(player);
        }
        return "No Storage";
    }

    /**
     * 摘要文本是从链接引用和仅提取模式派生的呈现，
     * 不是页面构建状态。
     */
    public static String buildLinkedSummary(RtsStorageSession session) {
        int count = session.linkedStorageInfo.size();
        if (count <= 0) {
            return "No Storage";
        }
        if (count == 1) {
            LinkedStorageRef ref = session.linkedStorageInfo.get(0);
            String name = session.linkedStorageInfo.getNameOrDefault(ref, "Linked Storage");
            return isExtractOnlyLink(session, ref) ? name + " [Extract]" : name;
        }
        int extractOnly = 0;
        for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
            if (isExtractOnlyLink(session, ref)) {
                extractOnly++;
            }
        }
        if (extractOnly <= 0) {
            return count + " linked storages";
        }
        return count + " linked storages (" + extractOnly + " extract-only)";
    }

    // ======================================================================
    //  会话维度 / 可见性 / 排序
    // ======================================================================

    /**
     * 引用清理属于解析器，这样每次查询都从相同的有效身份集合开始，
     * 而不会触及无关的会话状态。
     */
    public static void sanitizeSessionDimension(ServerPlayer player, RtsStorageSession session) {
        if (session == null || session.linkedStorageInfo.isEmpty()) {
            return;
        }
        session.linkedStorageInfo.removeIf(ref -> ref == null || ref.dimension() == null || ref.pos() == null);
        session.linkedStorageInfo.cleanupOrphans();
    }

    public static boolean isLinkedRefWorldVisible(ServerPlayer player, RtsStorageSession session, LinkedStorageRef ref) {
        if (player == null || session == null || ref == null || ref.pos() == null
                || !player.level().dimension().equals(ref.dimension())
                || session.linkedStorageInfo.isDetached(ref)
                || !player.level().hasChunkAt(ref.pos())) {
            return false;
        }
        UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
        if (backpackUuid != null) {
            return backpackUuid.equals(RtsLinkedStorageBlockEventHandler.readBackpackUuid(player.level(), ref.pos()));
        }
        return !player.level().getBlockState(ref.pos()).isAir();
    }

    // ======================================================================
    //  链接模式规范化
    // ======================================================================

    /**
     * 链接模式规范化被持久化和解析器权限检查重用，
     * 确保保存的数据和运行时处理器不会不一致。
     */
    public static byte sanitizeLinkMode(byte linkMode) {
        return linkMode == LINK_MODE_EXTRACT_ONLY ? LINK_MODE_EXTRACT_ONLY : LINK_MODE_BIDIRECTIONAL;
    }

    /**
     * 仅提取是一种直接控制解析器处理器视图的链接引用权限。
     */
    public static boolean isExtractOnlyLink(RtsStorageSession session, LinkedStorageRef ref) {
        return session != null
                && ref != null
                && sanitizeLinkMode(session.linkedStorageInfo.getMode(ref)) == LINK_MODE_EXTRACT_ONLY;
    }

    public static int sanitizeLinkedStoragePriority(int priority) {
        return net.minecraft.util.Mth.clamp(priority, -9999, 9999);
    }

}
