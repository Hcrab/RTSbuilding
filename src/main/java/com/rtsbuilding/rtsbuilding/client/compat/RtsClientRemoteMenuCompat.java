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

/**
 * 客户端远程菜单兼容层。
 *
 * <p>负责在容器菜单打开时安装 RTS 远程菜单包装器，
 * 并通过反射放宽容器有效性校验，使远程菜单在 RTS 模式下正常工作。</p>
 *
 * <p>移植自 {@code client_old/compat/RtsClientRemoteMenuCompat}。</p>
 */
public final class RtsClientRemoteMenuCompat {
    private static final String STORAGE_SCREEN_BASE_CLASS = "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase";

    private RtsClientRemoteMenuCompat() {
    }

    /**
     * 安装远程菜单包装器。
     * <p>将玩家当前的 containerMenu 包装为远程兼容版本，
     * 并更新屏幕中对菜单的内部引用。</p>
     *
     * @param minecraft Minecraft 实例
     * @param menu      当前容器菜单
     * @return 包装后的菜单（可能为原实例）
     */
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

    /**
     * 放宽容器菜单的校验限制。
     * <p>通过反射将菜单中的 {@link ContainerLevelAccess} 替换为宽松版本，
     * 使得远程容器在 RTS 模式下仍然通过 {@code stillValid} 检查。</p>
     *
     * @param menu 需要放宽校验的容器菜单
     */
    public static void relaxValidation(AbstractContainerMenu menu) {
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
                    // 某些运行时/终态字段无法通过反射修改
                }
            }
            type = type.getSuperclass();
        }
    }

    /**
     * 重新映射容器屏幕中的菜单引用。
     * <p>包装后的菜单实例发生变化时，需同步更新 Screen 中内部引用的菜单字段。</p>
     */
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
                    // 某些运行时/终态字段无法通过反射修改
                }
            }
            type = type.getSuperclass();
        }
    }

    /**
     * 检查屏幕与菜单组合是否安全。
     * <p>Sophisticated* 模组的界面基类对菜单类型有严格约束，
     * 此处仅在确认类型兼容时放行，否则返回 false 表示不安全。</p>
     */
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
            // 可选的模组客户端类可能在 dev/remapped 运行时解析失败，
            // 此时放行：兼容性守卫不能关闭原版菜单
            return false;
        }
    }

    /**
     * 始终有效的容器委托——覆盖 {@link Container#stillValid} 始终返回 true。
     * <p>远程容器在 RTS 模式下距离玩家很远，原版校验会失败。
     * 此委托确保所有调用转发给原始容器，唯独 {@code stillValid} 强制返回 true。</p>
     */
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
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.startOpen(player);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.stopOpen(player);
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

    /**
     * 宽松的 {@link ContainerLevelAccess} 委托。
     * <p>对于返回布尔值的 {@code evaluate} 调用，强制返回 {@code true}，
     * 使远程容器的距离校验通过。其他类型的结果透明转发。</p>
     */
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
