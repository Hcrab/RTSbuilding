package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 为 SOURCE_PREPARED 掉落生成一次原子的来源接管计划。
 *
 * <p>Minecraft 会在 durable root ACK 到达前自然合并相邻 ItemEntity。合并后其中一个 UUID
 * 消失、另一个实体数量增加；逐实体做“UUID + 数量完全相等”会把正常合并误判成来源丢失。
 * 本规划器按物品及组件分组，只在该组所有原始 UUID 当前可见实体的总数量仍与预期总量完全
 * 相等时接管整组。外部物品混入、玩家拾取或组件变化都会保持 fail-closed。</p>
 *
 * <p>本类只计算计划，不访问世界也不丢弃实体；调用方必须在同一服务器主线程片段内立即应用
 * {@link Plan#sourceEntityIdsToDiscard()}，随后把 claim 状态写回 TaskStore。</p>
 */
public final class BufferSourceClaimPlanner {
    private BufferSourceClaimPlanner() {
    }

    public static Plan plan(
            List<BufferEscrowEntry> entries,
            Map<UUID, ItemStack> observedSources,
            int maxStacks) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(observedSources, "observedSources");
        if (maxStacks <= 0) return Plan.empty();

        List<ClaimGroup> groups = groupPreparedEntries(entries);
        LinkedHashSet<UUID> claimed = new LinkedHashSet<>();
        LinkedHashSet<UUID> recovery = new LinkedHashSet<>();
        LinkedHashSet<UUID> discard = new LinkedHashSet<>();
        int processed = 0;

        for (ClaimGroup group : groups) {
            int groupSize = group.entries().size();
            // 相同物品实体可以互相合并，因此同组不能跨 revision 拆开接管。
            if (processed > 0 && processed + groupSize > maxStacks) break;

            int expectedCount = 0;
            int observedCount = 0;
            boolean exactComponents = true;
            List<UUID> liveSourceIds = new ArrayList<>();
            for (BufferEscrowEntry entry : group.entries()) {
                expectedCount = Math.addExact(expectedCount, entry.sourceSnapshot().getCount());
                ItemStack observed = observedSources.get(entry.sourceEntityId());
                if (observed == null || observed.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(group.template(), observed)) {
                    exactComponents = false;
                    continue;
                }
                observedCount = Math.addExact(observedCount, observed.getCount());
                liveSourceIds.add(entry.sourceEntityId());
            }

            boolean exactGroup = exactComponents && observedCount == expectedCount;
            for (BufferEscrowEntry entry : group.entries()) {
                (exactGroup ? claimed : recovery).add(entry.claimId());
            }
            if (exactGroup) discard.addAll(liveSourceIds);
            processed += groupSize;
            if (processed >= maxStacks) break;
        }
        return new Plan(claimed, recovery, discard, processed);
    }

    private static List<ClaimGroup> groupPreparedEntries(List<BufferEscrowEntry> entries) {
        List<ClaimGroup> groups = new ArrayList<>();
        Set<UUID> sourceIds = new LinkedHashSet<>();
        for (BufferEscrowEntry entry : entries) {
            if (entry.phase() != BufferEscrowPhase.SOURCE_PREPARED) continue;
            if (!sourceIds.add(entry.sourceEntityId())) {
                throw new IllegalArgumentException("同一 escrow 不能重复声明来源实体 UUID");
            }
            ClaimGroup matching = null;
            for (ClaimGroup group : groups) {
                if (ItemStack.isSameItemSameComponents(group.template(), entry.sourceSnapshot())) {
                    matching = group;
                    break;
                }
            }
            if (matching == null) {
                matching = new ClaimGroup(entry.sourceSnapshot().copyWithCount(1), new ArrayList<>());
                groups.add(matching);
            }
            matching.entries().add(entry);
        }
        return groups;
    }

    private record ClaimGroup(ItemStack template, List<BufferEscrowEntry> entries) {
    }

    public record Plan(
            Set<UUID> claimedClaimIds,
            Set<UUID> recoveryClaimIds,
            Set<UUID> sourceEntityIdsToDiscard,
            int processedStacks) {
        public Plan {
            claimedClaimIds = Set.copyOf(claimedClaimIds);
            recoveryClaimIds = Set.copyOf(recoveryClaimIds);
            sourceEntityIdsToDiscard = Set.copyOf(sourceEntityIdsToDiscard);
            if (processedStacks < 0) throw new IllegalArgumentException("processedStacks 不能为负数");
        }

        static Plan empty() {
            return new Plan(Set.of(), Set.of(), Set.of(), 0);
        }
    }
}
