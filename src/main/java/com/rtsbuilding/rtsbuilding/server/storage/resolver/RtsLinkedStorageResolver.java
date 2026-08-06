package com.rtsbuilding.rtsbuilding.server.storage.resolver;

import com.rtsbuilding.rtsbuilding.compat.bd.RtsBdCompat;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.wake.RtsCrossDimensionStorageWakeService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandler;

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
    public static String resolveDisplayName(WorldServer level, BlockPos pos) {
        return level == null || pos == null ? "Linked Storage"
                : level.getBlockState(pos).getBlock().getLocalizedName();
    }

    // ======================================================================
    //  处理器解析（委托给 RtsLinkedHandlerResolutionService）
    // ======================================================================

    /**
     * 将当前所有可访问的物品端点（包括 BD 网络回退）
     * 解析为已强制执行仅提取存储规则的处理器。
     */
    public static List<LinkedHandler> resolveLinkedHandlers(EntityPlayerMP player, RtsStorageSession session) {
        return RtsLinkedHandlerResolutionService.resolveLinkedHandlers(player, session);
    }

    /**
     * 同时解析流体端点和物品端点，确保仅提取链接
     * 不能接受存储的流体，同时仍允许提取。
     */
    public static List<LinkedFluidHandler> resolveLinkedFluidHandlers(EntityPlayerMP player, RtsStorageSession session) {
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
     * 共享相机会话、区块、交互权限和本次操作半径门控。
     * <p>
     * 同时强制基岩层边界：拒绝任何在世界最小建筑高度
     *（基岩层）或以下的坐标，防止在虚空中进行 RTS 操作。
     */
    public static boolean canAccessWorldTarget(
            net.minecraft.entity.player.EntityPlayerMP player,
            net.minecraft.util.math.BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }

        net.minecraft.world.WorldServer level = player.getServerWorld();
        if (!level.isBlockLoaded(pos)) {
            return false;
        }
        // ── Bedrock-layer boundary: reject positions below the world floor ──
        if (pos.getY() < 0 || pos.getY() >= level.getHeight()) {
            return false;
        }
        if (!level.isBlockModifiable(player, pos)) {
            return false;
        }
        return RtsCameraManager.isWithinActionRange(player, pos);
    }

    /**
     * 访问一个已属于当前会话的储存引用。
     *
     * <p>链接端点在绑定时已经由玩家明确选定；后续解析只验证会话归属、端点所在
     * 区块是否可用、端点身份、世界边界和互动权限，不再把当前 RTS 镜头的动作射程
     * 作为第二次门槛。异维端点仍必须通过服务器开关、插件门禁和短期区块唤醒，
     * 这样远程存储保持为玩家已建立的能力，而不是随镜头离开基地而失效。</p>
     */
    public static boolean canAccessLinkedRef(EntityPlayerMP player, RtsStorageSession session,
            LinkedStorageRef ref, WorldServer targetLevel) {
        if (player == null || session == null || ref == null || ref.pos() == null || targetLevel == null
                || !session.linkedStorageInfo.contains(ref) || session.linkedStorageInfo.isDetached(ref)
                || !RtsCameraManager.isActive(player)) {
            return false;
        }
        boolean sameDimension = player.dimension == ref.dimension();
        if (!sameDimension && !isCrossDimensionStorageAllowed(player)) {
            return false;
        }
        BlockPos pos = ref.pos();
        boolean ready = sameDimension
                ? targetLevel.isBlockLoaded(pos)
                : RtsCrossDimensionStorageWakeService.INSTANCE.ensureReady(player, targetLevel, pos);
        if (!ready || pos.getY() < 0 || pos.getY() >= targetLevel.getHeight()) {
            return false;
        }
        if (!RtsClaimProtectionService.canInteractBlockInWorld(
                player, targetLevel, pos, EnumFacing.UP, EnumHand.MAIN_HAND, ItemStack.EMPTY)) {
            return false;
        }
        return true;
    }

    public static boolean isCrossDimensionStorageAllowed(EntityPlayerMP player) {
        return player != null && Config.isCrossDimensionStorageEnabled()
                && RtsProgressionManager.canUse(player, RtsFeature.CROSS_DIMENSION_STORAGE);
    }

    /**
     * 存储可用性包括普通链接引用和 BD 网络回退，
     * 因为两者都通过此边界解析。
     */
    public static boolean hasAnyStorage(EntityPlayerMP player, RtsStorageSession session) {
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
    public static String buildAnyStorageSummary(EntityPlayerMP player, RtsStorageSession session) {
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
    public static void sanitizeSessionDimension(EntityPlayerMP player, RtsStorageSession session) {
        if (session == null || session.linkedStorageInfo.isEmpty()) {
            return;
        }
        session.linkedStorageInfo.removeIf(ref -> ref == null || ref.pos() == null);
        session.linkedStorageInfo.cleanupOrphans();
    }

    public static boolean isLinkedRefWorldVisible(EntityPlayerMP player, RtsStorageSession session, LinkedStorageRef ref) {
        if (player == null || player.getServer() == null || ref == null) {
            return false;
        }
        WorldServer level = player.getServer().getWorld(ref.dimension());
        if (!canAccessLinkedRef(player, session, ref, level)) return false;
        UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
        if (backpackUuid != null) {
            return backpackUuid.equals(RtsLinkedStorageBlockEventHandler.readBackpackUuid(level, ref.pos()));
        }
        return !level.isAirBlock(ref.pos());
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

    /**
     * 最终写入边界使用的实时权限检查。
     * 引用已解绑、会话缺失或模式为 Extract Only 时都拒绝写入，避免旧 handler 缓存重新放开权限。
     */
    public static boolean isStoreAllowed(RtsStorageSession session, LinkedStorageRef ref) {
        return session != null
                && ref != null
                && session.linkedStorageInfo.contains(ref)
                && !isExtractOnlyLink(session, ref);
    }

    public static int sanitizeLinkedStoragePriority(int priority) {
        return net.minecraft.util.math.MathHelper.clamp(priority, -9999, 9999);
    }

}
