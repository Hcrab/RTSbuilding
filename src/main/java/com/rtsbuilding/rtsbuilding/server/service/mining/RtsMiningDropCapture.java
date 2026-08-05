package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.BlockEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/** 在 1.12 HarvestDropsEvent 的最终掉落列表上接管 RTS 本次同步破坏。 */
public final class RtsMiningDropCapture {
    private static final ThreadLocal<ArrayDeque<CaptureContext>> ACTIVE =
            new ThreadLocal<ArrayDeque<CaptureContext>>() {
                @Override protected ArrayDeque<CaptureContext> initialValue() {
                    return new ArrayDeque<CaptureContext>();
                }
            };
    private RtsMiningDropCapture() { }

    public static <T> T capture(EntityPlayerMP player, RtsStorageSession session, Supplier<T> destruction) {
        return capture(player, session, null, destruction);
    }

    /**
     * 在自动入库开启时捕获精确掉落；目标距离玩家超过安全边界时，本次破坏会强制使用同一链路。
     *
     * <p>强制仅作用于这一次破坏，不会暗中改写玩家的自动入库设置。没有可写入的绑定存储时，
     * 有界缓冲会按既有策略回退到玩家背包，背包也满时才在玩家脚下生成余量。</p>
     */
    public static <T> T capture(EntityPlayerMP player, RtsStorageSession session,
            BlockPos targetPos, Supplier<T> destruction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(destruction, "destruction");
        boolean normalAutoStore = RtsMiningValidator.canAutoStoreDrops(player, session);
        boolean forcedRemoteSafety = targetPos != null
                && RtsRemoteDropSafetyPolicy.shouldForceAutoStore(
                com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.distanceSqToCenter(player, targetPos));
        if (!normalAutoStore && !forcedRemoteSafety) return destruction.get();
        if (!normalAutoStore && session.miningDropBuffer.shouldNotifyRemoteSafety(
                player.getServerForPlayer().getTotalWorldTime(), 100L)) {
            com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(player, new ChatComponentTranslation(
                    "message.rtsbuilding.remote_drop.auto_stored"), true);
        }
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        stack.push(new CaptureContext(player, session));
        try { return destruction.get(); }
        finally {
            stack.pop();
            if (stack.isEmpty()) ACTIVE.remove();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null || event.harvester != context.player
                || event.world != context.player.getServerForPlayer()) return;
        // absorber 只从事件列表删除已入队数量；未接收 remainder 继续由 Forge 生成。
        RtsDropAbsorber.enqueueCapturedDrops(context.player, context.session, event.drops);
    }

    /**
     * 兼容不经过 HarvestDropsEvent、而是在破坏回调中直接生成 EntityItem 的 1.12 老模组。
     *
     * <p>这里只接管当前同步破坏调用期间新生成的精确实体，不按坐标扫描世界，因此不会
     * 把目标附近原本存在的物品误认为本次掉落。缓存只能接收一部分时，实体保留精确余量
     * 继续进入世界；全部接收时才取消生成，保持物品守恒。</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    void onEntityJoinWorld(EntityJoinWorldEvent event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null || event.world != context.player.getServerForPlayer()
                || !(event.entity instanceof EntityItem)) return;
        EntityItem entity = (EntityItem) event.entity;
        if (entity.isDead || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(entity.getEntityItem())) return;

        ArrayList<ItemStack> candidate = new ArrayList<ItemStack>(1);
        candidate.add(entity.getEntityItem());
        boolean accepted = RtsDropAbsorber.enqueueCapturedDrops(
                context.player, context.session, candidate);
        if (accepted && candidate.isEmpty()) event.setCanceled(true);
    }

    private static final class CaptureContext {
        final EntityPlayerMP player;
        final RtsStorageSession session;
        CaptureContext(EntityPlayerMP player, RtsStorageSession session) {
            this.player = player; this.session = session;
        }
    }
}
