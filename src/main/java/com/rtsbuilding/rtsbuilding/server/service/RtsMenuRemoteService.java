package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

/**
 * 远程菜单服务：管理通过 RTS 模式打开的远程容器 GUI。
 *
 * <p>职责范围：</p>
 * <ul>
 *   <li>远程菜单的生命周期跟踪。</li>
 *   <li>菜单验证松弛化（AlwaysValidContainer）。</li>
 *   <li>兼容性包装（精妙存储、远程菜单等）。</li>
 * </ul>
 */
public final class RtsMenuRemoteService {

    public static final RtsMenuRemoteService INSTANCE = new RtsMenuRemoteService();

    private RtsMenuRemoteService() {
    }

    /**
     * 标记远程菜单已打开。
     */
    public static void markOpen(ServerPlayer player, RtsStorageSession session,
                                 AbstractContainerMenu menu, BlockPos pos) {
        if (menu == null) return;
        AbstractContainerMenu remoteMenu = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (player != null && player.containerMenu != remoteMenu) {
            player.containerMenu = remoteMenu;
        }
        if (session != null) {
            session.transfer.remoteMenuContainerId = remoteMenu.containerId;
            session.transfer.remoteMenuPos = pos == null ? null : pos.immutable();
            relaxMenuValidation(remoteMenu);
            if (RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
                RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu);
            } else {
                RtsRemoteMenuCompat.clearServerRemoteMenu(player);
            }
        }
    }

    /**
     * 清除远程菜单验证状态。
     */
    public static void clearValidation(ServerPlayer player, RtsStorageSession session) {
        if (session != null) {
            session.transfer.remoteMenuContainerId = -1;
            session.transfer.remoteMenuPos = null;
        }
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
    }

    /**
     * 关闭跟踪的远程菜单。
     */
    public static void closeTracked(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.transfer.remoteMenuContainerId < 0) return;
        if (player.containerMenu != null
                && player.containerMenu.containerId == session.transfer.remoteMenuContainerId
                && !(player.containerMenu instanceof InventoryMenu)) {
            player.closeContainer();
        }
        session.transfer.remoteMenuContainerId = -1;
        session.transfer.remoteMenuPos = null;
    }

    /**
     * 发送远程菜单打开提示（客户端方块更新）。
     */
    public static void sendOpenHint(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return;
        PacketDistributor.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(pos));
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) return;
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
        }
    }

    /**
     * 松弛菜单验证（替??ContainerLevelAccess ??Container 为其宽松版本???
     */
    public static void relaxMenuValidation(AbstractContainerMenu menu) {
        if (menu == null) return;
        boolean preserveContainerIdentity = menu instanceof net.minecraft.world.inventory.ChestMenu;
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();
                    if (net.minecraft.world.inventory.ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
                        Object current = field.get(menu);
                        if (current instanceof net.minecraft.world.inventory.ContainerLevelAccess access
                                && !(current instanceof RelaxedContainerLevelAccess)) {
                            field.set(menu, new RelaxedContainerLevelAccess(access));
                        } else if (current == null) {
                            field.set(menu, net.minecraft.world.inventory.ContainerLevelAccess.NULL);
                        }
                    } else if (fieldType == net.minecraft.world.Container.class && !preserveContainerIdentity) {
                        Object current = field.get(menu);
                        if (current instanceof net.minecraft.world.Container delegate
                                && !(current instanceof AlwaysValidContainer)) {
                            field.set(menu, new AlwaysValidContainer(delegate));
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private record RelaxedContainerLevelAccess(net.minecraft.world.inventory.ContainerLevelAccess delegate)
            implements net.minecraft.world.inventory.ContainerLevelAccess {
        @Override
        public <T> java.util.Optional<T> evaluate(java.util.function.BiFunction<net.minecraft.world.level.Level, BlockPos, T> evaluator) {
            java.util.Optional<T> result = delegate.evaluate(evaluator);
            if (result.isPresent() && result.get() instanceof Boolean) {
                @SuppressWarnings("unchecked")
                T forcedTrue = (T) Boolean.TRUE;
                return java.util.Optional.of(forcedTrue);
            }
            return result;
        }

        @Override
        public void execute(java.util.function.BiConsumer<net.minecraft.world.level.Level, BlockPos> consumer) {
            delegate.execute(consumer);
        }
    }

    private record AlwaysValidContainer(net.minecraft.world.Container delegate) implements net.minecraft.world.Container {
        @Override
        public int getContainerSize() { return delegate.getContainerSize(); }
        @Override
        public boolean isEmpty() { return delegate.isEmpty(); }
        @Override
        public net.minecraft.world.item.ItemStack getItem(int slot) { return delegate.getItem(slot); }
        @Override
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { return delegate.removeItem(slot, amount); }
        @Override
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { return delegate.removeItemNoUpdate(slot); }
        @Override
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) { delegate.setItem(slot, stack); }
        @Override
        public int getMaxStackSize() { return delegate.getMaxStackSize(); }
        @Override
        public void setChanged() { delegate.setChanged(); }
        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
        @Override
        public void startOpen(net.minecraft.world.entity.player.Player p) { delegate.startOpen(p); }
        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player p) { delegate.stopOpen(p); }
        @Override
        public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) { return delegate.canPlaceItem(slot, stack); }
        @Override
        public void clearContent() { delegate.clearContent(); }
    }
}
