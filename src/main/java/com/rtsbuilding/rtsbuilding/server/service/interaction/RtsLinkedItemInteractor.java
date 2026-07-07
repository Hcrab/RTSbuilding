package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.UseOnOutcome;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 链接物品远程交互器——处理使用从玩家链接存储系统中提取的固定物品进行远程交互。
 *
 * <p>当玩家从远程储存浏览器 PIN 了一个物品进行交互时，此交互器：
 * <ol>
 *   <li>从链接网络提取一个单位的指定物品</li>
 *   <li>临时放置到玩家主手</li>
 *   <li>依次尝试多种交互模式（物品对方块、物品空中使用、潜行对方块等）</li>
 *   <li>将任何剩余物品退还回链接网络</li>
 *   <li>强制刷新槽缓存并标记页面为脏</li>
 * </ol>
 *
 * <p>通过 {@link TemporaryContextSwitcher} 实现安全的临时上下文切换。
 */
public final class RtsLinkedItemInteractor {

    private RtsLinkedItemInteractor() {
    }

    /**
     * 使用固定/链接的物品与目标方块或实体交互。
     * 该物品从玩家的链接存储中提取、使用，
     * 任何剩余物品被退还。
     */
    public static InteractionResult interactWithLinkedItem(ServerPlayer player, ServerLevel level, RtsStorageSession session,
            Entity targetEntity, BlockHitResult blockHit, Vec3 hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.isBlank() || !RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return InteractionResult.PASS;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = RtsTransferExtractor.extractOneFromNetwork(extractHandlers, player, BuiltInRegistries.ITEM.get(id));
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
        UseOnOutcome outcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                Config.remotePovBlockReach(),
                () -> {
            if (targetEntity != null) {
                return InteractionHelper.useItemOnEntityWithMainHand(player, level, extracted, targetEntity, hit);
            }
            // Alt interaction: normal item interaction first, build-like secondary interaction later.
            UseOnOutcome primaryOn = InteractionHelper.useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (primaryOn.result().consumesAction()) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().isEmpty() ? extracted.copy() : primaryOn.remainder().copy();

            UseOnOutcome primaryUse = InteractionHelper.useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (primaryUse.result().consumesAction()) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().isEmpty() ? afterPrimaryOn : primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = InteractionHelper.useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (secondaryOn.result().consumesAction()) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().isEmpty() ? afterPrimaryUse : secondaryOn.remainder().copy();
            return InteractionHelper.useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!outcome.remainder().isEmpty()) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, outcome.remainder());
        }
        // Force-refresh slot cache and invalidate page cache after linked-item interaction
        ServiceRegistry.getInstance().serviceOp().markDirty(player, session);
        return outcome.result();
    }
}
