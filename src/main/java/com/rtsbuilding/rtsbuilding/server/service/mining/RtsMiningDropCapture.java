package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

/** 在 1.12 HarvestDropsEvent 的最终掉落列表上接管 RTS 本次同步破坏。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsMiningDropCapture {
    private static final ThreadLocal<ArrayDeque<CaptureContext>> ACTIVE =
            new ThreadLocal<ArrayDeque<CaptureContext>>() {
                @Override protected ArrayDeque<CaptureContext> initialValue() {
                    return new ArrayDeque<CaptureContext>();
                }
            };
    private RtsMiningDropCapture() { }

    public static <T> T capture(EntityPlayerMP player, RtsStorageSession session, Supplier<T> destruction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(destruction, "destruction");
        if (!RtsMiningValidator.canAutoStoreDrops(player, session)) return destruction.get();
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        stack.push(new CaptureContext(player, session));
        try { return destruction.get(); }
        finally {
            stack.pop();
            if (stack.isEmpty()) ACTIVE.remove();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null || event.getHarvester() != context.player
                || event.getWorld() != context.player.getServerWorld()) return;
        // absorber 只从事件列表删除已入队数量；未接收 remainder 继续由 Forge 生成。
        RtsDropAbsorber.enqueueCapturedDrops(context.player, context.session, event.getDrops());
    }

    private static final class CaptureContext {
        final EntityPlayerMP player;
        final RtsStorageSession session;
        CaptureContext(EntityPlayerMP player, RtsStorageSession session) {
            this.player = player; this.session = session;
        }
    }
}
