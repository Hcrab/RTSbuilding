package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 在原版已经计算完方块掉落、但掉落实体尚未进入世界时接管 RTS 挖掘掉落。
 *
 * <p>该类只负责界定一次同步破坏调用的所有权，并把事件中的精确 {@code ItemStack}
 * 交给轻量缓存；它不访问 AE/RS、不执行储存写入，也不改变非 RTS 挖掘。这样既保留
 * 其他模组对掉落列表的修改，又消除“生成实体后再按半径扫描”造成的移动、拾取竞争窗口。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsMiningDropCapture {
    private static final ThreadLocal<ArrayDeque<CaptureContext>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RtsMiningDropCapture() {
    }

    /** 在一次同步方块破坏期间开启精确掉落接管；嵌套调用按栈恢复上一层上下文。 */
    public static <T> T capture(
            ServerPlayer player, RtsStorageSession session, Supplier<T> destruction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(destruction, "destruction");
        if (!RtsMiningValidator.canAutoStoreDrops(player, session)) {
            return destruction.get();
        }
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        stack.push(new CaptureContext(player, session));
        try {
            return destruction.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) ACTIVE.remove();
        }
    }

    /** LOWEST 优先级用于接收其他模组已经修改完成的最终掉落列表。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBlockDrops(BlockDropsEvent event) {
        ArrayDeque<CaptureContext> stack = ACTIVE.get();
        CaptureContext context = stack.peek();
        if (context == null
                || event.getBreaker() != context.player()
                || event.getLevel() != context.player().serverLevel()) {
            return;
        }
        // 只移除已进入缓存的部分；缓存满时余量仍由 NeoForge 正常生成到世界。
        RtsDropAbsorber.enqueueCapturedDrops(context.player(), context.session(), event.getDrops());
    }

    private record CaptureContext(ServerPlayer player, RtsStorageSession session) {
    }
}
