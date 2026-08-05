package com.rtsbuilding.rtsbuilding.server.service.crafting;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiContainerTransferPayload;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端执行普通机器 GUI 的 JEI/HEI 链接存储转移。
 *
 * <p>本类只处理标准转移器已经声明好的“配方输入槽”，不猜测输出槽、流体槽或第三方自定义协议。
 * 客户端给出的槽号必须属于当前打开窗口、不得指向玩家背包，并且每个实际物品仍需通过
 * {@link Slot#isItemValid(ItemStack)}。旧输入先暂存在内存中，成功后统一退回链接存储；执行期若
 * 处理器异常失约，则退还外部提取物并原样恢复旧槽，避免兼容失败导致吞物或复制。</p>
 */
public final class RtsGenericJeiContainerFiller {
    private static final int MAX_TRANSFER_PASSES = 64;

    private RtsGenericJeiContainerFiller() {
    }

    public static void apply(
            EntityPlayerMP player,
            RtsStorageSession session,
            int windowId,
            List<Integer> targetSlotNumbers,
            List<List<ItemStack>> alternatives,
            boolean maxTransfer,
            boolean requireCompleteSets) {
        if (player == null || session == null
                || !RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        Container container = player.openContainer;
        if (container == null || container.windowId != windowId
                || !validShape(targetSlotNumbers, alternatives)) {
            logResult(player, container, windowId, "REJECTED_SHAPE", 0);
            return;
        }

        List<Slot> targets = resolveAndValidateTargets(
                player, container, targetSlotNumbers, alternatives);
        if (targets == null) {
            logResult(player, container, windowId, "REJECTED_SLOTS", 0);
            return;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(linked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        List<ItemStack> originals = snapshotTargets(targets);
        List<SourceBucket> available = snapshotAvailable(
                player, extractHandlers, originals, alternatives);
        List<ItemStack> assignment = assignOneCompleteSet(available, alternatives);
        if (assignment == null) {
            logResult(player, container, windowId, "MISSING", 0);
            return;
        }
        for (int i = 0; i < targets.size(); i++) {
            if (!targets.get(i).isItemValid(assignment.get(i))) {
                logResult(player, container, windowId, "REJECTED_ITEM", 0);
                return;
            }
        }

        List<ItemStack> clearedPool = copyStacks(originals);
        clearTargets(targets);
        List<Boolean> firstSetFromCleared = new ArrayList<Boolean>(targets.size());
        List<ItemStack> externallyExtracted = new ArrayList<ItemStack>();
        for (int i = 0; i < targets.size(); i++) {
            ItemStack wanted = assignment.get(i);
            ItemStack extracted = takeOne(clearedPool, wanted);
            boolean fromCleared = !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted)) {
                extracted = RtsTransferExtractor.extractOneMatchingPrototypeCombined(
                        extractHandlers, player, wanted);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted)) {
                    externallyExtracted.add(extracted.copy());
                }
            }
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted) || !targets.get(i).isItemValid(extracted)) {
                rollbackFirstSet(targets, originals, firstSetFromCleared,
                        externallyExtracted, insertHandlers, player);
                logResult(player, container, windowId, "RACE_ROLLBACK", 0);
                return;
            }
            extracted.stackSize = 1;
            targets.get(i).putStack(extracted);
            targets.get(i).onSlotChanged();
            firstSetFromCleared.add(fromCleared);
        }

        for (ItemStack remainder : clearedPool) {
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remainder)) {
                RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                        insertHandlers, player, remainder);
            }
        }

        int passes = 1;
        if (maxTransfer) {
            while (passes < MAX_TRANSFER_PASSES
                    && addAnotherCompleteSet(
                    targets, assignment, extractHandlers, insertHandlers, player)) {
                passes++;
            }
        }

        container.detectAndSendChanges();
        player.inventory.markDirty();
        RtsTransferInserter.refreshCache(player);
        ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
        logResult(player, container, windowId, "APPLIED", passes);
    }

    private static boolean validShape(List<Integer> targets,
                                      List<List<ItemStack>> alternatives) {
        if (targets == null || alternatives == null || targets.isEmpty()
                || targets.size() > C2SRtsJeiContainerTransferPayload.MAX_INPUTS
                || targets.size() != alternatives.size()) {
            return false;
        }
        Set<Integer> unique = new HashSet<Integer>();
        int total = 0;
        for (int i = 0; i < targets.size(); i++) {
            Integer slot = targets.get(i);
            List<ItemStack> choices = alternatives.get(i);
            if (slot == null || slot < 0 || !unique.add(slot)
                    || choices == null || choices.isEmpty()
                    || choices.size() > C2SRtsJeiContainerTransferPayload.MAX_ALTERNATIVES_PER_INPUT) {
                return false;
            }
            total += choices.size();
        }
        return total <= C2SRtsJeiContainerTransferPayload.MAX_TOTAL_ALTERNATIVES;
    }

    private static List<Slot> resolveAndValidateTargets(
            EntityPlayerMP player,
            Container container,
            List<Integer> targetSlotNumbers,
            List<List<ItemStack>> alternatives) {
        List<Slot> result = new ArrayList<Slot>(targetSlotNumbers.size());
        for (int i = 0; i < targetSlotNumbers.size(); i++) {
            int number = targetSlotNumbers.get(i);
            if (number < 0 || number >= container.inventorySlots.size()) {
                return null;
            }
            Slot slot = container.getSlot(number);
            if (slot == null || slot.inventory == player.inventory
                    || (slot.getHasStack() && !slot.canTakeStack(player))) {
                return null;
            }
            boolean acceptsAny = false;
            for (ItemStack candidate : alternatives.get(i)) {
                if (candidate != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(candidate)
                        && slot.isItemValid(candidate)) {
                    acceptsAny = true;
                    break;
                }
            }
            if (!acceptsAny) {
                return null;
            }
            result.add(slot);
        }
        return result;
    }

    private static List<ItemStack> snapshotTargets(List<Slot> targets) {
        List<ItemStack> result = new ArrayList<ItemStack>(targets.size());
        for (Slot target : targets) {
            ItemStack stack = target.getStack();
            result.add(stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? null : stack.copy());
        }
        return result;
    }

    private static List<SourceBucket> snapshotAvailable(
            EntityPlayerMP player,
            List<IItemHandler> handlers,
            List<ItemStack> originals,
            List<List<ItemStack>> alternatives) {
        List<SourceBucket> result = new ArrayList<SourceBucket>();
        for (ItemStack original : originals) {
            addIfRelevant(result, original, alternatives);
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            addIfRelevant(result, stack, alternatives);
        }
        for (IItemHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                addIfRelevant(result, handler.getStackInSlot(slot), alternatives);
            }
        }
        return result;
    }

    private static void addIfRelevant(List<SourceBucket> target, ItemStack stack,
                                      List<List<ItemStack>> alternatives) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || !matchesAny(stack, alternatives)) {
            return;
        }
        for (SourceBucket bucket : target) {
            if (sameStack(bucket.prototype, stack)) {
                bucket.count += stack.stackSize;
                return;
            }
        }
        ItemStack prototype = stack.copy();
        prototype.stackSize = 1;
        target.add(new SourceBucket(prototype, stack.stackSize));
    }

    private static boolean matchesAny(ItemStack stack,
                                      List<List<ItemStack>> alternatives) {
        for (List<ItemStack> choices : alternatives) {
            for (ItemStack choice : choices) {
                if (sameStack(stack, choice)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<ItemStack> assignOneCompleteSet(
            List<SourceBucket> sources,
            List<List<ItemStack>> alternatives) {
        List<Integer> copyToSource = new ArrayList<Integer>();
        for (int source = 0; source < sources.size(); source++) {
            int copies = (int) Math.min(alternatives.size(), sources.get(source).count);
            for (int copy = 0; copy < copies; copy++) {
                copyToSource.add(source);
            }
        }
        if (copyToSource.size() < alternatives.size()) {
            return null;
        }

        List<List<Integer>> edges = new ArrayList<List<Integer>>(alternatives.size());
        for (List<ItemStack> choices : alternatives) {
            List<Integer> matchingCopies = new ArrayList<Integer>();
            for (int copy = 0; copy < copyToSource.size(); copy++) {
                ItemStack source = sources.get(copyToSource.get(copy)).prototype;
                if (matchesAnyChoice(source, choices)) {
                    matchingCopies.add(copy);
                }
            }
            if (matchingCopies.isEmpty()) {
                return null;
            }
            edges.add(matchingCopies);
        }

        List<Integer> order = new ArrayList<Integer>(alternatives.size());
        for (int i = 0; i < alternatives.size(); i++) {
            order.add(i);
        }
        order.sort((left, right) -> Integer.compare(
                edges.get(left).size(), edges.get(right).size()));

        int[] ownerByCopy = new int[copyToSource.size()];
        java.util.Arrays.fill(ownerByCopy, -1);
        for (int ingredient : order) {
            boolean[] seen = new boolean[copyToSource.size()];
            if (!augment(ingredient, edges, ownerByCopy, seen)) {
                return null;
            }
        }

        ItemStack[] assigned = new ItemStack[alternatives.size()];
        for (int copy = 0; copy < ownerByCopy.length; copy++) {
            int ingredient = ownerByCopy[copy];
            if (ingredient >= 0) {
                assigned[ingredient] = sources.get(copyToSource.get(copy)).prototype.copy();
            }
        }
        List<ItemStack> result = new ArrayList<ItemStack>(assigned.length);
        for (ItemStack stack : assigned) {
            if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                return null;
            }
            stack.stackSize = 1;
            result.add(stack);
        }
        return result;
    }

    private static boolean augment(int ingredient, List<List<Integer>> edges,
                                   int[] ownerByCopy, boolean[] seen) {
        for (int copy : edges.get(ingredient)) {
            if (seen[copy]) {
                continue;
            }
            seen[copy] = true;
            if (ownerByCopy[copy] < 0
                    || augment(ownerByCopy[copy], edges, ownerByCopy, seen)) {
                ownerByCopy[copy] = ingredient;
                return true;
            }
        }
        return false;
    }

    private static void clearTargets(List<Slot> targets) {
        for (Slot target : targets) {
            target.putStack(null);
            target.onSlotChanged();
        }
    }

    private static ItemStack takeOne(List<ItemStack> pool, ItemStack prototype) {
        for (int i = 0; i < pool.size(); i++) {
            ItemStack stack = pool.get(i);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || !sameStack(stack, prototype)) {
                continue;
            }
            ItemStack one = stack.splitStack(1);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                pool.set(i, null);
            }
            return one;
        }
        return null;
    }

    private static boolean addAnotherCompleteSet(
            List<Slot> targets,
            List<ItemStack> assignment,
            List<IItemHandler> extractHandlers,
            List<IItemHandler> insertHandlers,
            EntityPlayerMP player) {
        for (int i = 0; i < targets.size(); i++) {
            ItemStack current = targets.get(i).getStack();
            ItemStack wanted = assignment.get(i);
            int limit = Math.min(current.getMaxStackSize(),
                    targets.get(i).getSlotStackLimit());
            if (!sameStack(current, wanted) || current.stackSize >= limit) {
                return false;
            }
        }

        List<ItemStack> acquired = new ArrayList<ItemStack>(assignment.size());
        for (ItemStack wanted : assignment) {
            ItemStack extracted = RtsTransferExtractor.extractOneMatchingPrototypeCombined(
                    extractHandlers, player, wanted);
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(extracted)) {
                for (ItemStack refund : acquired) {
                    RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                            insertHandlers, player, refund);
                }
                return false;
            }
            acquired.add(extracted);
        }
        for (int i = 0; i < targets.size(); i++) {
            com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.grow(targets.get(i).getStack(), acquired.get(i).stackSize);
            targets.get(i).onSlotChanged();
        }
        return true;
    }

    private static void rollbackFirstSet(
            List<Slot> targets,
            List<ItemStack> originals,
            List<Boolean> fromCleared,
            List<ItemStack> externallyExtracted,
            List<IItemHandler> insertHandlers,
            EntityPlayerMP player) {
        for (int i = 0; i < fromCleared.size(); i++) {
            targets.get(i).putStack(null);
            targets.get(i).onSlotChanged();
        }
        for (ItemStack extracted : externallyExtracted) {
            RtsTransferInserter.storeToLinkedWithFallbackPreferExisting(
                    insertHandlers, player, extracted);
        }
        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).putStack(originals.get(i).copy());
            targets.get(i).onSlotChanged();
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<ItemStack>(source.size());
        for (ItemStack stack : source) {
            result.add(stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? null : stack.copy());
        }
        return result;
    }

    private static boolean matchesAnyChoice(ItemStack stack,
                                            List<ItemStack> choices) {
        for (ItemStack choice : choices) {
            if (sameStack(stack, choice)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left != null && right != null
                && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(left) && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(right)
                && com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.areItemsEqual(left, right)
                && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static void logResult(EntityPlayerMP player, Container container,
                                  int windowId, String result, int passes) {
        RtsbuildingMod.LOGGER.info(
                "[RTS-JEI] side=S event=REMOTE_TRANSFER_RESULT player={} container={} window={} result={} passes={}",
                player == null ? "null" : com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.name(player),
                container == null ? "null" : container.getClass().getName(),
                windowId, result, passes);
    }

    private static final class SourceBucket {
        private final ItemStack prototype;
        private long count;

        private SourceBucket(ItemStack prototype, long count) {
            this.prototype = prototype;
            this.count = Math.max(0L, count);
        }
    }
}
