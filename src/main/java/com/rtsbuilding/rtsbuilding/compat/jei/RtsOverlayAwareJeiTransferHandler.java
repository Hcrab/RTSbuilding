package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiContainerTransferPayload;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 为 JEI/HEI 的标准基础转移器叠加 RTS 链接存储材料源。
 *
 * <p>本类不取代第三方模组的槽位声明，也不尝试理解自定义转移协议。只有能从官方
 * {@code BasicRecipeTransferHandler} 安全取得 {@link IRecipeTransferInfo} 时才包装；其余处理器
 * 原样返回。运行期任何反射或兼容异常也会立即委托原处理器，保证兼容代码失效不会崩客户端。</p>
 */
public final class RtsOverlayAwareJeiTransferHandler<C extends Container>
        implements IRecipeTransferHandler<C> {
    private static final String BASIC_HANDLER = "mezz.jei.transfer.BasicRecipeTransferHandler";
    private static final Map<IRecipeTransferHandler<?>, IRecipeTransferHandler<?>> CACHE =
            new IdentityHashMap<IRecipeTransferHandler<?>, IRecipeTransferHandler<?>>();

    private final IRecipeTransferHandler<C> delegate;
    private final IRecipeTransferInfo<C> transferInfo;

    private RtsOverlayAwareJeiTransferHandler(IRecipeTransferHandler<C> delegate,
                                              IRecipeTransferInfo<C> transferInfo) {
        this.delegate = delegate;
        this.transferInfo = transferInfo;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static synchronized IRecipeTransferHandler wrap(IRecipeTransferHandler handler) {
        if (handler == null || handler instanceof RtsOverlayAwareJeiTransferHandler
                || !BASIC_HANDLER.equals(handler.getClass().getName())) {
            return handler;
        }
        IRecipeTransferHandler<?> cached = CACHE.get(handler);
        if (cached != null) {
            return cached;
        }
        IRecipeTransferInfo<?> info = findTransferInfo(handler);
        if (info == null) {
            return handler;
        }
        IRecipeTransferHandler<?> wrapped = new RtsOverlayAwareJeiTransferHandler(handler, info);
        CACHE.put(handler, wrapped);
        RtsbuildingMod.LOGGER.info(
                "[RTS-JEI] side=C event=HANDLER_WRAP container={} handler={}",
                handler.getContainerClass().getName(), handler.getClass().getName());
        return wrapped;
    }

    @Override
    public Class<C> getContainerClass() {
        return delegate.getContainerClass();
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            C container, IRecipeLayout recipeLayout, EntityPlayer player,
            boolean maxTransfer, boolean doTransfer) {
        return transferWithOverlay(
                delegate, transferInfo, container, recipeLayout, player,
                maxTransfer, doTransfer);
    }

    @Nullable
    static <T extends Container> IRecipeTransferError transferWithOverlay(
            IRecipeTransferHandler<T> delegate,
            IRecipeTransferInfo<T> transferInfo,
            T container,
            IRecipeLayout recipeLayout,
            EntityPlayer player,
            boolean maxTransfer,
            boolean doTransfer) {
        try {
            IRecipeTransferError nativeError = delegate.transferRecipe(
                    container, recipeLayout, player, maxTransfer, false);
            if (!RtsJeiScreenContext.hasActiveContainerOverlay(container)) {
                return doTransfer
                        ? delegate.transferRecipe(container, recipeLayout, player, maxTransfer, true)
                        : nativeError;
            }
            if (nativeError == null) {
                return doTransfer
                        ? delegate.transferRecipe(container, recipeLayout, player, maxTransfer, true)
                        : null;
            }

            RtsJeiTransferPlan plan = RtsJeiTransferPlan.build(
                    container, recipeLayout, transferInfo);
            if (plan == null || !plan.availableInCombinedSnapshot()) {
                return nativeError;
            }
            if (doTransfer) {
                RtsPayloadRegistrar.sendToServer(new C2SRtsJeiContainerTransferPayload(
                        container.windowId,
                        plan.targetSlots(),
                        plan.alternatives(),
                        maxTransfer,
                        transferInfo.requireCompleteSets()));
                RtsbuildingMod.LOGGER.info(
                        "[RTS-JEI] side=C event=REMOTE_TRANSFER_SEND container={} window={} inputs={} max={}",
                        container.getClass().getName(), container.windowId,
                        plan.targetSlots().size(), maxTransfer);
            }
            return null;
        } catch (RuntimeException | LinkageError incompatibleJei) {
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-JEI] side=C event=COMPAT_FALLBACK container={} reason={}",
                    container == null ? "null" : container.getClass().getName(),
                    incompatibleJei.toString());
            return delegate.transferRecipe(
                    container, recipeLayout, player, maxTransfer, doTransfer);
        }
    }

    @Nullable
    private static IRecipeTransferInfo<?> findTransferInfo(IRecipeTransferHandler<?> handler) {
        Class<?> type = handler.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!IRecipeTransferInfo.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(handler);
                    if (value instanceof IRecipeTransferInfo) {
                        return (IRecipeTransferInfo<?>) value;
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
