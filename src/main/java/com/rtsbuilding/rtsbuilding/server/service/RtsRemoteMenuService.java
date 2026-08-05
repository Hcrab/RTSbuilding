package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.tileentity.TileEntity;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldServer;

/**
 * Forge 1.12.2 远程菜单状态服务。
 *
 * <p>现代版通过替换菜单内部的访问器放宽距离检查；1.12.2 的菜单没有
 * {@code ContainerLevelAccess}。这里保留第三方 {@link Container} 的真实类型，
 * 仅登记精确的玩家 UUID 与 windowId，由容器校验 Mixin 调用
 * {@link RtsRemoteMenuCompat#shouldForceStillValid(Container, net.minecraft.entity.player.EntityPlayer)}
 * 决定是否放宽距离。这样不会把同一玩家之后打开的普通菜单永久放宽。</p>
 */
public final class RtsRemoteMenuService {

    private RtsRemoteMenuService() {
    }

    /**
     * 1.12.2 的实际放宽点位于容器校验 Mixin；此入口负责同步当前菜单标记。
     * 它不能包装第三方容器，否则客户端 GUI 对具体容器类的强制转换会失效。
     */
    public static void relaxOpenedMenuValidation(Container menu) {
        // 标记必须同时具备玩家身份，因此由 markRemoteMenuOpen 完成。
    }

    /**
     * 在真正执行远程右键前准备目标区块。近距离目标保持原版轻量路径；远距离目标由
     * 单区块租约完成服务端强加载和客户端完整 Chunk 同步。
     */
    public static boolean prepareTargetChunk(EntityPlayerMP player, BlockPos pos) {
        return prepareTargetChunk(player, pos, RtsTraceIds.NONE);
    }

    public static boolean prepareTargetChunk(EntityPlayerMP player, BlockPos pos, long traceId) {
        return RtsRemoteMenuChunkLease.prepare(player, pos, traceId);
    }

    /** 远程交互未打开菜单时释放刚准备的区块，避免普通按钮或拉杆长期占票。 */
    public static void releasePreparedTarget(EntityPlayerMP player) {
        releasePreparedTarget(player, RtsTraceIds.NONE, "UNSPECIFIED");
    }

    public static void releasePreparedTarget(EntityPlayerMP player, long traceId, String reason) {
        RtsRemoteMenuChunkLease.release(player, traceId, reason);
    }

    public static void markRemoteMenuOpen(EntityPlayerMP player, RtsStorageSession session,
            Container menu, BlockPos pos) {
        markRemoteMenuOpen(player, session, menu, pos, RtsTraceIds.NONE);
    }

    public static void markRemoteMenuOpen(EntityPlayerMP player, RtsStorageSession session,
            Container menu, BlockPos pos, long traceId) {
        if (menu == null) {
            return;
        }
        Container remoteMenu = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (player != null && player.openContainer != remoteMenu) {
            player.openContainer = remoteMenu;
        }
        if (session != null) {
            session.transfer.remoteMenuContainerId = remoteMenu.windowId;
            session.transfer.remoteMenuTraceId = traceId;
            session.transfer.remoteMenuPos = pos == null ? null : pos.toImmutable();
        }
        if (session != null && RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu, traceId);
            if (RtsTraceIds.isPresent(traceId)) {
                RtsbuildingMod.LOGGER.info(
                        "[RTS-TRACE] side=S event=MENU_MARKED trace={} kind=REMOTE_GUI window={} menu={} target={}",
                        RtsTraceIds.format(traceId), remoteMenu.windowId,
                        remoteMenu.getClass().getName(), pos);
            }
        } else {
            RtsRemoteMenuCompat.clearServerRemoteMenu(player);
            RtsRemoteMenuChunkLease.release(player, traceId, "UNSUPPORTED_MENU");
        }
    }

    public static void clearValidation(EntityPlayerMP player, RtsStorageSession session) {
        clearValidation(player, session, "UNSPECIFIED");
    }

    public static void clearValidation(EntityPlayerMP player, RtsStorageSession session, String reason) {
        long traceId = session == null ? RtsTraceIds.NONE : session.transfer.remoteMenuTraceId;
        int windowId = session == null ? -1 : session.transfer.remoteMenuContainerId;
        if (RtsTraceIds.isPresent(traceId)) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=MENU_CLEARED trace={} kind=REMOTE_GUI window={} reason={}",
                    RtsTraceIds.format(traceId), windowId, reason);
        }
        if (session != null) {
            session.transfer.remoteMenuContainerId = -1;
            session.transfer.remoteMenuTraceId = RtsTraceIds.NONE;
            session.transfer.remoteMenuPos = null;
        }
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
        RtsRemoteMenuChunkLease.release(player, traceId, reason);
    }

    public static void closeTracked(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null) return;
        if (session != null && session.transfer.remoteMenuContainerId >= 0
                && player.openContainer != null
                && player.openContainer.windowId == session.transfer.remoteMenuContainerId
                && player.openContainer != player.inventoryContainer) {
            player.closeContainer();
        }
        clearValidation(player, session);
    }

    /**
     * 在远程交互前通知客户端，并重发目标方块与方块实体的当前状态。
     * 这保持原版 1.12.2 开窗前的同步顺序，避免客户端用陈旧 TE 数据创建 GUI。
     */
    public static void sendRemoteMenuOpenHint(EntityPlayerMP player, BlockPos pos) {
        sendRemoteMenuOpenHint(player, pos, RtsTraceIds.NONE);
    }

    public static void sendRemoteMenuOpenHint(EntityPlayerMP player, BlockPos pos, long traceId) {
        if (player == null || pos == null) {
            return;
        }
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(traceId, pos));
        if (RtsTraceIds.isPresent(traceId)) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=HINT_SENT trace={} kind=REMOTE_GUI target={}",
                    RtsTraceIds.format(traceId), pos);
        }
        WorldServer level = player.getServerForPlayer();
        if (level == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) {
            return;
        }
        player.playerNetServerHandler.sendPacket(new S23PacketBlockChange(
                pos.getX(), pos.getY(), pos.getZ(), level));
        TileEntity blockEntity = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
        if (blockEntity != null) {
            try {
                Packet updatePacket = blockEntity.getDescriptionPacket();
                if (updatePacket != null) {
                    player.playerNetServerHandler.sendPacket(updatePacket);
                }
            } catch (RuntimeException | LinkageError failure) {
                // 第三方 TE 的描述包只是开窗前的增量补强；坏实现不能把正常右键升级为崩服。
                RtsbuildingMod.LOGGER.warn(
                        "[RTS-TRACE] side=S event=TE_SYNC_FAILED trace={} kind=REMOTE_GUI target={} tile={} failure={} fallback=CHUNK_STATE",
                        RtsTraceIds.format(traceId), pos, blockEntity.getClass().getName(),
                        failure.getClass().getName());
            }
        }
    }
}
