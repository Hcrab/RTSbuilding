package com.rtsbuilding.rtsbuilding.platform;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class Platform {

    private Platform() {}

    // ======================================================================
    //  Registry
    // ======================================================================

    public static <T> DeferredRegister<T> createRegister(Registry<T> registry) {
        return DeferredRegister.create(registry, RtsbuildingMod.MODID);
    }

    public static DeferredRegister<Block> blockRegister() {
        return DeferredRegister.create(Registries.BLOCK, RtsbuildingMod.MODID);
    }

    public static DeferredRegister<Item> itemRegister() {
        return DeferredRegister.create(Registries.ITEM, RtsbuildingMod.MODID);
    }

    @SuppressWarnings("unchecked")
    public static DeferredRegister<EntityType<?>> entityRegister() {
        return (DeferredRegister<EntityType<?>>) (DeferredRegister<?>)
                DeferredRegister.create(Registries.ENTITY_TYPE, RtsbuildingMod.MODID);
    }

    public static DeferredRegister<BlockEntityType<?>> blockEntityRegister() {
        return (DeferredRegister<BlockEntityType<?>>) (DeferredRegister<?>)
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RtsbuildingMod.MODID);
    }

    public static DeferredRegister<CreativeModeTab> creativeTabRegister() {
        return DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RtsbuildingMod.MODID);
    }

    public static void registerModBus(IEventBus modBus, DeferredRegister<?>... registers) {
        for (var r : registers) r.register(modBus);
    }

    // ======================================================================
    //  Creative tab
    // ======================================================================

    public static DeferredHolder<CreativeModeTab, CreativeModeTab> registerCreativeTab(
            String id, Supplier<ItemStack> icon, CreativeModeTab.DisplayItemsGenerator items) {
        return creativeTabRegister().register(id, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + id))
                .icon(() -> icon.get())
                .displayItems(items)
                .build());
    }

    // ======================================================================
    //  Config
    // ======================================================================

    public static void registerConfig(ModContainer container, ModConfig.Type type, IConfigSpec spec, String fileName) {
        container.registerConfig(type, spec, fileName);
    }

    // ======================================================================
    //  Capabilities — item handler
    // ======================================================================

    /**
     * Get the item handler for a block entity at the given position and face.
     * On Fabric, this would use {@code BlockApiLookup} from Fabric API.
     */
    @Nullable
    public static IItemHandler getItemHandler(Level level, BlockPos pos, @Nullable Direction face) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
    }

    /**
     * Get the item handler from a block entity directly.
     */
    @Nullable
    public static IItemHandler getItemHandler(BlockEntity be, @Nullable Direction face) {
        return be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), face);
    }

    /**
     * Get the fluid handler for a block entity at the given position and face.
     * On Fabric, this would use {@code BlockApiLookup} for fluid storage.
     */
    @Nullable
    public static IFluidHandler getFluidHandler(Level level, BlockPos pos, @Nullable Direction face) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
    }

    /**
     * Get the fluid handler from an item stack (e.g. bucket, tank item).
     */
    @Nullable
    public static IFluidHandler getFluidHandler(ItemStack stack) {
        return stack.getCapability(Capabilities.FluidHandler.ITEM);
    }

    // ======================================================================
    //  Networking
    // ======================================================================

    /**
     * Send a custom payload packet to a specific player.
     */
    public static void sendPacket(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
