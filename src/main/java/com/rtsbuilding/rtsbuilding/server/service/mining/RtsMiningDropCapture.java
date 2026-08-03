package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 的精确挖掘掉落接管插头。
 *
 * <p>1.21.1 可以直接监听最终方块掉落列表；Forge 1.20.1 没有对应事件，因此在一次同步
 * 破坏调用期间拦截即将加入世界的 {@link ItemEntity}。缓存只接受得下全部物品时才取消
 * 实体生成，接受不完的余量仍按原版路径落到世界，不能吞物品。</p>
 *
 * <p>本类只翻译平台事件，不访问 AE/RS，也不拥有会话或任务状态。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsMiningDropCapture {
    private static final ThreadLocal<ArrayDeque<CaptureContext>> ACTIVE =
            ThreadLocal.withInitial(ArrayDeque::new);

    private RtsMiningDropCapture() {
    }

    /** 在一次同步方块破坏期间开启掉落接管；嵌套调用按栈恢复上一层上下文。 */
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
            if (stack.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    /**
     * LOWEST 让其他模组先完成实体替换或数量修改，再把最终物品交给 RTS 有界缓存。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        CaptureContext context = ACTIVE.get().peek();
        if (context == null
                || event.getLevel() != context.player().getLevel()
                || !(event.getEntity() instanceof ItemEntity itemEntity)
                || itemEntity.getItem().isEmpty()) {
            return;
        }

        ArrayList<ItemEntity> candidate = new ArrayList<>(1);
        candidate.add(itemEntity);
        RtsDropAbsorber.enqueueCapturedDrops(context.player(), context.session(), candidate);
        if (candidate.isEmpty()) {
            event.setCanceled(true);
        }
    }

    private record CaptureContext(ServerPlayer player, RtsStorageSession session) {
    }
}
