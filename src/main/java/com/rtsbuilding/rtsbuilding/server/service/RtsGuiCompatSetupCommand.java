package com.rtsbuilding.rtsbuilding.server.service;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * GUI 兼容 smoke 的服务端场景搭建命令。
 *
 * <p>这个命令只在本地探针环境变量启用时注册，不属于正常玩法 API。它负责把第三方 GUI
 * 目标快速放到玩家附近，让客户端探针能专注验证 RTS 右键链路和 screen/menu 生命周期。</p>
 */
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

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!isProbeEnabled()) {
            return;
        }

        dispatcher.register(Commands.literal(COMMAND_NAME)
                .then(Commands.argument("caseId", StringArgumentType.word())
                        .executes(context -> setupCase(context,
                                StringArgumentType.getString(context, "caseId")))));
    }

    private static int setupCase(CommandContext<CommandSourceStack> context, String caseId) {
        if ("ie_coke_oven".equals(caseId)) {
            return setupIeCokeOven(context.getSource());
        }
        String targetBlock = resolveTargetBlock(caseId);
        if (targetBlock == null || targetBlock.isBlank()) {
            context.getSource().sendFailure(
                    Component.literal("RTS GUI compat: unknown setup case " + caseId));
            return 0;
        }
        return setupSingleBlock(context.getSource(), caseId, targetBlock);
    }

    /**
     * 为单方块菜单兼容探针建立可重复的平坦场景。
     */
    private static int setupSingleBlock(
            CommandSourceStack source,
            String caseId,
            String targetBlockId) {
        try {
            ResourceLocation blockId = ResourceLocation.tryParse(targetBlockId);
            Block block = blockId == null
                    ? null
                    : RtsBuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
            if (block == null || block == Blocks.AIR) {
                source.sendFailure(Component.literal(
                        "RTS GUI compat: target block is not registered: " + targetBlockId));
                return 0;
            }

            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.getLevel();
            BlockPos base = player.blockPosition();
            int distance = resolveInt(
                    TARGET_DISTANCE_PROPERTY,
                    TARGET_DISTANCE_ENV,
                    DEFAULT_TARGET_DISTANCE);
            BlockPos targetPos = base.offset(0, 0, Math.max(2, distance));

            for (BlockPos pos : BlockPos.betweenClosed(
                    base.offset(-2, -1, 1),
                    targetPos.offset(2, 3, 2))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = 1; z <= Math.max(4, distance + 2); z++) {
                    level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            level.setBlock(targetPos, block.defaultBlockState(), 3);

            source.sendSuccess(Component.literal(
                    "RTS GUI compat: " + caseId + " ready at "
                            + targetPos.toShortString() + " block=" + targetBlockId), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "Failed to prepare GUI compat setup for {}", caseId, exception);
            source.sendFailure(Component.literal(
                    "RTS GUI compat setup failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int setupIeCokeOven(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.getLevel();
            Object multiblock = resolveIeCokeOvenMultiblock();
            @SuppressWarnings("unchecked")
            List<StructureTemplate.StructureBlockInfo> structure =
                    (List<StructureTemplate.StructureBlockInfo>) invoke(multiblock, "getStructure",
                            new Class<?>[] {Level.class}, level);
            BlockPos triggerOffset = (BlockPos) invoke(multiblock, "getTriggerOffset", new Class<?>[0]);

            BlockPos origin = player.blockPosition().offset(-1, 0, 4);
            clearSetupArea(level, origin);
            for (StructureTemplate.StructureBlockInfo blockInfo : structure) {
                level.setBlock(origin.offset(blockInfo.pos), blockInfo.state, 3);
            }

            boolean formed = (Boolean) invoke(multiblock, "createStructure",
                    new Class<?>[] {Level.class, BlockPos.class, Direction.class, net.minecraft.world.entity.player.Player.class},
                    // IE 会用 direction.getOpposite() 计算模板旋转；这里按模板原样铺放，所以传 SOUTH 保持无旋转匹配。
                    level, origin.offset(triggerOffset), Direction.SOUTH, player);
            if (!formed) {
                source.sendFailure(Component.literal("RTS GUI compat: IE coke oven structure did not form."));
                return 0;
            }

            BlockPos target = findNearestBlock(level, player.blockPosition(), origin, "immersiveengineering:coke_oven");
            if (target == null) {
                source.sendFailure(Component.literal("RTS GUI compat: formed IE coke oven but could not find target block."));
                return 0;
            }

            source.sendSuccess(Component.literal("RTS GUI compat: IE coke oven ready at "
                    + target.toShortString()
                    + ". Look at it and run /rtsbuilding_gui_compat_run ie_coke_oven"), false);
            return Command.SINGLE_SUCCESS;
        } catch (ReflectiveOperationException exception) {
            RtsbuildingMod.LOGGER.warn("Failed to prepare IE coke oven GUI compat setup", exception);
            source.sendFailure(Component.literal("RTS GUI compat: Immersive Engineering coke oven setup failed. "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage()));
            return 0;
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn("Unexpected IE coke oven GUI compat setup failure", exception);
            source.sendFailure(Component.literal("RTS GUI compat: setup failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static Object resolveIeCokeOvenMultiblock() throws ReflectiveOperationException {
        Class<?> multiblocks = Class.forName(
                "blusunrize.immersiveengineering.common.blocks.multiblocks.IEMultiblocks");
        Field field = multiblocks.getField("COKE_OVEN");
        Object value = field.get(null);
        if (value == null) {
            Method init = multiblocks.getMethod("init");
            init.invoke(null);
            value = field.get(null);
        }
        if (value == null) {
            throw new IllegalStateException("IEMultiblocks.COKE_OVEN is null");
        }
        return value;
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(name, parameterTypes);
        return method.invoke(target, args);
    }

    private static void clearSetupArea(ServerLevel level, BlockPos origin) {
        BlockPos from = origin.offset(-2, -1, -3);
        BlockPos to = origin.offset(4, 4, 5);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int x = -2; x <= 4; x++) {
            for (int z = -3; z <= 5; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos findNearestBlock(ServerLevel level, BlockPos playerPos, BlockPos origin, String blockId) {
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(4, 4, 4))) {
            String id = RtsBuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
            if (!blockId.equals(id)) {
                continue;
            }
            double distance = pos.distSqr(playerPos);
            if (distance < bestDistance) {
                nearest = pos.immutable();
                bestDistance = distance;
            }
        }
        return nearest;
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

    private static String resolveConfig(
            String propertyName,
            String environmentName,
            String fallback) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private static int resolveInt(
            String propertyName,
            String environmentName,
            int fallback) {
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
