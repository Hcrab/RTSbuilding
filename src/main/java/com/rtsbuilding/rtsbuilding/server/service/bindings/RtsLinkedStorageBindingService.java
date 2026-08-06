package com.rtsbuilding.rtsbuilding.server.service.bindings;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks.RtsBackpackCompat;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageBindings;
import com.rtsbuilding.rtsbuilding.server.storage.handler.RtsLinkedCapabilities;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsEndpointLeaseCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.UUID;

/**
 * 管理链接存储引用的生命周期（添加、切换、更新设置、移除）。
 *
 * <p>该服务处理链接存储引用的会话变更方面：
 * <ul>
 *   <li>添加新的链接存储引用（支持双箱子检测）</li>
 *   <li>切换（点击已链接的存储 = 解绑，再次点击 = 切换模式后重新链接）</li>
 *   <li>更新已有引用的设置（链接模式、优先级）</li>
 *   <li>管理精制背包的 UUID 和物品 ID 元数据</li>
 * </ul>
 *
 * <p>从 {@link RtsStorageBindings} 提取，将链接存储绑定逻辑与快速槽
 * 和 GUI 绑定关注点分离。方块/区块存在性的能力探测和进度门控
 * 仍来自 {@link RtsLinkedCapabilities} 和 {@link RtsLinkedStorageResolver}。
 * 属于 Phase 2 服务解耦的一部分。
 */
public final class RtsLinkedStorageBindingService {

    private RtsLinkedStorageBindingService() {
    }

    // ======================================================================
    //  Link / unlink
    // ======================================================================

    /**
     * 切换或重定向链接存储引用，同时保留现有的仅提取模式行为。
     * 没有物品或流体端点的目标会要求 UI 返回第零页而不保存会话数据。
     */
    public static RtsStorageBindings.UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode) {
        if (player == null || session == null || pos == null) {
            return RtsStorageBindings.UpdateResult.none();
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsClaimProtectionService.canInteractBlock(
                player, pos, Direction.UP, InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        Object itemHandler = RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
        Object fluidHandler = RtsLinkedCapabilities.findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            return RtsStorageBindings.UpdateResult.refreshFirst(false);
        }

        UUID backpackUuid = readBackpackUuid(player.serverLevel(), pos);
        String backpackItemId = readBackpackItemId(player.serverLevel(), pos);
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);

        if (session.linkedStorageInfo.contains(ref)) {
            byte existingMode = session.linkedStorageInfo.getMode(ref);
            if (existingMode == normalizedMode) {
                session.linkedStorageInfo.remove(ref);
            } else {
                session.linkedStorageInfo.setMode(ref, normalizedMode);
                session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
                applyBackpackMetadata(session, ref, backpackUuid, backpackItemId);
            }
        } else {
            // 大箱子检查：如果点击的是双箱子中未链接的一半，且另一半已链接，则执行解绑
            LinkedStorageRef existingRef = findDoubleChestLinkedRef(player, session, pos);
            if (existingRef != null) {
                session.linkedStorageInfo.remove(existingRef);
            } else {
                // AE2/RS 的多个终端可以指向同一网络；会话只保留一个代表端点，
                // 避免页面快照、转移和跨维唤醒重复扫描同一网络。
                if (hasEquivalentNetworkRef(player.serverLevel(), session, pos)) {
                    return RtsStorageBindings.UpdateResult.none();
                }
                if (session.linkedStorageInfo.size() >= Config.maxLinkedStorages()) {
                    return RtsStorageBindings.UpdateResult.none();
                }
                session.linkedStorageInfo.add(ref, normalizedMode, 0, backpackUuid, backpackItemId);
                session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
            }
        }
        // Mark BD network caches as stale so the resolver re-resolves them
        // instead of using the old cached handler (which may reference blocks
        // that were unlinked or changed).
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;
        // 包括 identity=null 的远程背包租约：绑定关系变化是其明确失效来源。
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
        return RtsStorageBindings.UpdateResult.refreshFirst(true);
    }

    /**
     * 更新已有链接存储行的设置。这有意不作为链接/创建操作：
     * 详情面板可以编辑模式和 AE 式优先级，但服务器仍然要求
     * 引用已经属于玩家的会话。
     */
    public static RtsStorageBindings.UpdateResult updateSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        return updateSettings(
                player,
                session,
                player == null ? null : player.serverLevel().dimension(),
                pos,
                linkMode,
                priority);
    }

    /**
     * 修改已存在链接的展示策略，不重新执行世界交互或近距离判定。
     *
     * <p>玩家只能修改自己会话中已有的完整维度+坐标引用；因此该操作既不会访问伪造端点，
     * 也不会把异维正常使用退化成原版近距离交互。</p>
     */
    public static RtsStorageBindings.UpdateResult updateSettings(ServerPlayer player, RtsStorageSession session,
            ResourceKey<Level> dimension, BlockPos pos, byte linkMode, int priority) {
        if (player == null || session == null || dimension == null || pos == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        LinkedStorageRef ref = new LinkedStorageRef(dimension, pos.immutable());
        if (!session.linkedStorageInfo.contains(ref)) {
            return RtsStorageBindings.UpdateResult.none();
        }
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        int normalizedPriority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority);
        byte oldMode = session.linkedStorageInfo.getMode(ref);
        int oldPriority = session.linkedStorageInfo.getPriority(ref);
        if (oldMode == normalizedMode && oldPriority == normalizedPriority) {
            return RtsStorageBindings.UpdateResult.none();
        }
        session.linkedStorageInfo.setMode(ref, normalizedMode);
        session.linkedStorageInfo.setPriority(ref, normalizedPriority);
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel != null && targetLevel.hasChunkAt(pos)) {
            session.linkedStorageInfo.setName(ref, RtsLinkedStorageResolver.resolveDisplayName(targetLevel, ref.pos()));
        }
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
    }

    // ======================================================================
    //  Internal helpers
    // ======================================================================

    /** 批量扫描只用本方法判断端点真实性；它不修改会话，也不加载新区块。 */
    public static boolean canLinkStorageTarget(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || !player.serverLevel().hasChunkAt(pos)
                || !RtsClaimProtectionService.canInteractBlock(
                        player, pos, Direction.UP, InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return false;
        }
        return RtsLinkedCapabilities.findLinkedItemHandler(player, pos) != null
                || RtsLinkedCapabilities.findFluidHandler(player, pos) != null;
    }

    /** 双箱子使用确定性的代表坐标；普通端点原样返回。 */
    public static BlockPos canonicalStoragePosition(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return pos == null ? BlockPos.ZERO : pos.immutable();
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return pos.immutable();
        }
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        return comparePositions(pos, other) <= 0 ? pos.immutable() : other.immutable();
    }

    /**
     * 批量绑定的幂等入口：已有端点保持不变，不复用单击切换的“再次点击即解绑”语义。
     */
    public static RtsStorageBindings.UpdateResult ensureStorageLinked(
            ServerPlayer player, RtsStorageSession session, BlockPos pos, byte linkMode) {
        if (player == null || session == null || pos == null
                || !canLinkStorageTarget(player, pos)) {
            return RtsStorageBindings.UpdateResult.none();
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        if (session.linkedStorageInfo.contains(ref)
                || findDoubleChestLinkedRef(player, session, pos) != null
                || hasEquivalentNetworkRef(player.serverLevel(), session, pos)
                || session.linkedStorageInfo.size() >= Config.maxLinkedStorages()) {
            return RtsStorageBindings.UpdateResult.none();
        }
        UUID backpackUuid = readBackpackUuid(player.serverLevel(), pos);
        String backpackItemId = readBackpackItemId(player.serverLevel(), pos);
        session.linkedStorageInfo.add(
                ref, RtsLinkedStorageResolver.sanitizeLinkMode(linkMode), 0,
                backpackUuid, backpackItemId);
        session.linkedStorageInfo.setName(
                ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), pos));
        session.bdCache.handlerStale = true;
        session.bdCache.fluidHandlerStale = true;
        RtsEndpointLeaseCache.INSTANCE.invalidatePlayer(player.getUUID());
        return RtsStorageBindings.UpdateResult.refreshFirst(true);
    }

    private static int comparePositions(BlockPos left, BlockPos right) {
        int y = Integer.compare(left.getY(), right.getY());
        if (y != 0) return y;
        int x = Integer.compare(left.getX(), right.getX());
        return x != 0 ? x : Integer.compare(left.getZ(), right.getZ());
    }

    private static void removeLinkedRef(RtsStorageSession session, LinkedStorageRef ref) {
        session.linkedStorageInfo.remove(ref);
    }

    private static void applyBackpackMetadata(RtsStorageSession session, LinkedStorageRef ref,
            UUID backpackUuid, String backpackItemId) {
        if (backpackUuid == null) {
            session.linkedStorageInfo.setBackpackUuid(ref, null);
            session.linkedStorageInfo.setBackpackItemId(ref, null);
            session.linkedStorageInfo.removeDetached(ref);
            return;
        }
        session.linkedStorageInfo.setBackpackUuid(ref, backpackUuid);
        session.linkedStorageInfo.setBackpackItemId(ref, backpackItemId);
        session.linkedStorageInfo.removeDetached(ref);
    }

    private static UUID readBackpackUuid(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackUuid(blockEntity).orElse(null);
    }

    private static String readBackpackItemId(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !RtsBackpackCompat.isAvailable()) {
            return "";
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return RtsBackpackCompat.getBackpackItemId(blockEntity).orElse("");
    }

    /** 仅比较本轮已加载端点的网络对象身份，不对普通容器做猜测或 ID 特判。 */
    private static boolean hasEquivalentNetworkRef(
            ServerLevel level, RtsStorageSession session, BlockPos candidate) {
        if (level == null || session == null || candidate == null) {
            return false;
        }
        Object ae2Identity = RtsAe2Compat.batchNetworkIdentity(level, candidate);
        Object refinedStorageIdentity = ae2Identity == null
                ? RtsRefinedStorageCompat.batchNetworkIdentity(level, candidate)
                : null;
        if (ae2Identity == null && refinedStorageIdentity == null) {
            return false;
        }
        for (LinkedStorageRef linked : session.linkedStorageInfo.getAll()) {
            if (linked == null || linked.pos() == null || !level.dimension().equals(linked.dimension())
                    || !level.hasChunkAt(linked.pos())) {
                continue;
            }
            if (ae2Identity != null
                    && ae2Identity == RtsAe2Compat.batchNetworkIdentity(level, linked.pos())) {
                return true;
            }
            if (refinedStorageIdentity != null
                    && refinedStorageIdentity == RtsRefinedStorageCompat.batchNetworkIdentity(level, linked.pos())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查给定的方块位置是否属于双箱子，且其另一半已在会话中链接。
     */
    private static boolean isDoubleChestHalfAlreadyLinked(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return false;
        }
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            return false;
        }
        Direction connectedDirection = ChestBlock.getConnectedDirection(state);
        BlockPos connectedPos = pos.relative(connectedDirection);
        LinkedStorageRef connectedRef = new LinkedStorageRef(level.dimension(), connectedPos);
        return session.linkedStorageInfo.contains(connectedRef);
    }

    /**
     * 查找已链接的相邻箱子半边的引用，如果目标不是双箱子的一部分
     * 或另一半未链接，则返回 null。
     */
    private static LinkedStorageRef findDoubleChestLinkedRef(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) {
            return null;
        }
        ChestType chestType = state.getValue(ChestBlock.TYPE);
        if (chestType == ChestType.SINGLE) {
            return null;
        }
        Direction connectedDirection = ChestBlock.getConnectedDirection(state);
        BlockPos connectedPos = pos.relative(connectedDirection);
        LinkedStorageRef connectedRef = new LinkedStorageRef(level.dimension(), connectedPos);
        if (session.linkedStorageInfo.contains(connectedRef)) {
            return connectedRef;
        }
        return null;
    }
}
