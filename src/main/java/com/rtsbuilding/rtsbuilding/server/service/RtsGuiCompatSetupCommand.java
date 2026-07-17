package com.rtsbuilding.rtsbuilding.server.service;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID)
public final class RtsGuiCompatSetupCommand {
    private static final String PROBE_REPORT_PROPERTY = "rtsbuilding.guiCompatProbeReport";
    private static final String PROBE_REPORT_ENV = "RTSBUILDING_GUI_COMPAT_PROBE_REPORT";
    private static final String TARGET_BLOCK_PROPERTY = "rtsbuilding.guiCompatTargetBlock";
    private static final String TARGET_BLOCK_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_BLOCK";
    private static final String TARGET_DISTANCE_PROPERTY = "rtsbuilding.guiCompatTargetDistance";
    private static final String TARGET_DISTANCE_ENV = "RTSBUILDING_GUI_COMPAT_TARGET_DISTANCE";
    private static final String COMMAND_NAME = "rtsbuilding_gui_compat_setup";
    private static final int DEFAULT_TARGET_DISTANCE = 20;

    private RtsGuiCompatSetupCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!isProbeEnabled()) {
            return;
        }
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(COMMAND_NAME)
                .then(Commands.argument("caseId", StringArgumentType.word())
                        .executes(context -> setupCase(context.getSource(),
                                StringArgumentType.getString(context, "caseId")))));
    }

    private static int setupCase(CommandSourceStack source, String caseId) {
        String targetBlock = resolveTargetBlock(caseId);
        if (targetBlock == null || targetBlock.isBlank()) {
            source.sendFailure(Component.literal("RTS GUI compat: no target block configured for " + caseId));
            return 0;
        }
        return setupSingleBlock(source, caseId, targetBlock);
    }

    private static int setupSingleBlock(CommandSourceStack source, String caseId, String targetBlockId) {
        try {
            Identifier blockId = Identifier.parse(targetBlockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
            if (block == null || block == Blocks.AIR) {
                source.sendFailure(Component.literal("RTS GUI compat: target block is not registered: "
                        + targetBlockId));
                return 0;
            }

            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.level();
            BlockPos base = player.blockPosition();
            int distance = resolveInt(TARGET_DISTANCE_PROPERTY, TARGET_DISTANCE_ENV, DEFAULT_TARGET_DISTANCE);
            BlockPos targetPos = base.offset(0, 0, Math.max(2, distance));

            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, 1), targetPos.offset(2, 3, 2))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = 1; z <= Math.max(4, distance + 2); z++) {
                    level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(targetPos, block.defaultBlockState(), 3);

            source.sendSuccess(() -> Component.literal("RTS GUI compat: " + caseId + " ready at "
                    + targetPos.toShortString() + " block=" + targetBlockId), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn("Failed to prepare GUI compat setup for {}", caseId, exception);
            source.sendFailure(Component.literal("RTS GUI compat setup failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static String resolveTargetBlock(String caseId) {
        String configured = resolveConfig(TARGET_BLOCK_PROPERTY, TARGET_BLOCK_ENV, "");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return switch (caseId) {
            case "vanilla_chest" -> "minecraft:chest";
            case "sophisticated_chest" -> "sophisticatedstorage:chest";
            case "sophisticated_barrel" -> "sophisticatedstorage:barrel";
            case "iron_furnace" -> "ironfurnaces:iron_furnace";
            case "mek_metallurgic_infuser" -> "mekanism:metallurgic_infuser";
            case "mek_enrichment_chamber" -> "mekanism:enrichment_chamber";
            case "if_resourceful_furnace" -> "industrialforegoing:resourceful_furnace";
            case "rs_grid" -> "refinedstorage:grid";
            case "rs_controller" -> "refinedstorage:controller";
            case "create_schematic_table" -> "create:schematic_table";
            case "create_schematicannon" -> "create:schematicannon";
            case "ie_coke_oven" -> "immersiveengineering:coke_oven";
            default -> "";
        };
    }

    private static boolean isProbeEnabled() {
        String property = System.getProperty(PROBE_REPORT_PROPERTY);
        if (property != null && !property.isBlank()) {
            return true;
        }
        String env = System.getenv(PROBE_REPORT_ENV);
        return env != null && !env.isBlank();
    }

    private static String resolveConfig(String propertyName, String environmentName, String fallback) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private static int resolveInt(String propertyName, String environmentName, int fallback) {
        String configured = resolveConfig(propertyName, environmentName, "");
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(configured));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
