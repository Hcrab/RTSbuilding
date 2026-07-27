package com.rtsbuilding.rtsbuilding.server.culling;

import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
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

    public static State load(EntityPlayerMP player) {
        if (player == null) {
            return State.EMPTY;
        }
        NBTTagCompound root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.CULLING);
        return decode(root, dimensionKey(player));
    }

    static State decode(NBTTagCompound root, String dimensionKey) {
        if (root == null || isBlank(dimensionKey)) {
            return State.EMPTY;
        }
        NBTTagCompound dimensions = root.getCompoundTag(NBT_DIMENSIONS);
        NBTTagCompound dimension = dimensions.getCompoundTag(dimensionKey);

        NBTTagList boxTags = dimension.getTagList(NBT_BOXES, Constants.NBT.TAG_COMPOUND);
        List<RtsCullingBoxSnapshot> boxes = new ArrayList<RtsCullingBoxSnapshot>(
                Math.min(MAX_BOXES, boxTags.tagCount()));
        for (int index = 0; index < boxTags.tagCount() && boxes.size() < MAX_BOXES; index++) {
            NBTTagCompound tag = boxTags.getCompoundTagAt(index);
            boxes.add(new RtsCullingBoxSnapshot(
                    BlockPos.fromLong(tag.getLong(NBT_MIN)),
                    BlockPos.fromLong(tag.getLong(NBT_MAX))));
        }

        NBTTagList revealedTags = dimension.getTagList(NBT_REVEALED, Constants.NBT.TAG_LONG);
        List<BlockPos> revealed = new ArrayList<BlockPos>(
                Math.min(MAX_REVEALED_BLOCKS, revealedTags.tagCount()));
        for (int index = 0; index < revealedTags.tagCount() && revealed.size() < MAX_REVEALED_BLOCKS; index++) {
            revealed.add(BlockPos.fromLong(((NBTTagLong) revealedTags.get(index)).getLong()));
        }
        return new State(boxes, revealed);
    }

    public static void save(EntityPlayerMP player, List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        if (player == null) {
            return;
        }
        NBTTagCompound root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.CULLING).copy();
        encode(root, dimensionKey(player), boxes, revealed);
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.CULLING, root);
    }

    static void encode(NBTTagCompound root, String dimensionKey,
            List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
        if (root == null || isBlank(dimensionKey)) {
            return;
        }
        NBTTagCompound dimensions = root.getCompoundTag(NBT_DIMENSIONS);
        NBTTagCompound dimension = new NBTTagCompound();

        NBTTagList boxTags = new NBTTagList();
        if (boxes != null) {
            for (RtsCullingBoxSnapshot box : boxes) {
                if (box == null || boxTags.tagCount() >= MAX_BOXES) {
                    continue;
                }
                NBTTagCompound tag = new NBTTagCompound();
                tag.setLong(NBT_MIN, box.min().toLong());
                tag.setLong(NBT_MAX, box.max().toLong());
                boxTags.appendTag(tag);
            }
        }
        dimension.setTag(NBT_BOXES, boxTags);

        int revealedCount = 0;
        if (revealed != null) {
            for (BlockPos pos : revealed) {
                if (pos != null && revealedCount < MAX_REVEALED_BLOCKS) revealedCount++;
            }
        }
        NBTTagList revealedTags = new NBTTagList();
        if (revealed != null) {
            for (BlockPos pos : revealed) {
                if (pos != null && revealedTags.tagCount() < revealedCount) {
                    revealedTags.appendTag(new NBTTagLong(pos.toLong()));
                }
            }
        }
        dimension.setTag(NBT_REVEALED, revealedTags);
        dimensions.setTag(dimensionKey, dimension);
        root.setTag(NBT_DIMENSIONS, dimensions);
    }

    private static String dimensionKey(EntityPlayerMP player) {
        return Integer.toString(player.dimension);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class State {
        static final State EMPTY = new State(
                Collections.<RtsCullingBoxSnapshot>emptyList(), Collections.<BlockPos>emptyList());

        private final List<RtsCullingBoxSnapshot> boxes;
        private final List<BlockPos> revealed;

        public State(List<RtsCullingBoxSnapshot> boxes, List<BlockPos> revealed) {
            this.boxes = boxes == null ? Collections.<RtsCullingBoxSnapshot>emptyList()
                    : Collections.unmodifiableList(new ArrayList<RtsCullingBoxSnapshot>(boxes));
            this.revealed = revealed == null ? Collections.<BlockPos>emptyList()
                    : Collections.unmodifiableList(new ArrayList<BlockPos>(revealed));
        }

        public List<RtsCullingBoxSnapshot> boxes() { return boxes; }
        public List<BlockPos> revealed() { return revealed; }
    }
}
