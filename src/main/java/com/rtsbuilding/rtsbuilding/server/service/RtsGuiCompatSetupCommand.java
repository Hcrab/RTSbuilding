package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.RtsGuiCompatMatrixSync;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;

/** 只在 GUI 自动探针启用时注册的 1.12.2 测试场地命令。 */
public final class RtsGuiCompatSetupCommand extends CommandBase {
    private static final String PROBE_REPORT_PROPERTY = "rtsbuilding.guiCompatProbeReport";
    private static final String PROBE_REPORT_ENV = "RTSBUILDING_GUI_COMPAT_PROBE_REPORT";
    private static final String MATRIX_REPORT_PROPERTY = "rtsbuilding.guiCompatMatrixReport";
    private static final String MATRIX_REPORT_ENV = "RTSBUILDING_GUI_COMPAT_MATRIX_REPORT";
    private static final String TARGET_BLOCK_PROPERTY = "rtsbuilding.guiCompatTargetBlock";
    private static final String TARGET_BLOCK_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_BLOCK";
    private static final String TARGET_DISTANCE_PROPERTY = "rtsbuilding.guiCompatTargetDistance";
    private static final String TARGET_DISTANCE_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_DISTANCE";
    private static final String COMMAND_NAME = "rtsbuilding_gui_compat_setup";
    private static final int DEFAULT_TARGET_DISTANCE = 20;

    @Override public String getCommandName() { return COMMAND_NAME; }
    @Override public String getCommandUsage(ICommandSender sender) {
        return "/" + COMMAND_NAME + " <caseId> [targetBlock] [distance] [meta] [x y z]";
    }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || args.length > 7 || (args.length > 4 && args.length < 7)) {
            throw new CommandException(getCommandUsage(sender));
        }
        String targetBlock = args.length >= 2 ? args[1] : resolveTargetBlock(args[0]);
        Integer distance = args.length >= 3 ? parseDistance(args[2]) : null;
        int meta = args.length >= 4 ? parseMeta(args[3]) : 0;
        BlockPos explicitTarget = args.length == 7
                ? new BlockPos(parseCoordinate(args[4]), parseCoordinate(args[5]), parseCoordinate(args[6]))
                : null;
        setupCase(getCommandSenderAsPlayer(sender), args[0], targetBlock, distance, meta, explicitTarget);
    }

    private static void setupCase(EntityPlayerMP player, String caseId, String targetBlock,
            Integer distanceOverride, int meta, BlockPos explicitTarget) throws CommandException {
        if (isBlank(targetBlock)) {
            throw new CommandException("RTS GUI compat: no target block configured for " + caseId);
        }
        setupSingleBlock(player, caseId, targetBlock, distanceOverride, meta, explicitTarget);
    }

    private static void setupSingleBlock(EntityPlayerMP player, String caseId, String targetBlockId,
            Integer distanceOverride, int meta, BlockPos explicitTarget)
            throws CommandException {
        BlockPos acknowledgedTarget = explicitTarget;
        try {
            ResourceLocation blockId = new ResourceLocation(targetBlockId);
            Block block = RtsRegistries.BLOCKS.getValue(blockId);
            if (block == null || block == Blocks.air) {
                throw new CommandException("RTS GUI compat: target block is not registered: " + targetBlockId);
            }

            WorldServer level = player.getServerForPlayer();
            BlockPos base = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.blockPosition(player);
            int distance = distanceOverride == null
                    ? resolveInt(TARGET_DISTANCE_PROPERTY, TARGET_DISTANCE_ENV, DEFAULT_TARGET_DISTANCE)
                    : distanceOverride.intValue();
            // 矩阵探针显式传绝对坐标，避免客户端与服务端在相机/登录同步的不同 tick
            // 分别用玩家位置推导目标，最终把方块放在校验位置之外。
            BlockPos targetPos = explicitTarget == null
                    ? base.add(0, 0, Math.max(2, distance))
                    : explicitTarget;
            acknowledgedTarget = targetPos;

            // 矩阵会连续测试数百种方块；这里只维护目标及脚下平台，避免每个候选都
            // 重写整条 120 格走廊并触发数千次邻居更新。
            level.getChunkProvider().provideChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4);
            // 两个固定测试格会被上万个候选反复复用；按钮、门和特殊机器可能在交互时改变邻格。
            // 潜影箱一类 GUI 会在打开前检查顶部伸展空间，因此每次必须先清掉上方残留，
            // 否则会把场地污染误报为“远距离打不开”。
            clearProbeBlock(level, targetPos.up());
            BlockState.defaultState(Blocks.stone).setInWorld(level, targetPos.down(), 3);
            BlockState desiredState = BlockState.of(block, meta);
            // 连续矩阵会在同一坐标替换数千种机器。必须先让旧方块带着自己的 TE
            // 完成原版 breakBlock：Blood Arsenal 等旧模组会在该回调里强制转换
            // world.getTileEntity。随后再明确 invalid/remove 捕获的旧实例，避免
            // Embers Breaker 仍留在本 tick 的更新队列里并对 air 读取 facing。
            clearProbeBlock(level, targetPos);
            boolean placed = desiredState.setInWorld(level, targetPos, 3);
            BlockState actualState = BlockState.fromWorld(level, targetPos);
            if (!placed || actualState.getBlock() != block) {
                throw new CommandException("RTS GUI compat: target state was rejected: "
                        + targetBlockId + " meta=" + meta + " actual="
                        + String.valueOf(com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS
                                .getKey(actualState.getBlock())));
            }
            RtsGuiCompatMatrixSync.markSetupComplete(targetPos, targetBlockId, meta);
            player.addChatMessage(new ChatComponentText("RTS GUI compat: " + caseId + " ready at "
                    + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ()
                    + " block=" + targetBlockId + " meta=" + meta));
        } catch (CommandException exception) {
            RtsGuiCompatMatrixSync.markSetupFailed(
                    acknowledgedTarget, targetBlockId, meta, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            RtsGuiCompatMatrixSync.markSetupFailed(
                    acknowledgedTarget, targetBlockId, meta, exception.getMessage());
            RtsbuildingMod.LOGGER.warn("Failed to prepare GUI compat setup for {}", caseId, exception);
            throw new CommandException("RTS GUI compat setup failed: " + exception.getMessage());
        }
    }

    /**
     * 清理一个会被矩阵复用的测试格，同时保留旧方块先执行 breakBlock、再失效旧 TE 的顺序。
     * 该顺序兼容 Blood Arsenal 对旧 TE 的强制转换，也避免 Embers 把旧 TE 留在本 tick 更新队列。
     */
    private static void clearProbeBlock(WorldServer level, BlockPos pos) {
        TileEntity previousTile = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
        level.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
        if (previousTile != null) {
            previousTile.invalidate();
        }
        TileEntity residualTile = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
        if (residualTile != null) {
            residualTile.invalidate();
        }
        level.removeTileEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String resolveTargetBlock(String caseId) {
        String configured = resolveConfig(TARGET_BLOCK_PROPERTY, TARGET_BLOCK_ENV, "");
        if (!isBlank(configured)) return configured;
        if ("vanilla_chest".equals(caseId)) return "minecraft:chest";
        if ("sophisticated_chest".equals(caseId)) return "sophisticatedstorage:chest";
        if ("sophisticated_barrel".equals(caseId)) return "sophisticatedstorage:barrel";
        if ("iron_furnace".equals(caseId)) return "ironfurnaces:iron_furnace";
        if ("mek_metallurgic_infuser".equals(caseId)) return "mekanism:metallurgic_infuser";
        if ("mek_enrichment_chamber".equals(caseId)) return "mekanism:enrichment_chamber";
        if ("if_resourceful_furnace".equals(caseId)) return "industrialforegoing:resourceful_furnace";
        if ("rs_grid".equals(caseId)) return "refinedstorage:grid";
        if ("rs_controller".equals(caseId)) return "refinedstorage:controller";
        if ("create_schematic_table".equals(caseId)) return "create:schematic_table";
        if ("create_schematicannon".equals(caseId)) return "create:schematicannon";
        if ("ie_coke_oven".equals(caseId)) return "immersiveengineering:coke_oven";
        return "";
    }

    public static boolean isProbeEnabled() {
        return !isBlank(System.getProperty(PROBE_REPORT_PROPERTY))
                || !isBlank(System.getenv(PROBE_REPORT_ENV))
                || isMatrixEnabled();
    }

    /** 仅用于真实整合包矩阵，生产客户端不会进入该分支。 */
    public static boolean isMatrixEnabled() {
        return !isBlank(System.getProperty(MATRIX_REPORT_PROPERTY))
                || !isBlank(System.getenv(MATRIX_REPORT_ENV));
    }

    private static String resolveConfig(String propertyName, String environmentName, String fallback) {
        String configured = System.getProperty(propertyName);
        if (isBlank(configured)) configured = System.getenv(environmentName);
        return isBlank(configured) ? fallback : configured;
    }

    private static int resolveInt(String propertyName, String environmentName, int fallback) {
        String configured = resolveConfig(propertyName, environmentName, "");
        if (isBlank(configured)) return fallback;
        try {
            return Math.max(1, Integer.parseInt(configured));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseDistance(String value) throws CommandException {
        try {
            return Math.max(2, Math.min(128, Integer.parseInt(value)));
        } catch (NumberFormatException invalid) {
            throw new CommandException("RTS GUI compat: invalid distance " + value);
        }
    }

    private static int parseMeta(String value) throws CommandException {
        try {
            int meta = Integer.parseInt(value);
            if (meta < 0 || meta > 15) throw new NumberFormatException("outside 0..15");
            return meta;
        } catch (NumberFormatException invalid) {
            throw new CommandException("RTS GUI compat: invalid block meta " + value);
        }
    }

    private static int parseCoordinate(String value) throws CommandException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException invalid) {
            throw new CommandException("RTS GUI compat: invalid coordinate " + value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
