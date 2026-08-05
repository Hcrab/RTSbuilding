package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Remote menu management service — handles validation bypass and state tracking for remotely opened container menus in RTS mode.
 *
 * <p>In RTS camera mode, the player is far away from container blocks in the world.
 * Vanilla {@code Container.stillValid()} and {@code ContainerLevelAccess.evaluate()}
 * would close the menu due to distance validation failure. This service replaces these validation components via reflection,
 * allowing remote menus to remain open.
 *
 * <p><b>Core methods:</b>
 * <ul>
 *   <li>{@link #relaxOpenedMenuValidation(AbstractContainerMenu)} —
 *       Scans the menu's {@link Container} and {@link ContainerLevelAccess} fields via reflection,
 *       replacing them with always-valid wrappers ({@link AlwaysValidContainer}, {@link RelaxedContainerLevelAccess})</li>
 *   <li>{@link #markRemoteMenuOpen(ServerPlayer, RtsStorageSession, AbstractContainerMenu, BlockPos)} —
 *       Records the remote menu's container ID and position, marks server-side remote menu state</li>
 *   <li>{@link #clearValidation(ServerPlayer, RtsStorageSession)} — Clears remote menu state</li>
 *   <li>{@link #closeTracked(ServerPlayer, RtsStorageSession)} — Closes the tracked remote menu</li>
 *   <li>{@link #sendRemoteMenuOpenHint(ServerPlayer, BlockPos)} —
 *       Sends remote menu open hint packet, refreshes client-side block state and block entity data</li>
 * </ul>
 *
 * <p><b>Internal wrappers:</b>
 * <ul>
 *   <li>{@link AlwaysValidContainer} — {@code stillValid()} always returns {@code true}</li>
 *   <li>{@link RelaxedContainerLevelAccess} — {@code evaluate()} forces {@code Boolean} results to return {@code true}</li>
 * </ul>
 *
 * <p><b>Design features:</b>
 * <ul>
 *   <li>Iterates through menu class and all its parent class fields via reflection, compatible with various mod menus</li>
 *   <li>ChestMenu preserves its original Container identity (preserveContainerIdentity=true)</li>
 *   <li>Silently ignores inaccessible or final fields during reflection access, does not break menu functionality</li>
 * </ul>
 */
public final class RtsRemoteMenuService {

    private RtsRemoteMenuService() {
    }

    public static void relaxOpenedMenuValidation(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        boolean preserveContainerIdentity = menu instanceof ChestMenu;
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();

                    if (ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
                        Object current = field.get(menu);
                        if (current instanceof ContainerLevelAccess access
                                && !(access instanceof RelaxedContainerLevelAccess)) {
                            field.set(menu, new RelaxedContainerLevelAccess(access));
                        } else if (current == null) {
                            field.set(menu, ContainerLevelAccess.NULL);
                        }
                        continue;
                    }

                    if (fieldType == Container.class && !preserveContainerIdentity) {
                        Object current = field.get(menu);
                        if (current instanceof Container delegate && !(delegate instanceof AlwaysValidContainer)) {
                            field.set(menu, new AlwaysValidContainer(delegate));
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // If a field is inaccessible/final in this runtime, keep default validation for that field.
                }
            }
            type = type.getSuperclass();
        }
    }

    public static void markRemoteMenuOpen(ServerPlayer player, RtsStorageSession session, AbstractContainerMenu menu, BlockPos pos) {
        if (menu == null) {
            return;
        }
        AbstractContainerMenu remoteMenu = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (player != null && player.containerMenu != remoteMenu) {
            player.containerMenu = remoteMenu;
        }
        if (session != null) {
            session.transfer.remoteMenuContainerId = remoteMenu.containerId;
            session.transfer.remoteMenuPos = pos == null ? null : pos.immutable();
        }
        relaxOpenedMenuValidation(remoteMenu);
        if (session != null && RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsRemoteMenuCompat.clearServerRemoteMenu(player);
        }
    }

    public static void clearValidation(ServerPlayer player, RtsStorageSession session) {
        if (session != null) {
            session.transfer.remoteMenuContainerId = -1;
            session.transfer.remoteMenuPos = null;
        }
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
    }

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

    public static void sendRemoteMenuOpenHint(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(pos));
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) {
            return;
        }
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
        }
    }

    private static final class AlwaysValidContainer implements Container {
        private final Container delegate;

        private AlwaysValidContainer(Container delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getContainerSize() {
            return this.delegate.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.delegate.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return this.delegate.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return this.delegate.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.delegate.setItem(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.delegate.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            this.delegate.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.startOpen(player);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.stopOpen(player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return this.delegate.canPlaceItem(slot, stack);
        }

        @Override
        public void clearContent() {
            this.delegate.clearContent();
        }
    }

    private static final class RelaxedContainerLevelAccess implements ContainerLevelAccess {
        private final ContainerLevelAccess delegate;

        private RelaxedContainerLevelAccess(ContainerLevelAccess delegate) {
            this.delegate = delegate == null ? ContainerLevelAccess.NULL : delegate;
        }

        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
            Optional<T> result = this.delegate.evaluate(evaluator);
            if (result.isPresent() && result.get() instanceof Boolean) {
                @SuppressWarnings("unchecked")
                T forcedTrue = (T) Boolean.TRUE;
                return Optional.of(forcedTrue);
            }
            return result;
        }

        @Override
        public void execute(BiConsumer<Level, BlockPos> consumer) {
            this.delegate.execute(consumer);
        }
    }
}
