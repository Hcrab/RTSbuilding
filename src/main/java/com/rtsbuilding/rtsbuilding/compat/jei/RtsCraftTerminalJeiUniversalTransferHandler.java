package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiUniversalTransferPayload;
import com.rtsbuilding.rtsbuilding.server.menu.RtsCraftTerminalMenu;
import com.rtsbuilding.rtsbuilding.server.menu.RtsMenuTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 通用 JEI 配方转移处理器，处理所有非原版3×3合成的配方类型。
 * 统计配方 INPUT 槽中的所有物品及数量，堆叠发送给服务端填入合成格（溢出放入玩家背包）。
 *
 * <p>通过 JEI 的 {@link IUniversalRecipeTransferHandler} 注册，
 * 只有没有精确 RecipeType handler 的配方才会 fallback 到此处理器。
 */
public final class RtsCraftTerminalJeiUniversalTransferHandler
        implements IUniversalRecipeTransferHandler<RtsCraftTerminalMenu> {

    private final IRecipeTransferHandlerHelper transferHelper;

    public RtsCraftTerminalJeiUniversalTransferHandler(IRecipeTransferHandlerHelper transferHelper) {
        Objects.requireNonNull(transferHelper, "transferHelper");
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<RtsCraftTerminalMenu> getContainerClass() {
        return RtsCraftTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<RtsCraftTerminalMenu>> getMenuType() {
        return Optional.of(RtsMenuTypes.RTS_CRAFT_TERMINAL.get());
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(RtsCraftTerminalMenu container, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        if (!isUnderRtsCraftTerminal()) {
            return transferHelper.createInternalError();
        }

        List<IRecipeSlotView> inputViews = collectInputViews(recipeSlots);
        if (inputViews.isEmpty()) {
            return transferHelper.createInternalError();
        }

        if (!doTransfer) {
            return checkMaterialAvailability(inputViews);
        }

        // 统计每种物品的总需求量，堆叠打包发送
        List<ItemStack> prototypes = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<StorageEntry> storageEntries = ClientRtsController.get().getStorageEntries();
        buildStackedIngredientList(inputViews, prototypes, quantities, storageEntries);

        if (prototypes.isEmpty()) {
            return null; // 无存储数据，交由服务端校验
        }

        // maxTransfer: like 3×3 crafting, send maximum craftable amount (up to 64)
        if (maxTransfer) {
            int maxCrafts = computeMaxCraftable(prototypes, quantities, storageEntries);
            if (maxCrafts > 1) {
                for (int i = 0; i < quantities.size(); i++) {
                    quantities.set(i, quantities.get(i) * Math.min(maxCrafts, 64));
                }
            }
        }

        PacketDistributor.sendToServer(new C2SRtsJeiUniversalTransferPayload(
                prototypes, quantities, true));
        return null;
    }

    /**
     * 检查关联存储中是否有足够材料完成该配方。
     */
    @Nullable
    private IRecipeTransferError checkMaterialAvailability(List<IRecipeSlotView> inputViews) {
        List<StorageEntry> storageEntries = ClientRtsController.get().getStorageEntries();
        if (storageEntries.isEmpty()) {
            return null; // 存储数据未加载，交由服务端校验
        }

        long[] remaining = new long[storageEntries.size()];
        for (int i = 0; i < storageEntries.size(); i++) {
            remaining[i] = storageEntries.get(i).count();
        }

        List<IRecipeSlotView> missingSlots = new ArrayList<>();

        for (IRecipeSlotView view : inputViews) {
            if (view.isEmpty()) {
                continue;
            }
            List<ItemStack> possibleItems = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
            boolean found = false;
            for (ItemStack needed : possibleItems) {
                if (needed.isEmpty()) continue;
                ResourceLocation neededId = BuiltInRegistries.ITEM.getKey(needed.getItem());
                if (neededId == null) continue;
                for (int i = 0; i < storageEntries.size(); i++) {
                    if (remaining[i] <= 0) continue;
                    if (neededId.toString().equals(storageEntries.get(i).itemId())) {
                        remaining[i]--;
                        found = true;
                        break;
                    }
                }
                if (found) break;
            }
            if (!found) {
                missingSlots.add(view);
            }
        }

        if (missingSlots.isEmpty()) {
            return null;
        }

        Component message = Component.translatable("jei.tooltip.error.recipe.transfer.missing");
        return transferHelper.createUserErrorForMissingSlots(message, missingSlots);
    }

    /**
     * 统计所有 INPUT 槽中每种物品出现的次数，按数量降序打包到 prototypes/quantities。
     * 对于标签原料（如 #stones），从 JEI 返回的多个可选物品中选择存储中实际存在的那个。
     */
    private static void buildStackedIngredientList(
            List<IRecipeSlotView> inputViews,
            List<ItemStack> prototypes,
            List<Integer> quantities,
            List<StorageEntry> storageEntries) {
        List<ItemStack> rawPrototypes = new ArrayList<>();
        for (IRecipeSlotView view : inputViews) {
            if (view.isEmpty()) continue;
            ItemStack picked = pickPrototypeFromStorage(view, storageEntries);
            if (!picked.isEmpty()) {
                rawPrototypes.add(picked.copyWithCount(1));
            }
        }

        // 合并同物品
        for (ItemStack raw : rawPrototypes) {
            ResourceLocation rawId = BuiltInRegistries.ITEM.getKey(raw.getItem());
            if (rawId == null) continue;
            String rawIdStr = rawId.toString();
            boolean merged = false;
            for (int i = 0; i < prototypes.size(); i++) {
                ResourceLocation existId = BuiltInRegistries.ITEM.getKey(prototypes.get(i).getItem());
                if (existId != null && existId.toString().equals(rawIdStr)) {
                    quantities.set(i, quantities.get(i) + 1);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                prototypes.add(raw);
                quantities.add(1);
            }
        }

        // 按数量降序排序（数量多的优先放合成格），同数量按ID排序
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < prototypes.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> {
            int cmp = Integer.compare(quantities.get(b), quantities.get(a));
            if (cmp == 0) {
                ResourceLocation idA = BuiltInRegistries.ITEM.getKey(prototypes.get(a).getItem());
                ResourceLocation idB = BuiltInRegistries.ITEM.getKey(prototypes.get(b).getItem());
                String strA = idA == null ? "" : idA.toString();
                String strB = idB == null ? "" : idB.toString();
                cmp = strA.compareTo(strB);
            }
            return cmp;
        });

        List<ItemStack> sortedProtos = new ArrayList<>(prototypes.size());
        List<Integer> sortedQtys = new ArrayList<>(quantities.size());
        for (int idx : indices) {
            sortedProtos.add(prototypes.get(idx));
            sortedQtys.add(quantities.get(idx));
        }
        prototypes.clear();
        prototypes.addAll(sortedProtos);
        quantities.clear();
        quantities.addAll(sortedQtys);
    }

    private static List<IRecipeSlotView> collectInputViews(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews().stream()
                .filter(view -> view.getRole() == RecipeIngredientRole.INPUT
                        || view.getRole() == RecipeIngredientRole.CATALYST)
                .toList();
    }

    /**
     * 从 JEI 返回的多个可选物品中，选择存储中实际存在的那个作为原型。
     * 对于标签原料（如 {@code #forge:stones}），JEI 可能返回花岗岩、闪长岩等，
     * 只有选择存储中实际有的物品，服务端才能成功提取。
     */
    private static ItemStack pickPrototypeFromStorage(IRecipeSlotView view, List<StorageEntry> storageEntries) {
        List<ItemStack> possibleItems = view.getIngredients(VanillaTypes.ITEM_STACK)
                .filter(s -> !s.isEmpty())
                .toList();
        if (possibleItems.isEmpty()) return ItemStack.EMPTY;

        // 优先匹配存储中数量最多的物品
        ItemStack bestPick = ItemStack.EMPTY;
        long bestCount = 0;
        for (ItemStack candidate : possibleItems) {
            ResourceLocation candidateId = BuiltInRegistries.ITEM.getKey(candidate.getItem());
            if (candidateId == null) continue;
            String idStr = candidateId.toString();
            for (StorageEntry entry : storageEntries) {
                if (idStr.equals(entry.itemId()) && entry.count() > bestCount) {
                    bestCount = entry.count();
                    bestPick = candidate;
                }
            }
        }
        return bestPick.isEmpty() ? possibleItems.get(0) : bestPick;
    }

    /**
     * 判断当前屏幕是否在 RTS 合成终端上下文中（穿透 JEI RecipesGui 覆盖层）。
     */
    private static boolean isUnderRtsCraftTerminal() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        Screen screen = getUnderlyingScreen(minecraft.screen);
        return screen instanceof RtsCraftTerminalScreen;
    }

    private static Screen getUnderlyingScreen(Screen currentScreen) {
        if (currentScreen == null) return null;
        if (!"mezz.jei.gui.recipes.RecipesGui".equals(currentScreen.getClass().getName())) {
            return currentScreen;
        }
        try {
            java.lang.reflect.Method getParentScreen =
                    currentScreen.getClass().getMethod("getParentScreen");
            @SuppressWarnings("unchecked")
            java.util.Optional<Screen> parent =
                    (java.util.Optional<Screen>) getParentScreen.invoke(currentScreen);
            Screen underlying = parent.orElse(null);
            return underlying != null ? underlying : currentScreen;
        } catch (Exception ignored) {
            return currentScreen;
        }
    }

    /**
     * 计算使用当前存储物品最多可以制作多少份该配方。
     */
    private static int computeMaxCraftable(List<ItemStack> prototypes, List<Integer> perCraft,
                                            List<StorageEntry> storageEntries) {
        int max = 64;
        for (int i = 0; i < prototypes.size(); i++) {
            ResourceLocation protoId = BuiltInRegistries.ITEM.getKey(prototypes.get(i).getItem());
            if (protoId == null) continue;
            String idStr = protoId.toString();
            long available = 0;
            for (StorageEntry e : storageEntries) {
                if (idStr.equals(e.itemId())) { available += e.count(); break; }
            }
            int needed = perCraft.get(i);
            if (needed > 0) {
                int canCraft = (int) (available / needed);
                max = Math.min(max, canCraft);
            }
        }
        return max;
    }
}
