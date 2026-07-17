package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class RtsClientRemoteMenuCompat {
    private static final String STORAGE_SCREEN_BASE_CLASS = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase";

    private RtsClientRemoteMenuCompat() {
    }

    public static AbstractContainerMenu install(Minecraft minecraft, AbstractContainerMenu menu) {
        if (minecraft == null || minecraft.player == null || menu == null) {
            return menu;
        }
        AbstractContainerMenu wrapped = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (RtsRemoteMenuCompat.isSupportedRemoteMenu(wrapped)) {
            RtsRemoteMenuCompat.markClientRemoteMenu(wrapped);
        } else {
            RtsRemoteMenuCompat.clearClientRemoteMenu();
        }
        if (!isScreenMenuPairSafe(minecraft.screen, wrapped)) {
            throw new IllegalStateException("Incompatible menu " + wrapped.getClass().getName()
                    + " for screen " + minecraft.screen.getClass().getName());
        }
        if (wrapped == menu) {
            return menu;
        }
        minecraft.player.containerMenu = wrapped;
        remapContainerScreenMenu(minecraft.screen, wrapped);
        return wrapped;
    }

    public static void relaxValidation(AbstractContainerMenu menu) {
        if (menu == null || RtsRemoteMenuCompat.isRemoteMenuPersistenceDisabledForProbe()) {
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
                    // Some runtime-specific/final fields cannot be patched reflectively.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void remapContainerScreenMenu(Screen screen, AbstractContainerMenu menu) {
        if (screen == null || menu == null) {
            return;
        }
        Class<?> type = screen.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!AbstractContainerMenu.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    field.set(screen, menu);
                    return;
                } catch (ReflectiveOperationException ignored) {
                    // Some runtime-specific/final fields cannot be patched reflectively.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static boolean isScreenMenuPairSafe(Screen screen, AbstractContainerMenu menu) {
        if (screen == null || menu == null) {
            return true;
        }
        String screenClassName = screen.getClass().getName();
        if (!screenClassName.startsWith("net.p3pp3rf1y.sophisticated")) {
            return true;
        }
        if (!isInstanceOf(screen, STORAGE_SCREEN_BASE_CLASS)) {
            return true;
        }
        return RtsRemoteMenuCompat.isStorageContainerMenuBase(menu);
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            return Class.forName(className).isInstance(instance);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Optional mod client classes can fail to resolve in dev/remapped runtimes.
            // In that case fail open: the compatibility guard must not close vanilla menus.
            return false;
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
        public net.minecraft.world.item.ItemStack getItem(int slot) {
            return this.delegate.getItem(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) {
            return this.delegate.removeItem(slot, amount);
        }

        @Override
        public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) {
            return this.delegate.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
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
        public void startOpen(net.minecraft.world.entity.ContainerUser user) {
            this.delegate.startOpen(user);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            this.delegate.stopOpen(user);
        }

        @Override
        public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack stack) {
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
