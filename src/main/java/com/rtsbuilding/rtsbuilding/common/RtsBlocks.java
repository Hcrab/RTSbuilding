package com.rtsbuilding.rtsbuilding.common;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 方块注册器 —— RTSbuilding 的所有方块在此集中注册。
 * <p>
 * 使用 {@link DeferredRegister} 进行惰性注册，确保在正确的注册阶段完成。
 * 提供 {@link #simpleBlock(String, BlockBehaviour.Properties, boolean)} 和
 * {@link #registerBlock(String, java.util.function.Supplier, boolean)} 两种工厂方法，
 * 分别用于注册普通方块和自定义方块子类。
 * 通过 {@link #getCreativeTabBlocks()} 获取需加入创造栏的方块列表。
 */
public final class RtsBlocks {

    /** 统一的方块注册表实例 */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, RtsbuildingMod.MODID);

    /** 需要自动注册到创造栏的方块集合（按注册顺序排列） */
    private static final Set<DeferredHolder<Block, ? extends Block>> CREATIVE_TAB_BLOCKS = new LinkedHashSet<>();

    // ============================================================
    //  方块定义
    // ============================================================

    // 方块注册示例（取消注释即可使用）
    // public static final DeferredHolder<Block, Block> EXAMPLE_BLOCK = simpleBlock("example_block",
    //         BlockBehaviour.Properties.of().strength(2.0f).requiresCorrectToolForDrops(),
    //         true);

    // ============================================================
    //  工厂方法
    // ============================================================

    /**
     * 注册一个简单的普通方块。
     *
     * @param id         方块的注册名
     * @param properties 方块属性（硬度、声音等）
     * @param creative   是否自动添加到创造栏标签页
     * @return 方块的 {@link DeferredHolder}
     */
    public static DeferredHolder<Block, Block> simpleBlock(String id, BlockBehaviour.Properties properties, boolean creative) {
        DeferredHolder<Block, Block> holder = BLOCKS.register(id, () -> new Block(properties));
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    /**
     * 注册任意自定义 {@link Block} 子类的方块。
     *
     * @param id       方块的注册名
     * @param factory  创建方块实例的工厂函数
     * @param creative 是否自动添加到创造栏标签页
     * @return 方块的 {@link DeferredHolder}
     */
    public static <T extends Block> DeferredHolder<Block, T> registerBlock(String id,
            java.util.function.Supplier<? extends T> factory, boolean creative) {
        DeferredHolder<Block, T> holder = BLOCKS.register(id, factory);
        if (creative) {
            CREATIVE_TAB_BLOCKS.add(holder);
        }
        return holder;
    }

    // ============================================================
    //  注册入口
    // ============================================================

    /**
     * 在模组总线上注册所有方块。
     * 应当在 {@link RtsbuildingMod} 的构造函数中调用。
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    // ============================================================
    //  工具方法
    // ============================================================

    /**
     * 获取所有标记为 {@code creative = true} 的方块集合。
     *
     * @return 不可修改的创造栏方块集合，按注册顺序排列
     */
    public static Set<DeferredHolder<Block, ? extends Block>> getCreativeTabBlocks() {
        return Collections.unmodifiableSet(CREATIVE_TAB_BLOCKS);
    }

    /**
     * 获取所有已注册方块的 {@link DeferredHolder} 列表。
     */
    public static java.util.Collection<DeferredHolder<Block, ? extends Block>> getAllBlocks() {
        return BLOCKS.getEntries();
    }

    private RtsBlocks() {
    }
}
