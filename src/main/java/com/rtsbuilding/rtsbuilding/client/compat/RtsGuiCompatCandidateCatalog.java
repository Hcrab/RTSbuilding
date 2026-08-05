package com.rtsbuilding.rtsbuilding.client.compat;

import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.World;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从当前真实运行时注册表发现可能拥有方块 GUI 的候选。
 *
 * <p>目录只做保守筛选，不声称“有方块实体就一定有 GUI”。矩阵会先做近距实测，
 * 只有确实打开屏幕的候选才进入远距保活测试。这样既覆盖元数据机器方块，也不会
 * 把管线、线缆和纯数据方块当成兼容失败。</p>
 */
@SideOnly(Side.CLIENT)
final class RtsGuiCompatCandidateCatalog {
    private RtsGuiCompatCandidateCatalog() {
    }

    static List<Candidate> discover() {
        Map<Class<?>, Boolean> activationCache = new HashMap<Class<?>, Boolean>();
        List<Candidate> result = new ArrayList<Candidate>();
        for (Block block : RtsRegistries.BLOCKS.getValuesCollection()) {
            ResourceLocation id = block == null ? null
                    : com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS.getKey(block);
            if (id == null || block == Blocks.air || Item.getItemFromBlock(block) == null
                    || "rtsbuilding".equals(id.getResourceDomain())) {
                continue;
            }
            boolean overridesActivation = overridesActivation(block, activationCache);
            for (Integer meta : metas(block)) {
                try {
                    BlockState state = BlockState.of(block, meta.intValue());
                    boolean tileEntity = block.hasTileEntity(state.getMetadata());
                    if (tileEntity || overridesActivation) {
                        result.add(new Candidate(id.toString(), meta.intValue(),
                                block.getClass().getName(), tileEntity, overridesActivation));
                    }
                } catch (RuntimeException | LinkageError ignored) {
                    // 某些技术方块拒绝孤立构造；它们留给结构化人工矩阵，不阻断整包发现。
                }
            }
        }
        Collections.sort(result, Comparator
                .comparing(Candidate::blockId)
                .thenComparingInt(Candidate::meta));
        return result;
    }

    private static Set<Integer> metas(Block block) {
        Set<Integer> metas = new LinkedHashSet<Integer>();
        // 1.7.10 没有统一的 BlockStateContainer；运行时探针逐个验证 0..15 比猜测更可靠。
        for (int meta = 0; meta <= 15; meta++) metas.add(Integer.valueOf(meta));
        return metas;
    }

    private static boolean overridesActivation(Block block, Map<Class<?>, Boolean> cache) {
        Class<?> type = block.getClass();
        Boolean cached = cache.get(type);
        if (cached != null) return cached.booleanValue();
        boolean value = false;
        try {
            Method method = type.getMethod("onBlockActivated", World.class,
                    int.class, int.class, int.class,
                    net.minecraft.entity.player.EntityPlayer.class, int.class,
                    float.class, float.class, float.class);
            value = method.getDeclaringClass() != Block.class;
        } catch (NoSuchMethodException | SecurityException ignored) {
            value = false;
        }
        cache.put(type, Boolean.valueOf(value));
        return value;
    }

    static final class Candidate {
        private final String blockId;
        private final int meta;
        private final String blockClass;
        private final boolean tileEntity;
        private final boolean overridesActivation;

        Candidate(String blockId, int meta, String blockClass,
                boolean tileEntity, boolean overridesActivation) {
            this.blockId = blockId;
            this.meta = meta;
            this.blockClass = blockClass;
            this.tileEntity = tileEntity;
            this.overridesActivation = overridesActivation;
        }

        String key() { return blockId + "@" + meta; }
        String blockId() { return blockId; }
        int meta() { return meta; }
        String blockClass() { return blockClass; }
        boolean tileEntity() { return tileEntity; }
        boolean overridesActivation() { return overridesActivation; }
        String namespace() {
            int colon = blockId.indexOf(':');
            return colon <= 0 ? "unknown" : blockId.substring(0, colon);
        }
    }
}
