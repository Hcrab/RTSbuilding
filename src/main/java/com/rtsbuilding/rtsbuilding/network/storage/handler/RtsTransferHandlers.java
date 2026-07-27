package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFillInventoryPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/**
 * 链接储存传输的 1.12 网络边界。这里只验证连接身份和请求边界；真实提取栈、插入余量、
 * 退款及容器广播始终由 TransferService 持有，避免在消息层复制并丢失 NBT 或 mutation。
 */
public final class RtsTransferHandlers {
    private static final String CAMERA = "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String REGISTRY = "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private RtsTransferHandlers() {}

    public static final class FillInventory implements IMessageHandler<C2SRtsFillInventoryPayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsFillInventoryPayload message, MessageContext context) {
            schedule(context, new Action() { @Override public void run(EntityPlayerMP player) {
                callTransfer("fillPlayerInventoryFromLinked", new Class<?>[]{EntityPlayerMP.class}, player);
            }}); return null;
        }
    }
    public static final class LinkedPickup implements IMessageHandler<C2SRtsLinkedPickupPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsLinkedPickupPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() { @Override public void run(EntityPlayerMP player) {
                callTransfer("pickupLinkedToCarried",
                        new Class<?>[]{EntityPlayerMP.class, ItemStack.class, int.class},
                        player, message.prototype(), message.amount());
            }}); return null;
        }
    }
    public static final class LinkedQuickMove implements IMessageHandler<C2SRtsLinkedQuickMovePayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsLinkedQuickMovePayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() { @Override public void run(EntityPlayerMP player) {
                callTransfer("quickMoveLinkedItem", new Class<?>[]{EntityPlayerMP.class, ItemStack.class},
                        player, message.prototype());
            }}); return null;
        }
    }
    public static final class ReturnCarried implements IMessageHandler<C2SRtsReturnCarriedPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsReturnCarriedPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() { @Override public void run(EntityPlayerMP player) {
                callTransfer("returnCarriedToLinked",
                        new Class<?>[]{EntityPlayerMP.class, String.class, int.class},
                        player, message.itemId(), message.amount());
            }}); return null;
        }
    }
    public static final class ImportMenuSlot implements IMessageHandler<C2SRtsImportMenuSlotPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsImportMenuSlotPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() { @Override public void run(EntityPlayerMP player) {
                Container menu = player.openContainer;
                if (menu == null || menu.windowId == 0 || message.menuSlot() >= menu.inventorySlots.size()) return;
                callTransfer("importMenuSlotToLinked", new Class<?>[]{EntityPlayerMP.class, int.class},
                        player, message.menuSlot());
            }}); return null;
        }
    }

    private interface Action { void run(EntityPlayerMP player); }
    private static void schedule(MessageContext context, final Action action) {
        final EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() { @Override public void run() {
            if (active(player)) action.run(player);
        }});
    }
    private static boolean active(EntityPlayerMP player) {
        try { return Boolean.TRUE.equals(Class.forName(CAMERA).getMethod("isActive", EntityPlayerMP.class)
                .invoke(null, player)); }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 storage camera adapter unavailable", exception);
        } catch (InvocationTargetException exception) { throw propagate("Storage camera check failed", exception); }
    }
    private static void callTransfer(String method, Class<?>[] types, Object... arguments) {
        try {
            Class<?> registryType = Class.forName(REGISTRY);
            Object registry = registryType.getMethod("getInstance").invoke(null);
            Object transfer = registryType.getMethod("transfer").invoke(registry);
            transfer.getClass().getMethod(method, types).invoke(transfer, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 transfer service adapter unavailable: " + method, exception);
        } catch (InvocationTargetException exception) { throw propagate("Transfer service failed", exception); }
    }
    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(message, cause);
    }
}
