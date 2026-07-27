package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Field;

/** 客户端远程容器存活与 GUI/Container 安全配对兼容层。 */
@SideOnly(Side.CLIENT)
public final class RtsClientRemoteMenuCompat {
    private static final String[] STORAGE_SCREEN_BASE_CLASSES = {
            "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase",
            "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageGuiContainerBase"
    };

    private RtsClientRemoteMenuCompat() {
    }

    public static Container install(Minecraft minecraft, Container menu) {
        if (minecraft == null || minecraft.player == null || menu == null) return menu;

        Container wrapped = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (RtsRemoteMenuCompat.isSupportedRemoteMenu(wrapped)) {
            RtsRemoteMenuCompat.markClientRemoteMenu(wrapped);
        } else {
            RtsRemoteMenuCompat.clearClientRemoteMenu();
        }

        GuiScreen screen = minecraft.currentScreen;
        if (!isScreenMenuPairSafe(screen, wrapped)) {
            throw new IllegalStateException("Incompatible container " + wrapped.getClass().getName()
                    + " for screen " + screen.getClass().getName());
        }

        minecraft.player.openContainer = wrapped;
        remapContainerScreenMenu(screen, wrapped);
        return wrapped;
    }

    /**
     * 1.12 没有 ContainerLevelAccess；距离校验通常落在 Container#canInteractWith
     * 或其持有的 IInventory。前者由远程菜单跟踪/mixin 放宽，后者在不破坏具体
     * Container 类型的前提下包装为始终可用。
     */
    public static void relaxValidation(Container menu) {
        if (menu == null || RtsRemoteMenuCompat.isRemoteMenuPersistenceDisabledForProbe()) return;
        boolean preserveInventoryIdentity = menu instanceof ContainerChest;
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!IInventory.class.isAssignableFrom(field.getType())
                        || !field.getType().isAssignableFrom(AlwaysValidInventory.class)
                        || preserveInventoryIdentity) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object current = field.get(menu);
                    if (current instanceof IInventory && !(current instanceof AlwaysValidInventory)) {
                        field.set(menu, new AlwaysValidInventory((IInventory) current));
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    // 可选模组的 final/受保护字段可能无法替换；容器级跟踪仍会继续工作。
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void remapContainerScreenMenu(GuiScreen screen, Container menu) {
        if (!(screen instanceof GuiContainer) || menu == null) return;
        ((GuiContainer) screen).inventorySlots = menu;
    }

    private static boolean isScreenMenuPairSafe(GuiScreen screen, Container menu) {
        if (screen == null || menu == null) return true;
        String name = screen.getClass().getName();
        if (!name.startsWith("net.p3pp3rf1y.sophisticated")) return true;

        for (String baseClass : STORAGE_SCREEN_BASE_CLASSES) {
            if (isInstanceOf(screen, baseClass)) {
                return RtsRemoteMenuCompat.isStorageContainerMenuBase(menu);
            }
        }
        // 可选模组类在该 1.12 运行环境不存在时不误关原版或其它 GUI。
        return true;
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            return Class.forName(className).isInstance(instance);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** 仅放宽可用距离，其余库存语义完整委托给原实例。 */
    private static final class AlwaysValidInventory implements IInventory {
        private final IInventory delegate;

        private AlwaysValidInventory(IInventory delegate) {
            this.delegate = delegate;
        }

        @Override public int getSizeInventory() { return delegate.getSizeInventory(); }
        @Override public boolean isEmpty() { return delegate.isEmpty(); }
        @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
        @Override public ItemStack decrStackSize(int slot, int amount) {
            return delegate.decrStackSize(slot, amount);
        }
        @Override public ItemStack removeStackFromSlot(int slot) {
            return delegate.removeStackFromSlot(slot);
        }
        @Override public void setInventorySlotContents(int slot, ItemStack stack) {
            delegate.setInventorySlotContents(slot, stack);
        }
        @Override public int getInventoryStackLimit() { return delegate.getInventoryStackLimit(); }
        @Override public void markDirty() { delegate.markDirty(); }
        @Override public boolean isUsableByPlayer(EntityPlayer player) { return true; }
        @Override public void openInventory(EntityPlayer player) { delegate.openInventory(player); }
        @Override public void closeInventory(EntityPlayer player) { delegate.closeInventory(player); }
        @Override public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return delegate.isItemValidForSlot(slot, stack);
        }
        @Override public int getField(int id) { return delegate.getField(id); }
        @Override public void setField(int id, int value) { delegate.setField(id, value); }
        @Override public int getFieldCount() { return delegate.getFieldCount(); }
        @Override public void clear() { delegate.clear(); }
        @Override public String getName() { return delegate.getName(); }
        @Override public boolean hasCustomName() { return delegate.hasCustomName(); }
        @Override public ITextComponent getDisplayName() { return delegate.getDisplayName(); }
    }
}
