package com.rtsbuilding.rtsbuilding.server.service.interaction;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.UseOnOutcome;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 链接/储存页物品远程交互器——处理使用玩家在 RTS 储存页中选中的固定物品进行远程交互。
 *
 * <p>当玩家从远程储存浏览器 PIN 了一个物品进行交互时，此交互器：
 * <ol>
 *   <li>从链接网络、玩家背包储存视图或创造模式来源取得一个单位的指定物品</li>
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
    public static EnumActionResult interactWithLinkedItem(EntityPlayerMP player, WorldServer level, RtsStorageSession session,
            Entity targetEntity, RayTraceResult blockHit, Vec3d hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.trim().isEmpty()) {
            return EnumActionResult.PASS;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        boolean includePlayerMainInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
        boolean creativeSource = player.capabilities.isCreativeMode;
        if (!canUseSelectedItemSource(!activeLinked.isEmpty(), includePlayerMainInventory, creativeSource)) {
            return EnumActionResult.PASS;
        }

        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ResourceLocation id = parseId(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            return EnumActionResult.PASS;
        }

        Item item = ForgeRegistries.ITEMS.getValue(id);
        ItemStack extracted = extractSelectedItem(player, extractHandlers, item, includePlayerMainInventory, creativeSource);
        if (extracted.isEmpty()) {
            return EnumActionResult.PASS;
        }

        Vec3d interactionPos = InteractionHelper.resolveInteractionPosition(targetEntity, blockHit, hit);
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
            if (blockHit == null) {
                return InteractionHelper.useItemWithMainHand(player, level, extracted, false);
            }
            // 普通右键优先；仅在原版明确 PASS 时继续尝试潜行语义。
            UseOnOutcome primaryOn = InteractionHelper.useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (consumesAction(primaryOn.result())) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().copy();

            UseOnOutcome primaryUse = InteractionHelper.useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (consumesAction(primaryUse.result())) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = InteractionHelper.useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (consumesAction(secondaryOn.result())) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().copy();
            return InteractionHelper.useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!creativeSource && !outcome.remainder().isEmpty()) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, outcome.remainder());
        }
        // Force-refresh slot cache and invalidate page cache after linked-item interaction
        ServiceRegistry.getInstance().serviceOp().markDirty(player, session);
        return outcome.result();
    }

    static boolean canUseSelectedItemSource(boolean hasLinkedHandlers, boolean includePlayerMainInventory,
            boolean creativeSource) {
        return hasLinkedHandlers || includePlayerMainInventory || creativeSource;
    }

    private static ItemStack extractSelectedItem(EntityPlayerMP player, List<IItemHandler> extractHandlers, Item item,
            boolean includePlayerMainInventory, boolean creativeSource) {
        if (creativeSource) {
            return new ItemStack(item);
        }
        if (includePlayerMainInventory) {
            return RtsTransferExtractor.extractOneFromNetwork(extractHandlers, player, item);
        }
        return RtsTransferExtractor.extractOneFromLinked(extractHandlers, item);
    }

    private static ResourceLocation parseId(String itemId) {
        try {
            return itemId == null || itemId.trim().isEmpty() ? null : new ResourceLocation(itemId);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static boolean consumesAction(EnumActionResult result) {
        return result == EnumActionResult.SUCCESS;
    }
}
