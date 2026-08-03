package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.compat.create.BlueprintCreatePlacementCompat;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 需要真实 Create 运行时的蓝图兼容回归。
 *
 * <p>独立命名空间让普通 RTS 测试与第三方整合测试可以分别执行。测试直接验证真实
 * Create 方块实体的保存结果，不用假方块实体冒充玩家环境。</p>
 */
@GameTestHolder(CreateBlueprintCompatibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CreateBlueprintCompatibilityGameTests {
    public static final String NAMESPACE = "rtsbuilding_create_compat";
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private CreateBlueprintCompatibilityGameTests() {
    }

    /** 保险库在新位置重建时不能保留来源世界的控制器绝对坐标。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void createVaultBlueprintRebuildsControllerAtPlacement(GameTestHelper helper) {
        Block vault = RtsBuiltInRegistries.BLOCK.get(new ResourceLocation("create", "item_vault"));
        helper.assertTrue(vault != Blocks.AIR, "Create item vault must exist in the compatibility run");

        BlockPos staleController = new BlockPos(-175, 63, 2035);
        BlockPos firstRel = new BlockPos(2, 1, 2);
        BlockState state = vault.defaultBlockState();
        for (int i = 0; i < 3; i++) {
            BlockPos target = helper.absolutePos(firstRel.offset(i, 0, 0));
            helper.assertTrue(helper.getLevel().setBlock(
                            target, state, BlueprintCreatePlacementCompat.placementFlags(state)),
                    "Create vault should be written with the blueprint placement flags");
            CompoundTag prepared = BlueprintCreatePlacementCompat.prepareBlockEntityTag(
                    helper.getLevel(), target, state, staleVaultTag(staleController, i));
            applyBlockEntityTag(helper, target, prepared);
            BlueprintCreatePlacementCompat.finishPlacement(
                    helper.getLevel(), target, state, ItemStack.EMPTY);
        }

        helper.succeedWhen(() -> {
            for (int i = 0; i < 3; i++) {
                BlockPos relative = firstRel.offset(i, 0, 0);
                helper.assertBlockPresent(vault, relative);
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
                helper.assertTrue(blockEntity != null, "Placed Create vault must keep its block entity");
                CompoundTag saved = blockEntity.saveWithFullMetadata();
                assertNoStalePosition(helper, saved, "Controller", staleController);
                assertNoStalePosition(
                        helper, saved, "LastKnownPos", staleController.offset(i, 0, 0));
            }
        });
    }

    /** 传送带丢弃旧拓扑，但保留外壳、覆盖和染色等玩家可见配置。 */
    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 120)
    public static void createBeltBlueprintDropsStaleRuntimeTopology(GameTestHelper helper) {
        Block belt = RtsBuiltInRegistries.BLOCK.get(new ResourceLocation("create", "belt"));
        helper.assertTrue(belt != Blocks.AIR, "Create belt must exist in the compatibility run");

        BlockState state = belt.defaultBlockState();
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos staleController = new BlockPos(-172, 63, 2034);
        CompoundTag raw = new CompoundTag();
        raw.putString("id", "create:belt");
        raw.put("Controller", NbtUtils.writeBlockPos(staleController));
        raw.putBoolean("IsController", true);
        raw.putInt("Length", 5);
        raw.putInt("Index", 2);
        raw.putFloat("Speed", 32.0F);
        raw.putBoolean("NeedsSpeedUpdate", true);
        raw.put("Inventory", new CompoundTag());
        raw.putString("Casing", "ANDESITE");
        raw.putBoolean("Covered", true);
        raw.putString("Dye", "RED");

        CompoundTag prepared = BlueprintCreatePlacementCompat.prepareBlockEntityTag(
                helper.getLevel(), target, state, raw);
        helper.assertTrue(prepared != null, "Create belt blueprint data must be prepared");
        for (String key : new String[]{
                "Controller", "IsController", "Length", "Index",
                "Speed", "NeedsSpeedUpdate", "Inventory"}) {
            helper.assertTrue(!prepared.contains(key),
                    "Prepared Create belt retained stale runtime key: " + key);
        }
        helper.assertTrue("ANDESITE".equals(prepared.getString("Casing")),
                "Prepared Create belt lost its casing");
        helper.assertTrue(prepared.getBoolean("Covered"),
                "Prepared Create belt lost its cover");
        helper.assertTrue("RED".equals(prepared.getString("Dye")),
                "Prepared Create belt lost its dye");
        helper.assertTrue(
                BlueprintCreatePlacementCompat.placementFlags(state) == Block.UPDATE_CLIENTS,
                "Create belt placement must avoid per-segment neighbor updates");
        helper.succeed();
    }

    private static void applyBlockEntityTag(GameTestHelper helper, BlockPos target, CompoundTag tag) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(target);
        helper.assertTrue(blockEntity != null, "Placed Create block must create a block entity");
        if (tag == null || tag.isEmpty()) {
            return;
        }
        CompoundTag positioned = tag.copy();
        positioned.putInt("x", target.getX());
        positioned.putInt("y", target.getY());
        positioned.putInt("z", target.getZ());
        blockEntity.load(positioned);
        blockEntity.setChanged();
    }

    private static CompoundTag staleVaultTag(BlockPos staleController, int index) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "create:item_vault");
        tag.put("LastKnownPos", NbtUtils.writeBlockPos(staleController.offset(index, 0, 0)));
        if (index == 0) {
            tag.putInt("Length", 3);
            tag.putInt("Size", 1);
        } else {
            tag.put("Controller", NbtUtils.writeBlockPos(staleController));
        }
        return tag;
    }

    private static void assertNoStalePosition(
            GameTestHelper helper, CompoundTag tag, String key, BlockPos stale) {
        if (!tag.contains(key)) {
            return;
        }
        int[] value = tag.getIntArray(key);
        helper.assertTrue(value.length != 3
                        || value[0] != stale.getX()
                        || value[1] != stale.getY()
                        || value[2] != stale.getZ(),
                "Placed Create block entity retained stale " + key + "=" + stale);
    }
}
