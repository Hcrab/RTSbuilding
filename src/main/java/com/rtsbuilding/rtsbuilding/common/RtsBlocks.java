package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import cpw.mods.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** 1.12.2 方块注册器；方块与对应 ItemBlock 仍保持同一注册 ID。 */
public final class RtsBlocks {
    private static final List<Handle<? extends Block>> ALL_BLOCKS = new ArrayList<>();
    private static final Set<Handle<? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    public static Handle<Block> simpleBlock(String id, Material material, boolean creative) {
        return registerBlock(id, () -> new LegacySimpleBlock(material), creative);
    }

    public static <T extends Block> Handle<T> registerBlock(String id, Supplier<? extends T> factory,
            boolean creative) {
        T block = factory.get();
        block.setBlockName(RtsbuildingMod.MODID + "." + id);
        if (creative) block.setCreativeTab(RtsCreativeTabs.RTSBUILDING_TAB);
        Handle<T> handle = new Handle<>(id, block);
        ALL_BLOCKS.add(handle);
        if (creative) CREATIVE_TAB_BLOCKS.add(handle);
        return handle;
    }

    public static Set<Handle<? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    public static Collection<Handle<? extends Block>> getAllBlocks() {
        return Collections.unmodifiableList(ALL_BLOCKS);
    }

    public static synchronized void register() {
        if (registered) return;
        for (Handle<? extends Block> handle : ALL_BLOCKS) {
            GameRegistry.registerBlock(handle.get(), handle.id());
        }
        registered = true;
    }

    private static boolean registered;

    /** 只公开受保护构造器，不添加额外方块行为。 */
    private static final class LegacySimpleBlock extends Block {
        private LegacySimpleBlock(Material material) {
            super(material);
        }
    }

    public static final class Handle<T> {
        private final String id;
        private final T value;

        private Handle(String id, T value) {
            this.id = id;
            this.value = value;
        }

        public String id() { return id; }
        public T get() { return value; }
    }

    private RtsBlocks() {
    }
}
