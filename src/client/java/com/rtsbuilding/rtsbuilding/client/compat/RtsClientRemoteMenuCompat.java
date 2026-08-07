package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

public final class RtsClientRemoteMenuCompat {
  private static final String STORAGE_SCREEN_BASE_CLASS =
      "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase";

  private RtsClientRemoteMenuCompat() {}

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
      throw new IllegalStateException(
          "Incompatible menu "
              + wrapped.getClass().getName()
              + " for screen "
              + minecraft.screen.getClass().getName());
    }
    if (wrapped == menu) {
      return menu;
    }
    minecraft.player.containerMenu = wrapped;
    remapContainerScreenMenu(minecraft.screen, wrapped);
    return wrapped;
  }

  /**
   * 尽可能替换菜单内部的距离校验入口，并返回可写入客户端日志的扫描结果。
   *
   * <p>第三方菜单字段不可访问或链接失败时只跳过该字段，保留原菜单行为。 兼容失败最多导致远程 GUI 关闭，不能让诊断/反射层炸掉客户端。
   */
  public static RelaxationReport relaxValidation(AbstractContainerMenu menu) {
    if (menu == null || RtsRemoteMenuCompat.isRemoteMenuPersistenceDisabledForProbe()) {
      return RelaxationReport.empty();
    }
    boolean preserveContainerIdentity = menu instanceof ChestMenu;
    int scannedFields = 0;
    int accessWrappers = 0;
    int nullAccessReplacements = 0;
    int containerWrappers = 0;
    int skippedFields = 0;
    String firstSkippedField = "";
    Class<?> type = menu.getClass();
    while (type != null && type != Object.class) {
      for (Field field : type.getDeclaredFields()) {
        scannedFields++;
        try {
          field.setAccessible(true);
          Class<?> fieldType = field.getType();

          if (ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
            Object current = field.get(menu);
            if (current instanceof ContainerLevelAccess access
                && !(access instanceof RelaxedContainerLevelAccess)) {
              field.set(menu, new RelaxedContainerLevelAccess(access));
              accessWrappers++;
            } else if (current == null) {
              field.set(menu, ContainerLevelAccess.NULL);
              nullAccessReplacements++;
            }
            continue;
          }

          if (fieldType == Container.class && !preserveContainerIdentity) {
            Object current = field.get(menu);
            if (current instanceof Container delegate
                && !(delegate instanceof AlwaysValidContainer)) {
              field.set(menu, new AlwaysValidContainer(delegate));
              containerWrappers++;
            }
          }
        } catch (ReflectiveOperationException
            | InaccessibleObjectException
            | SecurityException
            | LinkageError ignored) {
          // 单字段失败时回落到原始校验；不要让可选兼容破坏整个 Screen。
          skippedFields++;
          if (firstSkippedField.isBlank()) {
            firstSkippedField =
                type.getName()
                    + "#"
                    + field.getName()
                    + " ("
                    + ignored.getClass().getSimpleName()
                    + ")";
          }
        }
      }
      type = type.getSuperclass();
    }
    return new RelaxationReport(
        scannedFields,
        accessWrappers,
        nullAccessReplacements,
        containerWrappers,
        skippedFields,
        firstSkippedField);
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
      ClassLoader loader =
          instance == null
              ? RtsClientRemoteMenuCompat.class.getClassLoader()
              : instance.getClass().getClassLoader();
      return Class.forName(className, false, loader).isInstance(instance);
    } catch (ClassNotFoundException | LinkageError ignored) {
      // Optional mod client classes can fail to resolve in dev/remapped runtimes.
      // In that case fail open: the compatibility guard must not close vanilla menus.
      return false;
    }
  }

  /** 反射放宽的聚合结果；仅用于诊断和测试，不参与玩家行为判定。 */
  public record RelaxationReport(
      int scannedFields,
      int accessWrappers,
      int nullAccessReplacements,
      int containerWrappers,
      int skippedFields,
      String firstSkippedField) {

    public RelaxationReport {
      firstSkippedField = firstSkippedField == null ? "" : firstSkippedField;
    }

    static RelaxationReport empty() {
      return new RelaxationReport(0, 0, 0, 0, 0, "");
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
