package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.UseOnOutcome;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 空手远程交互器——处理 RTS 模式下空手（未持有物品）的远程交互。
 *
 * <p>交互优先级：
 * <ol>
 *   <li>先尝试与目标实体交互（{@link InteractionHelper#useItemOnEntityWithMainHand}）</li>
 *   <li>然后尝试与目标方块交互（{@link InteractionHelper#useItemOnWithMainHand}）</li>
 *   <li>最后回退到在空中使用物品（{@link InteractionHelper#useItemWithMainHand}）</li>
 * </ol>
 *
 * <p>通常用于打开容器的 GUI、与按钮/拉杆交互等不需要物品的操作。
 * 通过 {@link TemporaryContextSwitcher} 实现安全的临时上下文切换。
 */
public final class RtsEmptyHandInteractor {

    private RtsEmptyHandInteractor() {
    }

    /**
     * 使用空手与目标方块或实体交互。
     */
    public static InteractionResult interactWithEmptyHand(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, RayContext rayContext) {
        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                Config.remotePovBlockReach(),
                () -> {
                    if (targetEntity != null) {
                        return InteractionHelper.useItemOnEntityWithMainHand(player, level, ItemStack.EMPTY, targetEntity, hit).result();
                    }
                    if (blockHit != null) {
                        UseOnOutcome primary = InteractionHelper.useItemOnWithMainHand(player, level, ItemStack.EMPTY, blockHit, false);
                        if (primary.result().consumesAction()) {
                            return primary.result();
                        }
                    }
                    return InteractionHelper.useItemWithMainHand(player, level, ItemStack.EMPTY, false).result();
                });
    }
}
