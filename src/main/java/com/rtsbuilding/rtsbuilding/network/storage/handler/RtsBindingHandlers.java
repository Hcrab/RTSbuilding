package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsRemoteMenuResultPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetBdNetworkPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetQuickSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteInteractionResult;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.InvocationTargetException;

/** 快捷槽和远程 GUI 绑定的 1.12 服务端网络边界。 */
public final class RtsBindingHandlers {
    private static final String CAMERA = "com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager";
    private static final String REGISTRY = "com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry";
    private RtsBindingHandlers() {}

    public static final class SetBdNetwork implements IMessageHandler<C2SRtsSetBdNetworkPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSetBdNetworkPayload message, MessageContext context) {
            schedule(context, true, new Action() { @Override public void run(EntityPlayerMP player) {
                callBinding("setBdNetworkEnabled", new Class<?>[]{EntityPlayerMP.class, boolean.class},
                        player, message.enabled());
            }}); return null;
        }
    }
    public static final class StoreHotbarSlot implements IMessageHandler<C2SRtsStoreHotbarSlotPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsStoreHotbarSlotPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, true, new Action() { @Override public void run(EntityPlayerMP player) {
                callBinding("storeHotbarSlot", new Class<?>[]{EntityPlayerMP.class, byte.class},
                        player, message.slot());
            }}); return null;
        }
    }
    public static final class SetQuickSlot implements IMessageHandler<C2SRtsSetQuickSlotPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSetQuickSlotPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, true, new Action() { @Override public void run(EntityPlayerMP player) {
                callBinding("setQuickSlot", new Class<?>[]{EntityPlayerMP.class, byte.class, String.class, ItemStack.class},
                        player, message.slot(), message.itemId(), message.previewStack());
            }}); return null;
        }
    }
    public static final class SetGuiBinding implements IMessageHandler<C2SRtsSetGuiBindingPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsSetGuiBindingPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, true, new Action() { @Override public void run(EntityPlayerMP player) {
                if (!message.clear() && !inRange(player, message.pos())) return;
                callBinding("setGuiBinding",
                        new Class<?>[]{EntityPlayerMP.class, byte.class, boolean.class, BlockPos.class,
                                EnumFacing.class, String.class},
                        player, message.slot(), message.clear(), message.pos(), message.face(), message.itemIdHint());
            }}); return null;
        }
    }
    public static final class OpenGuiBinding implements IMessageHandler<C2SRtsOpenGuiBindingPayload, IMessage> {
        @Override public IMessage onMessage(final C2SRtsOpenGuiBindingPayload message, MessageContext context) {
            if (!message.isValid()) return null;
            schedule(context, true, new Action() { @Override public void run(EntityPlayerMP player) {
                long traceId = message.traceId();
                Container before = player.openContainer;
                RtsbuildingMod.LOGGER.info(
                        "[RTS-TRACE] side=S event=C2S_RECEIVED trace={} kind=REMOTE_GUI source=GUI_BINDING slot={} windowBefore={}",
                        RtsTraceIds.format(traceId), message.slot(), before == null ? -1 : before.windowId);
                RtsRemoteMenuCompat.beginServerRemoteMenuOpen(player, traceId);
                try {
                    // 绑定所有权、维度和目标距离由服务端绑定服务按该玩家会话重新校验。
                    callBinding("openGuiBinding",
                            new Class<?>[]{EntityPlayerMP.class, byte.class, long.class},
                            player, message.slot(), traceId);
                    Container after = player.openContainer;
                    RtsRemoteInteractionResult result = after != null && after != before
                            ? RtsRemoteInteractionResult.menuOpened(after.windowId)
                            : RtsRemoteInteractionResult.noMenu(
                                    S2CRtsRemoteMenuResultPayload.REASON_NO_EFFECT);
                    sendTerminal(player, traceId, result);
                    if (result.outcome() != S2CRtsRemoteMenuResultPayload.MENU_OPENED) {
                        RtsRemoteMenuCompat.cancelServerRemoteMenuOpen(player, traceId, "NO_MENU");
                    }
                } catch (RuntimeException | LinkageError failure) {
                    sendTerminal(player, traceId, RtsRemoteInteractionResult.failed());
                    RtsRemoteMenuCompat.cancelServerRemoteMenuOpen(player, traceId, "EXCEPTION");
                    throw failure;
                }
            }}); return null;
        }
    }
    public static final class CloseRemoteMenu implements IMessageHandler<C2SRtsCloseRemoteMenuPayload, IMessage> {
        @Override public IMessage onMessage(C2SRtsCloseRemoteMenuPayload message, MessageContext context) {
            schedule(context, false, new Action() { @Override public void run(EntityPlayerMP player) {
                // 关闭清理不能依赖 RTS 仍处于 active，否则异常退出会留下远程容器。
                callBinding("closeRemoteMenu", new Class<?>[]{EntityPlayerMP.class}, player);
            }}); return null;
        }
    }

    private interface Action { void run(EntityPlayerMP player); }
    private static void schedule(MessageContext context, final boolean requireActive, final Action action) {
        final EntityPlayerMP player = context.getServerHandler().playerEntity;
        com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleServer(player, new Runnable() { @Override public void run() {
            if (!requireActive || active(player)) action.run(player);
        }});
    }
    private static boolean active(EntityPlayerMP player) {
        return camera("isActive", new Class<?>[]{EntityPlayerMP.class}, player);
    }
    private static boolean inRange(EntityPlayerMP player, BlockPos pos) {
        return camera("isWithinActionRange", new Class<?>[]{EntityPlayerMP.class, BlockPos.class}, player, pos);
    }
    private static boolean camera(String method, Class<?>[] types, Object... arguments) {
        try { return Boolean.TRUE.equals(Class.forName(CAMERA).getMethod(method, types).invoke(null, arguments)); }
        catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 storage camera adapter unavailable", exception);
        } catch (InvocationTargetException exception) { throw propagate("Storage camera check failed", exception); }
    }
    private static void callBinding(String method, Class<?>[] types, Object... arguments) {
        try {
            Class<?> registryType = Class.forName(REGISTRY);
            Object registry = registryType.getMethod("getInstance").invoke(null);
            Object binding = registryType.getMethod("binding").invoke(registry);
            binding.getClass().getMethod(method, types).invoke(binding, arguments);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("1.12 binding service adapter unavailable: " + method, exception);
        } catch (InvocationTargetException exception) { throw propagate("Binding service failed", exception); }
    }

    private static void sendTerminal(
            EntityPlayerMP player, long traceId, RtsRemoteInteractionResult result) {
        if (result == null) result = RtsRemoteInteractionResult.failed();
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=S event=RESULT trace={} kind=REMOTE_GUI source=GUI_BINDING outcome={} reason={} window={}",
                RtsTraceIds.format(traceId),
                S2CRtsRemoteMenuResultPayload.outcomeName(result.outcome()),
                S2CRtsRemoteMenuResultPayload.reasonName(result.reason()), result.windowId());
        if (RtsTraceIds.isPresent(traceId)) {
            RtsClientboundPackets.sendToPlayer(player, new S2CRtsRemoteMenuResultPayload(
                    traceId, result.outcome(), result.reason(), result.windowId()));
        }
    }

    private static RuntimeException propagate(String message, InvocationTargetException exception) {
        Throwable cause = exception.getCause();
        return cause instanceof RuntimeException ? (RuntimeException) cause : new IllegalStateException(message, cause);
    }
}
