package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.common.blueprint.model.BlueprintFormat;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Minecraft 1.12.2 没有 Mojang GameTest 框架，因此这里提供可直接由 Java 8 执行的自检入口。
 *
 * <p>{@code --portable} 会执行不需要启动世界的真实生产契约；{@code --strict} 还会把所有
 * 尚缺专用服务端夹具的原 GameTest 视为阻断并返回失败。这样既不会把未执行的世界测试算作
 * 通过，也不会在移植期间丢失原测试清单。</p>
 *
 * <p>示例：{@code java -cp <forge+mod classpath> com.rtsbuilding.rtsbuilding.gametest.RtsServerGameTests --portable}</p>
 */
public final class RtsServerGameTests {
    private static final int EXPECTED_PORTABLE_SCENARIOS = 5;
    private static final int EXPECTED_WORLD_SCENARIOS = 33;
    private static final String WORLD_FIXTURE =
            "需要 Forge 1.12.2 专用服务器世界、假玩家 tick 驱动及对应生产包完成移植";

    private RtsServerGameTests() {}

    private interface Check { void run() throws Exception; }

    public enum Outcome { PASS, BLOCKED, FAILED }

    public static final class Scenario {
        private final String id;
        private final String group;
        private final String intent;
        private final String requirement;
        private final Check check;

        private Scenario(String id, String group, String intent, String requirement, Check check) {
            this.id = id;
            this.group = group;
            this.intent = intent;
            this.requirement = requirement;
            this.check = check;
        }

        public String id() { return id; }
        public String group() { return group; }
        public String intent() { return intent; }
        public String requirement() { return requirement; }
        public boolean isPortable() { return check != null; }
    }

    public static final class Result {
        private final Scenario scenario;
        private final Outcome outcome;
        private final String detail;

        private Result(Scenario scenario, Outcome outcome, String detail) {
            this.scenario = scenario;
            this.outcome = outcome;
            this.detail = detail;
        }

        public Scenario scenario() { return scenario; }
        public Outcome outcome() { return outcome; }
        public String detail() { return detail; }
    }

    public static void main(String[] args) throws Exception {
        String mode = args == null || args.length == 0 ? "--portable" : args[0];
        if ("--list".equals(mode)) {
            printInventory();
            return;
        }
        boolean strict = "--strict".equals(mode);
        if (!strict && !"--portable".equals(mode)) {
            throw new IllegalArgumentException("Usage: --portable | --strict | --list");
        }
        List<Result> results = run(strict);
        int passed = count(results, Outcome.PASS);
        int blocked = count(results, Outcome.BLOCKED);
        int failed = count(results, Outcome.FAILED);
        System.out.println("RTS_112_SELFTEST_SUMMARY pass=" + passed + " blocked=" + blocked + " failed=" + failed
                + " mode=" + (strict ? "strict" : "portable"));
        if (failed > 0) throw new AssertionError("RTS 1.12.2 self-test has " + failed + " failed checks");
        if (strict && blocked > 0) {
            throw new IllegalStateException("RTS 1.12.2 strict self-test still has " + blocked + " blocked scenarios");
        }
        if (!strict && passed == 0) throw new IllegalStateException("No portable production contracts executed");
    }

    public static List<Result> run(boolean strict) {
        List<Result> results = new ArrayList<Result>();
        for (Scenario scenario : scenarios()) {
            if (scenario.check == null) {
                Result result = new Result(scenario, Outcome.BLOCKED, scenario.requirement);
                results.add(result);
                if (strict) print(result);
                continue;
            }
            try {
                scenario.check.run();
                Result result = new Result(scenario, Outcome.PASS, "ok");
                results.add(result);
                print(result);
            } catch (Throwable failure) {
                Result result = new Result(scenario, Outcome.FAILED,
                        failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
                results.add(result);
                print(result);
            }
        }
        if (!strict) {
            System.out.println("DEFERRED_WORLD_SCENARIOS=" + count(results, Outcome.BLOCKED)
                    + " (run --strict to enumerate and fail on them)");
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * 执行一个具名契约，供 1.12 专用服务器测试命令使用。
     *
     * <p>尚未接上真实世界夹具的场景必须返回 {@link Outcome#BLOCKED}，不能因为命令入口存在
     * 就伪装成通过。未知名称则作为调用错误抛出，便于自动化脚本判定拼写或清单漂移。</p>
     */
    public static Result runScenario(String id) {
        Scenario selected = null;
        for (Scenario scenario : scenarios()) {
            if (scenario.id.equals(id)) {
                selected = scenario;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("Unknown RTS 1.12.2 test scenario: " + id);
        }
        if (selected.check == null) {
            return new Result(selected, Outcome.BLOCKED, selected.requirement);
        }
        try {
            selected.check.run();
            return new Result(selected, Outcome.PASS, "ok");
        } catch (Throwable failure) {
            return new Result(selected, Outcome.FAILED,
                    failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage()));
        }
    }

    public static List<Scenario> scenarios() {
        List<Scenario> list = new ArrayList<Scenario>();
        list.add(portable("portable.task_identity", "identity",
                "同一 owner/submission 幂等，不同玩家隔离", new Check() {
                    @Override public void run() { checkTaskIdentity(); }
                }));
        list.add(portable("portable.legacy_submission", "identity",
                "旧任务迁移标识确定且可解析", new Check() {
                    @Override public void run() { checkLegacySubmission(); }
                }));
        list.add(portable("portable.wire_ordinals", "protocol",
                "蓝图、任务、工作流和排序枚举的线序保持稳定", new Check() {
                    @Override public void run() { checkWireOrdinals(); }
                }));
        list.add(portable("portable.nbt_round_trip", "persistence",
                "1.12 NBT 压缩往返保留 UUID、维度和提交标识", new Check() {
                    @Override public void run() throws Exception { checkNbtRoundTrip(); }
                }));
        list.add(portable("portable.scenario_inventory", "harness",
                "原 1.21 世界 GameTest 清单完整且名称唯一", new Check() {
                    @Override public void run() { checkScenarioInventory(); }
                }));

        addWorld(list, "installedPluginIsDurableBeforeAutomaticSaveTick", "plugin",
                "插件安装在自动刷盘前已经持久化");
        addWorld(list, "remoteControlReinstallRestoresMiningFeaturesWithoutBecomingTool", "plugin",
                "遥控插件卸载重装恢复依赖能力且不被当作工具");
        addWorld(list, "areaDestroyStoneWithoutHarvestTierShowsWarning", "progression",
                "缺少采掘等级时石头范围破坏被拒绝");
        addWorld(list, "areaDestroySnowWithoutHarvestTierStillWorks", "progression",
                "雪类目标不应被采掘等级误拦截");
        addWorld(list, "areaDestroyUsesHeldNetheritePickaxeWithoutToolLease", "mining",
                "范围破坏冻结主手工具槽且不创建错误租约");
        addWorld(list, "chainMineSnowWithoutHarvestTierStillWorks", "mining",
                "连锁采雪不依赖错误的采掘等级");
        addWorld(list, "rtsEmptyHandRightClickOpensChest", "interaction",
                "RTS 空手右键保持原版箱子交互");
        addWorld(list, "linkedStorageCountsChestContents", "storage",
                "链接存储准确聚合箱子内容");
        addWorld(list, "storeHotbarSlotMovesItemsIntoLinkedChest", "storage",
                "快捷栏真实物品栈移动到链接箱子");
        addWorld(list, "placeBatchBuildsBlocksInWorld", "placement",
                "批量放置在世界中落块");
        addWorld(list, "fiveRtsPlayersKeepIndependentSessions", "multiplayer",
                "五玩家会话隔离");
        addWorld(list, "fivePlayersPlaceBatchesWithoutCrossTalk", "multiplayer",
                "五玩家批量放置不串线");
        addWorld(list, "fivePlayersAreaDestroyWithoutCrossTalk", "multiplayer",
                "五玩家范围破坏不串线");
        addWorld(list, "repeatedAreaDestroyBatchesDoNotAccumulateDelay", "task",
                "重复范围任务不累积启动延迟");
        addWorld(list, "areaDestroyAutoStoresDropsIntoLinkedChest", "mining",
                "范围破坏掉落自动回收到链接存储");
        addWorld(list, "underwaterAreaDestroyAutoStoresDropsIntoLinkedChest", "mining",
                "水下范围破坏仍准确回收掉落");
        addWorld(list, "singlePlacementWithoutPresetKeepsVanillaStairFacing", "placement",
                "无预设时保留原版楼梯朝向");
        addWorld(list, "singlePlacementAppliesSelectedBlockStatePreset", "placement",
                "单方块放置应用所选状态预设");
        addWorld(list, "singlePlacementWithoutPresetUsesVanillaBottomSlab", "placement",
                "无预设时使用原版下半砖语义");
        addWorld(list, "singlePlacementOverridesVanillaSlabHitHalf", "placement",
                "预设可覆盖原版半砖点击面");
        addWorld(list, "quickBuildPlacementOverridesVanillaSlabHitHalf", "placement",
                "快速建造保持半砖预设");
        addWorld(list, "creativeQuickBuildOverwriteReplacesOccupiedBlock", "placement",
                "创造模式快速建造允许覆盖");
        addWorld(list, "survivalQuickBuildCannotSpoofOverwrite", "placement",
                "生存模式不能伪造创造覆盖");
        addWorld(list, "restoredTaskWorkflowIdDoesNotSwallowStatefulPlacement", "workflow",
                "恢复后的 workflow id 不吞掉带状态放置");
        addWorld(list, "chainMiningAdvancesContinuouslyAndAutoStoresEveryDrop", "mining",
                "连锁挖掘连续推进并回收所有掉落");
        addWorld(list, "queuedChainMiningStartsWithIndependentProgress", "mining",
                "排队连锁任务进度独立");
        addWorld(list, "overlappingChainMiningCompletesWithoutDuplicateDrops", "mining",
                "重叠连锁挖掘不复制掉落");
        addWorld(list, "singleLinkedChestJunkSearchAndPaginationStayCorrect", "storage",
                "单箱大量杂物搜索与分页正确");
        addWorld(list, "manyLinkedChestsJunkSearchCacheAndDirtyRefreshStayCorrect", "storage",
                "多箱缓存、搜索和脏刷新正确");
        addWorld(list, "durableBlueprintWaitsForRootAckThenPlacesExactlyOnce", "blueprint",
                "耐久蓝图等待根确认且只放置一次");
        addWorld(list, "denseFunnelIsBoundedAndNeverUsesAnotherDimensionTarget", "funnel",
                "密集漏斗有界且不跨维度取目标");
        addWorld(list, "placedRecoveryPreservesUnavailableClaimsAndConsumesOnlyExactLoadedClaim", "recovery",
                "放置恢复保留不可用 claim 且只消费精确已加载 claim");
        addWorld(list, "twoPlayersCanUseSameBlueprintSubmissionWithoutCrossTalk", "blueprint",
                "两玩家相同 submission 仍按 owner 隔离");
        return Collections.unmodifiableList(list);
    }

    private static Scenario portable(String id, String group, String intent, Check check) {
        return new Scenario(id, group, intent, "Java 8 + Forge 1.12.2 classpath", check);
    }

    private static void addWorld(List<Scenario> list, String id, String group, String intent) {
        list.add(new Scenario(id, group, intent, WORLD_FIXTURE, null));
    }

    private static void checkTaskIdentity() {
        UUID ownerA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID ownerB = UUID.fromString("22222222-2222-2222-2222-222222222222");
        SubmissionId submission = SubmissionId.parse("33333333-3333-3333-3333-333333333333");
        TaskId first = TaskId.fromSubmission(ownerA, submission);
        require(first.equals(TaskId.fromSubmission(ownerA, submission)), "same submission was not idempotent");
        require(!first.equals(TaskId.fromSubmission(ownerB, submission)), "different owners shared a TaskId");
        require(first.equals(TaskId.parse(first.toString())), "TaskId string round-trip failed");
    }

    private static void checkLegacySubmission() {
        UUID owner = UUID.fromString("44444444-4444-4444-4444-444444444444");
        SubmissionId first = SubmissionId.fromLegacy(owner, "placement", "save-slot-7");
        SubmissionId second = SubmissionId.fromLegacy(owner, "placement", "save-slot-7");
        require(first.equals(second), "legacy submission migration was not deterministic");
        require(!first.equals(SubmissionId.fromLegacy(owner, "mining", "save-slot-7")),
                "legacy domains were not isolated");
        require(first.equals(SubmissionId.parse(first.toString())), "SubmissionId string round-trip failed");
    }

    private static void checkWireOrdinals() {
        require(Arrays.equals(new String[]{"VANILLA_NBT", "SPONGE_SCHEM", "LITEMATIC", "BUILDING_GADGETS_JSON"},
                names(BlueprintFormat.values())), "BlueprintFormat wire order changed");
        require(BlueprintFormat.fromFileName("a.schematic") == BlueprintFormat.SPONGE_SCHEM,
                "legacy schematic extension mapping failed");
        require(Arrays.equals(new String[]{"PLACEMENT", "DESTRUCTION", "MINING", "BLUEPRINT", "FUNNEL", "PLACED_RECOVERY"},
                names(TaskType.values())), "TaskType persistence order changed");
        require(Arrays.equals(new String[]{"MINE_SINGLE", "ULTIMINE", "AREA_MINE", "AREA_DESTROY", "PLACE_SINGLE",
                "PLACE_BATCH", "QUICK_BUILD", "BLUEPRINT_BUILD", "STOP_MINING"}, names(RtsWorkflowType.values())),
                "RtsWorkflowType persistence order changed");
        require(RtsStorageSort.byId(-1) == RtsStorageSort.QUANTITY
                && RtsStorageSort.byId(999) == RtsStorageSort.QUANTITY
                && RtsStorageSort.byId(RtsStorageSort.NAME.ordinal()) == RtsStorageSort.NAME,
                "storage sort wire fallback changed");
    }

    private static String[] names(Enum<?>[] values) {
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) names[i] = values[i].name();
        return names;
    }

    private static void checkNbtRoundTrip() throws Exception {
        UUID owner = UUID.fromString("55555555-5555-5555-5555-555555555555");
        NBTTagCompound root = new NBTTagCompound();
        root.setUniqueId("owner", owner);
        root.setInteger("dimension", -1);
        root.setString("submission", SubmissionId.fromLegacy(owner, "blueprint", "portable-test").toString());
        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("block", "minecraft:stone");
        payload.setInteger("metadata", 3);
        root.setTag("payload", payload);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(root, output);
        NBTTagCompound restored = CompressedStreamTools.readCompressed(
                new ByteArrayInputStream(output.toByteArray()));
        require(owner.equals(restored.getUniqueId("owner")), "owner UUID was lost");
        require(restored.getInteger("dimension") == -1, "dimension was lost");
        require(restored.getCompoundTag("payload").getInteger("metadata") == 3, "metadata was lost");
        SubmissionId.parse(restored.getString("submission"));
    }

    /** 防止移植过程中通过遗漏场景缩小严格模式的失败面。 */
    private static void checkScenarioInventory() {
        List<Scenario> inventory = scenarios();
        int portable = 0;
        int world = 0;
        Set<String> ids = new HashSet<String>();
        for (Scenario scenario : inventory) {
            require(ids.add(scenario.id), "duplicate scenario id: " + scenario.id);
            if (scenario.isPortable()) portable++;
            else world++;
        }
        require(portable == EXPECTED_PORTABLE_SCENARIOS,
                "portable scenario inventory changed: " + portable);
        require(world == EXPECTED_WORLD_SCENARIOS,
                "world scenario inventory changed: " + world);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int count(List<Result> results, Outcome outcome) {
        int count = 0;
        for (Result result : results) if (result.outcome == outcome) count++;
        return count;
    }

    private static void print(Result result) {
        System.out.println(result.outcome + " " + result.scenario.id + " [" + result.scenario.group + "] " + result.detail);
    }

    private static void printInventory() {
        for (Scenario scenario : scenarios()) {
            System.out.println((scenario.isPortable() ? "PORTABLE " : "BLOCKED  ") + scenario.id
                    + " [" + scenario.group + "] " + scenario.intent + " :: " + scenario.requirement);
        }
    }
}
