package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 蓝图世界虚影的唯一数据模型。
 *
 * <p>它只保存已经生成的方块快照、材料是否齐备以及是否因上限截断，不读取面板选择、
 * 不裁剪 RTS 边界，也不执行渲染。生产屏幕与世界渲染器直接共享本记录，避免每帧在两种
 * 同名 Preview 之间重复包装。</p>
 */
public final class BlueprintGhostPreview {
    public static final BlueprintGhostPreview EMPTY =
            new BlueprintGhostPreview(Collections.<BlueprintGhostBlock>emptyList(), false, false);

    private final List<BlueprintGhostBlock> blocks;
    private final boolean materialsReady;
    private final boolean truncated;

    public BlueprintGhostPreview(List<BlueprintGhostBlock> blocks, boolean materialsReady, boolean truncated) {
        this.blocks = blocks == null ? Collections.<BlueprintGhostBlock>emptyList()
                : Collections.unmodifiableList(new ArrayList<BlueprintGhostBlock>(blocks));
        this.materialsReady = materialsReady;
        this.truncated = truncated;
    }

    public List<BlueprintGhostBlock> blocks() { return blocks; }
    public boolean materialsReady() { return materialsReady; }
    public boolean truncated() { return truncated; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlueprintGhostPreview)) return false;
        BlueprintGhostPreview that = (BlueprintGhostPreview) other;
        return materialsReady == that.materialsReady && truncated == that.truncated
                && Objects.equals(blocks, that.blocks);
    }

    @Override public int hashCode() {
        return Objects.hash(blocks, Boolean.valueOf(materialsReady), Boolean.valueOf(truncated));
    }

    @Override public String toString() {
        return "BlueprintGhostPreview[blocks=" + blocks + ", materialsReady=" + materialsReady
                + ", truncated=" + truncated + "]";
    }
}
