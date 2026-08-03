package com.rtsbuilding.rtsbuilding.compat.create;

import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Create 蓝图放置的窄兼容插头。
 *
 * <p>Create 的传送带、保险库等方块实体会保存运行时网络拓扑。原样加载结构 NBT
 * 会让新建筑继续引用旧世界的绝对坐标。本类复用 Create 自己的蓝图 NBT 写出器，
 * 并仅在 Create 不可用或接口变化时退回到已知字段清理。</p>
 *
 * <p>这个类不负责普通蓝图放置流程，也不硬依赖 Create。所有第三方调用都经过反射，
 * 因而未安装 Create 时不会触发第三方类加载。</p>
 */
public final class BlueprintCreatePlacementCompat {
    private static final String CREATE_NAMESPACE = "create";
    private static final String BELT_PATH = "belt";
    private static final int UPDATE_CLIENTS = Block.UPDATE_CLIENTS;
    private static final int UPDATE_CLIENTS_KNOWN_SHAPE =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static volatile Method prepareBlockEntityData;
    private static volatile boolean prepareMethodIncludesLevel;
    private static volatile boolean reflectionLookupAttempted;

    private BlueprintCreatePlacementCompat() {
    }

    /** Create 传送带应在整条蓝图放完后初始化，不能在每一段落地时触发邻居重算。 */
    public static int placementFlags(BlockState state) {
        if (!isCreate(state)) {
            return Block.UPDATE_ALL;
        }
        return isCreateBelt(state) ? UPDATE_CLIENTS : UPDATE_CLIENTS_KNOWN_SHAPE;
    }

    /**
     * 把来源结构 NBT 转换成 Create 认可的安全蓝图 NBT。
     *
     * <p>1.20.1 Create 使用 {@code prepareBlockEntityData(BlockState, BlockEntity)}，
     * 新版 Create 增加了 Level 参数。兼容层同时识别两种签名，减少两个主线之间的维护分叉。</p>
     */
    @Nullable
    public static CompoundTag prepareBlockEntityTag(
            ServerLevel level, BlockPos target, BlockState state, @Nullable CompoundTag original) {
        if (original == null || original.isEmpty() || !isCreate(state)) {
            return original;
        }
        CompoundTag prepared = prepareWithCreate(level, target, state, original);
        if (prepared == null) {
            prepared = fallbackSanitize(state, original);
        }
        if (isCreateBelt(state)) {
            copyIfPresent(original, prepared, "Casing");
            copyIfPresent(original, prepared, "Covered");
            copyIfPresent(original, prepared, "Dye");
        }
        return prepared;
    }

    /** NBT 应用完成后补齐 Create 标准蓝图链路使用的放置回调。 */
    public static void finishPlacement(
            ServerLevel level, BlockPos target, BlockState state, @Nullable ItemStack stack) {
        if (!isCreate(state)) {
            return;
        }
        try {
            state.getBlock().setPlacedBy(
                    level, target, state, null, stack == null ? ItemStack.EMPTY : stack);
        } catch (RuntimeException ignored) {
            // 第三方回调失败不应撤销已经成功写入世界的整个蓝图任务。
        }
    }

    @Nullable
    private static CompoundTag prepareWithCreate(
            ServerLevel level, BlockPos target, BlockState state, CompoundTag original) {
        if (!(state.getBlock() instanceof EntityBlock entityBlock)) {
            return new CompoundTag();
        }
        BlockEntity virtual = entityBlock.newBlockEntity(target, state);
        if (virtual == null) {
            return new CompoundTag();
        }
        CompoundTag loadTag = original.copy();
        loadTag.putInt("x", target.getX());
        loadTag.putInt("y", target.getY());
        loadTag.putInt("z", target.getZ());
        try {
            virtual.load(loadTag);
            Method method = resolvePrepareMethod(state);
            if (method == null) {
                return null;
            }
            Object result = prepareMethodIncludesLevel
                    ? method.invoke(null, level, state, virtual)
                    : method.invoke(null, state, virtual);
            return result instanceof CompoundTag tag ? tag.copy() : new CompoundTag();
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Method resolvePrepareMethod(BlockState state) {
        if (reflectionLookupAttempted) {
            return prepareBlockEntityData;
        }
        synchronized (BlueprintCreatePlacementCompat.class) {
            if (reflectionLookupAttempted) {
                return prepareBlockEntityData;
            }
            reflectionLookupAttempted = true;
            try {
                ClassLoader loader = state.getBlock().getClass().getClassLoader();
                Class<?> helper = Class.forName(
                        "com.simibubi.create.foundation.utility.BlockHelper", false, loader);
                try {
                    prepareBlockEntityData = helper.getMethod(
                            "prepareBlockEntityData",
                            net.minecraft.world.level.Level.class,
                            BlockState.class,
                            BlockEntity.class);
                    prepareMethodIncludesLevel = true;
                } catch (NoSuchMethodException ignored) {
                    prepareBlockEntityData = helper.getMethod(
                            "prepareBlockEntityData",
                            BlockState.class,
                            BlockEntity.class);
                    prepareMethodIncludesLevel = false;
                }
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                prepareBlockEntityData = null;
            }
            return prepareBlockEntityData;
        }
    }

    private static CompoundTag fallbackSanitize(BlockState state, CompoundTag original) {
        CompoundTag sanitized = original.copy();
        ResourceLocation id = RtsBuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id == null ? "" : id.getPath();
        if (BELT_PATH.equals(path)) {
            removeAll(sanitized,
                    "Controller", "IsController", "Length", "Index", "Inventory",
                    "Speed", "NeedsSpeedUpdate");
        } else if ("item_vault".equals(path) || "fluid_tank".equals(path)) {
            removeAll(sanitized,
                    "Controller", "LastKnownPos", "Length", "Size", "Inventory", "StorageType");
        }
        return sanitized;
    }

    private static void removeAll(CompoundTag tag, String... keys) {
        for (String key : keys) {
            tag.remove(key);
        }
    }

    private static void copyIfPresent(CompoundTag source, CompoundTag target, String key) {
        if (source.contains(key) && source.get(key) != null) {
            target.put(key, source.get(key).copy());
        }
    }

    private static boolean isCreate(BlockState state) {
        if (state == null) {
            return false;
        }
        ResourceLocation id = RtsBuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && CREATE_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isCreateBelt(BlockState state) {
        ResourceLocation id = RtsBuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null
                && CREATE_NAMESPACE.equals(id.getNamespace())
                && BELT_PATH.equals(id.getPath());
    }
}
