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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
                                                        StringArgumentType.getString(context, "adapter"), ""))
                                                .then(Commands.argument("interactionItemId", ResourceLocationArgument.id())
                                                        .executes(context -> setupCase(
                                                                context.getSource(),
                                                                StringArgumentType.getString(context, "caseId"),
                                                                ResourceLocationArgument.getId(context, "blockId").toString(),
                                                                IntegerArgumentType.getInteger(context, "distance"),
                                                                StringArgumentType.getString(context, "adapter"),
                                                                ResourceLocationArgument.getId(context, "interactionItemId").toString()))))))));
    }

    private static int setupCase(CommandSourceStack source, String caseId) {
        String targetBlock = resolveTargetBlock(caseId);
        if (targetBlock == null || targetBlock.isBlank()) {
            source.sendFailure(Component.literal("RTS GUI compat: no target block configured for " + caseId));
            return 0;
        }
        int distance = resolveInt(TARGET_DISTANCE_PROPERTY, TARGET_DISTANCE_ENV, DEFAULT_TARGET_DISTANCE);
        return setupCase(source, caseId, targetBlock, distance, "single_block", "");
    }

    private static int setupCase(CommandSourceStack source, String caseId, String targetBlockId,
            int distance, String adapter, String interactionItemId) {
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
            // 大型整合包测试世界可能残留敌对生物。探针只验证 GUI，不应把战斗伤害
            // 混入兼容结论；每个用例都恢复生命并同步无敌能力。
            player.getAbilities().invulnerable = true;
            player.onUpdateAbilities();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            BlockPos base = player.blockPosition();
            BlockPos targetPos = base.offset(0, 0, Math.max(2, distance));
            ChunkPos targetChunk = new ChunkPos(targetPos);

            // 探针必须区分“菜单不兼容”和“远处区块根本没加载”。这里只在显式启用
            // GUI 探针时注册命令，因此可以安全地强加载目标区块，避免 160 格用例产生假阴性。
            level.setChunkForced(targetChunk.x, targetChunk.z, true);
            level.getChunk(targetPos);

            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, 1), targetPos.offset(2, 3, 2))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            for (int x = -2; x <= 2; x++) {
                for (int z = 1; z <= Math.max(4, distance + 2); z++) {
                    level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            BlockState initialState = initialStateForAdapter(block.defaultBlockState(), adapter);
            if (!level.setBlock(targetPos, initialState, 3)) {
                source.sendFailure(Component.literal("RTS GUI compat: failed to place target block at "
                        + targetPos.toShortString()));
                return 0;
            }
            // 模组方块经常在玩家放置回调里初始化所有者、附件、颜色或多方块构建器。
            // 探针必须尽量复刻真实放置，而不是只把注册表默认状态塞进世界。
            block.setPlacedBy(level, targetPos, level.getBlockState(targetPos), player, new ItemStack(block));
            if (!applyAdapter(adapter, level, player, targetPos)) {
                source.sendFailure(Component.literal("RTS GUI compat: unsupported setup adapter: " + adapter));
                return 0;
            }
            if (!prepareInteractionItem(player, interactionItemId)) {
                source.sendFailure(Component.literal("RTS GUI compat: interaction item is not registered: "
                        + interactionItemId));
                return 0;
            }

            ResourceLocation actualBlockId = BuiltInRegistries.BLOCK.getKey(level.getBlockState(targetPos).getBlock());
            if (!blockId.equals(actualBlockId)) {
                source.sendFailure(Component.literal("RTS GUI compat: target changed after placement: expected="
                        + blockId + " actual=" + actualBlockId + " pos=" + targetPos.toShortString()));
                return 0;
            }
            level.sendBlockUpdated(targetPos, level.getBlockState(targetPos), level.getBlockState(targetPos), 3);

            source.sendSuccess(() -> Component.literal("RTS GUI compat: " + caseId + " ready at "
                    + targetPos.toShortString() + " block=" + targetBlockId + " adapter=" + adapter
                    + (interactionItemId.isBlank() ? "" : " interactionItem=" + interactionItemId)
                    + " forcedChunk=" + targetChunk.x + "," + targetChunk.z), false);
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
            case "oritech_assembler" -> prepareOritechMachine(level, targetPos,
                    new BlockPos(0, 0, 1), new BlockPos(0, 1, 0), new BlockPos(0, 1, 1));
            case "oritech_centrifuge" -> prepareOritechMachine(level, targetPos,
                    new BlockPos(0, 1, 0));
            case "powah_reactor" -> hasBooleanPropertyValue(level.getBlockState(targetPos), "core", true);
            case "pipez_item_extract" -> preparePipezItemExtract(level, targetPos);
            case "securitycraft_inventory_scanner_pair" -> prepareSecurityCraftInventoryScannerPair(
                    level, player, targetPos);
            case "productive_metalworks_minimal_foundry" -> prepareProductiveMetalworksFoundry(
                    level, player, targetPos);
            case "extreme_reactors_minimal_reactor" -> prepareExtremeReactorsMinimalReactor(
                    level, targetPos);
            case "integrated_terminal_storage_part" -> prepareIntegratedTerminalStoragePart(
                    level, player, targetPos);
            default -> false;
        };
    }

    /**
     * 为需要“拿着某件物品右键”才开窗的机器准备真实工具槽。
     * 探针随后走生产用的 tool-slot 远程交互链，不直接调用第三方方块，也不伪造菜单。
     */
    private static boolean prepareInteractionItem(ServerPlayer player, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return true;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }
        player.getInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.get(id)));
        player.getInventory().setChanged();
        return true;
    }

    /**
     * Oritech 控制器第一次右键只会组装多方块，第二次才会打开菜单。
     * 探针的职责是验证一次真实远程右键能否开窗，因此搭建阶段先按机器朝向放好核心并标记完成；
     * 这里仅使用注册表与原版方块状态，不让主模组在运行时硬依赖 Oritech 类。
     */
    private static boolean prepareOritechMachine(ServerLevel level, BlockPos controllerPos, BlockPos... relativeCores) {
        ResourceLocation coreId = ResourceLocation.parse("oritech:machine_core_1");
        Block core = BuiltInRegistries.BLOCK.getOptional(coreId).orElse(null);
        if (core == null || core == Blocks.AIR) {
            return false;
        }
        BlockState controller = level.getBlockState(controllerPos);
        net.minecraft.core.Direction facing = controller.hasProperty(HorizontalDirectionalBlock.FACING)
                ? controller.getValue(HorizontalDirectionalBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        for (BlockPos relative : relativeCores) {
            BlockPos rotated = rotateOritechOffset(relative, facing);
            level.setBlock(controllerPos.offset(rotated), core.defaultBlockState(), 3);
        }
        return setBooleanProperty(level, controllerPos, "machine_assembled", true);
    }

    /**
     * Pipez 只有点中已启用的抽取臂才会打开配置界面。这里在朝向玩家的 NORTH 侧放置真实箱子，
     * 再调用 Pipez 自己公开的状态 API；反射仅用于避免主模组对整合包可选模组产生硬依赖。
     */
    private static boolean preparePipezItemExtract(ServerLevel level, BlockPos pipePos) {
        Direction extractSide = Direction.NORTH;
        level.setBlock(pipePos.relative(extractSide), Blocks.CHEST.defaultBlockState(), 3);
        Block pipe = level.getBlockState(pipePos).getBlock();
        try {
            var setExtracting = pipe.getClass().getMethod(
                    "setExtracting", Level.class, BlockPos.class, Direction.class, boolean.class);
            var setDisconnected = pipe.getClass().getMethod(
                    "setDisconnected", Level.class, BlockPos.class, Direction.class, boolean.class);
            setDisconnected.invoke(pipe, level, pipePos, extractSide, false);
            setExtracting.invoke(pipe, level, pipePos, extractSide, true);
            level.sendBlockUpdated(pipePos, level.getBlockState(pipePos), level.getBlockState(pipePos), 3);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-GUI-PROBE] failed to prepare Pipez extraction side at {}",
                    pipePos.toShortString(), exception);
            return false;
        }
    }

    /**
     * SecurityCraft 的物品扫描器只有找到扫描方向上另一台反向扫描器时才会打开菜单。
     * 这里搭建最短的两端结构并走双方真实放置回调，使所有者与中间扫描区域都由模组自己初始化。
     */
    private static boolean prepareSecurityCraftInventoryScannerPair(
            ServerLevel level, ServerPlayer player, BlockPos scannerPos) {
        Block scanner = level.getBlockState(scannerPos).getBlock();
        BlockState first = withDirectionProperty(scanner.defaultBlockState(), "facing", Direction.SOUTH);
        BlockPos partnerPos = scannerPos.relative(Direction.SOUTH, 2);
        BlockState partner = withDirectionProperty(scanner.defaultBlockState(), "facing", Direction.NORTH);

        level.setBlock(scannerPos, first, 3);
        scanner.setPlacedBy(level, scannerPos, level.getBlockState(scannerPos), player, new ItemStack(scanner));
        level.setBlock(partnerPos, partner, 3);
        scanner.setPlacedBy(level, partnerPos, level.getBlockState(partnerPos), player, new ItemStack(scanner));
        level.sendBlockUpdated(scannerPos, level.getBlockState(scannerPos), level.getBlockState(scannerPos), 3);
        level.sendBlockUpdated(partnerPos, level.getBlockState(partnerPos), level.getBlockState(partnerPos), 3);
        return level.getBlockState(partnerPos).is(scanner);
    }

    /**
     * Productive Metalworks 在开窗前会重新扫描真实多方块，不能通过伪造方块实体状态绕过。
     * 探针搭建最小 3×3 铸造厂：底层加热线圈、两层耐火砖外墙、正面中央保留控制器，
     * 内部保持为空。所有方块均从注册表取得，因此主模组不会硬依赖该可选模组的类。
     */
    private static boolean prepareProductiveMetalworksFoundry(
            ServerLevel level, ServerPlayer player, BlockPos controllerPos) {
        Block wall = registeredBlock("productivemetalworks:black_fire_bricks");
        Block coil = registeredBlock("productivemetalworks:powered_heating_coil");
        if (wall == null || coil == null) {
            return false;
        }

        // 控制器位于北侧墙中央；结构向南延伸，避免覆盖玩家与远程射线起点。
        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 2; z++) {
                level.setBlock(controllerPos.offset(x, -1, z), coil.defaultBlockState(), 3);
            }
        }
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = 0; z <= 2; z++) {
                    boolean wallCell = x == -1 || x == 1 || z == 0 || z == 2;
                    if (!wallCell || (x == 0 && z == 0 && y == 0)) {
                        continue;
                    }
                    level.setBlock(controllerPos.offset(x, y, z), wall.defaultBlockState(), 3);
                }
            }
        }
        Block controller = level.getBlockState(controllerPos).getBlock();
        controller.setPlacedBy(level, controllerPos, level.getBlockState(controllerPos), player,
                new ItemStack(controller));
        return level.getBlockState(controllerPos).is(controller);
    }

    /**
     * Extreme Reactors 控制器只有在多方块完成组装后才允许打开 GUI。这里搭建标准最小
     * 3×3×3 被动反应堆：单格燃料棒、顶部控制棒、一个被动能量口，其余外壳使用基础机壳。
     * 结构向目标南侧延伸，控制器仍位于玩家可命中的北侧墙中央。
     */
    private static boolean prepareExtremeReactorsMinimalReactor(
            ServerLevel level, BlockPos controllerPos) {
        Block casing = registeredBlock("bigreactors:basic_reactorcasing");
        Block fuelRod = registeredBlock("bigreactors:basic_reactorfuelrod");
        Block controlRod = registeredBlock("bigreactors:basic_reactorcontrolrod");
        Block powerTap = registeredBlock("bigreactors:basic_reactorpowertapfe_passive");
        if (casing == null || fuelRod == null || controlRod == null || powerTap == null) {
            return false;
        }
        Block controller = level.getBlockState(controllerPos).getBlock();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = 0; z <= 2; z++) {
                    boolean boundary = x == -1 || x == 1 || y == -1 || y == 1 || z == 0 || z == 2;
                    if (boundary) {
                        level.setBlock(controllerPos.offset(x, y, z), casing.defaultBlockState(), 3);
                    }
                }
            }
        }

        level.setBlock(controllerPos,
                withDirectionProperty(controller.defaultBlockState(), "facing", Direction.NORTH), 3);
        level.setBlock(controllerPos.offset(0, 0, 1), fuelRod.defaultBlockState(), 3);
        level.setBlock(controllerPos.offset(0, 1, 1), controlRod.defaultBlockState(), 3);
        level.setBlock(controllerPos.offset(1, 0, 1), powerTap.defaultBlockState(), 3);
        return level.getBlockState(controllerPos).is(controller);
    }

    /**
     * Integrated Terminals 的存储终端不是独立方块，而是安装在 Integrated Dynamics 线缆表面的部件。
     * 通过其注册物品的真实 {@code useOn} 路径把终端装到朝向玩家的 NORTH 面，避免反射修改部件容器；
     * ATM10 Sky 将 Integrated Dynamics 的全局耗能倍率设为 0，因此单线缆网络即可正常启用该终端。
     */
    private static boolean prepareIntegratedTerminalStoragePart(
            ServerLevel level, ServerPlayer player, BlockPos cablePos) {
        ResourceLocation partId = ResourceLocation.parse("integratedterminals:part_terminal_storage");
        var partItem = BuiltInRegistries.ITEM.getOptional(partId).orElse(null);
        if (partItem == null || partItem == Items.AIR) {
            return false;
        }

        ItemStack partStack = new ItemStack(partItem);
        player.setItemInHand(InteractionHand.MAIN_HAND, partStack);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(cablePos).add(0.0D, 0.0D, -0.5D),
                Direction.NORTH,
                cablePos,
                false);
        InteractionResult result = partStack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        level.sendBlockUpdated(cablePos, level.getBlockState(cablePos), level.getBlockState(cablePos), 3);
        return result.consumesAction();
    }

    private static BlockPos rotateOritechOffset(BlockPos pos, net.minecraft.core.Direction facing) {
        return switch (facing) {
            case NORTH -> new BlockPos(pos.getZ(), pos.getY(), pos.getX());
            case WEST -> new BlockPos(pos.getX(), pos.getY(), -pos.getZ());
            case SOUTH -> new BlockPos(-pos.getZ(), pos.getY(), -pos.getX());
            case EAST -> new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
            default -> pos;
        };
    }

    private static boolean setBooleanProperty(ServerLevel level, BlockPos pos, String propertyName, boolean value) {
        BlockState state = level.getBlockState(pos);
        BlockState updated = withBooleanProperty(state, propertyName, value);
        if (updated == state) {
            return hasBooleanPropertyValue(state, propertyName, value);
        }
        level.setBlock(pos, updated, 3);
        return true;
    }

    private static BlockState initialStateForAdapter(BlockState state, String adapter) {
        return "powah_reactor".equals(adapter) ? withBooleanProperty(state, "core", true) : state;
    }

    private static BlockState withBooleanProperty(BlockState state, String propertyName, boolean value) {
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && propertyName.equals(property.getName())) {
                return state.setValue(booleanProperty, value);
            }
        }
        return state;
    }

    private static BlockState withDirectionProperty(
            BlockState state, String propertyName, Direction value) {
        for (var property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty
                    && propertyName.equals(property.getName())
                    && directionProperty.getPossibleValues().contains(value)) {
                return state.setValue(directionProperty, value);
            }
        }
        return state;
    }

    private static Block registeredBlock(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(key)
                .filter(block -> block != Blocks.AIR)
                .orElse(null);
    }

    private static boolean hasBooleanPropertyValue(BlockState state, String propertyName, boolean value) {
        for (var property : state.getProperties()) {
            if (property instanceof BooleanProperty booleanProperty && propertyName.equals(property.getName())) {
                return state.getValue(booleanProperty) == value;
            }
        }
        return false;
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
