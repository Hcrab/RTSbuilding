package com.rtsbuilding.rtsbuilding.client.screen.shape;

import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 形状破坏候选的纯分类 owner。
 * <p>
 * 本类按输入顺序去重并拆分“真实可破坏方块”和“仅用于显示所选范围的空包络方块”。
 * 它不读取 Minecraft 世界、采掘等级、边界、配置或网络；生产适配器通过
 * {@link Predicate} 注入当前世界里的可破坏判断，服务端最终权限仍由服务端校验。
 */
public final class ShapeDestroyTargetClassifier {
    private ShapeDestroyTargetClassifier() {
    }

    public static final class Selection {
        private final List<BlockPos> breakableBlocks;
        private final List<BlockPos> envelopeBlocks;

        public Selection(List<BlockPos> breakableBlocks, List<BlockPos> envelopeBlocks) {
            this.breakableBlocks = immutableDistinct(breakableBlocks);
            this.envelopeBlocks = immutableDistinct(envelopeBlocks);
        }

        public List<BlockPos> breakableBlocks() { return this.breakableBlocks; }
        public List<BlockPos> envelopeBlocks() { return this.envelopeBlocks; }

        public boolean isEmpty() {
            return this.breakableBlocks.isEmpty() && this.envelopeBlocks.isEmpty();
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return java.util.Objects.equals(this.breakableBlocks, that.breakableBlocks)
                    && java.util.Objects.equals(this.envelopeBlocks, that.envelopeBlocks);
        }
        @Override public int hashCode() { return java.util.Objects.hash(this.breakableBlocks, this.envelopeBlocks); }
    }

    public static Selection classify(
            List<BlockPos> rawTargets,
            Predicate<BlockPos> breakableTarget) {
        List<BlockPos> breakable = breakableTargets(rawTargets, breakableTarget);
        return new Selection(breakable, envelopeTargets(rawTargets, breakable));
    }

    public static List<BlockPos> breakableTargets(
            List<BlockPos> targets,
            Predicate<BlockPos> breakableTarget) {
        if (targets == null || targets.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Predicate<BlockPos> predicate =
                breakableTarget == null ? ignored -> true : breakableTarget;
        LinkedHashSet<BlockPos> breakable = new LinkedHashSet<>(targets.size());
        for (BlockPos pos : targets) {
            if (pos != null && predicate.test(pos)) {
                breakable.add(pos);
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockPos>(breakable));
    }

    public static List<BlockPos> envelopeTargets(
            List<BlockPos> rawTargets,
            List<BlockPos> breakableTargets) {
        if (rawTargets == null || rawTargets.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        Set<BlockPos> breakable = new HashSet<>();
        if (breakableTargets != null) {
            for (BlockPos pos : breakableTargets) {
                if (pos != null) {
                    breakable.add(pos);
                }
            }
        }
        LinkedHashSet<BlockPos> envelope = new LinkedHashSet<>(rawTargets.size());
        for (BlockPos pos : rawTargets) {
            if (pos != null && !breakable.contains(pos)) {
                envelope.add(pos);
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockPos>(envelope));
    }

    private static List<BlockPos> immutableDistinct(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<BlockPos> distinct = new LinkedHashSet<>(positions.size());
        for (BlockPos pos : positions) {
            if (pos != null) {
                distinct.add(pos);
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<BlockPos>(distinct));
    }
}
