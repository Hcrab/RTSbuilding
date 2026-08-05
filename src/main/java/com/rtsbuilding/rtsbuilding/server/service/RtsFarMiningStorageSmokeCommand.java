package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.platform.block.BlockState;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsDropAbsorber;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningDropCapture;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningStateMachine;
import com.rtsbuilding.rtsbuilding.server.storage.handler.RtsLinkedCapabilities;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldServer;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 真实整合包矩阵的远距挖掘落物探针。
 *
 * <p>该命令只会在显式提供报告路径时注册。它通过生产用的方块破坏、HarvestDrops 捕获、
 * 有界掉落缓冲和链接存储解析链完成一次 120 格挖掘；不会用测试专属插入逻辑绕过任何环节。
 * 普通发布环境没有对应系统属性，因此不会增加玩家可见命令或修改世界。</p>
 */
public final class RtsFarMiningStorageSmokeCommand extends CommandBase {
    public static final String REPORT_PROPERTY = "rtsbuilding.farMiningStorageSmokeReport";
    private static final String COMMAND_NAME = "rtsbuilding_far_mining_storage_smoke";
    private static final int TARGET_DISTANCE = 120;

    @Override public String getCommandName() { return COMMAND_NAME; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/" + COMMAND_NAME; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    public static boolean isEnabled() {
        return !System.getProperty(REPORT_PROPERTY, "").trim().isEmpty();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 0) throw new CommandException(getCommandUsage(sender));
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        String result;
        try {
            result = runProductionPath(player);
        } catch (Throwable failure) {
            RtsbuildingMod.LOGGER.error("RTS 远距挖掘自动入库探针失败", failure);
            result = "FAIL\t" + failure.getClass().getName() + "\t" + safeMessage(failure);
        }
        writeReport(result);
        if (!result.startsWith("PASS\t")) {
            throw new CommandException("RTS far mining storage smoke failed; see report");
        }
    }

    private static String runProductionPath(EntityPlayerMP player) {
        WorldServer level = player.getServerForPlayer();
        BlockPos base = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.blockPosition(player);
        BlockPos storagePos = base.add(2, 0, 0);
        BlockPos targetPos = base.add(0, 0, TARGET_DISTANCE);
        level.getChunkProvider().provideChunk(storagePos.getX() >> 4, storagePos.getZ() >> 4);
        level.getChunkProvider().provideChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4);
        BlockState.defaultState(Blocks.stone).setInWorld(level, storagePos.down(), 3);
        BlockState.defaultState(Blocks.chest).setInWorld(level, storagePos, 3);
        BlockState.defaultState(Blocks.stone).setInWorld(level, targetPos.down(), 3);
        BlockState.defaultState(Blocks.dirt).setInWorld(level, targetPos, 3);

        double distance = Math.sqrt(com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.distanceSqToCenter(player, targetPos));
        if (distance < 100.0D) {
            throw new IllegalStateException("target was not actually remote: " + distance);
        }

        RtsStorageSession session = ServiceRegistry.getInstance().session().getOrCreate(player);
        session.linkedStorageInfo.clear();
        session.miningDropBuffer.stacks.clear();
        session.miningDropBuffer.clearTimingWhenEmpty();
        // 探针刻意关闭玩家设置：120 格目标必须由远距离安全策略强制进入精确掉落链路。
        session.sessionFlags.autoStoreMinedDrops = false;
        session.linkedStorageInfo.add(
                new LinkedStorageRef(player.dimension, storagePos.toImmutable()),
                RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL, 0);

        IItemHandler storage = RtsLinkedCapabilities.findLinkedItemHandler(player, storagePos);
        if (storage == null) throw new IllegalStateException("vanilla chest capability was not resolved");
        int before = countDirt(storage);
        int cobblestoneBefore = countBlock(storage, Blocks.cobblestone);
        GameType previous = player.theItemInWorldManager.getGameType();
        RtsMiningStateMachine.MiningBreakResult breakResult;
        boolean directDropEnteredWorld;
        try {
            player.theItemInWorldManager.setGameType(GameType.SURVIVAL);
            breakResult = RtsMiningStateMachine.destroyMinedBlock(player, session, targetPos, 0);
            // 模拟部分 1.12 老模组绕过 HarvestDropsEvent、直接生成掉落实体的行为。
            directDropEnteredWorld = RtsMiningDropCapture.capture(player, session, targetPos,
                    () -> level.spawnEntityInWorld(new EntityItem(level,
                            targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D,
                            new ItemStack(Blocks.cobblestone))));
            RtsDropAbsorber.drainDropBuffer(player, session, 64, Long.MAX_VALUE);
        } finally {
            player.theItemInWorldManager.setGameType(previous);
        }
        int after = countDirt(storage);
        int cobblestoneAfter = countBlock(storage, Blocks.cobblestone);
        if (!breakResult.broken()) throw new IllegalStateException("production mining path did not break target");
        if (after <= before) throw new IllegalStateException("linked chest did not receive the remote drop");
        if (directDropEnteredWorld) {
            throw new IllegalStateException("direct EntityItem drop bypassed the exact capture hook");
        }
        if (cobblestoneAfter <= cobblestoneBefore) {
            throw new IllegalStateException("linked chest did not receive the direct EntityItem drop");
        }
        if (!session.miningDropBuffer.isEmpty()) {
            throw new IllegalStateException("drop buffer remained non-empty after storage drain");
        }
        if (session.sessionFlags.autoStoreMinedDrops) {
            throw new IllegalStateException("far-drop safety mutated the player's auto-store setting");
        }
        return "PASS\tdistance=" + Math.round(distance)
                + "\tforcedAutoStore=true"
                + "\tstandardStored=" + (after - before)
                + "\tdirectEntityStored=" + (cobblestoneAfter - cobblestoneBefore)
                + "\tbuffer=empty";
    }

    private static int countDirt(IItemHandler handler) {
        return countBlock(handler, Blocks.dirt);
    }

    private static int countBlock(IItemHandler handler, net.minecraft.block.Block block) {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) && stack.getItem() == net.minecraft.item.Item.getItemFromBlock(block)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private static void writeReport(String line) throws CommandException {
        Path report = Paths.get(System.getProperty(REPORT_PROPERTY)).toAbsolutePath().normalize();
        try {
            if (report.getParent() != null) Files.createDirectories(report.getParent());
            Files.write(report, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException failure) {
            throw new CommandException("Unable to write far mining storage report: " + safeMessage(failure));
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? "" : failure.getMessage().replace('\t', ' ')
                .replace('\r', ' ').replace('\n', ' ');
    }
}
