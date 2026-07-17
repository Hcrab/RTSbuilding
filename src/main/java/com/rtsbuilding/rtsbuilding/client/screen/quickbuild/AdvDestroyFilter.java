package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 高级破坏过滤器抽象基类。多个过滤器之间为 OR 关系（任一匹配即破坏）。
 */
public abstract class AdvDestroyFilter {

    public enum FilterType {
        ITEM_STACK,
        MOD_ID,
        TAG
    }

    public abstract FilterType getType();

    /** 判断方块是否匹配此过滤器 */
    public abstract boolean matches(BlockState state, Level level, BlockPos pos);

    /** 用于 UI 显示简短描述 */
    public abstract String getDisplayName();

    /** 序列化为可持久化结构 */
    public abstract Serialized serialize();

    /** 反序列化 */
    public static AdvDestroyFilter deserialize(Serialized s) {
        return switch (s.type) {
            case "ITEM_STACK" -> AdvDestroyItemStackFilter.deserialize(s);
            case "MOD_ID" -> AdvDestroyModIdFilter.deserialize(s);
            case "TAG" -> AdvDestroyTagFilter.deserialize(s);
            default -> null;
        };
    }

    /** 可持久化的序列化结构 */
    public static final class Serialized {
        public String type;       // "ITEM_STACK" / "MOD_ID" / "TAG"
        public String itemId;     // ResourceLocation 字符串，e.g. "minecraft:stone"
        public String modId;      // mod namespace，e.g. "minecraft"
        public String tagId;      // tag ResourceLocation 字符串，e.g. "c:ores"

        // Gson 需要无参构造
        public Serialized() {}

        public static Serialized itemStack(String itemId) {
            Serialized s = new Serialized();
            s.type = "ITEM_STACK";
            s.itemId = itemId;
            return s;
        }

        public static Serialized modId(String modId) {
            Serialized s = new Serialized();
            s.type = "MOD_ID";
            s.modId = modId;
            return s;
        }

        public static Serialized tag(String tagId) {
            Serialized s = new Serialized();
            s.type = "TAG";
            s.tagId = tagId;
            return s;
        }
    }
}
