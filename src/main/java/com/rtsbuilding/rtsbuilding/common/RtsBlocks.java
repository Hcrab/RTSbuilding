package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * RTSBuilding 的方块注册所有者。
 *
 * <p>当前版本没有自定义方块，但保留与主线一致的注册入口，后续新增内容无需再次修改模组主类。
 */
public final class RtsBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, RtsbuildingMod.MODID);

    private static final Set<RegistryObject<? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    private RtsBlocks() {
    }

    public static RegistryObject<Block> simpleBlock(
            String id, BlockBehaviour.Properties properties, boolean creative) {
        RegistryObject<Block> holder = BLOCKS.register(id, () -> new Block(properties));
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    public static <T extends Block> RegistryObject<T> registerBlock(
            String id, Supplier<? extends T> factory, boolean creative) {
        RegistryObject<T> holder = BLOCKS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static Set<RegistryObject<? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    public static Collection<RegistryObject<Block>> getAllBlocks() {
        return BLOCKS.getEntries();
    }
}
