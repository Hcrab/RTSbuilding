package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** 只在 GUI 自动探针启用时注册的 1.12.2 测试场地命令。 */
public final class RtsGuiCompatSetupCommand extends CommandBase {
    private static final String PROBE_REPORT_PROPERTY = "rtsbuilding.guiCompatProbeReport";
    private static final String PROBE_REPORT_ENV = "RTSBUILDING_GUI_COMPAT_PROBE_REPORT";
    private static final String TARGET_BLOCK_PROPERTY = "rtsbuilding.guiCompatTargetBlock";
    private static final String TARGET_BLOCK_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_BLOCK";
    private static final String TARGET_DISTANCE_PROPERTY = "rtsbuilding.guiCompatTargetDistance";
    private static final String TARGET_DISTANCE_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_DISTANCE";
    private static final String COMMAND_NAME = "rtsbuilding_gui_compat_setup";
    private static final int DEFAULT_TARGET_DISTANCE = 20;

    @Override public String getName() { return COMMAND_NAME; }
    @Override public String getUsage(ICommandSender sender) { return "/" + COMMAND_NAME + " <caseId>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) throw new CommandException(getUsage(sender));
        setupCase(getCommandSenderAsPlayer(sender), args[0]);
    }

    private static void setupCase(EntityPlayerMP player, String caseId) throws CommandException {
        String targetBlock = resolveTargetBlock(caseId);
        if (isBlank(targetBlock)) {
            throw new CommandException("RTS GUI compat: no target block configured for " + caseId);
        }
        setupSingleBlock(player, caseId, targetBlock);
    }

    private static void setupSingleBlock(EntityPlayerMP player, String caseId, String targetBlockId)
            throws CommandException {
        try {
            ResourceLocation blockId = new ResourceLocation(targetBlockId);
            Block block = ForgeRegistries.BLOCKS.getValue(blockId);
            if (block == null || block == Blocks.AIR) {
                throw new CommandException("RTS GUI compat: target block is not registered: " + targetBlockId);
            }

            WorldServer level = player.getServerWorld();
            BlockPos base = player.getPosition();
            int distance = resolveInt(TARGET_DISTANCE_PROPERTY, TARGET_DISTANCE_ENV, DEFAULT_TARGET_DISTANCE);
            BlockPos targetPos = base.add(0, 0, Math.max(2, distance));

            for (BlockPos pos : BlockPos.getAllInBox(base.add(-2, -1, 1), targetPos.add(2, 3, 2))) {
                level.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = 1; z <= Math.max(4, distance + 2); z++) {
                    level.setBlockState(base.add(x, -1, z), Blocks.STONE.getDefaultState(), 3);
                }
            }
            level.setBlockState(targetPos, block.getDefaultState(), 3);
            player.sendMessage(new TextComponentString("RTS GUI compat: " + caseId + " ready at "
                    + targetPos.getX() + "," + targetPos.getY() + "," + targetPos.getZ()
                    + " block=" + targetBlockId));
        } catch (CommandException exception) {
            throw exception;
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn("Failed to prepare GUI compat setup for {}", caseId, exception);
            throw new CommandException("RTS GUI compat setup failed: " + exception.getMessage());
        }
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
                || !isBlank(System.getenv(PROBE_REPORT_ENV));
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
