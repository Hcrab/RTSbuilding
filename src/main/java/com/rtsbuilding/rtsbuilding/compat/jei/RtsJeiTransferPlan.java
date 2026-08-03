package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把 JEI 的可视配方与模组声明的输入槽整理成有界、可发往服务端的转移计划。
 *
 * <p>本类不修改容器，也不信任客户端结果作为最终权限判定。客户端库存快照只用于决定
 * HEI 的加号是否应当从“缺少材料”恢复为可点击；实际槽位、数量和链接存储都会由服务端
 * 再次验证。这样既保留即时反馈，又不会因旧快照产生复制或越权。</p>
 */
final class RtsJeiTransferPlan {
    static final int MAX_INPUTS = 36;
    static final int MAX_ALTERNATIVES_PER_INPUT = 16;

    private final List<Integer> targetSlots;
    private final List<List<ItemStack>> alternatives;
    private final boolean availableInCombinedSnapshot;

    private RtsJeiTransferPlan(List<Integer> targetSlots,
                               List<List<ItemStack>> alternatives,
                               boolean availableInCombinedSnapshot) {
        this.targetSlots = targetSlots;
        this.alternatives = alternatives;
        this.availableInCombinedSnapshot = availableInCombinedSnapshot;
    }

    @Nullable
    static <C extends Container> RtsJeiTransferPlan build(
            C container, IRecipeLayout layout, IRecipeTransferInfo<C> transferInfo) {
        if (container == null || layout == null || transferInfo == null
                || !transferInfo.canHandle(container)) {
            return null;
        }

        List<Slot> recipeSlots = new ArrayList<Slot>(transferInfo.getRecipeSlots(container));
        recipeSlots.sort(Comparator.comparingInt(slot -> slot.slotNumber));
        List<InputIngredient> ingredients = readInputAlternatives(layout);
        if (ingredients.isEmpty() || ingredients.size() > MAX_INPUTS) {
            return null;
        }

        List<Integer> targetSlots = new ArrayList<Integer>(ingredients.size());
        List<List<ItemStack>> alternatives = new ArrayList<List<ItemStack>>(ingredients.size());
        for (InputIngredient ingredient : ingredients) {
            if (ingredient.recipeSlotOrdinal < 0
                    || ingredient.recipeSlotOrdinal >= recipeSlots.size()) {
                return null;
            }
            Slot slot = recipeSlots.get(ingredient.recipeSlotOrdinal);
            if (slot == null || slot.slotNumber < 0
                    || slot.slotNumber >= container.inventorySlots.size()) {
                return null;
            }
            targetSlots.add(slot.slotNumber);
            alternatives.add(ingredient.alternatives);
        }

        boolean available = canSupplyCombinedSnapshot(
                container, transferInfo, alternatives);
        return new RtsJeiTransferPlan(
                Collections.unmodifiableList(targetSlots),
                Collections.unmodifiableList(alternatives),
                available);
    }

    List<Integer> targetSlots() {
        return targetSlots;
    }

    List<List<ItemStack>> alternatives() {
        return alternatives;
    }

    boolean availableInCombinedSnapshot() {
        return availableInCombinedSnapshot;
    }

    private static List<InputIngredient> readInputAlternatives(IRecipeLayout layout) {
        Map<Integer, ? extends IGuiIngredient<ItemStack>> raw =
                layout.getItemStacks().getGuiIngredients();
        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> ordered =
                new ArrayList<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>>(raw.entrySet());
        ordered.sort(Comparator.comparingInt(Map.Entry::getKey));

        List<InputIngredient> result = new ArrayList<InputIngredient>();
        int recipeSlotOrdinal = 0;
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : ordered) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (ingredient == null || !ingredient.isInput()) {
                continue;
            }
            int currentOrdinal = recipeSlotOrdinal++;
            if (ingredient.getAllIngredients().isEmpty()) {
                continue;
            }
            List<ItemStack> alternatives = normalizeAlternatives(ingredient);
            if (alternatives.isEmpty()) {
                return Collections.emptyList();
            }
            result.add(new InputIngredient(
                    currentOrdinal, Collections.unmodifiableList(alternatives)));
            if (result.size() > MAX_INPUTS) {
                return Collections.emptyList();
            }
        }
        return result;
    }

    private static List<ItemStack> normalizeAlternatives(IGuiIngredient<ItemStack> ingredient) {
        List<ItemStack> result = new ArrayList<ItemStack>();
        addUnique(result, ingredient.getDisplayedIngredient());
        ClientRtsController controller = ClientRtsController.get();
        for (ItemStack candidate : ingredient.getAllIngredients()) {
            String id = itemId(candidate);
            if (!id.isEmpty() && controller.getStorageTotalCount(id) > 0L) {
                addUnique(result, candidate);
            }
            if (result.size() >= MAX_ALTERNATIVES_PER_INPUT) {
                return result;
            }
        }
        for (ItemStack candidate : ingredient.getAllIngredients()) {
            addUnique(result, candidate);
            if (result.size() >= MAX_ALTERNATIVES_PER_INPUT) {
                break;
            }
        }
        return result;
    }

    private static void addUnique(List<ItemStack> target, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()
                || target.size() >= MAX_ALTERNATIVES_PER_INPUT) {
            return;
        }
        for (ItemStack existing : target) {
            if (sameStack(existing, candidate)) {
                return;
            }
        }
        ItemStack copy = candidate.copy();
        copy.setCount(1);
        target.add(copy);
    }

    private static <C extends Container> boolean canSupplyCombinedSnapshot(
            C container, IRecipeTransferInfo<C> info,
            List<List<ItemStack>> ingredients) {
        List<SnapshotSource> sources = new ArrayList<SnapshotSource>();
        Set<Integer> visitedSlots = new HashSet<Integer>();
        collectSlots(sources, visitedSlots, info.getRecipeSlots(container));
        collectSlots(sources, visitedSlots, info.getInventorySlots(container));

        Map<String, Long> remoteByItem = new LinkedHashMap<String, Long>();
        ClientRtsController controller = ClientRtsController.get();
        for (List<ItemStack> alternatives : ingredients) {
            for (ItemStack candidate : alternatives) {
                String id = itemId(candidate);
                if (!id.isEmpty() && !remoteByItem.containsKey(id)) {
                    remoteByItem.put(id, controller.getStorageTotalCount(id));
                }
            }
        }

        for (Map.Entry<String, Long> entry : remoteByItem.entrySet()) {
            if (entry.getValue() > 0L) {
                sources.add(SnapshotSource.remote(entry.getKey(), entry.getValue()));
            }
        }
        return canAssignCompleteSet(sources, ingredients);
    }

    private static void collectSlots(List<SnapshotSource> target, Set<Integer> visited,
                                     List<Slot> slots) {
        if (slots == null) {
            return;
        }
        for (Slot slot : slots) {
            if (slot == null || !visited.add(slot.slotNumber)) {
                continue;
            }
            ItemStack stack = slot.getStack();
            if (stack != null && !stack.isEmpty()) {
                target.add(SnapshotSource.exact(stack.copy(), stack.getCount()));
            }
        }
    }

    /** 用有容量的二分匹配避免矿辞候选的贪心误判。 */
    private static boolean canAssignCompleteSet(
            List<SnapshotSource> sources,
            List<List<ItemStack>> ingredients) {
        List<Integer> copyToSource = new ArrayList<Integer>();
        for (int source = 0; source < sources.size(); source++) {
            int copies = (int) Math.min(ingredients.size(), sources.get(source).count);
            for (int copy = 0; copy < copies; copy++) {
                copyToSource.add(source);
            }
        }
        if (copyToSource.size() < ingredients.size()) {
            return false;
        }

        List<List<Integer>> edges = new ArrayList<List<Integer>>(ingredients.size());
        for (List<ItemStack> alternatives : ingredients) {
            List<Integer> matchingCopies = new ArrayList<Integer>();
            for (int copy = 0; copy < copyToSource.size(); copy++) {
                SnapshotSource source = sources.get(copyToSource.get(copy));
                if (source.matchesAny(alternatives)) {
                    matchingCopies.add(copy);
                }
            }
            if (matchingCopies.isEmpty()) {
                return false;
            }
            edges.add(matchingCopies);
        }

        List<Integer> order = new ArrayList<Integer>(ingredients.size());
        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            order.add(ingredient);
        }
        order.sort((left, right) -> Integer.compare(
                edges.get(left).size(), edges.get(right).size()));
        int[] ownerByCopy = new int[copyToSource.size()];
        java.util.Arrays.fill(ownerByCopy, -1);
        for (int ingredient : order) {
            if (!augment(ingredient, edges, ownerByCopy,
                    new boolean[copyToSource.size()])) {
                return false;
            }
        }
        return true;
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

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = Item.REGISTRY.getNameForObject(stack.getItem());
        return id == null ? "" : id.toString();
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return ItemStack.areItemsEqual(left, right)
                && ItemStack.areItemStackTagsEqual(left, right);
    }

    private static final class SnapshotSource {
        @Nullable
        private final ItemStack exactStack;
        private final String remoteItemId;
        private long count;

        private SnapshotSource(@Nullable ItemStack exactStack,
                               String remoteItemId, long count) {
            this.exactStack = exactStack;
            this.remoteItemId = remoteItemId == null ? "" : remoteItemId;
            this.count = Math.max(0L, count);
        }

        private static SnapshotSource exact(ItemStack stack, long count) {
            return new SnapshotSource(stack, "", count);
        }

        private static SnapshotSource remote(String itemId, long count) {
            return new SnapshotSource(null, itemId, count);
        }

        private boolean matchesAny(List<ItemStack> alternatives) {
            for (ItemStack candidate : alternatives) {
                if (exactStack != null && sameStack(exactStack, candidate)) {
                    return true;
                }
                if (!remoteItemId.isEmpty() && remoteItemId.equals(itemId(candidate))) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 保留 JEI 输入在配方槽列表中的原始位置。空白配方格不需要传给服务端，
     * 但必须占用 ordinal，否则台阶这类居中摆放配方会被错误压到第一行。
     */
    private static final class InputIngredient {
        private final int recipeSlotOrdinal;
        private final List<ItemStack> alternatives;

        private InputIngredient(int recipeSlotOrdinal, List<ItemStack> alternatives) {
            this.recipeSlotOrdinal = recipeSlotOrdinal;
            this.alternatives = alternatives;
        }
    }
}
