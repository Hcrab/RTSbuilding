package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFillInventoryPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsBulkStorageOpPayload;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/**
 * 链接储存传输的 1.12 网络边界。这里只验证连接身份和请求边界；真实提取栈、插入余量、
 * 退款及容器广播始终由 TransferService 持有，避免在消息层复制并丢失 NBT 或 mutation。
 */
public final class RtsTransferHandlers {
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
            scheduleCarriedTransfer(context, new Action() { @Override public void run(EntityPlayerMP player) {
                logReceived(player, "PICKUP", -1, message.prototype(), message.amount());
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
                logReceived(player, "QUICK_MOVE", -1, message.prototype(), message.prototype().getCount());
                callTransfer("quickMoveLinkedItem", new Class<?>[]{EntityPlayerMP.class, ItemStack.class},
                        player, message.prototype());
            }}); return null;
        }
    }
    public static final class ReturnCarried implements IMessageHandler<C2SRtsReturnCarriedPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsReturnCarriedPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            scheduleCarriedTransfer(context, new Action() { @Override public void run(EntityPlayerMP player) {
                logReceived(player, "DEPOSIT", -1, player.inventory.getItemStack(), message.amount());
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
                // 玩家背包的 ContainerPlayer 合法 windowId 就是 0；只按当前服务端容器及槽位边界校验。
                if (menu == null || message.menuSlot() >= menu.inventorySlots.size()) return;
                ItemStack stack = menu.inventorySlots.get(message.menuSlot()).getStack();
                logReceived(player, "IMPORT_MENU_SLOT", message.menuSlot(), stack, stack.getCount());
                callTransfer("importMenuSlotToLinked", new Class<?>[]{EntityPlayerMP.class, int.class},
                        player, message.menuSlot());
            }}); return null;
        }
    }

    public static final class BulkStorageOperation
            implements IMessageHandler<C2SRtsBulkStorageOpPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsBulkStorageOpPayload message,
                MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, new Action() {
                @Override public void run(EntityPlayerMP player) {
                    callTransfer("bulkStorageOperation",
                            new Class<?>[]{EntityPlayerMP.class, byte.class, ItemStack.class, int.class},
                            player, message.action(), message.prototype().copy(), message.amount());
                }
            });
            return null;
        }
    }

    private interface Action { void run(EntityPlayerMP player); }
    private static void schedule(MessageContext context, final Action action) {
        final EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() { @Override public void run() {
            // overlay 可在 RTS 相机关闭后继续使用；权限、会话与真实链接均由 TransferService 复核。
            action.run(player);
        }});
    }
    private static void scheduleCarriedTransfer(MessageContext context, final Action action) {
        final EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(new Runnable() { @Override public void run() {
            try {
                action.run(player);
            } finally {
                syncCarriedStack(player);
            }
        }});
    }
    /**
     * 1.12 的普通容器槽广播不会同步鼠标携带槽。overlay 会先做本地预览，因此无论服务端接受、
     * 拒绝还是只完成部分传输，都必须用 vanilla 的 window=-1 包把服务端真值确认回客户端。
     */
    private static void syncCarriedStack(EntityPlayerMP player) {
        ItemStack carried = player.inventory.getItemStack();
        player.connection.sendPacket(new SPacketSetSlot(
                -1, -1, carried.isEmpty() ? ItemStack.EMPTY : carried.copy()));
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
    private static void logReceived(EntityPlayerMP player, String action, int slot, ItemStack stack, int amount) {
        RtsbuildingMod.LOGGER.info(
                "[RTS-OVERLAY] side=S event=TRANSFER_RECEIVED action={} player={} menu={} window={} slot={} item={} amount={}",
                action,
                player.getName(),
                player.openContainer == null ? "null" : player.openContainer.getClass().getName(),
                player.openContainer == null ? -1 : player.openContainer.windowId,
                slot,
                stack == null || stack.isEmpty() ? "empty" : stack.getItem().getRegistryName(),
                amount);
    }
    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(message, cause);
    }
}
