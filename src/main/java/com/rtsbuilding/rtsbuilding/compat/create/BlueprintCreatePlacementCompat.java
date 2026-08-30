package com.rtsbuilding.rtsbuilding.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * <p>Create 的结构蓝图不会把传送带、保险库等方块实体的运行时网络数据原样写回世界：
 * 它先通过自己的安全 NBT 写出器重建可复制配置，并且放置传送带时刻意不触发邻居更新。
 * RTS 导入的原版结构 NBT 可能仍包含旧世界的绝对 {@code Controller}/{@code LastKnownPos}，
 * 若直接加载就会让新建筑继续引用旧坐标。</p>
 *
 * <p>本类只在方块命名空间为 {@code create} 时生效。通过反射调用 Create 已公开的
 * {@code BlockHelper.prepareBlockEntityData}，因此 RTS 本身不对 Create 建立硬依赖；
 * Create 不存在时不会加载任何第三方类型。</p>
 */
public final class BlueprintCreatePlacementCompat {
    private static final String CREATE_NAMESPACE = "create";
    private static final String BELT_PATH = "belt";
    private static final int UPDATE_CLIENTS = Block.UPDATE_CLIENTS;
    private static final int UPDATE_CLIENTS_KNOWN_SHAPE =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static volatile Method prepareBlockEntityData;
    private static volatile boolean reflectionLookupAttempted;

    private BlueprintCreatePlacementCompat() {
    }

    /** Create 的传送带必须等整条蓝图放完后自行初始化，不能在每一段落地时通知邻居。 */
    public static int placementFlags(BlockState state) {
        if (!isCreate(state)) {
            return Block.UPDATE_ALL;
        }
        return isCreateBelt(state) ? UPDATE_CLIENTS : UPDATE_CLIENTS_KNOWN_SHAPE;
    }

    /**
     * 把原始结构 NBT 转成 Create 自己认可的安全蓝图 NBT。
     * 反射不可用时只清理已确认会携带旧绝对坐标的传送带/多方块运行时字段。
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

    /** Create 的标准蓝图链路会在 NBT 应用后补一次 setPlacedBy，以便模组完成连接初始化。 */
    public static void finishPlacement(
            ServerLevel level, BlockPos target, BlockState state, @Nullable ItemStack stack) {
        if (!isCreate(state)) {
            return;
        }
        try {
            state.getBlock().setPlacedBy(
                    level, target, state, null, stack == null ? ItemStack.EMPTY : stack);
        } catch (RuntimeException ignored) {
            // 第三方放置回调不应把已经成功写入世界的整个蓝图任务打断。
        }
    }

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
            virtual.loadWithComponents(loadTag, level.registryAccess());
            Method method = resolvePrepareMethod(state);
            if (method == null) {
                return null;
            }
            Object result = method.invoke(null, level, state, virtual);
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
                prepareBlockEntityData = helper.getMethod(
                        "prepareBlockEntityData",
                        net.minecraft.world.level.Level.class,
                        BlockState.class,
                        BlockEntity.class);
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                prepareBlockEntityData = null;
            }
            return prepareBlockEntityData;
        }
    }

    private static CompoundTag fallbackSanitize(BlockState state, CompoundTag original) {
        CompoundTag sanitized = original.copy();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
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
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && CREATE_NAMESPACE.equals(id.getNamespace());
    }

    private static boolean isCreateBelt(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null
                && CREATE_NAMESPACE.equals(id.getNamespace())
                && BELT_PATH.equals(id.getPath());
    }
}
