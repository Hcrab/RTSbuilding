package com.rtsbuilding.rtsbuilding.gametest;

import com.rtsbuilding.rtsbuilding.compat.create.BlueprintCreatePlacementCompat;
import com.rtsbuilding.rtsbuilding.server.service.placement.BlockPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 需要真实 Create 运行时的蓝图兼容回归。
 *
 * <p>它与普通 RTS GameTest 分开注册，因为普通套件的内嵌假客户端没有完成 Create
 * payload 协商。此处不创建玩家，只验证生产使用的方块写入、NBT 安全处理和连接初始化。</p>
 */
@GameTestHolder(CreateBlueprintCompatibilityGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CreateBlueprintCompatibilityGameTests {
    public static final String NAMESPACE = "rtsbuilding_create_compat";
    private static final String EMPTY_TEMPLATE = "gametest/empty";

    private CreateBlueprintCompatibilityGameTests() {
    }

    /**
     * 回归用户提供的 stone.nbt 症结：保险库不得在新位置继续引用来源世界的绝对坐标。
     */
    @GameTest(
            template = EMPTY_TEMPLATE,
            templateNamespace = NAMESPACE,
            timeoutTicks = 120)
    public static void createVaultBlueprintRebuildsControllerAtPlacement(GameTestHelper helper) {
        ResourceLocation vaultId = ResourceLocation.fromNamespaceAndPath("create", "item_vault");
        Block vault = BuiltInRegistries.BLOCK.get(vaultId);
        helper.assertTrue(vault != Blocks.AIR, "Create item vault must exist in the compatibility run");

        BlockPos staleController = new BlockPos(-175, 63, 2035);
        BlockPos firstRel = new BlockPos(2, 1, 2);
        BlockState state = vault.defaultBlockState();
        for (int i = 0; i < 3; i++) {
            BlockPos target = helper.absolutePos(firstRel.offset(i, 0, 0));
            CompoundTag raw = staleVaultTag(staleController, i);
            helper.assertTrue(BlockPlacer.setBlueprintBlock(helper.getLevel(), target, state),
                    "Create vault should be written with the blueprint placement flags");
            CompoundTag prepared = BlueprintCreatePlacementCompat.prepareBlockEntityTag(
                    helper.getLevel(), target, state, raw);
            BlockPlacer.applyBlueprintBlockEntity(helper.getLevel(), target, prepared);
            BlockPlacer.finishBlueprintPlacement(
                    helper.getLevel(), target, state, ItemStack.EMPTY);
        }

        helper.succeedWhen(() -> {
            for (int i = 0; i < 3; i++) {
                BlockPos relative = firstRel.offset(i, 0, 0);
                helper.assertBlockPresent(vault, relative);
                BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
                helper.assertTrue(blockEntity != null, "Placed Create vault must keep its block entity");
                CompoundTag saved = blockEntity.saveWithFullMetadata(helper.getLevel().registryAccess());
                assertNoStalePosition(helper, saved, "Controller", staleController);
                assertNoStalePosition(
                        helper, saved, "LastKnownPos", staleController.offset(i, 0, 0));
            }
        });
    }

    /**
     * 传送带必须丢弃来源世界的控制器、长度和库存等运行时拓扑，
     * 但要保留玩家可见的外壳、覆盖和染色配置。
     */
    @GameTest(
            template = EMPTY_TEMPLATE,
            templateNamespace = NAMESPACE,
            timeoutTicks = 120)
    public static void createBeltBlueprintDropsStaleRuntimeTopology(GameTestHelper helper) {
        ResourceLocation beltId = ResourceLocation.fromNamespaceAndPath("create", "belt");
        Block belt = BuiltInRegistries.BLOCK.get(beltId);
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
