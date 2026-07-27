package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把 JEI 4 的九宫格原型转成 RTS 服务端可验证的填充请求。
 *
 * <p>JEI 4 只按容器类和分类保存一个处理器，而 RTS 终端沿用原版
 * {@link ContainerWorkbench}。注册本类会覆盖 JEI 的原版工作台处理器，因此构造时必须
 * 保存原处理器，并在当前屏幕不是 RTS 终端时原样委托。捕获过程只反射 JEI 4 的注册表
 * 内部实现；失败时插件不会注册本类，形成安全降级。
 */
public final class RtsCraftTerminalJeiTransferHandler
        implements IRecipeTransferHandler<ContainerWorkbench> {
    private static final int GRID_SIZE = 9;
    private static final int JEI_FIRST_INPUT_SLOT = 1;

    private final IRecipeTransferHandlerHelper transferHelper;
    private final IRecipeTransferHandler<ContainerWorkbench> vanillaDelegate;

    RtsCraftTerminalJeiTransferHandler(
            IRecipeTransferHandlerHelper transferHelper,
            IRecipeTransferHandler<ContainerWorkbench> vanillaDelegate) {
        if (transferHelper == null || vanillaDelegate == null) {
            throw new IllegalArgumentException("JEI transfer helpers must be present");
        }
        this.transferHelper = transferHelper;
        this.vanillaDelegate = vanillaDelegate;
    }

    @Override
    public Class<ContainerWorkbench> getContainerClass() {
        return ContainerWorkbench.class;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            ContainerWorkbench container,
            IRecipeLayout recipeLayout,
            EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer) {
        if (!isRtsCraftTerminalScreen(container)) {
            return this.vanillaDelegate.transferRecipe(
                    container, recipeLayout, player, maxTransfer, doTransfer);
        }

        ResolvedRecipe resolved = resolveRecipe(recipeLayout, player);
        if (resolved == null) {
            return this.transferHelper.createUserErrorWithTooltip(
                    "RTSBuilding could not resolve this 1.12.2 crafting recipe");
        }
        if (doTransfer) {
            RtsPayloadRegistrar.sendToServer(new C2SRtsJeiTransferPayload(
                    resolved.recipeId, resolved.prototypes, maxTransfer, true));
        }
        return null;
    }

    private static boolean isRtsCraftTerminalScreen(ContainerWorkbench container) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null
                && minecraft.currentScreen instanceof RtsCraftTerminalScreen
                && ((RtsCraftTerminalScreen) minecraft.currentScreen).inventorySlots == container;
    }

    @Nullable
    private static ResolvedRecipe resolveRecipe(IRecipeLayout recipeLayout, EntityPlayer player) {
        if (recipeLayout == null || player == null || player.world == null) {
            return null;
        }
        List<ItemStack> prototypes = buildIngredientPrototypes(recipeLayout);
        InventoryCrafting matrix = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer ignored) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < GRID_SIZE; i++) {
            matrix.setInventorySlotContents(i, prototypes.get(i).copy());
        }

        IRecipe recipe;
        try {
            recipe = CraftingManager.findMatchingRecipe(matrix, player.world);
        } catch (RuntimeException incompatibleRecipe) {
            return null;
        }
        ResourceLocation id = recipe == null ? null : recipe.getRegistryName();
        return id == null ? null : new ResolvedRecipe(id.toString(), prototypes);
    }

    private static List<ItemStack> buildIngredientPrototypes(IRecipeLayout recipeLayout) {
        List<ItemStack> prototypes = new ArrayList<ItemStack>(GRID_SIZE);
        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients =
                recipeLayout.getItemStacks().getGuiIngredients();
        for (int i = 0; i < GRID_SIZE; i++) {
            IGuiIngredient<ItemStack> ingredient = ingredients.get(JEI_FIRST_INPUT_SLOT + i);
            prototypes.add(choosePrototype(ingredient));
        }
        return prototypes;
    }

    private static ItemStack choosePrototype(@Nullable IGuiIngredient<ItemStack> ingredient) {
        if (ingredient == null || !ingredient.isInput()) {
            return ItemStack.EMPTY;
        }
        ItemStack displayed = ingredient.getDisplayedIngredient();
        if (displayed != null && !displayed.isEmpty()) {
            return one(displayed);
        }
        for (ItemStack candidate : ingredient.getAllIngredients()) {
            if (candidate != null && !candidate.isEmpty()) {
                return one(candidate);
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack one(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * JEI 4 的 API 没有公开“取得当前处理器”的方法；此处仅探测官方 4.x 实现。
     * 任一结构不符都返回 {@code null}，调用者据此不安装 RTS 转移覆盖。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    static IRecipeTransferHandler<ContainerWorkbench> captureVanillaDelegate(
            IRecipeTransferRegistry registry) {
        if (registry == null) {
            return null;
        }
        try {
            Object table = findHandlerTable(registry);
            if (table == null) {
                return null;
            }
            Method get = table.getClass().getMethod("get", Object.class, Object.class);
            Object handler = get.invoke(
                    table, ContainerWorkbench.class, VanillaRecipeCategoryUid.CRAFTING);
            return handler instanceof IRecipeTransferHandler
                    ? (IRecipeTransferHandler<ContainerWorkbench>) handler
                    : null;
        } catch (ReflectiveOperationException | RuntimeException incompatibleJei) {
            return null;
        }
    }

    @Nullable
    private static Object findHandlerTable(IRecipeTransferRegistry registry)
            throws ReflectiveOperationException {
        Class<?> type = registry.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("recipeTransferHandlers");
                field.setAccessible(true);
                return field.get(registry);
            } catch (NoSuchFieldException missingHere) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static final class ResolvedRecipe {
        private final String recipeId;
        private final List<ItemStack> prototypes;

        private ResolvedRecipe(String recipeId, List<ItemStack> prototypes) {
            this.recipeId = recipeId;
            this.prototypes = prototypes;
        }
    }
}
