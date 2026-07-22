package com.rtsbuilding.rtsbuilding.server.culling;

import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 按“当前存档中的玩家 + 维度”保存范围剔除区域。
 *
 * <p>剔除只影响客户端视觉，但坐标属于世界数据，因此不能放进全局客户端设置。玩家文件天然随存档
 * 隔离；维度键再保证主世界、下界和末地不会互相套用同一组坐标。</p>
 */
public final class RtsCullingPersistence {
    private static final String NBT_DIMENSIONS = "dimensions";
    private static final String NBT_BOXES = "boxes";
    private static final String NBT_MIN = "min";
    private static final String NBT_MAX = "max";
    private static final String NBT_REVEALED = "revealed";
    private static final int MAX_BOXES = 128;
    private static final int MAX_REVEALED_BLOCKS = 4096;

    private RtsCullingPersistence() {
    }

    public static State load(ServerPlayer player) {
        if (player == null) {
            return State.EMPTY;
        }
        CompoundTag root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.CULLING);
        return decode(root, dimensionKey(player));
    }

    static State decode(CompoundTag root, String dimensionKey) {
        if (root == null || dimensionKey == null || dimensionKey.isBlank()) {
            return State.EMPTY;
        }
        CompoundTag dimensions = root.getCompound(NBT_DIMENSIONS);
        CompoundTag dimension = dimensions.getCompound(dimensionKey);

        ListTag boxTags = dimension.getList(NBT_BOXES, Tag.TAG_COMPOUND);
        List<RtsCullingBoxSnapshot> boxes = new ArrayList<>(Math.min(MAX_BOXES, boxTags.size()));
        for (int index = 0; index < boxTags.size() && boxes.size() < MAX_BOXES; index++) {
            CompoundTag tag = boxTags.getCompound(index);
            boxes.add(new RtsCullingBoxSnapshot(
                    BlockPos.of(tag.getLong(NBT_MIN)),
                    BlockPos.of(tag.getLong(NBT_MAX))));
        }

        long[] revealedValues = dimension.getLongArray(NBT_REVEALED);
        List<BlockPos> revealed = new ArrayList<>(Math.min(MAX_REVEALED_BLOCKS, revealedValues.length));
        for (int index = 0; index < revealedValues.length && revealed.size() < MAX_REVEALED_BLOCKS; index++) {
            revealed.add(BlockPos.of(revealedValues[index]));
        }
        return new State(List.copyOf(boxes), List.copyOf(revealed));
    }

    public static void save(ServerPlayer player, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        if (player == null) {
            return;
        }
        CompoundTag root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.CULLING).copy();
        encode(root, dimensionKey(player), boxes, revealed);
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.CULLING, root);
    }

    static void encode(CompoundTag root, String dimensionKey,
            List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        if (root == null || dimensionKey == null || dimensionKey.isBlank()) {
            return;
        }
        CompoundTag dimensions = root.getCompound(NBT_DIMENSIONS);
        CompoundTag dimension = new CompoundTag();

        ListTag boxTags = new ListTag();
        if (boxes != null) {
            for (RtsCullingBoxSnapshot box : boxes) {
                if (box == null || boxTags.size() >= MAX_BOXES) {
                    continue;
                }
                CompoundTag tag = new CompoundTag();
                tag.putLong(NBT_MIN, box.min().asLong());
                tag.putLong(NBT_MAX, box.max().asLong());
                boxTags.add(tag);
            }
        }
        dimension.put(NBT_BOXES, boxTags);

        long[] revealedValues = revealed == null
                ? new long[0]
                : revealed.stream().filter(java.util.Objects::nonNull)
                        .limit(MAX_REVEALED_BLOCKS).mapToLong(BlockPos::asLong).toArray();
        dimension.putLongArray(NBT_REVEALED, revealedValues);
        dimensions.put(dimensionKey, dimension);
        root.put(NBT_DIMENSIONS, dimensions);
    }

    private static String dimensionKey(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    public record State(List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        static final State EMPTY = new State(List.of(), List.of());
    }
}
