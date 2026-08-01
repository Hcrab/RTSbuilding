package com.rtsbuilding.rtsbuilding.server.service;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantments;
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
                                StringArgumentType.getString(context, "caseId")))
                        .then(Commands.argument("blockId", ResourceLocationArgument.id())
                                .then(Commands.argument("distance", IntegerArgumentType.integer(2, 512))
                                        .then(Commands.argument("adapter", StringArgumentType.word())
                                                .executes(context -> setupCase(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "caseId"),
                                                        ResourceLocationArgument.getId(context, "blockId").toString(),
                                                        IntegerArgumentType.getInteger(context, "distance"),
                                                        StringArgumentType.getString(context, "adapter"))))))));
    }

    private static int setupCase(CommandSourceStack source, String caseId) {
        String targetBlock = resolveTargetBlock(caseId);
        if (targetBlock == null || targetBlock.isBlank()) {
            source.sendFailure(Component.literal("RTS GUI compat: no target block configured for " + caseId));
            return 0;
        }
        int distance = resolveInt(TARGET_DISTANCE_PROPERTY, TARGET_DISTANCE_ENV, DEFAULT_TARGET_DISTANCE);
        return setupCase(source, caseId, targetBlock, distance, "single_block");
    }

    private static int setupCase(CommandSourceStack source, String caseId, String targetBlockId,
            int distance, String adapter) {
        try {
            ResourceLocation blockId = ResourceLocation.parse(targetBlockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
            if (block == null || block == Blocks.AIR) {
                source.sendFailure(Component.literal("RTS GUI compat: target block is not registered: "
                        + targetBlockId));
                return 0;
            }

            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos base = player.blockPosition();
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
            if (!applyAdapter(adapter, level, player, targetPos)) {
                source.sendFailure(Component.literal("RTS GUI compat: unsupported setup adapter: " + adapter));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("RTS GUI compat: " + caseId + " ready at "
                    + targetPos.toShortString() + " block=" + targetBlockId + " adapter=" + adapter), false);
            return Command.SINGLE_SUCCESS;
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn("Failed to prepare GUI compat setup for {}", caseId, exception);
            source.sendFailure(Component.literal("RTS GUI compat setup failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static boolean applyAdapter(String adapter, ServerLevel level, ServerPlayer player, BlockPos targetPos) {
        if (!"single_block".equals(adapter)) {
            player.getInventory().clearContent();
        }
        return switch (adapter) {
            case "single_block" -> true;
            case "vanilla_chest" -> {
                if (level.getBlockEntity(targetPos) instanceof Container container) {
                    container.setItem(0, new ItemStack(Blocks.STONE, 16));
                    container.setChanged();
                    yield true;
                }
                yield false;
            }
            case "vanilla_enchanting" -> {
                placeEnchantingBookshelves(level, targetPos);
                player.experienceLevel = Math.max(player.experienceLevel, 30);
                player.totalExperience = Math.max(player.totalExperience, 1395);
                give(player, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.LAPIS_LAZULI, 8));
                yield true;
            }
            case "vanilla_crafting" -> {
                give(player, new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.SPRUCE_PLANKS));
                yield true;
            }
            case "vanilla_furnace" -> {
                give(player, new ItemStack(Items.RAW_IRON), new ItemStack(Items.COAL));
                yield true;
            }
            case "vanilla_anvil" -> {
                give(player, new ItemStack(Items.IRON_SWORD));
                player.experienceLevel = Math.max(player.experienceLevel, 30);
                yield true;
            }
            case "vanilla_smithing" -> {
                give(player,
                        new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        new ItemStack(Items.DIAMOND_SWORD),
                        new ItemStack(Items.NETHERITE_INGOT));
                yield true;
            }
            case "vanilla_stonecutter" -> {
                give(player, new ItemStack(Items.STONE, 4));
                yield true;
            }
            case "vanilla_brewing" -> {
                if (level.getBlockEntity(targetPos) instanceof Container container) {
                    container.setItem(4, new ItemStack(Items.BLAZE_POWDER));
                    container.setChanged();
                }
                give(player,
                        PotionContents.createItemStack(Items.POTION, Potions.WATER),
                        new ItemStack(Items.NETHER_WART));
                yield true;
            }
            case "vanilla_grindstone" -> {
                ItemStack sword = new ItemStack(Items.IRON_SWORD);
                sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.SHARPNESS), 1);
                give(player, sword);
                yield true;
            }
            default -> false;
        };
    }

    private static void give(ServerPlayer player, ItemStack... stacks) {
        for (ItemStack stack : stacks) {
            player.getInventory().add(stack);
        }
        player.getInventory().setChanged();
    }

    private static void placeEnchantingBookshelves(ServerLevel level, BlockPos tablePos) {
        int placed = 0;
        for (int y = 0; y <= 1 && placed < 15; y++) {
            for (int x = -2; x <= 2 && placed < 15; x++) {
                for (int z = -2; z <= 2 && placed < 15; z++) {
                    boolean ring = (Math.abs(x) == 2 && Math.abs(z) <= 1)
                            || (Math.abs(z) == 2 && Math.abs(x) <= 1);
                    if (!ring) {
                        continue;
                    }
                    level.setBlock(tablePos.offset(x, y, z), Blocks.BOOKSHELF.defaultBlockState(), 3);
                    placed++;
                }
            }
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
