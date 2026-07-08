package com.rtsbuilding.rtsbuilding.compat.integrateddynamics;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Integrated Dynamics 的挖掘兼容入口。
 *
 * <p>ID cable 不是普通 loot-table 方块，它在自己的 cable/part 组件逻辑里负责
 * 移除网络节点、生成 cable 掉落和播放声音。RTS 远程挖掘如果只走
 * {@code ServerPlayerGameMode#destroyBlock}，很容易因为玩家真实视线没有命中
 * cable center 而退回普通方块破坏路径，结果方块能没掉但不会掉 cable。</p>
 *
 * <p>本类只做可选反射调用，不让 RTSBuilding 对 Integrated Dynamics 形成硬依赖。
 * 目标不是接管 ID 的内部逻辑，而是在确认目标就是 ID cable 时，把最终移除交还给
 * ID 自己的 {@code CableHelpers.removeCable}。</p>
 */
public final class RtsIntegratedDynamicsCompat {
    private static final String MOD_ID = "integrateddynamics";
    private static final ResourceLocation CABLE_BLOCK_ID = ResourceLocation.tryParse("integrateddynamics:cable");

    private static boolean methodLookupDone;
    private static Method removeCableMethod;

    private RtsIntegratedDynamicsCompat() {
    }

    /**
     * 如果目标是 Integrated Dynamics cable，则用 ID 自己的移除逻辑处理。
     *
     * @return {@code true} 表示目标已由 ID 兼容逻辑处理，调用方不应再走普通
     *         {@code destroyBlock}。
     */
    public static boolean tryDestroyCable(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || CABLE_BLOCK_ID == null || !ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState before = level.getBlockState(pos);
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(before.getBlock());
        if (!CABLE_BLOCK_ID.equals(blockId)) {
            return false;
        }

        Method method = removeCableMethod();
        if (method == null) {
            return false;
        }
        try {
            method.invoke(null, level, pos, player);
            BlockState after = level.getBlockState(pos);
            return !after.equals(before) || !CABLE_BLOCK_ID.equals(BuiltInRegistries.BLOCK.getKey(after.getBlock()));
        } catch (IllegalAccessException | InvocationTargetException ex) {
            RtsbuildingMod.LOGGER.warn("Failed to invoke Integrated Dynamics cable removal at {}", pos, ex);
            return false;
        }
    }

    private static Method removeCableMethod() {
        if (methodLookupDone) {
            return removeCableMethod;
        }
        methodLookupDone = true;
        try {
            Class<?> helperClass = Class.forName("org.cyclops.integrateddynamics.core.helper.CableHelpers");
            removeCableMethod = helperClass.getMethod("removeCable", Level.class, BlockPos.class, Player.class);
        } catch (ClassNotFoundException | NoSuchMethodException ex) {
            RtsbuildingMod.LOGGER.debug("Integrated Dynamics cable removal helper is unavailable", ex);
            removeCableMethod = null;
        }
        return removeCableMethod;
    }
}
