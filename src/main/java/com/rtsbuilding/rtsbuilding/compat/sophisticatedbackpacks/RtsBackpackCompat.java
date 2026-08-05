package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

/**
 * 1.7.10 的安全空适配器。
 *
 * <p>Retro Sophisticated Backpacks 是 1.12.2 专用依赖，GTNH 没有它对应的 Capability API。
 * 保留相同公共入口可让共享储存流程继续编译，而不会在 GTNH 启动时反射不存在的现代类。
 * 若以后为普通 1.7.10 加入背包模组支持，应新增对应模组适配器，不应恢复 1.12 Capability。</p>
 */
public final class RtsBackpackCompat {
    private RtsBackpackCompat() {
    }

    public static boolean isAvailable() { return false; }
    public static boolean isBackpackBlockEntity(TileEntity blockEntity) { return false; }
    public static Optional<UUID> getBackpackUuid(TileEntity blockEntity) { return Optional.empty(); }
    public static Optional<String> getBackpackItemId(TileEntity blockEntity) { return Optional.empty(); }
    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId) { return Optional.empty(); }
    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId, EntityPlayerMP player) {
        return Optional.empty();
    }
    public static Optional<IItemHandler> findBackpackHandlerByUuid(EntityPlayerMP player, UUID uuid) {
        return Optional.empty();
    }
    public static boolean isBackpackItem(ItemStack stack) { return false; }
}
