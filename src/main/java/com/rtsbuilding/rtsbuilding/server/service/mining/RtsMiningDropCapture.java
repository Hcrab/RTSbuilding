package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

    /**
     * Fabric 的 Block.popResource Mixin 在实体生成前调用此方法。
     * 返回仍需交给原版生成的余量；缓存满时绝不吞掉未接收物品。
     */
    public static ItemStack captureDrop(ItemStack stack) {
        ArrayDeque<CaptureContext> contexts = ACTIVE.get();
        CaptureContext context = contexts.peek();
        if (context == null || stack == null || stack.isEmpty()) {
            return stack;
        }
        int accepted = RtsDropAbsorber.enqueueCapturedStack(context.player(), context.session(), stack);
        if (accepted <= 0) {
            return stack;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(accepted);
        return remainder;
    }

    private record CaptureContext(ServerPlayer player, RtsStorageSession session) {
    }
}
