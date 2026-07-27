package com.rtsbuilding.rtsbuilding.compat.bd;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Beyond Dimensions 1.12.2 的可选兼容入口。
 *
 * <p>BD 0.1.7.x 确实提供 Forge 1.12.2 版本，但包名和存储 API 与现代版完全不同。
 * 这里仅在确认模组存在后反射解析旧版公开 API，因而未安装 BD 时服务端可以安全加载本类。
 * API 形状不匹配时会明确把适配器判为不可用，不会伪造一个空网络。</p>
 */
public final class RtsBdCompat {
    private static final String MOD_ID = "beyonddimensions";
    private static final String DIMENSIONS_NET =
            "com.wintercogs.beyonddimensions.DataBase.DimensionsNet";
    private static final String UNIFIED_STORAGE =
            "com.wintercogs.beyonddimensions.DataBase.Storage.UnifiedStorage";
    private static final String ITEM_STACK_TYPE =
            "com.wintercogs.beyonddimensions.DataBase.Stack.ItemStackType";
    private static final String ITEM_HANDLER =
            "com.wintercogs.beyonddimensions.DataBase.Storage.ItemUnifiedStorageHandler";
    private static final String FLUID_HANDLER =
            "com.wintercogs.beyonddimensions.DataBase.Storage.FluidUnifiedStorageHandler";

    private static volatile LegacyApi api;
    private static volatile boolean apiLookupAttempted;
    private static volatile String unavailableReason = "Beyond Dimensions 未安装";

    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }

    private RtsBdCompat() {
    }

    /** 只有模组存在且 0.1.7.x API 完整匹配时才返回 true。 */
    public static boolean isAvailable() {
        return resolveApi() != null;
    }

    /** 供诊断界面/日志区分“没装”与“旧版 API 不匹配”。 */
    public static String getUnavailableReason() {
        resolveApi();
        return unavailableReason;
    }

    public static boolean hasPrimaryNetwork(EntityPlayerMP player) {
        LegacyApi resolved = resolveApi();
        return resolved != null && player != null && resolved.getNetwork(player) != null;
    }

    @Nullable
    public static IItemHandler createNetworkItemHandler(EntityPlayerMP player) {
        LegacyApi resolved = resolveApi();
        if (resolved == null || player == null) {
            return null;
        }
        Object storage = resolved.getStorage(player);
        if (storage == null) {
            return null;
        }
        try {
            Object raw = resolved.itemHandlerConstructor.newInstance(storage);
            if (!(raw instanceof IItemHandler)) {
                unavailableReason = "Beyond Dimensions 物品适配器未实现 Forge IItemHandler";
                return null;
            }
            return new BdNetworkItemHandler((IItemHandler) raw, storage, resolved);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | RuntimeException | LinkageError failure) {
            unavailableReason = "Beyond Dimensions 物品适配器初始化失败: "
                    + failure.getClass().getSimpleName();
            return null;
        }
    }

    @Nullable
    public static IFluidHandler createNetworkFluidHandler(EntityPlayerMP player) {
        LegacyApi resolved = resolveApi();
        if (resolved == null || player == null) {
            return null;
        }
        Object storage = resolved.getStorage(player);
        if (storage == null) {
            return null;
        }
        try {
            Object raw = resolved.fluidHandlerConstructor.newInstance(storage);
            if (raw instanceof IFluidHandler) {
                return (IFluidHandler) raw;
            }
            unavailableReason = "Beyond Dimensions 流体适配器未实现 Forge IFluidHandler";
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                | RuntimeException | LinkageError failure) {
            unavailableReason = "Beyond Dimensions 流体适配器初始化失败: "
                    + failure.getClass().getSimpleName();
        }
        return null;
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdNetworkItemHandler) {
            ((BdNetworkItemHandler) handler).release();
        }
    }

    /**
     * 1.12.2 的 BD handler 直接读取 UnifiedStorage，不持有槽位快照，因此刷新无需重建。
     * 保留此入口以维持主服务的生命周期协议，并拒绝刷新已经 release 的包装器。
     */
    public static void refreshNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdNetworkItemHandler) {
            ((BdNetworkItemHandler) handler).refresh();
        }
    }

    public static String getNetworkDisplayName(EntityPlayerMP player) {
        // BD 0.1.7.x 没有公开的网络自定义名称接口。
        return "Beyond Dimensions Network";
    }

    @Nullable
    private static LegacyApi resolveApi() {
        if (!Loader.isModLoaded(MOD_ID)) {
            unavailableReason = "Beyond Dimensions 未安装";
            return null;
        }
        if (apiLookupAttempted) {
            return api;
        }
        synchronized (RtsBdCompat.class) {
            if (apiLookupAttempted) {
                return api;
            }
            try {
                ClassLoader loader = RtsBdCompat.class.getClassLoader();
                Class<?> dimensionsNet = Class.forName(DIMENSIONS_NET, false, loader);
                Class<?> unifiedStorage = Class.forName(UNIFIED_STORAGE, false, loader);
                Class<?> itemStackType = Class.forName(ITEM_STACK_TYPE, false, loader);
                Class<?> itemHandler = Class.forName(ITEM_HANDLER, false, loader);
                Class<?> fluidHandler = Class.forName(FLUID_HANDLER, false, loader);
                LegacyApi resolved = new LegacyApi(
                        dimensionsNet.getMethod("getNetFromPlayer", EntityPlayer.class),
                        dimensionsNet.getMethod("getUnifiedStorage"),
                        itemHandler.getConstructor(unifiedStorage),
                        fluidHandler.getConstructor(unifiedStorage),
                        itemStackType.getConstructor(ItemStack.class),
                        unifiedStorage.getMethod("getStackByStack",
                                Class.forName(
                                        "com.wintercogs.beyonddimensions.DataBase.Stack.IStackType",
                                        false, loader)),
                        itemStackType.getMethod("getStackAmount"));
                api = resolved;
                unavailableReason = "";
            } catch (ClassNotFoundException | NoSuchMethodException | SecurityException
                    | LinkageError failure) {
                api = null;
                unavailableReason = "Beyond Dimensions 1.12.2 API 不兼容: "
                        + failure.getClass().getSimpleName();
            } finally {
                apiLookupAttempted = true;
            }
            return api;
        }
    }

    /** 精确对应 BD 0.1.7.x 的公开 1.12.2 API 形状。 */
    private static final class LegacyApi {
        private final Method getNetFromPlayer;
        private final Method getUnifiedStorage;
        private final Constructor<?> itemHandlerConstructor;
        private final Constructor<?> fluidHandlerConstructor;
        private final Constructor<?> itemStackTypeConstructor;
        private final Method getStackByStack;
        private final Method getStackAmount;

        private LegacyApi(Method getNetFromPlayer, Method getUnifiedStorage,
                Constructor<?> itemHandlerConstructor, Constructor<?> fluidHandlerConstructor,
                Constructor<?> itemStackTypeConstructor, Method getStackByStack,
                Method getStackAmount) {
            this.getNetFromPlayer = getNetFromPlayer;
            this.getUnifiedStorage = getUnifiedStorage;
            this.itemHandlerConstructor = itemHandlerConstructor;
            this.fluidHandlerConstructor = fluidHandlerConstructor;
            this.itemStackTypeConstructor = itemStackTypeConstructor;
            this.getStackByStack = getStackByStack;
            this.getStackAmount = getStackAmount;
        }

        @Nullable
        private Object getNetwork(EntityPlayerMP player) {
            try {
                return getNetFromPlayer.invoke(null, player);
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException
                    | LinkageError failure) {
                unavailableReason = "Beyond Dimensions 网络查询失败: "
                        + failure.getClass().getSimpleName();
                return null;
            }
        }

        @Nullable
        private Object getStorage(EntityPlayerMP player) {
            Object network = getNetwork(player);
            if (network == null) {
                return null;
            }
            try {
                return getUnifiedStorage.invoke(network);
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException
                    | LinkageError failure) {
                unavailableReason = "Beyond Dimensions 存储查询失败: "
                        + failure.getClass().getSimpleName();
                return null;
            }
        }

        private long reportedCount(Object storage, ItemStack displayed) {
            if (displayed == null || displayed.isEmpty()) {
                return 0L;
            }
            try {
                // 构造器会保留 item、metadata 与完整 NBT；查询不会把同物品的不同变体合并。
                Object key = itemStackTypeConstructor.newInstance(displayed.copy());
                Object stored = getStackByStack.invoke(storage, key);
                if (stored == null) {
                    return 0L;
                }
                Object amount = getStackAmount.invoke(stored);
                return amount instanceof Number ? Math.max(0L, ((Number) amount).longValue()) : 0L;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | RuntimeException | LinkageError failure) {
                return Math.max(0, displayed.getCount());
            }
        }
    }

    /**
     * 包装 BD 自带的真实 Forge handler，同时补上 RTS 的任意槽和 long 数量协议。
     * 所有返回值都来自 BD handler 本身，所以 metadata/NBT、simulate 与 remainder 语义不会丢失。
     */
    private static final class BdNetworkItemHandler implements IItemHandler,
            ReportedCountItemHandler, DirectExtractHandler, AnySlotInsertItemHandler {
        private IItemHandler delegate;
        private Object storage;
        private LegacyApi api;

        private BdNetworkItemHandler(IItemHandler delegate, Object storage, LegacyApi api) {
            this.delegate = delegate;
            this.storage = storage;
            this.api = api;
        }

        @Override
        public int getSlots() {
            return delegate == null ? 0 : delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate == null ? ItemStack.EMPTY : delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (delegate == null || stack == null || stack.isEmpty()) {
                return stack == null ? ItemStack.EMPTY : stack;
            }
            // BD 0.1.7.x 的 ItemStackType 构造器持有传入对象；传副本避免其返回 remainder 时改写调用方。
            return delegate.insertItem(slot, stack.copy(), simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate == null || amount <= 0
                    ? ItemStack.EMPTY : delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate == null ? 0 : delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate != null && delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack tryExtractItem(Item target, int amount, boolean simulate) {
            if (delegate == null || target == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < delegate.getSlots(); slot++) {
                ItemStack candidate = delegate.getStackInSlot(slot);
                if (candidate.isEmpty() || candidate.getItem() != target) {
                    continue;
                }
                ItemStack extracted = delegate.extractItem(slot, amount, simulate);
                if (!extracted.isEmpty()) {
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            // BD 的 1.12 handler 明确忽略 slot 参数并直接调用 UnifiedStorage.insert。
            return insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            return tryExtractItem(targetItem, amount, simulate);
        }

        @Override
        public long getReportedCount(int slot) {
            if (delegate == null || storage == null || api == null) {
                return 0L;
            }
            return api.reportedCount(storage, delegate.getStackInSlot(slot));
        }

        private void refresh() {
            // 真实 handler 不缓存槽列表；只要尚未 release，下一次调用自然读取最新 UnifiedStorage。
        }

        private void release() {
            delegate = null;
            storage = null;
            api = null;
        }
    }
}
