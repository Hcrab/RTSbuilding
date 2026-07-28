package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
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

    public static void markRemoteMenuOpen(EntityPlayerMP player, RtsStorageSession session,
            Container menu, BlockPos pos) {
        if (menu == null) {
            return;
        }
        Container remoteMenu = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (player != null && player.openContainer != remoteMenu) {
            player.openContainer = remoteMenu;
        }
        if (session != null) {
            session.transfer.remoteMenuContainerId = remoteMenu.windowId;
            session.transfer.remoteMenuPos = pos == null ? null : pos.toImmutable();
        }
        if (session != null && RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsRemoteMenuCompat.clearServerRemoteMenu(player);
        }
    }

    public static void clearValidation(EntityPlayerMP player, RtsStorageSession session) {
        if (session != null) {
            session.transfer.remoteMenuContainerId = -1;
            session.transfer.remoteMenuPos = null;
        }
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
    }

    public static void closeTracked(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null || session.transfer.remoteMenuContainerId < 0) {
            return;
        }
        if (player.openContainer != null
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
        if (player == null || pos == null) {
            return;
        }
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(pos));
        WorldServer level = player.getServerWorld();
        if (level == null || !level.isBlockLoaded(pos)) {
            return;
        }
        player.connection.sendPacket(new SPacketBlockChange(level, pos));
        TileEntity blockEntity = level.getTileEntity(pos);
        if (blockEntity != null) {
            SPacketUpdateTileEntity updatePacket = blockEntity.getUpdatePacket();
            if (updatePacket != null) {
                player.connection.sendPacket(updatePacket);
            }
        }
    }
}
