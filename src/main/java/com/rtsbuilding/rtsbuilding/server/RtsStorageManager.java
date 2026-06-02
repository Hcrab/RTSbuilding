package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Field;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage.RtsSophisticatedStorageCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.data.RtsStorageSessionStore;

public final class RtsStorageManager {
    private static final int PAGE_SIZE = 90;
    private static final int FLUID_TRANSFER_MB = FluidType.BUCKET_VOLUME;
    private static final long INTERNAL_FLUID_CAPACITY_MB = 100L * FluidType.BUCKET_VOLUME;
    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;
    private static final double REMOTE_POV_EPSILON = 0.1D;
    private static final double FUNNEL_RADIUS = 2.0D;
    private static final int FUNNEL_MAX_ENTITIES_PER_TICK = 24;
    private static final int FUNNEL_MAX_ITEMS_PER_TICK = 48;
    private static final int FUNNEL_BUFFER_MAX_STACKS = 16;
    private static final int FUNNEL_TICK_INTERVAL = 2;
    private static final int SHIFT_IMPORT_MAX_CRAFT_ITERATIONS = 64;
    // Shared with RtsStorageSession so the extracted state object cannot drift
    // from the packet/UI limits that RtsStorageManager still owns.
    static final int CRAFTABLE_BATCH_SIZE = 12;
    private static final int ULTIMINE_MAX_BLOCKS = 256;
    private static final int ULTIMINE_BLOCKS_PER_TICK = 8;
    static final int RECENT_ENTRY_LIMIT = 24;
    private static final long QUEST_DETECT_COOLDOWN_TICKS = 60L;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_MAIN_INVENTORY_END_EXCLUSIVE = 36;
    // Session arrays use these fixed slot counts; all validation remains here.
    static final int QUICK_SLOT_COUNT = 27;
    static final int GUI_BINDING_SLOT_COUNT = 8;
    private static final int QUICK_BUILD_BATCH_BLOCKS_PER_TICK = 64;
    private static final int QUICK_BUILD_BATCH_MAX_QUEUED_JOBS = 4;
    private static final int QUICK_BUILD_COMPLETION_SOUND_DELAY_TICKS = 3;
    static final byte LINK_MODE_BIDIRECTIONAL = C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL;
    private static final byte LINK_MODE_EXTRACT_ONLY = C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
    private static final String CATEGORY_ALL = "all";
    private static final String CATEGORY_MOD_PREFIX = "mod|";
    private static final String CATEGORY_TAB_PREFIX = "tab|";
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE / 2L;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ITEM_CREATIVE_TAB_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> BROKEN_CREATIVE_TAB_CACHE = ConcurrentHashMap.newKeySet();
    private static volatile boolean creativeTabCacheWarmNormal;
    private static volatile boolean creativeTabCacheWarmOperator;
    private static final PreviewCraftingMenu PREVIEW_CRAFTING_MENU = new PreviewCraftingMenu();

    private RtsStorageManager() {
    }

    public static void onRtsEnabled(ServerPlayer player) {
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void warmCreativeTabCaches(MinecraftServer server) {
        if (server == null) {
            return;
        }
        synchronized (RtsStorageManager.class) {
            clearCreativeTabCacheState();
            ServerLevel level = server.overworld();
            if (level == null) {
                return;
            }
            warmCreativeTabCacheMode(level, false);
            warmCreativeTabCacheMode(level, true);
        }
    }

    public static void onRtsDisabled(ServerPlayer player) {
        // Keep linked-storage state across RTS toggles, but stop active mining.
        Session session = getOrCreateSession(player);
        stopActiveMining(player, session);
        session.placeBatchJobs.clear();
        disableFunnelAndFlushBuffer(player, session);
        closeTrackedRemoteMenu(player, session);
        saveSessionToPlayerNbt(player, session);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.placeBatchJobs.clear();
            disableFunnelAndFlushBuffer(player, session);
            closeTrackedRemoteMenu(player, session);
            saveSessionToPlayerNbt(player, session);
        }
        SESSIONS.remove(player.getUUID());
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            saveSessionToPlayerNbt(player, session);
        }
    }

    public static void onPlayerTickPre(ServerPlayer player) {
        // RTS no longer spoofs player position for Sophisticated Storage menu validation.
    }

    public static void onPlayerTickPost(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.remoteMenuContainerId < 0 && !RtsSophisticatedStorageCompat.isSupportedRemoteMenu(player.containerMenu)) {
            clearRemoteMenuValidation(player, session);
        }
        if (session.remoteMenuContainerId >= 0
                && (player.containerMenu == null || player.containerMenu.containerId != session.remoteMenuContainerId)) {
            forceRemoteMenuClosedVisual(player, session.remoteMenuPos);
            session.remoteMenuContainerId = -1;
            session.remoteMenuPos = null;
        }
        tickQuickBuildCompletionSound(player, session);
        tickPlaceBatchJobs(player, session);
    }

    private static Session getOrCreateSession(ServerPlayer player) {
        Session existing = SESSIONS.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        Session created = new Session();
        loadSessionFromPersistentStorage(player, created);
        SESSIONS.put(player.getUUID(), created);
        return created;
    }

    private static void loadSessionFromPersistentStorage(ServerPlayer player, Session session) {
        CompoundTag root = RtsStorageSessionStore.loadSession(player);
        boolean loadedFromWorldStore = !root.isEmpty();
        if (!loadedFromWorldStore) {
            root = player.getPersistentData().getCompound(RtsStorageSessionCodec.ROOT_KEY);
        }
        if (root.isEmpty()) {
            return;
        }
        RtsStorageSessionCodec.load(player, session, root);
        if (!loadedFromWorldStore) {
            saveSessionToPlayerNbt(player, session);
        }
    }

    private static void saveSessionToPlayerNbt(ServerPlayer player, Session session) {
        CompoundTag root = RtsStorageSessionCodec.serialize(session);
        player.getPersistentData().put(RtsStorageSessionCodec.ROOT_KEY, root.copy());
        RtsStorageSessionStore.saveSession(player, root);
    }

    public static void tickMining(MinecraftServer server) {
        for (var entry : SESSIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            Session session = entry.getValue();
            tickActiveMining(player, session);
            tickFunnel(player, session);
        }
    }

    public static void setMode(ServerPlayer player, BuilderMode mode) {
        Session session = getOrCreateSession(player);
        session.mode = mode;
        if (mode != BuilderMode.FUNNEL && session.funnelEnabled) {
            disableFunnelAndFlushBuffer(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void setFunnelEnabled(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (session.funnelEnabled == enabled) {
            return;
        }
        if (enabled) {
            session.funnelEnabled = true;
            session.funnelTickCooldown = 0;
        } else {
            disableFunnelAndFlushBuffer(player, session);
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void updateFunnelTarget(ServerPlayer player, BlockPos target) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FUNNEL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (!session.funnelEnabled || target == null) {
            return;
        }
        session.funnelTarget = target.immutable();
    }

    public static void setAutoStoreMinedDrops(ServerPlayer player, boolean enabled) {
        if (enabled && !RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
            return;
        }
        Session session = getOrCreateSession(player);
        session.autoStoreMinedDrops = enabled;
        saveSessionToPlayerNbt(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static BuilderMode getMode(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? BuilderMode.INTERACT : session.mode;
    }

    public static void linkStorage(ServerPlayer player, BlockPos pos, byte linkMode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.LINK_STORAGE)) {
            return;
        }
        if (!canAccessWorldTarget(player, pos)) {
            return;
        }

        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);

        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        IItemHandler itemHandler = findLinkedItemHandler(player, pos);
        IFluidHandler fluidHandler = findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            requestPage(player, 0, session.search, session.category, session.sort, session.ascending);
            return;
        }

        byte normalizedMode = sanitizeLinkMode(linkMode);
        if (session.linkedStorages.contains(ref)) {
            byte existingMode = session.linkedModes.getOrDefault(ref, LINK_MODE_BIDIRECTIONAL);
            if (existingMode == normalizedMode) {
                session.linkedStorages.remove(ref);
                session.linkedNames.remove(ref);
                session.linkedModes.remove(ref);
            } else {
                session.linkedModes.put(ref, normalizedMode);
                session.linkedNames.put(ref, resolveDisplayName(player.serverLevel(), ref.pos()));
            }
        } else {
            session.linkedStorages.add(ref);
            session.linkedNames.put(ref, resolveDisplayName(player.serverLevel(), ref.pos()));
            session.linkedModes.put(ref, normalizedMode);
        }
        saveSessionToPlayerNbt(player, session);
        requestPage(player, 0, session.search, session.category, session.sort, session.ascending);
    }

    public static void openCraftTerminal(ServerPlayer player) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            player.displayClientMessage(Component.literal("Link at least one storage first."), true);
            return;
        }

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new RtsCraftTerminalMenu(
                        containerId,
                        inventory,
                        new ContainerLevelAccess() {
                            @Override
                            public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
                                return Optional.ofNullable(evaluator.apply(player.serverLevel(), player.blockPosition()));
                            }

                            @Override
                            public void execute(BiConsumer<Level, BlockPos> consumer) {
                                consumer.accept(player.serverLevel(), player.blockPosition());
                            }
                        }),
                Component.literal("RTS Craft Terminal")));
        relaxOpenedMenuValidation(player.containerMenu);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void detectQuests(ServerPlayer player, byte mode) {
        Session session = getOrCreateSession(player);
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        runQuestDetect(player, session, true);
    }

    public static void rotateBlock(ServerPlayer player, BlockPos pos) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ROTATE_BLOCK)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, pos)) {
            return;
        }
        rotatePlacedBlock(player.serverLevel(), pos, (byte) 1);
    }

    public static void storeHotbarSlotToLinked(ServerPlayer player, byte slotId) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        int slot = clampHotbarSlot(slotId);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return;
        }

        ItemStack remaining = storeToLinkedOnlyPreferExisting(handlers, inSlot.copy());
        if (remaining.getCount() == inSlot.getCount()) {
            return;
        }

        player.getInventory().setItem(slot, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void setQuickSlot(ServerPlayer player, byte slotId, String itemId) {
        Session session = getOrCreateSession(player);
        int slot = slotId;
        if (!isValidQuickSlotIndex(slot)) {
            return;
        }

        String normalized = "";
        if (itemId != null && !itemId.isBlank()) {
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return;
            }
            normalized = itemId;
        }

        if (normalized.equals(session.quickSlotItemIds[slot])) {
            return;
        }

        session.quickSlotItemIds[slot] = normalized;
        saveSessionToPlayerNbt(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void setGuiBinding(ServerPlayer player, byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (!clear && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) {
            return;
        }
        Session session = getOrCreateSession(player);
        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return;
        }

        if (clear) {
            if (session.guiBindings[slot] == null) {
                return;
            }
            session.guiBindings[slot] = null;
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        if (pos == null || !canAccessWorldTarget(player, pos)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        MenuProvider provider = resolveBindableMenuProvider(level, pos);
        if (!canBindGuiTarget(level, pos)) {
            player.displayClientMessage(Component.literal("Target has no bindable GUI."), true);
            return;
        }

        String label = provider == null || provider.getDisplayName() == null ? "" : provider.getDisplayName().getString();
        if (label.isBlank()) {
            label = resolveDisplayName(level, pos);
        }
        String iconItemId = resolveGuiBindingIconItemId(level, pos, face, itemIdHint, label);

        session.guiBindings[slot] = new GuiBinding(
                pos.immutable(),
                level.dimension(),
                label,
                iconItemId,
                face);
        saveSessionToPlayerNbt(player, session);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void openGuiBinding(ServerPlayer player, byte slotId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }

        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return;
        }

        GuiBinding binding = session.guiBindings[slot];
        if (binding == null || binding.pos() == null || binding.dimension() == null) {
            return;
        }
        if (!player.serverLevel().dimension().equals(binding.dimension())) {
            player.displayClientMessage(Component.literal("Bound GUI is in another dimension."), true);
            return;
        }
        if (!canAccessWorldTarget(player, binding.pos())) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = binding.pos();
        sendRemoteMenuOpenHint(player, pos);
        SyntheticBlockInteraction interaction = createGuiBindingInteraction(player, pos, binding.face());
        BlockHitResult hit = interaction.hit();
        Vec3 hitLocation = hit.getLocation();
        Vec3 interactionPos = interaction.interactionPos();

        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        InteractionResult interactResult = withTemporaryUseItemContext(
                player,
                interactionPos,
                hitLocation,
                null,
                REMOTE_POV_BLOCK_REACH,
                () -> withTemporaryMainHandItem(
                        player,
                        ItemStack.EMPTY,
                        () -> withTemporaryShiftKey(player, false, () -> player.gameMode.useItemOn(
                                player,
                                level,
                                ItemStack.EMPTY,
                                InteractionHand.MAIN_HAND,
                                hit))));
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            markRemoteMenuOpen(player, session, menuAfterInteract, pos);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        if (!interactResult.consumesAction()) {
            interactResult = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    null,
                    REMOTE_POV_BLOCK_REACH,
                    () -> withTemporaryMainHandItem(
                            player,
                            ItemStack.EMPTY,
                            () -> withTemporaryShiftKey(player, true, () -> player.gameMode.useItemOn(
                                    player,
                                    level,
                                    ItemStack.EMPTY,
                                    InteractionHand.MAIN_HAND,
                                    hit))));
            AbstractContainerMenu menuAfterSecondaryInteract = player.containerMenu;
            if (menuAfterSecondaryInteract != menuBeforeInteract) {
                markRemoteMenuOpen(player, session, menuAfterSecondaryInteract, pos);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
        }

        if (!interactResult.consumesAction()) {
            MenuProvider provider = resolveBindableMenuProvider(level, pos);
            if (provider == null) {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
            player.openMenu(provider);
            if (player.containerMenu != null && player.containerMenu != menuBeforeInteract) {
                markRemoteMenuOpen(player, session, player.containerMenu, pos);
            } else {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
            }
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static long countLinkedItemsMatching(ServerPlayer player, Predicate<ItemStack> predicate) {
        if (player == null || predicate == null) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return 0L;
        }

        long total = 0L;
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        for (LinkedHandler linkedHandler : linked) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!predicate.test(stack)) {
                    continue;
                }
                total = saturatedAdd(total, getHandlerReportedCount(handler, slot, stack));
            }
        }
        return total;
    }

    public static boolean canAccessBlueprintTarget(ServerPlayer player, BlockPos pos) {
        return canAccessWorldTarget(player, pos);
    }

    public static long countBlueprintMaterial(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) {
            return 0L;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0L;
        }

        long total = 0L;
        for (LinkedHandler linkedHandler : resolveLinkedHandlers(player, session)) {
            IItemHandler handler = linkedHandler.handler();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && stack.getItem() == item) {
                    total = saturatedAdd(total, getHandlerReportedCount(handler, slot, stack));
                }
            }
        }

        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total = saturatedAdd(total, stack.getCount());
            }
        }
        return total;
    }

    public static ItemStack extractBlueprintMaterial(ServerPlayer player, Item item, int count) {
        if (player == null || item == null || item == Items.AIR || count <= 0) {
            return ItemStack.EMPTY;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return ItemStack.EMPTY;
        }
        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = activeLinked.stream().map(LinkedHandler::handler).toList();
        return extractMatchingFromNetwork(handlers, player, item, count);
    }

    public static void refundBlueprintMaterial(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        List<IItemHandler> handlers = session == null
                ? List.of()
                : resolveLinkedHandlers(player, session).stream().map(LinkedHandler::handler).toList();
        refundToLinked(handlers, player, stack);
    }

    public static void noteBlueprintBlockPlaced(ServerPlayer player, BlockPos pos, String itemId) {
        if (player == null || pos == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        playRemotePlacedBlockSound(player, player.serverLevel(), session, pos, true);
        recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
    }

    public static void refreshBlueprintStoragePage(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    private static boolean currentPinyinSearchEnabled(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session != null && session.pinyinSearchEnabled;
    }

    private static List<String> currentLocalizedSearchMatches(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? List.of() : List.copyOf(session.localizedSearchMatches);
    }

    private static boolean currentCraftPinyinSearchEnabled(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session != null && session.craftPinyinSearchEnabled;
    }

    private static List<String> currentCraftLocalizedSearchMatches(ServerPlayer player) {
        Session session = player == null ? null : SESSIONS.get(player.getUUID());
        return session == null ? List.of() : List.copyOf(session.craftLocalizedSearchMatches);
    }

    private static Set<String> sanitizeLocalizedSearchMatches(List<String> localizedSearchMatches) {
        if (localizedSearchMatches == null || localizedSearchMatches.isEmpty()) {
            return Set.of();
        }
        Set<String> sanitized = new HashSet<>();
        for (String itemId : localizedSearchMatches) {
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            sanitized.add(key.toString());
            if (sanitized.size() >= 8192) {
                break;
            }
        }
        return sanitized;
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending) {
        requestPage(player, page, search, category, sort, ascending, currentPinyinSearchEnabled(player));
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled) {
        requestPage(
                player,
                page,
                search,
                category,
                sort,
                ascending,
                pinyinSearchEnabled,
                currentLocalizedSearchMatches(player));
    }

    public static void requestPage(ServerPlayer player, int page, String search, String category, RtsStorageSort sort,
            boolean ascending, boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = getOrCreateSession(player);
        refreshMissingGuiBindingIcons(player, session);
        session.search = search == null ? "" : search;
        session.category = normalizeCategory(category);
        session.sort = sort == null ? RtsStorageSort.QUANTITY : sort;
        session.ascending = ascending;
        session.pinyinSearchEnabled = pinyinSearchEnabled;
        session.localizedSearchMatches.clear();
        session.localizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));

        sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        boolean includePlayerMainInventory = shouldIncludePlayerMainInventoryInStorageView(player, session);
        List<Long> linkedPackedPositions = toPackedPositions(player, session.linkedStorages);
        if (!hasAnyStorage(player, session)
                && activeHandlers.isEmpty()
                && activeFluidHandlers.isEmpty()
                && !hasPositiveInternalFluid(session)
                && !includePlayerMainInventory) {
            sendEmptyPage(player, session);
            session.page = 0;
            saveSessionToPlayerNbt(player, session);
            return;
        }

        Map<String, Long> counts = new HashMap<>();
        List<Entry> exactEntries = new ArrayList<>();
        Map<String, Long> namespaceTotals = new HashMap<>();
        for (LinkedHandler linked : activeHandlers) {
            IItemHandler handler = linked.handler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) {
                    continue;
                }
                long reportedCount = getHandlerReportedCount(handler, i, stack);
                mergeCount(counts, id.toString(), reportedCount);
                mergeExactEntry(exactEntries, stack, reportedCount);
                mergeCount(namespaceTotals, id.getNamespace(), reportedCount);
            }
        }
        if (includePlayerMainInventory) {
            accumulatePlayerMainInventoryCounts(player, counts, namespaceTotals);
            accumulatePlayerMainInventoryEntries(player, exactEntries);
        }

        Map<String, Long> fluidAmounts = new HashMap<>();
        Map<String, Long> fluidCapacities = new HashMap<>();

        for (var entry : session.internalFluidMb.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            mergeCount(fluidAmounts, entry.getKey(), entry.getValue());
        }

        for (LinkedFluidHandler linked : activeFluidHandlers) {
            IFluidHandler handler = linked.handler();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack fluid = handler.getFluidInTank(tank);
                if (fluid.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                if (id == null) {
                    continue;
                }
                String fluidId = id.toString();
                mergeCount(fluidAmounts, fluidId, fluid.getAmount());
                mergeCount(fluidCapacities, fluidId, Math.max(0, handler.getTankCapacity(tank)));
            }
        }

        long internalFluidCapacityMb = internalFluidCapacityMb(player);
        for (String fluidId : fluidAmounts.keySet()) {
            mergeCount(fluidCapacities, fluidId, internalFluidCapacityMb);
            ResourceLocation rl = ResourceLocation.tryParse(fluidId);
            if (rl != null) {
                mergeCount(namespaceTotals, rl.getNamespace(), fluidAmounts.getOrDefault(fluidId, 0L));
            }
        }

        Map<String, Set<String>> itemTabKeys = new HashMap<>();
        Map<String, Set<String>> modTabKeys = new HashMap<>();
        if (!counts.isEmpty()) {
            boolean operatorTabs = player.canUseGameMasterBlocks();
            if (ensureCreativeTabContents(player)) {
                for (String itemId : counts.keySet()) {
                    ResourceLocation rl = ResourceLocation.tryParse(itemId);
                    if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
                        continue;
                    }
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    Set<String> tabs = resolveCreativeTabKeys(itemId, item, operatorTabs);
                    if (tabs.isEmpty()) {
                        continue;
                    }
                    Set<String> copied = new HashSet<>(tabs);
                    itemTabKeys.put(itemId, copied);
                    modTabKeys.computeIfAbsent(rl.getNamespace(), ignored -> new HashSet<>()).addAll(copied);
                }
            }
        }

        List<String> namespaces = new ArrayList<>(namespaceTotals.keySet());
        namespaces.sort(RtsStorageManager::compareNamespace);

        List<String> categories = new ArrayList<>();
        categories.add(CATEGORY_ALL);
        for (String namespace : namespaces) {
            categories.add(encodeModCategory(namespace));
            List<String> tabs = new ArrayList<>(modTabKeys.getOrDefault(namespace, Set.of()));
            tabs.sort(RtsStorageManager::compareTabKey);
            for (String tabKey : tabs) {
                categories.add(encodeTabCategory(namespace, tabKey));
            }
        }

        CategorySelection selectedCategory = parseCategorySelection(session.category);
        if (!isValidCategorySelection(selectedCategory, categories)) {
            session.category = CATEGORY_ALL;
            selectedCategory = CategorySelection.all();
        }

        String query = session.search.toLowerCase(Locale.ROOT).trim();
        List<Entry> entries = new ArrayList<>();
        for (Entry exactEntry : exactEntries) {
            String id = exactEntry.itemId();
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!matchesSearchQuery(
                    rl,
                    id,
                    exactEntry.label(),
                    query,
                    session.pinyinSearchEnabled,
                    session.localizedSearchMatches)) {
                continue;
            }
            Set<String> tabs = itemTabKeys.getOrDefault(id, Set.of());
            if (!selectedCategory.matches(exactEntry.namespace(), tabs)) {
                continue;
            }
            entries.add(exactEntry);
        }

        List<FluidEntry> fluidEntries = new ArrayList<>();
        for (var e : fluidAmounts.entrySet()) {
            String id = e.getKey();
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!matchesSearchQuery(rl, id, null, query, session.pinyinSearchEnabled, session.localizedSearchMatches)) {
                continue;
            }
            String namespace = rl == null ? "unknown" : rl.getNamespace();
            String path = rl == null ? id : rl.getPath();
            if (selectedCategory.isCreativeTab() || !selectedCategory.matches(namespace, Set.of())) {
                continue;
            }
            long amount = Math.max(0L, e.getValue());
            long capacity = Math.max(amount, fluidCapacities.getOrDefault(id, internalFluidCapacityMb));
            fluidEntries.add(new FluidEntry(id, namespace, path, amount, capacity));
        }

        Comparator<Entry> comparator = switch (sort) {
            case MOD -> Comparator.comparing(Entry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::namespace, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
            case QUANTITY -> Comparator.comparingLong(Entry::count)
                    .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Entry::path, String.CASE_INSENSITIVE_ORDER);
        };
        if (sort == RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        } else if (sort != RtsStorageSort.QUANTITY && !ascending) {
            comparator = comparator.reversed();
        }
        entries.sort(comparator);

        Comparator<FluidEntry> fluidComparator = switch (sort) {
            case MOD -> Comparator.comparing(FluidEntry::namespace).thenComparing(FluidEntry::path);
            case NAME -> Comparator.comparing(FluidEntry::path).thenComparing(FluidEntry::namespace);
            case QUANTITY -> Comparator.comparingLong(FluidEntry::amount).thenComparing(FluidEntry::path);
        };
        if (!ascending) {
            fluidComparator = fluidComparator.reversed();
        }
        fluidEntries.sort(fluidComparator);

        int totalEntries = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalEntries / (double) PAGE_SIZE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, totalEntries);

        List<ItemStack> itemStacks = new ArrayList<>();
        List<Long> itemCounts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Entry e = entries.get(i);
            itemStacks.add(e.stack().copy());
            itemCounts.add(e.count());
        }

        List<String> totalItemIds = new ArrayList<>(counts.size());
        List<Long> totalItemCounts = new ArrayList<>(counts.size());
        for (var entry : counts.entrySet()) {
            totalItemIds.add(entry.getKey());
            totalItemCounts.add(entry.getValue());
        }

        List<String> fluidIds = new ArrayList<>(fluidEntries.size());
        List<Long> fluidAmountList = new ArrayList<>(fluidEntries.size());
        List<Long> fluidCapacityList = new ArrayList<>(fluidEntries.size());
        for (FluidEntry entry : fluidEntries) {
            fluidIds.add(entry.fluidId());
            fluidAmountList.add(entry.amount());
            fluidCapacityList.add(entry.capacity());
        }

        List<String> recentIds = new ArrayList<>(session.recentEntries.size());
        List<Long> recentAmounts = new ArrayList<>(session.recentEntries.size());
        List<Long> recentCapacities = new ArrayList<>(session.recentEntries.size());
        List<Byte> recentKinds = new ArrayList<>(session.recentEntries.size());
        for (RecentEntry recent : session.recentEntries) {
            recentIds.add(recent.id());
            recentAmounts.add(recent.amount());
            recentCapacities.add(recent.capacity());
            recentKinds.add(recent.kind());
        }

        List<String> quickSlotItemIds = new ArrayList<>(QUICK_SLOT_COUNT);
        for (String quickSlotItemId : session.quickSlotItemIds) {
            quickSlotItemIds.add(quickSlotItemId == null ? "" : quickSlotItemId);
        }

        List<String> guiBindingLabels = new ArrayList<>(GUI_BINDING_SLOT_COUNT);
        List<String> guiBindingItemIds = new ArrayList<>(GUI_BINDING_SLOT_COUNT);
        for (GuiBinding guiBinding : session.guiBindings) {
            guiBindingLabels.add(guiBinding == null || guiBinding.label() == null ? "" : guiBinding.label());
            guiBindingItemIds.add(guiBinding == null || guiBinding.itemId() == null ? "" : guiBinding.itemId());
        }

        Map<String, Long> funnelBufferSummary = summarizeFunnelBuffer(session);
        List<String> funnelBufferItemIds = new ArrayList<>(funnelBufferSummary.size());
        List<Long> funnelBufferCounts = new ArrayList<>(funnelBufferSummary.size());
        for (var entry : funnelBufferSummary.entrySet()) {
            funnelBufferItemIds.add(entry.getKey());
            funnelBufferCounts.add(entry.getValue());
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsStoragePagePayload(
                hasAnyStorage(player, session),
                buildAnyStorageSummary(player, session),
                linkedPackedPositions,
                safePage,
                totalPages,
                totalEntries,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                categories,
                itemStacks,
                itemCounts,
                totalItemIds,
                totalItemCounts,
                fluidIds,
                fluidAmountList,
                fluidCapacityList,
                recentIds,
                recentAmounts,
                recentCapacities,
                recentKinds,
                quickSlotItemIds,
                guiBindingLabels,
                guiBindingItemIds,
                session.funnelEnabled,
                funnelBufferItemIds,
                funnelBufferCounts));

        session.page = safePage;
        saveSessionToPlayerNbt(player, session);
    }

    private static void sendEmptyPage(ServerPlayer player, Session session) {
        PacketDistributor.sendToPlayer(player, new S2CRtsStoragePagePayload(
                hasAnyStorage(player, session),
                buildAnyStorageSummary(player, session),
                toPackedPositions(player, session.linkedStorages),
                0,
                1,
                0,
                session.search,
                session.category,
                (byte) session.sort.ordinal(),
                session.ascending,
                session.autoStoreMinedDrops,
                List.of(CATEGORY_ALL),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                buildQuickSlotPayload(session),
                buildGuiBindingLabelPayload(session),
                buildGuiBindingItemIdPayload(session),
                session.funnelEnabled,
                List.of(),
                List.of()));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit) {
        requestCraftables(player, search, showUnavailable, offset, limit, currentCraftPinyinSearchEnabled(player));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled) {
        requestCraftables(
                player,
                search,
                showUnavailable,
                offset,
                limit,
                pinyinSearchEnabled,
                currentCraftLocalizedSearchMatches(player));
    }

    public static void requestCraftables(ServerPlayer player, String search, boolean showUnavailable, int offset, int limit,
            boolean pinyinSearchEnabled, List<String> localizedSearchMatches) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        session.craftSearch = search == null ? "" : search.trim();
        session.craftShowUnavailable = showUnavailable;
        session.craftPinyinSearchEnabled = pinyinSearchEnabled;
        session.craftLocalizedSearchMatches.clear();
        session.craftLocalizedSearchMatches.addAll(sanitizeLocalizedSearchMatches(localizedSearchMatches));
        int batchOffset = Math.max(0, offset);
        int batchLimit = Math.max(1, limit);
        session.craftRequestedCount = Math.max(CRAFTABLE_BATCH_SIZE, batchOffset + batchLimit);
        saveSessionToPlayerNbt(player, session);

        if (session.craftSearch.isBlank()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            sendCraftables(player, session, List.of(), 0, false, false);
            return;
        }

        Map<String, Long> availableCounts = summarizeAvailableCraftItems(player, session, activeLinked);
        Map<String, List<CraftableCandidate>> byResultItem = new LinkedHashMap<>();
        var recipeManager = player.serverLevel().getRecipeManager();
        for (ResourceLocation id : recipeManager.getRecipeIds().toList()) {
            Recipe<?> raw = recipeManager.byKey(id).orElse(null);
            if (!(raw instanceof CraftingRecipe craftingRecipe)) {
                continue;
            }
            if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
                continue;
            }
            CraftableCandidate candidate = buildCraftableCandidate(
                    player,
                    id,
                    craftingRecipe,
                    availableCounts,
                    session.craftSearch,
                    session.craftPinyinSearchEnabled,
                    session.craftLocalizedSearchMatches);
            if (candidate == null) {
                continue;
            }
            byResultItem.computeIfAbsent(candidate.resultItemId(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<CraftableGroupEntry> groupedEntries = new ArrayList<>(byResultItem.size());
        for (List<CraftableCandidate> options : byResultItem.values()) {
            if (options == null || options.isEmpty()) {
                continue;
            }
            options.sort(CraftableCandidate::compareForRecipeSelection);
            boolean anyCraftable = options.stream().anyMatch(CraftableCandidate::craftable);
            if (!session.craftShowUnavailable && !anyCraftable) {
                continue;
            }
            groupedEntries.add(new CraftableGroupEntry(options.get(0), List.copyOf(options)));
        }

        groupedEntries.sort(CraftableGroupEntry::compareForPanel);
        int safeOffset = Math.min(groupedEntries.size(), batchOffset);
        int endExclusive = Math.min(groupedEntries.size(), safeOffset + batchLimit);
        boolean append = safeOffset > 0;
        boolean hasMore = endExclusive < groupedEntries.size();
        sendCraftables(player, session, new ArrayList<>(groupedEntries.subList(safeOffset, endExclusive)), safeOffset, append, hasMore);
    }

    public static void craftRecipeToLinked(ServerPlayer player, String recipeId) {
        craftRecipeToLinked(player, recipeId, 1);
    }

    public static void craftRecipeToLinked(ServerPlayer player, String recipeId, int craftCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            refreshCraftables(player, session);
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            refreshCraftables(player, session);
            return;
        }

        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            refreshCraftables(player, session);
            return;
        }

        Recipe<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (!(raw instanceof CraftingRecipe craftingRecipe)) {
            refreshCraftables(player, session);
            return;
        }
        if (!supportsWorkbenchCraftPanelRecipe(craftingRecipe)) {
            refreshCraftables(player, session);
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            refreshCraftables(player, session);
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        boolean includePlayerFallback = hasAnyStorage(player, session)
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        ItemStack previewResult = resolveCraftablePreviewResult(craftingRecipe, player);
        String resultLabel = previewResult.isEmpty() ? "item" : previewResult.getHoverName().getString();
        ResourceLocation previewResultId = previewResult.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(previewResult.getItem());
        int requestedCrafts = Math.max(1, Math.min(999, craftCount));
        int completedCrafts = 0;
        int totalCraftedCount = 0;
        boolean storageFull = false;
        String craftedItemId = previewResultId == null ? "" : previewResultId.toString();
        Map<String, Integer> consumedCounts = new LinkedHashMap<>();

        for (int i = 0; i < requestedCrafts; i++) {
            CraftExecutionResult result = craftSingleRecipeToLinked(player, handlers, craftingRecipe, includePlayerFallback);
            if (!result.success()) {
                storageFull = result.storageFull();
                break;
            }
            completedCrafts++;
            totalCraftedCount += result.resultCount();
            if (!result.resultItemId().isBlank()) {
                craftedItemId = result.resultItemId();
            }
            mergeConsumedCounts(consumedCounts, result.consumedCounts());
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        refreshCraftables(player, session);
        if (completedCrafts <= 0) {
            if (storageFull) {
                player.displayClientMessage(Component.literal("Craft: linked storage is full."), true);
            } else {
                player.displayClientMessage(Component.literal("Craft: missing ingredients."), true);
            }
            return;
        }

        recordRecentItem(session, craftedItemId, S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, totalCraftedCount);
        saveSessionToPlayerNbt(player, session);
        PacketDistributor.sendToPlayer(player, new S2CRtsCraftFeedbackPayload(
                craftedItemId,
                totalCraftedCount,
                new ArrayList<>(consumedCounts.keySet()),
                new ArrayList<>(consumedCounts.values())));
        StringBuilder summary = new StringBuilder("Crafted ")
                .append(totalCraftedCount)
                .append(" ")
                .append(resultLabel);
        if (completedCrafts < requestedCrafts) {
            summary.append(" (").append(completedCrafts).append("/").append(requestedCrafts).append(" crafts)");
            summary.append(storageFull ? ", linked storage full." : ", missing ingredients for the rest.");
        } else {
            summary.append(".");
        }
        player.displayClientMessage(Component.literal(summary.toString()), true);
        runQuestDetect(player, session, false);
    }

    private static void sendCraftables(
            ServerPlayer player,
            Session session,
            List<CraftableGroupEntry> candidates,
            int offset,
            boolean append,
            boolean hasMore) {
        List<String> recipeIds = new ArrayList<>(candidates.size());
        List<String> resultItemIds = new ArrayList<>(candidates.size());
        List<Integer> resultCounts = new ArrayList<>(candidates.size());
        List<Boolean> craftable = new ArrayList<>(candidates.size());
        List<String> missingSummaries = new ArrayList<>(candidates.size());
        List<Integer> recipeOptionCounts = new ArrayList<>(candidates.size());
        List<String> optionRecipeIds = new ArrayList<>();
        List<Integer> optionResultCounts = new ArrayList<>();
        List<Boolean> optionCraftable = new ArrayList<>();
        List<String> optionSummaries = new ArrayList<>();
        List<String> optionMissingSummaries = new ArrayList<>();
        for (CraftableGroupEntry group : candidates) {
            CraftableCandidate candidate = group.primary();
            recipeIds.add(candidate.recipeId());
            resultItemIds.add(candidate.resultItemId());
            resultCounts.add(candidate.resultCount());
            craftable.add(candidate.craftable());
            missingSummaries.add(candidate.missingSummary());
            recipeOptionCounts.add(group.options().size());
            for (CraftableCandidate option : group.options()) {
                optionRecipeIds.add(option.recipeId());
                optionResultCounts.add(option.resultCount());
                optionCraftable.add(option.craftable());
                optionSummaries.add(option.recipeSummary());
                optionMissingSummaries.add(option.missingSummary());
            }
        }

        PacketDistributor.sendToPlayer(player, new S2CRtsCraftablesPayload(
                session.craftSearch,
                session.craftShowUnavailable,
                Math.max(0, offset),
                append,
                hasMore,
                recipeIds,
                resultItemIds,
                resultCounts,
                craftable,
                missingSummaries,
                recipeOptionCounts,
                optionRecipeIds,
                optionResultCounts,
                optionCraftable,
                optionSummaries,
                optionMissingSummaries));
    }

    private static void refreshCraftables(ServerPlayer player, Session session) {
        requestCraftables(
                player,
                session.craftSearch,
                session.craftShowUnavailable,
                0,
                Math.max(CRAFTABLE_BATCH_SIZE, session.craftRequestedCount),
                session.craftPinyinSearchEnabled,
                List.copyOf(session.craftLocalizedSearchMatches));
    }

    private static Map<String, Long> summarizeAvailableCraftItems(ServerPlayer player, Session session, List<LinkedHandler> activeLinked) {
        Map<String, Long> counts = new HashMap<>();
        for (LinkedHandler linked : activeLinked) {
            IItemHandler handler = linked.handler();
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null) {
                    continue;
                }
                mergeCount(counts, id.toString(), getHandlerReportedCount(handler, i, stack));
            }
        }

        boolean includePlayerMainInventory = hasAnyStorage(player, session)
                && !(player.containerMenu instanceof RtsCraftTerminalMenu);
        if (includePlayerMainInventory) {
            accumulatePlayerMainInventoryCounts(player, counts, new HashMap<>());
        }
        return counts;
    }

    private static CraftableCandidate buildCraftableCandidate(ServerPlayer player, ResourceLocation recipeId, CraftingRecipe recipe,
            Map<String, Long> availableCounts, String search, boolean pinyinSearchEnabled,
            Set<String> localizedSearchMatches) {
        if (player == null || recipeId == null || recipe == null) {
            return null;
        }

        ItemStack result = resolveCraftablePreviewResult(recipe, player);
        if (result.isEmpty()) {
            return null;
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (resultId == null) {
            return null;
        }
        String resultLabel = result.getHoverName().getString();
        if (!matchesCraftablesSearch(resultId, resultLabel, search, pinyinSearchEnabled, localizedSearchMatches)) {
            return null;
        }

        RecipeAvailability availability = evaluateRecipeAvailability(recipe, availableCounts);
        return new CraftableCandidate(
                recipeId.toString(),
                resultId.toString(),
                Math.max(1, result.getCount()),
                resultLabel,
                availability.craftable(),
                availability.missingSummary(),
                availability.missingTotal(),
                buildRecipeSummary(recipe));
    }

    private static boolean supportsWorkbenchCraftPanelRecipe(CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() < 1 || shaped.getWidth() > 3 || shaped.getHeight() < 1 || shaped.getHeight() > 3) {
                return false;
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            if (shapeless.getIngredients().isEmpty() || shapeless.getIngredients().size() > 9) {
                return false;
            }
        } else {
            return false;
        }

        boolean anyNonEmpty = false;
        for (Ingredient ingredient : mapCraftingIngredients(recipe)) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            anyNonEmpty = true;
        }
        return anyNonEmpty;
    }

    private static ItemStack resolveCraftablePreviewResult(CraftingRecipe recipe, ServerPlayer player) {
        if (recipe == null || player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = recipe.getResultItem(player.serverLevel().registryAccess());
        if (!result.isEmpty()) {
            return result.copy();
        }

        Ingredient[] mapped = mapCraftingIngredients(recipe);
        List<ItemStack> previewStacks = new ArrayList<>(9);
        for (Ingredient ingredient : mapped) {
            if (ingredient == null || ingredient.isEmpty()) {
                previewStacks.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] options = ingredient.getItems();
            if (options.length <= 0 || options[0].isEmpty()) {
                return ItemStack.EMPTY;
            }
            previewStacks.add(options[0].copyWithCount(1));
        }

        TransientCraftingContainer input = createCraftingGrid(previewStacks);
        ItemStack assembled = recipe.assemble(input, player.serverLevel().registryAccess());
        return assembled.isEmpty() ? ItemStack.EMPTY : assembled.copy();
    }

    private static TransientCraftingContainer createCraftingGrid(List<ItemStack> stacks) {
        TransientCraftingContainer container = new TransientCraftingContainer(PREVIEW_CRAFTING_MENU, 3, 3);
        if (stacks == null) {
            return container;
        }
        for (int i = 0; i < Math.min(9, stacks.size()); i++) {
            ItemStack stack = stacks.get(i);
            container.setItem(i, stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return container;
    }

    private static RecipeAvailability evaluateRecipeAvailability(CraftingRecipe recipe, Map<String, Long> availableCounts) {
        Ingredient[] required = mapCraftingIngredients(recipe);
        Map<String, Long> remaining = new HashMap<>(availableCounts);
        Map<String, ItemStack> testStackCache = new HashMap<>();
        Map<String, Integer> missing = new LinkedHashMap<>();
        int missingTotal = 0;
        for (Ingredient ingredient : required) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            String matchedId = selectBestIngredientMatch(ingredient, remaining, testStackCache);
            if (matchedId != null) {
                remaining.computeIfPresent(matchedId, (id, count) -> count > 1L ? count - 1L : null);
                continue;
            }
            missing.merge(resolveIngredientLabel(ingredient), 1, Integer::sum);
            missingTotal++;
        }

        if (missingTotal <= 0) {
            return new RecipeAvailability(true, "", 0);
        }
        return new RecipeAvailability(false, buildMissingSummary(missing), missingTotal);
    }

    private static boolean matchesCraftablesSearch(ResourceLocation resultId, String resultLabel, String search,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return true;
        }
        String rawId = resultId.toString().toLowerCase(Locale.ROOT);
        if (localizedSearchMatches != null && localizedSearchMatches.contains(rawId)) {
            return true;
        }
        String label = resultLabel == null ? "" : resultLabel.toLowerCase(Locale.ROOT);
        String namespace = resultId.getNamespace().toLowerCase(Locale.ROOT);
        for (String token : query.split("\\s+")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (token.startsWith("@")) {
                String modQuery = token.substring(1).trim();
                if (!modQuery.isEmpty() && !namespace.contains(modQuery)) {
                    return false;
                }
                continue;
            }
            if (!rawId.contains(token) && !label.contains(token)
                    && !(pinyinSearchEnabled && RtsPinyinSearch.contains(resultLabel, token))) {
                return false;
            }
        }
        return true;
    }

    private static String selectBestIngredientMatch(Ingredient ingredient, Map<String, Long> remaining, Map<String, ItemStack> testStackCache) {
        String bestId = null;
        long bestCount = 0L;
        for (var entry : remaining.entrySet()) {
            long available = entry.getValue() == null ? 0L : entry.getValue();
            if (available <= 0L) {
                continue;
            }
            ItemStack probe = resolveIngredientProbeStack(entry.getKey(), testStackCache);
            if (probe.isEmpty() || !ingredient.test(probe)) {
                continue;
            }
            if (bestId == null || available > bestCount) {
                bestId = entry.getKey();
                bestCount = available;
            }
        }
        return bestId;
    }

    private static ItemStack resolveIngredientProbeStack(String itemId, Map<String, ItemStack> testStackCache) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ItemStack cached = testStackCache.get(itemId);
        if (cached != null) {
            return cached;
        }

        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            testStackCache.put(itemId, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        ItemStack resolved = new ItemStack(BuiltInRegistries.ITEM.get(key));
        testStackCache.put(itemId, resolved);
        return resolved;
    }

    private static String resolveIngredientLabel(Ingredient ingredient) {
        for (ItemStack option : ingredient.getItems()) {
            if (!option.isEmpty()) {
                return option.getHoverName().getString();
            }
        }
        return "Ingredient";
    }

    private static String buildMissingSummary(Map<String, Integer> missing) {
        if (missing.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder("Missing: ");
        int index = 0;
        int total = missing.size();
        for (var entry : missing.entrySet()) {
            if (index > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append(" x").append(entry.getValue());
            index++;
            if (index >= 3 && total > index) {
                summary.append("...");
                break;
            }
        }
        return summary.toString();
    }

    private static String buildRecipeSummary(CraftingRecipe recipe) {
        if (recipe == null) {
            return "Recipe";
        }
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (Ingredient ingredient : mapCraftingIngredients(recipe)) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            ingredients.merge(resolveIngredientLabel(ingredient), 1, Integer::sum);
        }
        if (ingredients.isEmpty()) {
            return "Recipe";
        }
        StringBuilder summary = new StringBuilder();
        int index = 0;
        int total = ingredients.size();
        for (var entry : ingredients.entrySet()) {
            if (index > 0) {
                summary.append(", ");
            }
            summary.append(entry.getKey());
            if (entry.getValue() > 1) {
                summary.append(" x").append(entry.getValue());
            }
            index++;
            if (index >= 3 && total > index) {
                summary.append("...");
                break;
            }
        }
        return summary.isEmpty() ? "Recipe" : summary.toString();
    }

    private static void mergeConsumedCounts(Map<String, Integer> into, Map<String, Integer> added) {
        if (into == null || added == null || added.isEmpty()) {
            return;
        }
        for (var entry : added.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            int delta = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            if (delta <= 0) {
                continue;
            }
            into.merge(entry.getKey(), delta, Integer::sum);
        }
    }

    private static Map<String, Integer> collectConsumedCounts(ExtractedIngredient[] extracted) {
        Map<String, Integer> consumed = new LinkedHashMap<>();
        if (extracted == null) {
            return consumed;
        }
        for (ExtractedIngredient ingredient : extracted) {
            if (ingredient == null || ingredient.stack().isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(ingredient.stack().getItem());
            if (itemId == null) {
                continue;
            }
            consumed.merge(itemId.toString(), Math.max(1, ingredient.stack().getCount()), Integer::sum);
        }
        return consumed;
    }

    static String normalizeCategory(String category) {
        if (category == null) {
            return CATEGORY_ALL;
        }
        String value = category.toLowerCase(Locale.ROOT).trim();
        if (value.isEmpty() || CATEGORY_ALL.equals(value)) {
            return CATEGORY_ALL;
        }
        if (value.startsWith(CATEGORY_MOD_PREFIX) || value.startsWith(CATEGORY_TAB_PREFIX)) {
            return value;
        }
        // Backward compatibility for legacy category payloads that only sent namespace.
        return encodeModCategory(value);
    }

    private static boolean matchesSearchQuery(ResourceLocation id, String rawId, String label, String query,
            boolean pinyinSearchEnabled, Set<String> localizedSearchMatches) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String normalizedId = rawId == null ? "" : rawId.toLowerCase(Locale.ROOT);
        if (localizedSearchMatches != null && localizedSearchMatches.contains(normalizedId)) {
            return true;
        }
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            if (modQuery.isEmpty()) {
                return true;
            }
            String namespace = id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
            return namespace.contains(modQuery);
        }
        if (normalizedId.contains(query)) {
            return true;
        }
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return normalizedLabel.contains(query) || (pinyinSearchEnabled && RtsPinyinSearch.contains(label, query));
    }

    private static int compareNamespace(String a, String b) {
        if ("minecraft".equals(a)) {
            return "minecraft".equals(b) ? 0 : -1;
        }
        if ("minecraft".equals(b)) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    private static int compareTabKey(String a, String b) {
        ResourceLocation aId = ResourceLocation.tryParse(a);
        ResourceLocation bId = ResourceLocation.tryParse(b);
        String aName = aId == null ? a : aId.getPath();
        String bName = bId == null ? b : bId.getPath();
        int byName = aName.compareToIgnoreCase(bName);
        return byName != 0 ? byName : a.compareToIgnoreCase(b);
    }

    private static String encodeModCategory(String namespace) {
        return CATEGORY_MOD_PREFIX + namespace;
    }

    private static String encodeTabCategory(String namespace, String tabKey) {
        return CATEGORY_TAB_PREFIX + namespace + "|" + tabKey;
    }

    private static Set<String> resolveCreativeTabKeys(String itemId, Item item, boolean operatorTabs) {
        Set<String> tabKeys = ITEM_CREATIVE_TAB_CACHE.get(creativeTabItemCacheKey(itemId, operatorTabs));
        return tabKeys == null ? Set.of() : tabKeys;
    }

    private static boolean ensureCreativeTabContents(ServerPlayer player) {
        boolean operatorTabs = player.canUseGameMasterBlocks();
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return true;
        }
        synchronized (RtsStorageManager.class) {
            if (isCreativeTabCacheWarm(operatorTabs)) {
                return true;
            }
            warmCreativeTabCacheMode(player.serverLevel(), operatorTabs);
            return true;
        }
    }

    private static void warmCreativeTabCacheMode(ServerLevel level, boolean operatorTabs) {
        if (isCreativeTabCacheWarm(operatorTabs)) {
            return;
        }
        rebuildCreativeTabContentsSafely(level, operatorTabs);
        setCreativeTabCacheWarm(operatorTabs);
    }

    private static void clearCreativeTabCacheState() {
        ITEM_CREATIVE_TAB_CACHE.clear();
        BROKEN_CREATIVE_TAB_CACHE.clear();
        creativeTabCacheWarmNormal = false;
        creativeTabCacheWarmOperator = false;
    }

    private static void rebuildCreativeTabContentsSafely(ServerLevel level, boolean operatorTabs) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(),
                operatorTabs,
                level.registryAccess());
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, true);
        rebuildCreativeTabContentsSafely(parameters, operatorTabs, false);
    }

    private static void rebuildCreativeTabContentsSafely(
            CreativeModeTab.ItemDisplayParameters parameters,
            boolean operatorTabs,
            boolean categoryTabs) {
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            if (tab == null) {
                continue;
            }
            boolean category = tab.getType() == CreativeModeTab.Type.CATEGORY;
            if (category != categoryTabs) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (isBrokenCreativeTab(key, operatorTabs)) {
                continue;
            }
            try {
                tab.buildContents(parameters);
                if (category) {
                    indexCreativeTabContents(tab, key, operatorTabs);
                }
            } catch (RuntimeException | LinkageError ex) {
                markBrokenCreativeTab(key, operatorTabs, ex);
            }
        }
    }

    private static void indexCreativeTabContents(CreativeModeTab tab, ResourceLocation key, boolean operatorTabs) {
        if (key == null || !tab.shouldDisplay()) {
            return;
        }
        String tabKey = key.toString();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                continue;
            }
            ITEM_CREATIVE_TAB_CACHE.compute(creativeTabItemCacheKey(itemId.toString(), operatorTabs), (ignored, existing) -> {
                Set<String> tabs = existing == null ? ConcurrentHashMap.newKeySet() : existing;
                tabs.add(tabKey);
                return tabs;
            });
        }
    }

    private static boolean isBrokenCreativeTab(ResourceLocation key, boolean operatorTabs) {
        return BROKEN_CREATIVE_TAB_CACHE.contains(creativeTabModeKey(key, operatorTabs));
    }

    private static void markBrokenCreativeTab(ResourceLocation key, boolean operatorTabs, Throwable ex) {
        String tabKey = key == null ? "unknown" : key.toString();
        if (!BROKEN_CREATIVE_TAB_CACHE.add(creativeTabModeKey(tabKey, operatorTabs))) {
            return;
        }
        RtsbuildingMod.LOGGER.warn(
                "Skipping RTS creative tab {} for {} cache because it failed to build. "
                        + "The RTS storage browser will continue without this tab.",
                tabKey,
                operatorTabs ? "operator" : "normal",
                ex);
    }

    private static String creativeTabModeKey(ResourceLocation key, boolean operatorTabs) {
        return creativeTabModeKey(key == null ? "unknown" : key.toString(), operatorTabs);
    }

    private static String creativeTabModeKey(String key, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + key;
    }

    private static String creativeTabItemCacheKey(String itemId, boolean operatorTabs) {
        return (operatorTabs ? "op|" : "normal|") + itemId;
    }

    private static boolean isCreativeTabCacheWarm(boolean operatorTabs) {
        return operatorTabs ? creativeTabCacheWarmOperator : creativeTabCacheWarmNormal;
    }

    private static void setCreativeTabCacheWarm(boolean operatorTabs) {
        if (operatorTabs) {
            creativeTabCacheWarmOperator = true;
        } else {
            creativeTabCacheWarmNormal = true;
        }
    }

    private static boolean hasPositiveInternalFluid(Session session) {
        if (session == null) {
            return false;
        }
        for (Long amount : session.internalFluidMb.values()) {
            if (amount != null && amount > 0L) {
                return true;
            }
        }
        return false;
    }

    private static boolean shouldIncludePlayerMainInventoryInStorageView(ServerPlayer player, Session session) {
        if (player == null || player.containerMenu instanceof RtsCraftTerminalMenu) {
            return false;
        }
        if (session != null && !hasAnyStorage(player, session)) {
            return true;
        }
        return player.containerMenu == player.inventoryMenu;
    }

    private static CategorySelection parseCategorySelection(String normalizedCategory) {
        if (normalizedCategory == null || CATEGORY_ALL.equals(normalizedCategory)) {
            return CategorySelection.all();
        }
        if (normalizedCategory.startsWith(CATEGORY_MOD_PREFIX)) {
            String namespace = normalizedCategory.substring(CATEGORY_MOD_PREFIX.length());
            if (namespace.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.mod(namespace);
        }
        if (normalizedCategory.startsWith(CATEGORY_TAB_PREFIX)) {
            String payload = normalizedCategory.substring(CATEGORY_TAB_PREFIX.length());
            int split = payload.indexOf('|');
            if (split <= 0 || split >= payload.length() - 1) {
                return CategorySelection.all();
            }
            String namespace = payload.substring(0, split);
            String tabKey = payload.substring(split + 1);
            if (namespace.isBlank() || tabKey.isBlank()) {
                return CategorySelection.all();
            }
            return CategorySelection.tab(namespace, tabKey);
        }
        return CategorySelection.all();
    }

    private static boolean isValidCategorySelection(CategorySelection selection, List<String> categories) {
        if (selection == null || selection.type() == CategorySelection.Type.ALL) {
            return true;
        }
        String token = switch (selection.type()) {
            case MOD -> encodeModCategory(selection.namespace());
            case TAB -> encodeTabCategory(selection.namespace(), selection.tabKey());
            case ALL -> CATEGORY_ALL;
        };
        return categories.contains(token);
    }

    public static void placeSelected(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild) {
        placeSelectedInternal(
                player,
                clickedPos,
                face,
                hitX,
                hitY,
                hitZ,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ,
                quickBuild,
                true,
                true);
    }

    public static void enqueuePlaceBatch(ServerPlayer player, List<BlockPos> clickedPositions, Direction face,
            byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        if (clickedPositions == null || clickedPositions.isEmpty() || face == null) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        List<BlockPos> positions = new ArrayList<>(Math.min(clickedPositions.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockPos pos : clickedPositions) {
            if (pos == null || !canAccessWorldTarget(player, pos)) {
                continue;
            }
            positions.add(pos.immutable());
            if (positions.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                break;
            }
        }
        if (positions.isEmpty()) {
            return;
        }
        while (session.placeBatchJobs.size() >= QUICK_BUILD_BATCH_MAX_QUEUED_JOBS) {
            session.placeBatchJobs.removeFirst();
        }
        session.placeBatchJobs.addLast(new PlaceBatchJob(
                positions,
                face,
                rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                rayOriginX,
                rayOriginY,
                rayOriginZ,
                rayDirX,
                rayDirY,
                rayDirZ));
    }

    private static void tickPlaceBatchJobs(ServerPlayer player, Session session) {
        int remaining = QUICK_BUILD_BATCH_BLOCKS_PER_TICK;
        boolean finishedJob = false;
        while (remaining > 0 && !session.placeBatchJobs.isEmpty()) {
            PlaceBatchJob job = session.placeBatchJobs.peekFirst();
            while (remaining > 0 && job.hasNext()) {
                BlockPos clickedPos = job.next();
                Vec3 faceNormal = Vec3.atLowerCornerOf(job.face().getNormal());
                Vec3 hitLocation = Vec3.atCenterOf(clickedPos).add(faceNormal.scale(0.5D));
                boolean keepGoing = placeSelectedInternal(
                        player,
                        clickedPos,
                        job.face(),
                        hitLocation.x,
                        hitLocation.y,
                        hitLocation.z,
                        job.rotateSteps(),
                        job.forcePlace(),
                        job.skipIfOccupied(),
                        job.itemId(),
                        job.rayOriginX(),
                        job.rayOriginY(),
                        job.rayOriginZ(),
                        job.rayDirX(),
                        job.rayDirY(),
                        job.rayDirZ(),
                        true,
                        false,
                        false);
                remaining--;
                if (!keepGoing) {
                    session.placeBatchJobs.removeFirst();
                    finishedJob = true;
                    break;
                }
            }
            if (!session.placeBatchJobs.isEmpty() && session.placeBatchJobs.peekFirst() == job && !job.hasNext()) {
                session.placeBatchJobs.removeFirst();
                finishedJob = true;
            }
        }
        if (finishedJob) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    private static boolean placeSelectedInternal(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, byte rotateSteps, boolean forcePlace, boolean skipIfOccupied, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild, boolean refreshStoragePage,
            boolean sendRemoteHint) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, clickedPos) || face == null) {
            return false;
        }
        sanitizeSessionDimension(player, session);
        boolean useSelectedStorageItem = itemId != null && !itemId.isBlank();

        ServerLevel level = player.serverLevel();
        Vec3 hitLocation = new Vec3(hitX, hitY, hitZ);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clickedPos, false);
        Vec3 interactionPos = resolveInteractionPosition(null, hit, hitLocation);
        RayContext rayContext = parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);
        if (sendRemoteHint) {
            sendRemoteMenuOpenHint(player, clickedPos);
        }

        if (!useSelectedStorageItem) {
            ItemStack sourceSnapshot = player.getMainHandItem().copy();
            boolean sourcePlacesBlock = sourceSnapshot.getItem() instanceof BlockItem;
            if (skipIfOccupied && player.getMainHandItem().getItem() instanceof BlockItem) {
                if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                    if (refreshStoragePage) {
                        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                    }
                    return true;
                }
            }

            BlockState beforeClicked = level.getBlockState(clickedPos);
            BlockPos adjacentPos = clickedPos.relative(face);
            BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

            AbstractContainerMenu menuBeforeMainHandUse = player.containerMenu;
            InteractionResult mainHandUse = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            hit)));
            AbstractContainerMenu menuAfterMainHandUse = player.containerMenu;
            if (menuAfterMainHandUse != menuBeforeMainHandUse) {
                markRemoteMenuOpen(player, session, menuAfterMainHandUse, clickedPos);
                return false;
            }

            if (mainHandUse.consumesAction()) {
                BlockPos placedPos = detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
                if (placedPos != null) {
                    PlacedBlockTrackerData.get(level).mark(placedPos);
                    if (sourcePlacesBlock) {
                        playRemotePlacedBlockSound(player, level, session, placedPos, quickBuild);
                    } else {
                        playRemoteUseSound(player, level, null, placedPos, sourceSnapshot);
                    }
                    ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                    if (sourceId != null) {
                        recordRecentItem(session, sourceId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
                    }
                } else if (!sourceSnapshot.isEmpty()) {
                    playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
                    ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                    if (sourceId != null) {
                        recordRecentItem(session, sourceId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                    }
                }
                saveSessionToPlayerNbt(player, session);
                return true;
            }

            // Some items (e.g. bucket) work via "use in air" fallback instead of use-on-block.
            AbstractContainerMenu menuBeforeUseFallback = player.containerMenu;
            InteractionResult mainHandUseFallback = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND)));
            AbstractContainerMenu menuAfterUseFallback = player.containerMenu;
            if (menuAfterUseFallback != menuBeforeUseFallback) {
                markRemoteMenuOpen(player, session, menuAfterUseFallback, clickedPos);
                return false;
            }
            if (mainHandUseFallback.consumesAction()) {
                if (!sourceSnapshot.isEmpty()) {
                    playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
                    ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                    if (sourceId != null) {
                        recordRecentItem(session, sourceId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                    }
                }
                saveSessionToPlayerNbt(player, session);
                return true;
            }

            return false;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        boolean includePlayerMainInventory = shouldIncludePlayerMainInventoryInStorageView(player, session);
        if (activeLinked.isEmpty() && !includePlayerMainInventory) {
            return false;
        }

        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        if (skipIfOccupied && item instanceof BlockItem) {
            if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                if (refreshStoragePage) {
                    requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                }
                return true;
            }
        }
        ItemStack extracted = includePlayerMainInventory
                ? extractOneFromNetwork(handlers, player, item)
                : extractOneFromLinked(handlers, item);
        if (extracted.isEmpty()) {
            if (refreshStoragePage) {
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
            return false;
        }
        ItemStack selectedSoundStack = extracted.copy();
        boolean selectedPlacesBlock = item instanceof BlockItem;

        BlockState beforeClicked = level.getBlockState(clickedPos);
        BlockPos adjacentPos = clickedPos.relative(face);
        BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

        AbstractContainerMenu menuBeforeSelectedUse = player.containerMenu;
        UseOnOutcome selectedOutcome = withTemporaryUseItemContext(
                player,
                interactionPos,
                hitLocation,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> useItemOnWithMainHand(player, level, extracted, hit, forcePlace));
        AbstractContainerMenu menuAfterSelectedUse = player.containerMenu;
        if (menuAfterSelectedUse != menuBeforeSelectedUse) {
            markRemoteMenuOpen(player, session, menuAfterSelectedUse, clickedPos);
        }

        UseOnOutcome finalOutcome = selectedOutcome;
        if (!selectedOutcome.result().consumesAction()) {
            ItemStack fallbackStack = selectedOutcome.remainder().isEmpty() ? extracted.copy() : selectedOutcome.remainder().copy();
            finalOutcome = withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hitLocation,
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> useItemWithMainHand(player, level, fallbackStack, forcePlace));
        }
        if (!finalOutcome.remainder().isEmpty()) {
            refundToLinked(handlers, player, finalOutcome.remainder());
        }

        if (!finalOutcome.result().consumesAction()) {
            if (refreshStoragePage) {
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
            return false;
        }

        BlockPos placedPos = detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
        if (placedPos != null) {
            rotatePlacedBlock(level, placedPos, rotateSteps);
            PlacedBlockTrackerData.get(level).mark(placedPos);
            if (selectedPlacesBlock) {
                playRemotePlacedBlockSound(player, level, session, placedPos, quickBuild);
            } else {
                playRemoteUseSound(player, level, null, placedPos, selectedSoundStack);
            }
            recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        } else {
            playRemoteUseSound(player, level, null, clickedPos, selectedSoundStack);
            recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
        }

        if (refreshStoragePage) {
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
        return true;
    }

    private static BlockPos resolvePlacementTargetPos(ServerLevel level, BlockPos clickedPos, Direction face) {
        if (level == null || clickedPos == null || face == null) {
            return null;
        }
        if (!level.hasChunkAt(clickedPos)) {
            return clickedPos;
        }
        return level.getBlockState(clickedPos).canBeReplaced() ? clickedPos : clickedPos.relative(face);
    }

    public static void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        Session session = getOrCreateSession(player);
        if (!RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeItemHandlers = resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        List<IItemHandler> itemHandlers = new ArrayList<>(activeItemHandlers.size());
        for (LinkedHandler linked : activeItemHandlers) {
            itemHandlers.add(linked.handler());
        }

        boolean changed = switch (sourceType) {
            case C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM ->
                storeFluidFromLinkedItem(player, session, itemHandlers, activeFluidHandlers, itemId);
            case C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT ->
                storeFluidFromToolSlot(player, session, activeFluidHandlers, clampHotbarSlot(toolSlot));
            default -> false;
        };
        if (changed) {
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, boolean forcePlace, String fluidId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessFluidPlacementTarget(player, clickedPos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (fluidId == null || fluidId.isBlank()) {
            return;
        }

        ResourceLocation fluidKey = ResourceLocation.tryParse(fluidId);
        if (fluidKey == null || !BuiltInRegistries.FLUID.containsKey(fluidKey)) {
            return;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidKey);
        if (fluid == null) {
            return;
        }

        List<LinkedFluidHandler> activeFluidHandlers = resolveLinkedFluidHandlers(player, session);
        if (extractFluidFromNetwork(session, activeFluidHandlers, fluid, FLUID_TRANSFER_MB, false) < FLUID_TRANSFER_MB) {
            return;
        }

        ServerLevel level = player.serverLevel();
        FluidStack transfer = new FluidStack(fluid, FLUID_TRANSFER_MB);

        int filledIntoBlock = fillFluidHandlerAtTarget(level, clickedPos, face, transfer);
        if (filledIntoBlock > 0) {
            int consumed = extractFluidFromNetwork(session, activeFluidHandlers, fluid, filledIntoBlock, true);
            if (consumed > 0) {
                recordRecentFluid(session, fluidId, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, consumed, FLUID_TRANSFER_MB);
                saveSessionToPlayerNbt(player, session);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            }
            return;
        }

        BlockHitResult hit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, clickedPos, false);
        BlockPos placePos = resolveFluidPlacementPos(level, player, hit, transfer);
        if (placePos == null) {
            return;
        }
        BlockHitResult placementHit = resolveFluidPlacementHit(hit, placePos);

        if (!placeFluidBlock(level, player, placePos, transfer, placementHit)) {
            return;
        }

        int extracted = extractFluidFromNetwork(session, activeFluidHandlers, fluid, FLUID_TRANSFER_MB, true);
        if (extracted > 0) {
            recordRecentFluid(session, fluidId, S2CRtsStoragePagePayload.RECENT_FLUID_PLACED, extracted, FLUID_TRANSFER_MB);
            saveSessionToPlayerNbt(player, session);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        }
    }

    public static void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face, double hitX,
            double hitY, double hitZ, byte sourceType, byte toolSlot, String itemId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.INTERACT)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        RayContext rayContext = parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        ServerLevel level = player.serverLevel();
        Entity targetEntity = null;
        BlockHitResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        BlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        BlockState beforeAdjacent = null;

        if (entityId >= 0) {
            targetEntity = level.getEntity(entityId);
            if (targetEntity == null || !targetEntity.isAlive()) {
                return;
            }
            effectiveBlockPos = targetEntity.blockPosition();
            if (!level.hasChunkAt(effectiveBlockPos) || !level.mayInteract(player, effectiveBlockPos)) {
                return;
            }
        } else {
            if (clickedPos == null || !canAccessWorldTarget(player, clickedPos)) {
                return;
            }
            effectiveBlockPos = clickedPos.immutable();
            blockHit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, effectiveBlockPos, false);
            beforeClicked = level.getBlockState(effectiveBlockPos);
            adjacentPos = effectiveBlockPos.relative(face);
            beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;
        }

        InteractionResult result = InteractionResult.PASS;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        if (blockHit != null) {
            sendRemoteMenuOpenHint(player, effectiveBlockPos);
        }
        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT
                ? player.getInventory().getItem(clampHotbarSlot(toolSlot)).copy()
                : ItemStack.EMPTY;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? createSoundStack(itemId)
                : toolSnapshot.copy();
        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
            result = interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
            result = interactWithLinkedItem(player, level, session, targetEntity, blockHit, hit, itemId, rayContext);
        }
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            markRemoteMenuOpen(player, session, menuAfterInteract, effectiveBlockPos);
        }

        boolean playedSpecificSound = false;
        if (result.consumesAction() && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = detectPlacedPos(level, effectiveBlockPos, beforeClicked, adjacentPos, beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
                if (!soundStack.isEmpty() && soundStack.getItem() instanceof BlockItem) {
                    playRemotePlacedBlockSound(player, level, session, placedPos, false);
                } else {
                    playRemoteUseSound(player, level, targetEntity, placedPos, soundStack);
                }
                playedSpecificSound = true;
            }
        }
        if (result.consumesAction()) {
            if (!playedSpecificSound) {
                playRemoteUseSound(player, level, targetEntity, effectiveBlockPos, soundStack);
            }
            if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM && itemId != null && !itemId.isBlank()) {
                recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
            } else if (!toolSnapshot.isEmpty()) {
                ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(toolSnapshot.getItem());
                if (toolId != null) {
                    recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void returnCarriedToLinked(ServerPlayer player, String itemId, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }
        if (itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }

        ResourceLocation carriedId = BuiltInRegistries.ITEM.getKey(carried.getItem());
        if (carriedId == null || !itemId.equals(carriedId.toString())) {
            return;
        }

        int returned = Math.min(amount, carried.getCount());
        if (returned <= 0) {
            return;
        }

        ItemStack toStore = carried.split(returned);
        player.containerMenu.setCarried(carried);
        OverflowOutcome overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, toStore);
        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void quickDropLinkedItem(ServerPlayer player, String itemId, byte amount, double dropX, double dropY,
            double dropZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (!Double.isFinite(dropX) || !Double.isFinite(dropY) || !Double.isFinite(dropZ)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        int wanted = Math.max(1, Math.min(64, amount));
        ItemStack extracted = extractMatchingFromQuickDropSources(handlers, player, item, wanted);
        if (extracted.isEmpty()) {
            return;
        }

        Vec3 dropPos = new Vec3(dropX, dropY, dropZ);
        BlockPos dropBlock = BlockPos.containing(dropPos);
        if (!player.serverLevel().hasChunkAt(dropBlock)
                || !RtsCameraManager.isWithinActionRadius(player, dropBlock)
                || !RtsProgressionManager.canAccessHomeRadius(player, dropBlock)) {
            refundToLinked(handlers, player, extracted);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        ItemEntity dropped = new ItemEntity(player.serverLevel(), dropPos.x, dropPos.y, dropPos.z, extracted);
        dropped.setDeltaMovement(Vec3.ZERO);
        dropped.setPickUpDelay(10);
        player.serverLevel().addFreshEntity(dropped);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void importMenuSlotToLinked(ServerPlayer player, int menuSlot) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Slot slot = menu.slots.get(menuSlot);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) {
            return;
        }

        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        if (menu instanceof CraftingMenu && menuSlot == 0) {
            CraftingMenu craftingMenu = (CraftingMenu) menu;
            ItemStack[] craftBlueprint = snapshotCraftGridBlueprint(craftingMenu);
            ItemStack resultSnapshot = slot.getItem().copy();
            if (resultSnapshot.isEmpty()) {
                return;
            }
            ItemStack resultPrototype = resultSnapshot.copyWithCount(1);
            boolean craftedAny = false;
            for (int guard = 0; guard < SHIFT_IMPORT_MAX_CRAFT_ITERATIONS; guard++) {
                Slot resultSlot = craftingMenu.getSlot(0);
                ItemStack currentResult = resultSlot.getItem();
                if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                    // Try one refill step so shift-craft can continue seamlessly in the same click.
                    refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, true);
                    currentResult = resultSlot.getItem();
                    if (currentResult.isEmpty() || !ItemStack.isSameItemSameTags(currentResult, resultPrototype)) {
                        break;
                    }
                }

                int[] before = snapshotPlayerMatchingCounts(player, resultPrototype);
                ItemStack moved = craftingMenu.quickMoveStack(player, menuSlot);
                if (moved.isEmpty()) {
                    break;
                }

                ItemStack gained = drainPlayerInventoryDelta(player, resultPrototype, before);
                if (gained.isEmpty()) {
                    break;
                }

                ResourceLocation gainedId = BuiltInRegistries.ITEM.getKey(gained.getItem());
                if (gainedId != null) {
                    recordRecentItem(session, gainedId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, gained.getCount());
                }
                overflow = overflow.merge(storeToLinkedWithFallbackPreferExisting(handlers, player, gained));
                craftedAny = true;

                // Refill from linked storage first, then player main inventory, without touching the hotbar.
                refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, false, true);
            }

            if (!craftedAny) {
                return;
            }
            refillCraftGridFromBlueprint(craftingMenu, handlers, player, craftBlueprint, true, true);
        } else {
            ItemStack inSlot = slot.getItem();
            ItemStack moved = slot.safeTake(inSlot.getCount(), inSlot.getCount(), player);
            if (moved.isEmpty()) {
                return;
            }
            if (menu instanceof CraftingMenu && menuSlot == 0) {
                ResourceLocation craftedId = BuiltInRegistries.ITEM.getKey(moved.getItem());
                if (craftedId != null) {
                    recordRecentItem(session, craftedId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, moved.getCount());
                }
            }
            overflow = storeToLinkedWithFallbackPreferExisting(handlers, player, moved);
        }

        if (overflow.hasOverflow()) {
            sendStorageOverflowHint(player, "Import", overflow);
        }
        menu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void refillCraftGridFromLinked(ServerPlayer player, CraftingMenu craftingMenu, ItemStack[] blueprint) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || craftingMenu == null || blueprint == null || blueprint.length != 9) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        refillCraftGridFromBlueprint(craftingMenu, handlers, player, blueprint, false, true);
        craftingMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void refillCurrentCraftGridFromBlueprintIds(
            ServerPlayer player,
            List<String> blueprintIds,
            String craftedItemId,
            int craftedCount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.CRAFT_TERMINAL)) {
            return;
        }
        if (player == null || blueprintIds == null || blueprintIds.size() != 9) {
            return;
        }
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session != null && craftedItemId != null && !craftedItemId.isBlank() && craftedCount > 0) {
            recordRecentItem(session, craftedItemId, S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED, craftedCount);
            saveSessionToPlayerNbt(player, session);
        }

        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < blueprint.length; i++) {
            String itemId = blueprintIds.get(i);
            if (itemId == null || itemId.isBlank()) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                blueprint[i] = ItemStack.EMPTY;
                continue;
            }
            blueprint[i] = new ItemStack(BuiltInRegistries.ITEM.get(key));
        }
        refillCraftGridFromLinked(player, craftingMenu, blueprint);
    }

    public static void applyJeiTransfer(ServerPlayer player, String recipeId, boolean maxTransfer, boolean clearGridFirst) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.JEI_TRANSFER)) {
            return;
        }
        Session session = getOrCreateSession(player);
        sanitizeSessionDimension(player, session);
        if (!(player.containerMenu instanceof CraftingMenu craftingMenu)) {
            return;
        }
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }

        ResourceLocation key = ResourceLocation.tryParse(recipeId);
        if (key == null) {
            return;
        }
        Recipe<?> raw = player.serverLevel().getRecipeManager().byKey(key).orElse(null);
        if (!(raw instanceof CraftingRecipe craftingRecipe)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        Ingredient[] required = mapCraftingIngredients(craftingRecipe);
        if (required.length != 9) {
            return;
        }

        List<ItemStack> cleared = new ArrayList<>(9);
        if (clearGridFirst) {
            for (int i = 0; i < 9; i++) {
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (existing.isEmpty()) {
                    cleared.add(ItemStack.EMPTY);
                    continue;
                }
                ItemStack copy = existing.copy();
                grid.set(ItemStack.EMPTY);
                grid.setChanged();
                cleared.add(copy);
            }
        } else {
            for (int i = 0; i < 9; i++) {
                cleared.add(ItemStack.EMPTY);
            }
        }

        boolean anyInserted = false;
        int maxPasses = maxTransfer ? 64 : 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean passInsertedAny = false;
            for (int i = 0; i < 9; i++) {
                Ingredient ingredient = required[i];
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                Slot grid = craftingMenu.getSlot(1 + i);
                ItemStack existing = grid.getItem();
                if (!existing.isEmpty()) {
                    if (!ingredient.test(existing)) {
                        continue;
                    }
                    if (existing.getCount() >= existing.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = extractOneMatchingIngredientCombined(handlers, player, ingredient, existing);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    existing.grow(1);
                    grid.setChanged();
                    passInsertedAny = true;
                    anyInserted = true;
                    continue;
                }

                ItemStack extracted = extractOneMatchingIngredientCombined(handlers, player, ingredient, ItemStack.EMPTY);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                passInsertedAny = true;
                anyInserted = true;
            }

            if (!passInsertedAny) {
                break;
            }
            if (!maxTransfer) {
                break;
            }
        }

        for (ItemStack stack : cleared) {
            if (stack.isEmpty()) {
                continue;
            }
            storeToLinkedWithFallbackPreferExisting(handlers, player, stack);
        }
        refreshCraftingResult(craftingMenu);
        craftingMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        if (anyInserted) {
            runQuestDetect(player, session, false);
        }
    }

    private static CraftExecutionResult craftSingleRecipeToLinked(ServerPlayer player, List<IItemHandler> handlers,
            CraftingRecipe recipe, boolean includePlayerFallback) {
        Ingredient[] required = mapCraftingIngredients(recipe);
        if (required.length != 9) {
            return CraftExecutionResult.failure(false);
        }

        ExtractedIngredient[] extracted = new ExtractedIngredient[9];
        List<ItemStack> inputStacks = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            Ingredient ingredient = required[i];
            if (ingredient == null || ingredient.isEmpty()) {
                inputStacks.add(ItemStack.EMPTY);
                continue;
            }

            ExtractedIngredient taken = takeIngredientForCraft(handlers, player, ingredient, includePlayerFallback);
            if (taken == null || taken.stack().isEmpty()) {
                rollbackCraftIngredients(handlers, player, extracted);
                return CraftExecutionResult.failure(false);
            }
            extracted[i] = taken;
            inputStacks.add(taken.stack().copyWithCount(1));
        }

        TransientCraftingContainer input = createCraftingGrid(inputStacks);
        if (!recipe.matches(input, player.serverLevel())) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        ItemStack result = recipe.assemble(input, player.serverLevel().registryAccess());
        if (result.isEmpty()) {
            rollbackCraftIngredients(handlers, player, extracted);
            return CraftExecutionResult.failure(false);
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result.copy());
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(input);
        for (ItemStack remain : remaining) {
            if (!remain.isEmpty()) {
                outputs.add(remain.copy());
            }
        }

        List<ItemStack> storedOutputs = new ArrayList<>(outputs.size());
        for (ItemStack stack : outputs) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, stack);
            int storedCount = Math.max(0, stack.getCount() - remain.getCount());
            if (storedCount > 0) {
                storedOutputs.add(stack.copyWithCount(storedCount));
            }
            if (!remain.isEmpty()) {
                rollbackStoredCraftOutputs(handlers, storedOutputs);
                rollbackCraftIngredients(handlers, player, extracted);
                return CraftExecutionResult.failure(true);
            }
        }

        ResourceLocation resultId = BuiltInRegistries.ITEM.getKey(result.getItem());
        return new CraftExecutionResult(
                true,
                false,
                resultId == null ? "" : resultId.toString(),
                Math.max(1, result.getCount()),
                collectConsumedCounts(extracted));
    }

    private static ExtractedIngredient takeIngredientForCraft(List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, boolean includePlayerFallback) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
        if (!fromLinked.isEmpty()) {
            return new ExtractedIngredient(fromLinked, false);
        }
        if (!includePlayerFallback) {
            return null;
        }
        ItemStack fromPlayer = extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY);
        if (!fromPlayer.isEmpty()) {
            return new ExtractedIngredient(fromPlayer, true);
        }
        return null;
    }

    private static void rollbackCraftIngredients(List<IItemHandler> handlers, ServerPlayer player, ExtractedIngredient[] extracted) {
        for (int i = extracted.length - 1; i >= 0; i--) {
            ExtractedIngredient ingredient = extracted[i];
            if (ingredient == null || ingredient.stack().isEmpty()) {
                continue;
            }
            if (ingredient.fromPlayer()) {
                moveToPlayerInventoryOnly(player, ingredient.stack());
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, ingredient.stack());
            if (!remain.isEmpty()) {
                moveToPlayerInventoryOnly(player, remain);
            }
        }
    }

    private static void rollbackStoredCraftOutputs(List<IItemHandler> handlers, List<ItemStack> storedOutputs) {
        for (int i = storedOutputs.size() - 1; i >= 0; i--) {
            ItemStack stored = storedOutputs.get(i);
            int remaining = stored.getCount();
            while (remaining > 0) {
                ItemStack extracted = extractOneMatchingPrototypeFromLinked(handlers, stored);
                if (extracted.isEmpty()) {
                    break;
                }
                remaining -= extracted.getCount();
            }
        }
    }

    private static boolean canStoreStacksToLinkedOnly(List<IItemHandler> handlers, List<ItemStack> stacks) {
        List<List<ItemStack>> virtual = new ArrayList<>(handlers.size());
        for (IItemHandler handler : handlers) {
            List<ItemStack> slots = new ArrayList<>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                slots.add(handler.getStackInSlot(slot).copy());
            }
            virtual.add(slots);
        }

        for (ItemStack original : stacks) {
            if (original == null || original.isEmpty()) {
                continue;
            }
            ItemStack remain = original.copy();

            for (int h = 0; h < handlers.size() && !remain.isEmpty(); h++) {
                IItemHandler handler = handlers.get(h);
                List<ItemStack> slots = virtual.get(h);
                for (int slot = 0; slot < slots.size() && !remain.isEmpty(); slot++) {
                    ItemStack current = slots.get(slot);
                    if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, remain)) {
                        continue;
                    }
                    int slotLimit = Math.min(handler.getSlotLimit(slot), current.getMaxStackSize());
                    int free = Math.max(0, slotLimit - current.getCount());
                    if (free <= 0) {
                        continue;
                    }
                    int move = Math.min(free, remain.getCount());
                    current.grow(move);
                    remain.shrink(move);
                }
            }

            for (int h = 0; h < handlers.size() && !remain.isEmpty(); h++) {
                IItemHandler handler = handlers.get(h);
                List<ItemStack> slots = virtual.get(h);
                for (int slot = 0; slot < slots.size() && !remain.isEmpty(); slot++) {
                    ItemStack current = slots.get(slot);
                    if (!current.isEmpty()) {
                        continue;
                    }
                    int slotLimit = Math.min(handler.getSlotLimit(slot), remain.getMaxStackSize());
                    if (slotLimit <= 0) {
                        continue;
                    }
                    ItemStack probe = remain.copy();
                    probe.setCount(slotLimit);
                    ItemStack rejected = handler.insertItem(slot, probe, true);
                    int accepted = slotLimit - rejected.getCount();
                    if (accepted <= 0) {
                        continue;
                    }
                    int move = Math.min(accepted, remain.getCount());
                    ItemStack filled = remain.copy();
                    filled.setCount(move);
                    slots.set(slot, filled);
                    remain.shrink(move);
                }
            }

            if (!remain.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void breakPlaced(ServerPlayer player, BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        boolean undoRecovery = allowAdjacentFallback;
        if (!undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !canAccessWorldTarget(player, pos)) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!undoRecovery && !hasAnyStorage(player, session)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.immutable();
        if (!tracker.isPlaced(targetPos)) {
            if (!allowAdjacentFallback) {
                return;
            }
            Direction resolvedFace = face == null ? Direction.UP : face;
            BlockPos adjacent = targetPos.relative(resolvedFace);
            if (!canAccessWorldTarget(player, adjacent) || !tracker.isPlaced(adjacent)) {
                return;
            }
            targetPos = adjacent;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (!undoRecovery && activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            // Never route recovered items into the container being broken.
            if (linked.pos().equals(targetPos)) {
                continue;
            }
            handlers.add(linked.handler());
        }
        boolean hasLinkedRecoveryTarget = !handlers.isEmpty();

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            tracker.clear(targetPos);
            return;
        }

        Set<UUID> dropIdsBeforeBreak = snapshotNearbyDropIds(level, targetPos);
        boolean removed = breakPlacedWithSimulatedSilkTool(player, level, targetPos);
        if (!removed || !level.getBlockState(targetPos).isAir()) {
            return;
        }

        tracker.clear(targetPos);
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        List<ItemEntity> droppedEntities = collectNewNearbyDrops(level, targetPos, dropIdsBeforeBreak);
        for (ItemEntity droppedEntity : droppedEntities) {
            ItemStack droppedStack = droppedEntity.getItem();
            if (droppedStack.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, droppedStack);
            if (remain.isEmpty()) {
                droppedEntity.discard();
                continue;
            }

            overflow = overflow.merge(storeToLinkedWithFallback(handlers, player, remain));
            droppedEntity.discard();
        }
        if (overflow.hasOverflow()) {
            if (hasLinkedRecoveryTarget) {
                sendStorageOverflowHint(player, "Absorb", overflow);
            } else if (overflow.dropped() > 0) {
                player.displayClientMessage(
                        Component.literal("Inventory full, dropped " + overflow.dropped() + "."),
                        true);
            }
        }

        // If a linked storage block itself is broken, unlink it immediately.
        LinkedStorageRef targetRef = new LinkedStorageRef(player.serverLevel().dimension(), targetPos);
        if (session.linkedStorages.remove(targetRef)) {
            session.linkedNames.remove(targetRef);
            session.linkedModes.remove(targetRef);
            saveSessionToPlayerNbt(player, session);
        }

        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        if (!droppedEntities.isEmpty()) {
            runQuestDetect(player, session, false);
        }
    }

    private static boolean breakPlacedWithSimulatedSilkTool(ServerPlayer player, ServerLevel level, BlockPos targetPos) {
        ItemStack simulatedTool = createSimulatedSilkNetheritePick(level);
        return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(player, simulatedTool, () -> player.gameMode.destroyBlock(targetPos)));
    }

    private static ItemStack createSimulatedSilkNetheritePick(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(Enchantments.SILK_TOUCH, 1);
        return tool;
    }

    private static Set<UUID> snapshotNearbyDropIds(ServerLevel level, BlockPos pos) {
        Set<UUID> ids = new HashSet<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            ids.add(entity.getUUID());
        }
        return ids;
    }

    private static List<ItemEntity> collectNewNearbyDrops(ServerLevel level, BlockPos pos, Set<UUID> beforeIds) {
        List<ItemEntity> out = new ArrayList<>();
        Set<UUID> known = beforeIds == null ? Set.of() : beforeIds;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5D))) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            if (known.contains(entity.getUUID())) {
                continue;
            }
            out.add(entity);
        }
        return out;
    }

    public static void pickupLinkedToCarried(ServerPlayer player, ItemStack prototype, int amount) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        boolean includePlayerMainInventory = shouldIncludePlayerMainInventoryInStorageView(player, session);
        if (!hasAnyStorage(player, session) && !includePlayerMainInventory) {
            return;
        }
        if (prototype == null || prototype.isEmpty() || amount <= 0) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty() && !includePlayerMainInventory) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ItemStack carried = player.containerMenu.getCarried();
        int maxStack = prototype.getMaxStackSize();
        int wanted = Math.min(amount, maxStack);
        if (!carried.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(carried, prototype)) {
                return;
            }
            wanted = Math.min(wanted, carried.getMaxStackSize() - carried.getCount());
            if (wanted <= 0) {
                return;
            }
        }

        ItemStack extracted = extractMatchingFromNetwork(handlers, player, prototype.getItem(), prototype, wanted);
        if (extracted.isEmpty()) {
            return;
        }

        if (carried.isEmpty()) {
            player.containerMenu.setCarried(extracted);
        } else {
            carried.grow(extracted.getCount());
            player.containerMenu.setCarried(carried);
        }
        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    public static void quickMoveLinkedItem(ServerPlayer player, ItemStack prototype) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session) || prototype == null || prototype.isEmpty()) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        int maxStack = Math.max(1, prototype.getMaxStackSize());
        ItemStack extracted = extractMatchingFromLinked(handlers, prototype.getItem(), prototype, maxStack);
        if (extracted.isEmpty()) {
            return;
        }

        ItemStack remain;
        if (movesLinkedQuickMoveToPlayerInventory(player.containerMenu)) {
            remain = moveToPlayerInventoryOnly(player, extracted);
        } else {
            remain = moveLinkedStackIntoOpenMenu(player, extracted);
            if (!remain.isEmpty()) {
                remain = moveToPlayerInventoryOnly(player, remain);
            }
        }

        if (!remain.isEmpty()) {
            refundToLinked(handlers, player, remain);
        }

        player.containerMenu.broadcastChanges();
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        runQuestDetect(player, session, false);
    }

    public static void fillPlayerInventoryFromLinked(ServerPlayer player) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.STORAGE_BROWSER)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);
        if (!hasAnyStorage(player, session)) {
            return;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return;
        }
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        int movedCount = 0;
        boolean inventoryFull = false;
        outer: for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                while (true) {
                    ItemStack preview = handler.getStackInSlot(slot);
                    if (preview.isEmpty()) {
                        break;
                    }

                    int requestAmount = Math.max(1, preview.getMaxStackSize());
                    ItemStack extracted = handler.extractItem(slot, requestAmount, false);
                    if (extracted.isEmpty()) {
                        break;
                    }

                    int extractedCount = extracted.getCount();
                    ItemStack remain = moveToPlayerInventoryOnly(player, extracted);
                    movedCount += Math.max(0, extractedCount - remain.getCount());
                    if (!remain.isEmpty()) {
                        refundToLinked(handlers, player, remain);
                        inventoryFull = true;
                        break outer;
                    }
                }
            }
        }

        if (movedCount > 0) {
            player.containerMenu.broadcastChanges();
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            player.displayClientMessage(
                    Component.literal(inventoryFull
                            ? "Moved " + movedCount + " items to inventory. Inventory is full."
                            : "Moved " + movedCount + " items to inventory."),
                    true);
        } else if (inventoryFull) {
            player.displayClientMessage(Component.literal("Inventory is full."), true);
        }
    }

    public static void mine(ServerPlayer player, BlockPos pos, Direction face, boolean start, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery) {
        if (start && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);

        if (start) {
            if (!canAccessWorldTarget(player, pos)) {
                stopActiveMining(player, session);
                return;
            }

            if (allowPlacedBlockRecovery
                    && PlacedBlockTrackerData.get(player.serverLevel()).isPlaced(pos)
                    && hasAnyStorage(player, session)) {
                BlockState before = player.serverLevel().getBlockState(pos);
                breakPlaced(player, pos, face, false);
                BlockState after = player.serverLevel().getBlockState(pos);
                if (!before.equals(after)) {
                    stopActiveMining(player, session);
                    return;
                }
            }
            stopActiveMining(player, session);
            if (player.isCreative()) {
                destroyMinedBlock(player, session, pos, slot);
                requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
            session.miningToolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
            beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        stopActiveMining(player, session);
    }

    public static void startUltimine(ServerPlayer player, BlockPos pos, Direction face, byte toolSlot, String toolItemId,
            ItemStack toolPrototype, int requestedLimit) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return;
        }
        int limit = Math.max(1, Math.min(Math.min(ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, limit, true);
            if (targets.isEmpty()) {
                stopActiveMining(player, session);
                return;
            }
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        stopActiveMining(player, session);
        ToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, toolLease.stack(), limit, false);
        if (targets.isEmpty()) {
            returnMiningTool(player, session, toolLease);
            return;
        }

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = slot;
        beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool, int limit) {
        return collectUltimineTargets(player, seed, toolSlot, linkedTool, limit, player != null && player.isCreative());
    }

    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool, int limit, boolean creative) {
        if (!canAccessWorldTarget(player, seed)) {
            return new ArrayDeque<>();
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> targets = RtsUltimineCollector.collect(
                level,
                seed,
                limit,
                (candidatePos, state, seedState) -> isUltimineCandidate(
                        player,
                        candidatePos,
                        state,
                        seedState,
                        toolSlot,
                        linkedTool,
                        creative));
        return new ArrayDeque<>(targets);
    }

    private static boolean isUltimineCandidate(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            BlockState seedState,
            int toolSlot,
            ItemStack linkedTool,
            boolean creative) {
        if (state.isAir() || state.getBlock() != seedState.getBlock()) {
            return false;
        }
        if (!canAccessWorldTarget(player, pos)) {
            return false;
        }
        if (creative) {
            return true;
        }
        if (state.getDestroySpeed(player.serverLevel(), pos) < 0.0F) {
            return false;
        }
        return computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool) > 0.0F;
    }

    private static void breakCreativeUltimineTargets(ServerPlayer player, Session session, Deque<BlockPos> targets, int toolSlot) {
        while (!targets.isEmpty()) {
            BlockPos target = targets.removeFirst();
            if (!canAccessWorldTarget(player, target)) {
                continue;
            }
            destroyMinedBlock(player, session, target, toolSlot);
        }
    }

    private static void beginRemoteMining(ServerPlayer player, Session session, BlockPos pos, Direction face, int toolSlot) {
        if (session.miningPos != null && !session.miningPos.equals(pos)) {
            player.serverLevel().destroyBlockProgress(player.getId(), session.miningPos, -1);
            sendMineProgress(player, session.miningPos, -1);
        }
        session.miningPos = pos.immutable();
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = clampHotbarSlot(toolSlot);
        session.miningProgress = 0.0F;
        session.miningStage = -1;
    }

    private static void tickActiveMining(ServerPlayer player, Session session) {
        if (session.miningPos == null) {
            if (!session.ultimineTargets.isEmpty()) {
                processUltimineTargets(player, session);
            }
            return;
        }
        if (!canAccessWorldTarget(player, session.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.miningPos;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.miningToolSlot, session.miningToolLease.stack());
        if (step <= 0.0F) {
            return;
        }

        session.miningProgress += step;
        int stage = Math.min(9, (int) (session.miningProgress * 10.0F));
        if (stage != session.miningStage) {
            level.destroyBlockProgress(player.getId(), pos, stage);
            sendMineProgress(player, pos, stage);
            session.miningStage = stage;
        }

        if (session.miningProgress < 1.0F) {
            return;
        }

        boolean broken = destroyMinedBlock(player, session, pos, session.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (broken && !session.ultimineTargets.isEmpty()) {
            removeUltimineTarget(session, pos);
            session.ultimineProcessedTargets = Math.max(session.ultimineProcessedTargets, 1);
            if (session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                session.ultimineAbsorbedDrops |= absorbNearbyDropsIntoLinked(player, pos, session);
            }
            session.miningPos = null;
            session.miningProgress = 0.0F;
            session.miningStage = -1;
            processUltimineTargets(player, session);
            return;
        }

        sendMineProgress(player, pos, -1);
        if (broken && session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
            boolean absorbed = absorbNearbyDropsIntoLinked(player, pos, session);
            if (absorbed) {
                runQuestDetect(player, session, false);
            }
        }
        returnMiningTool(player, session, session.miningToolLease);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        resetMiningState(session);
    }

    private static void processUltimineTargets(ServerPlayer player, Session session) {
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        while (processedThisTick < ULTIMINE_BLOCKS_PER_TICK && !session.ultimineTargets.isEmpty()) {
            BlockPos target = session.ultimineTargets.removeFirst();
            processedThisTick++;
            session.ultimineProcessedTargets++;

            if (!canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir() || targetState.getDestroySpeed(level, target) < 0.0F) {
                continue;
            }
            if (computeRemoteDestroyStep(player, targetState, target, session.miningToolSlot, session.miningToolLease.stack()) <= 0.0F) {
                continue;
            }
            boolean targetBroken = destroyMinedBlock(player, session, target, session.miningToolSlot);
            if (targetBroken && session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                session.ultimineAbsorbedDrops |= absorbNearbyDropsIntoLinked(player, target, session);
            }
        }

        sendUltimineBatchProgress(player, session);
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    private static void sendUltimineBatchProgress(ServerPlayer player, Session session) {
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos == null) {
            return;
        }
        int total = Math.max(1, session.ultimineTotalTargets);
        int stage = Math.min(9, (int) (session.ultimineProcessedTargets / (double) total * 10.0D));
        sendMineProgress(player, progressPos, stage);
    }

    private static void finishUltimineBatch(ServerPlayer player, Session session) {
        if (session.ultimineAbsorbedDrops) {
            runQuestDetect(player, session, false);
        }
        returnMiningTool(player, session, session.miningToolLease);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            sendMineProgress(player, progressPos, -1);
        }
        resetMiningState(session);
    }

    private static void removeUltimineTarget(Session session, BlockPos pos) {
        session.ultimineTargets.removeIf(target -> target.equals(pos));
    }

    private static void stopActiveMining(ServerPlayer player, Session session) {
        BlockPos progressPos = session.miningPos != null ? session.miningPos : session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            sendMineProgress(player, progressPos, -1);
        }
        returnMiningTool(player, session, session.miningToolLease);
        requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
        resetMiningState(session);
    }

    private static void resetMiningState(Session session) {
        session.miningPos = null;
        session.ultimineTargets.clear();
        session.ultimineProgressPos = null;
        session.ultimineTotalTargets = 0;
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningProgress = 0.0F;
        session.miningStage = -1;
        session.miningToolLease = ToolLease.empty();
    }

    private static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot, ItemStack linkedTool) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(
                    player,
                    linkedTool,
                    () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
        }
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
    }

    private static boolean destroyMinedBlock(ServerPlayer player, Session session, BlockPos pos, int toolSlot) {
        if (session != null && session.miningToolLease != null && !session.miningToolLease.isEmpty()) {
            ToolLease lease = session.miningToolLease;
            MiningDestroyOutcome outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            session.miningToolLease = lease.withStack(protectBorrowedToolRemainder(player, lease, outcome.remainder()));
            return outcome.broken();
        }
        return withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
    }

    private static ToolLease borrowMiningTool(ServerPlayer player, Session session, String toolItemId,
            ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || toolPrototype.isEmpty()
                || toolItemId == null || toolItemId.isBlank()) {
            return ToolLease.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(toolItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ToolLease.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item instanceof BlockItem || toolPrototype.getItem() != item) {
            return ToolLease.empty();
        }

        ToolLease playerLease = borrowMiningToolFromPlayerInventory(player, toolPrototype, selectedToolSlot);
        if (!playerLease.isEmpty()) {
            return playerLease;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return ToolLease.empty();
        }
        for (LinkedHandler linked : activeLinked) {
            ToolLease linkedLease = borrowMiningToolFromLinkedHandler(linked.handler(), toolPrototype);
            if (!linkedLease.isEmpty()) {
                return linkedLease;
            }
        }
        return ToolLease.empty();
    }

    private static ToolLease borrowMiningToolFromPlayerInventory(ServerPlayer player, ItemStack prototype, int selectedToolSlot) {
        int selected = clampHotbarSlot(selectedToolSlot);
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT; slot++) {
            if (slot == selected) {
                continue;
            }
            ToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        return ToolLease.empty();
    }

    private static ToolLease borrowMiningToolFromPlayerSlot(ServerPlayer player, ItemStack prototype, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ToolLease.empty();
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, prototype)) {
            return ToolLease.empty();
        }
        ItemStack borrowed = current.split(1);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        player.getInventory().setChanged();
        return borrowed.isEmpty() ? ToolLease.empty() : ToolLease.playerSlot(slot, borrowed);
    }

    private static ToolLease borrowMiningToolFromLinkedHandler(IItemHandler handler, ItemStack prototype) {
        if (handler == null || prototype == null || prototype.isEmpty()) {
            return ToolLease.empty();
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, prototype)) {
                continue;
            }
            ItemStack borrowed = handler.extractItem(slot, 1, false);
            if (!borrowed.isEmpty() && ItemStack.isSameItemSameTags(borrowed, prototype)) {
                return ToolLease.linkedSlot(handler, slot, borrowed);
            }
            if (!borrowed.isEmpty()) {
                insertToHandlerPreferExisting(handler, borrowed);
            }
        }
        return ToolLease.empty();
    }

    private static void returnMiningTool(ServerPlayer player, Session session, ToolLease lease) {
        if (player == null || session == null || lease == null || lease.isEmpty()) {
            return;
        }
        ItemStack remain = lease.returnToSource(player);
        if (remain.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }
        storeToLinkedWithFallback(handlers, player, remain);
    }

    private static ItemStack protectBorrowedToolRemainder(ServerPlayer player, ToolLease lease, ItemStack remainder) {
        if (remainder != null && !remainder.isEmpty()) {
            return remainder;
        }
        ItemStack original = lease.original();
        if (!shouldProtectEmptyBorrowedToolRemainder(original)) {
            return ItemStack.EMPTY;
        }
        RtsbuildingMod.LOGGER.warn(
                "RTS borrowed mining tool from {} became empty after block break; restoring original stack as a safety fallback for {}.",
                lease.describeSource(),
                player == null ? "unknown player" : player.getGameProfile().getName());
        return original.copy();
    }

    private static boolean shouldProtectEmptyBorrowedToolRemainder(ItemStack original) {
        return original != null
                && !original.isEmpty()
                && !(original.getItem() instanceof BlockItem)
                && original.getMaxStackSize() == 1
                && !original.isDamageableItem();
    }

    private static MiningDestroyOutcome destroyBlockWithTemporaryMainHand(ServerPlayer player, BlockPos pos, ItemStack tool) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        boolean broken;
        ItemStack remainder;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningDestroyOutcome(broken, remainder);
    }

    private static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        return destroyStep;
    }

    private static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    private static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    private static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    private static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        PacketDistributor.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static boolean isValidQuickSlotIndex(int slot) {
        return slot >= 0 && slot < QUICK_SLOT_COUNT;
    }

    private static boolean isValidGuiBindingSlot(int slot) {
        return slot >= 0 && slot < GUI_BINDING_SLOT_COUNT;
    }

    private static boolean canBindGuiTarget(ServerLevel level, BlockPos pos) {
        if (resolveBindableMenuProvider(level, pos) != null) {
            return true;
        }
        return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) != null;
    }

    private static MenuProvider resolveBindableMenuProvider(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider != null) {
            return provider;
        }
        return level.getBlockEntity(pos) instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    private static String resolveGuiBindingIconItemId(ServerLevel level, BlockPos pos, Direction face, String itemIdHint, String label) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return "";
        }
        ResourceLocation hintKey = ResourceLocation.tryParse(itemIdHint);
        if (hintKey != null && BuiltInRegistries.ITEM.containsKey(hintKey)) {
            return hintKey.toString();
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "";
        }
        ItemStack cloneStack = state.getBlock().getCloneItemStack(level, pos, state);
        Item item = cloneStack.isEmpty() ? state.getBlock().asItem() : cloneStack.getItem();
        if (item == null || item == Items.AIR) {
            return RtsAe2Compat.resolveGuiBindingIconItemId(level, pos, face, label);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            return id.toString();
        }
        return RtsAe2Compat.resolveGuiBindingIconItemId(level, pos, face, label);
    }

    private static void refreshMissingGuiBindingIcons(ServerPlayer player, Session session) {
        if (player == null || session == null || player.server == null) {
            return;
        }

        boolean changed = false;
        for (int i = 0; i < session.guiBindings.length; i++) {
            GuiBinding binding = session.guiBindings[i];
            if (binding == null || binding.pos() == null || binding.dimension() == null) {
                continue;
            }
            if (binding.itemId() != null && !binding.itemId().isBlank()) {
                continue;
            }

            ServerLevel bindingLevel = player.server.getLevel(binding.dimension());
            if (bindingLevel == null || !bindingLevel.hasChunkAt(binding.pos())) {
                continue;
            }

            String resolvedItemId = resolveGuiBindingIconItemId(
                    bindingLevel,
                    binding.pos(),
                    binding.face(),
                    "",
                    binding.label());
            if (resolvedItemId.isBlank()) {
                continue;
            }

            session.guiBindings[i] = new GuiBinding(
                    binding.pos(),
                    binding.dimension(),
                    binding.label(),
                    resolvedItemId,
                    binding.face());
            changed = true;
        }

        if (changed) {
            saveSessionToPlayerNbt(player, session);
        }
    }

    private static boolean storeFluidFromLinkedItem(ServerPlayer player, Session session, List<IItemHandler> itemHandlers,
            List<LinkedFluidHandler> fluidHandlers, String itemId) {
        if (itemId == null || itemId.isBlank() || itemHandlers.isEmpty()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack extracted = extractOneFromNetwork(itemHandlers, player, item);
        if (extracted.isEmpty()) {
            return false;
        }

        ContainerDrainOutcome simulated = drainContainer(extracted, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(extracted, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            refundToLinked(itemHandlers, player, extracted);
            return false;
        }

        if (!executed.remainder().isEmpty()) {
            refundToLinked(itemHandlers, player, executed.remainder());
        }
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.getFluid());
        if (fluidId != null) {
            recordRecentFluid(session, fluidId.toString(), S2CRtsStoragePagePayload.RECENT_FLUID_USED, inserted, FLUID_TRANSFER_MB);
        }
        return true;
    }

    private static boolean storeFluidFromToolSlot(ServerPlayer player, Session session, List<LinkedFluidHandler> fluidHandlers,
            int toolSlot) {
        int slot = clampHotbarSlot(toolSlot);
        ItemStack inSlot = player.getInventory().getItem(slot);
        if (inSlot.isEmpty()) {
            return false;
        }

        ItemStack single = inSlot.copyWithCount(1);
        ContainerDrainOutcome simulated = drainContainer(single, FLUID_TRANSFER_MB, false);
        if (simulated.isEmpty() || simulated.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack targetFluid = simulated.fluid().copy();
        targetFluid.setAmount(FLUID_TRANSFER_MB);
        if (insertFluidIntoNetwork(player, session, fluidHandlers, targetFluid, false) < FLUID_TRANSFER_MB) {
            return false;
        }

        ContainerDrainOutcome executed = drainContainer(single, FLUID_TRANSFER_MB, true);
        if (executed.isEmpty() || executed.fluid().getAmount() < FLUID_TRANSFER_MB) {
            return false;
        }
        FluidStack insertFluid = executed.fluid().copy();
        insertFluid.setAmount(FLUID_TRANSFER_MB);
        int inserted = insertFluidIntoNetwork(player, session, fluidHandlers, insertFluid, true);
        if (inserted < FLUID_TRANSFER_MB) {
            return false;
        }

        ItemStack remainingInSlot = inSlot.copy();
        remainingInSlot.shrink(1);
        if (remainingInSlot.isEmpty()) {
            player.getInventory().setItem(slot, executed.remainder());
        } else {
            player.getInventory().setItem(slot, remainingInSlot);
            pushToPlayerInventoryOrDrop(player, executed.remainder());
        }
        player.containerMenu.broadcastChanges();
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(insertFluid.getFluid());
        if (fluidId != null) {
            recordRecentFluid(session, fluidId.toString(), S2CRtsStoragePagePayload.RECENT_FLUID_USED, inserted, FLUID_TRANSFER_MB);
        }
        return true;
    }

    private static void pushToPlayerInventoryOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = stack.copy();
        player.getInventory().add(remainder);
        if (!remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }

    private static ContainerDrainOutcome drainContainer(ItemStack container, int amount, boolean execute) {
        if (container.isEmpty() || amount <= 0) {
            return ContainerDrainOutcome.EMPTY;
        }
        ItemStack single = container.copyWithCount(1);
        Optional<IFluidHandlerItem> optHandler = FluidUtil.getFluidHandler(single).resolve();
        if (optHandler.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }

        IFluidHandlerItem handler = optHandler.get();
        FluidStack simulated = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        if (!execute) {
            return new ContainerDrainOutcome(simulated.copy(), handler.getContainer().copy());
        }

        FluidStack drained = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty()) {
            return ContainerDrainOutcome.EMPTY;
        }
        return new ContainerDrainOutcome(drained.copy(), handler.getContainer().copy());
    }

    private static int insertFluidIntoNetwork(ServerPlayer player, Session session, List<LinkedFluidHandler> fluidHandlers, FluidStack fluidStack,
            boolean execute) {
        if (fluidStack.isEmpty() || fluidStack.getAmount() <= 0) {
            return 0;
        }
        int remaining = fluidStack.getAmount();

        for (LinkedFluidHandler linked : fluidHandlers) {
            if (remaining <= 0) {
                break;
            }
            FluidStack candidate = fluidStack.copy();
            candidate.setAmount(remaining);
            int filled = linked.handler().fill(candidate,
                    execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE);
            if (filled > 0) {
                remaining -= filled;
            }
        }

        if (remaining <= 0) {
            return fluidStack.getAmount();
        }

        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid());
        if (id == null) {
            return fluidStack.getAmount() - remaining;
        }
        String fluidId = id.toString();
        long stored = session.internalFluidMb.getOrDefault(fluidId, 0L);
        long space = Math.max(0L, internalFluidCapacityMb(player) - stored);
        int toInternal = (int) Math.min((long) remaining, space);
        if (toInternal > 0) {
            if (execute) {
                session.internalFluidMb.put(fluidId, stored + toInternal);
            }
            remaining -= toInternal;
        }
        return fluidStack.getAmount() - remaining;
    }

    private static int extractFluidFromNetwork(Session session, List<LinkedFluidHandler> fluidHandlers, Fluid fluid, int amount,
            boolean execute) {
        if (fluid == null || amount <= 0) {
            return 0;
        }

        int remaining = amount;
        for (LinkedFluidHandler linked : fluidHandlers) {
            if (remaining <= 0) {
                break;
            }
            FluidStack drained = drainMatchingFluid(linked.handler(), fluid, remaining, execute);
            if (!drained.isEmpty()) {
                remaining -= drained.getAmount();
            }
        }

        if (remaining > 0) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id != null) {
                String fluidId = id.toString();
                long internal = session.internalFluidMb.getOrDefault(fluidId, 0L);
                int drainedInternal = (int) Math.min((long) remaining, Math.max(0L, internal));
                if (drainedInternal > 0) {
                    if (execute) {
                        long left = internal - drainedInternal;
                        if (left > 0L) {
                            session.internalFluidMb.put(fluidId, left);
                        } else {
                            session.internalFluidMb.remove(fluidId);
                        }
                    }
                    remaining -= drainedInternal;
                }
            }
        }

        return amount - remaining;
    }

    private static FluidStack drainMatchingFluid(IFluidHandler handler, Fluid fluid, int amount, boolean execute) {
        if (handler == null || fluid == null || amount <= 0) {
            return FluidStack.EMPTY;
        }
        IFluidHandler.FluidAction action = execute ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
        FluidStack request = new FluidStack(fluid, amount);
        FluidStack exact = handler.drain(request, action);
        if (!exact.isEmpty()) {
            return exact;
        }

        FluidStack genericPreview = handler.drain(amount, IFluidHandler.FluidAction.SIMULATE);
        if (genericPreview.isEmpty() || genericPreview.getFluid() != fluid) {
            return FluidStack.EMPTY;
        }
        if (!execute) {
            return genericPreview;
        }
        FluidStack generic = handler.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return !generic.isEmpty() && generic.getFluid() == fluid ? generic : FluidStack.EMPTY;
    }

    private static int fillFluidHandlerAtTarget(ServerLevel level, BlockPos clickedPos, Direction face, FluidStack fluidStack) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(clickedPos)) {
            return 0;
        }
        List<IFluidHandler> candidates = new ArrayList<>();
        addFluidHandlerCandidate(level, clickedPos, face, candidates);
        addFluidHandlerCandidate(level, clickedPos, null, candidates);
        for (Direction direction : Direction.values()) {
            addFluidHandlerCandidate(level, clickedPos, direction, candidates);
        }

        BlockPos adjacent = clickedPos.relative(face);
        if (level.hasChunkAt(adjacent)) {
            addFluidHandlerCandidate(level, adjacent, face.getOpposite(), candidates);
            addFluidHandlerCandidate(level, adjacent, null, candidates);
            for (Direction direction : Direction.values()) {
                addFluidHandlerCandidate(level, adjacent, direction, candidates);
            }
        }

        for (IFluidHandler handler : candidates) {
            FluidStack candidate = fluidStack.copy();
            int simulated = handler.fill(candidate, IFluidHandler.FluidAction.SIMULATE);
            if (simulated <= 0) {
                continue;
            }
            candidate.setAmount(simulated);
            return handler.fill(candidate, IFluidHandler.FluidAction.EXECUTE);
        }
        return 0;
    }

    private static void addFluidHandlerCandidate(ServerLevel level, BlockPos pos, Direction side, List<IFluidHandler> out) {
        IFluidHandler handler = findFluidHandler(level, pos, side);
        if (handler != null && !out.contains(handler)) {
            out.add(handler);
        }
    }

    private static BlockPos resolveFluidPlacementPos(ServerLevel level, ServerPlayer player, BlockHitResult hit,
            FluidStack fluidStack) {
        BlockPos clicked = hit.getBlockPos();
        if (canPlaceFluidAt(level, player, clicked, fluidStack, resolveFluidPlacementHit(hit, clicked))) {
            return clicked;
        }

        BlockPos adjacent = clicked.relative(hit.getDirection());
        if (level.hasChunkAt(adjacent)
                && canPlaceFluidAt(level, player, adjacent, fluidStack, resolveFluidPlacementHit(hit, adjacent))) {
            return adjacent;
        }
        return null;
    }

    private static boolean placeFluidBlock(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack,
            BlockHitResult placementHit) {
        if (!canPlaceFluidAt(level, player, pos, fluidStack, placementHit)) {
            return false;
        }

        Fluid fluid = fluidStack.getFluid();
        BlockState state = level.getBlockState(pos);
        if (fluid.getFluidType().isVaporizedOnPlacement(level, pos, fluidStack)) {
            fluid.getFluidType().onVaporize(player, level, pos, fluidStack);
            return true;
        }

        if (state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(level, pos, state, fluid)) {
            return liquidContainer.placeLiquid(level, pos, state, fluid.defaultFluidState());
        }

        BlockState placeState = fluid.getFluidType().getBlockForFluidState(
                level,
                pos,
                fluid.getFluidType().getStateForPlacement(level, pos, fluidStack));
        if (placeState.isAir()) {
            return false;
        }

        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        if ((isDestNonSolid || isDestReplaceable) && !state.liquid()) {
            level.destroyBlock(pos, true);
        }
        return level.setBlock(pos, placeState, 11);
    }

    private static boolean canPlaceFluidAt(ServerLevel level, ServerPlayer player, BlockPos pos, FluidStack fluidStack,
            BlockHitResult placementHit) {
        if (fluidStack.isEmpty() || !level.hasChunkAt(pos)) {
            return false;
        }
        Fluid fluid = fluidStack.getFluid();
        if (!fluid.getFluidType().canBePlacedInLevel(level, pos, fluidStack)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        BlockPlaceContext context = new BlockPlaceContext(
                level,
                player,
                InteractionHand.MAIN_HAND,
                ItemStack.EMPTY,
                placementHit == null ? new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false) : placementHit);
        boolean canContain = state.getBlock() instanceof LiquidBlockContainer liquidContainer
                && liquidContainer.canPlaceLiquid(level, pos, state, fluid);
        boolean isDestNonSolid = !state.isSolid();
        boolean isDestReplaceable = state.canBeReplaced(context);
        return level.isEmptyBlock(pos) || isDestNonSolid || isDestReplaceable || canContain;
    }

    private static BlockHitResult resolveFluidPlacementHit(BlockHitResult sourceHit, BlockPos targetPos) {
        if (targetPos == null) {
            return new BlockHitResult(Vec3.atCenterOf(BlockPos.ZERO), Direction.UP, BlockPos.ZERO, false);
        }
        if (sourceHit == null) {
            return new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);
        }

        BlockPos clicked = sourceHit.getBlockPos();
        Direction face = sourceHit.getDirection();
        if (targetPos.equals(clicked)) {
            return new BlockHitResult(sourceHit.getLocation(), face, targetPos, false);
        }

        if (targetPos.equals(clicked.relative(face))) {
            Direction targetFace = face.getOpposite();
            Vec3 targetLocation = Vec3.atCenterOf(targetPos).add(
                    targetFace.getStepX() * 0.498D,
                    targetFace.getStepY() * 0.498D,
                    targetFace.getStepZ() * 0.498D);
            return new BlockHitResult(targetLocation, targetFace, targetPos, false);
        }

        return new BlockHitResult(Vec3.atCenterOf(targetPos), face, targetPos, false);
    }

    private static int getPlayerMainInventoryStart(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return 0;
    }

    private static int getPlayerMainInventoryEndExclusive(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return Math.min(PLAYER_MAIN_INVENTORY_END_EXCLUSIVE, player.getInventory().getContainerSize());
    }

    private static void accumulatePlayerMainInventoryCounts(ServerPlayer player, Map<String, Long> counts,
            Map<String, Long> namespaceTotals) {
        if (player == null || counts == null || namespaceTotals == null) {
            return;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            mergeCount(counts, id.toString(), stack.getCount());
            mergeCount(namespaceTotals, id.getNamespace(), stack.getCount());
        }
    }

    private static void accumulatePlayerMainInventoryEntries(ServerPlayer player, List<Entry> exactEntries) {
        if (player == null || exactEntries == null) {
            return;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            mergeExactEntry(exactEntries, stack, stack.getCount());
        }
    }

    private static void mergeExactEntry(List<Entry> entries, ItemStack stack, long count) {
        if (entries == null || stack == null || stack.isEmpty() || count <= 0L) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        ItemStack prototype = stack.copy();
        prototype.setCount(1);
        for (int i = 0; i < entries.size(); i++) {
            Entry existing = entries.get(i);
            if (!ItemStack.isSameItemSameTags(existing.stack(), prototype)) {
                continue;
            }
            entries.set(i, new Entry(
                    existing.stack(),
                    existing.itemId(),
                    existing.namespace(),
                    existing.path(),
                    existing.label(),
                    saturatedAdd(existing.count(), count)));
            return;
        }
        entries.add(new Entry(
                prototype,
                id.toString(),
                id.getNamespace(),
                id.getPath(),
                prototype.getHoverName().getString(),
                count));
    }

    private static ItemStack extractOne(IItemHandler handler, Item targetItem) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractMatching(IItemHandler handler, Item targetItem, int limit) {
        return extractMatching(handler, targetItem, ItemStack.EMPTY, limit);
    }

    private static ItemStack extractMatching(IItemHandler handler, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameTags(stack, expected)) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, remaining, false);
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(extracted, preferred)) {
                    ItemStack remain = insertToHandlerPreferExisting(handler, extracted);
                    if (!remain.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameTags(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                ItemStack remain = insertToHandlerPreferExisting(handler, extracted);
                if (!remain.isEmpty()) {
                    return out;
                }
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    private static ItemStack extractOneFromLinked(List<IItemHandler> handlers, Item targetItem) {
        for (IItemHandler handler : handlers) {
            ItemStack extracted = extractOne(handler, targetItem);
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneFromPlayerMainInventory(ServerPlayer player, Item targetItem) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem) {
        ItemStack extracted = extractOneFromLinked(handlers, targetItem);
        if (!extracted.isEmpty()) {
            return extracted;
        }
        return extractOneFromPlayerMainInventory(player, targetItem);
    }

    private static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient) {
        return extractOneMatchingIngredient(handlers, ingredient, ItemStack.EMPTY);
    }

    private static ItemStack extractOneMatchingIngredient(List<IItemHandler> handlers, Ingredient ingredient, ItemStack preferred) {
        if (ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromHandlers(handlers, ingredient, preferred);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromHandlers(handlers, ingredient, ItemStack.EMPTY);
    }

    private static ItemStack extractOneMatchingIngredientFromHandlers(List<IItemHandler> handlers, Ingredient ingredient,
            ItemStack preferred) {
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(stack, preferred)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (extracted.isEmpty()) {
                    continue;
                }
                if (ingredient.test(extracted)) {
                    return extracted;
                }
                ItemStack remainder = insertToHandlerPreferExisting(handler, extracted);
                if (!remainder.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneMatchingIngredientCombined(List<IItemHandler> handlers, ServerPlayer player,
            Ingredient ingredient, ItemStack preferred) {
        ItemStack fromLinked = extractOneMatchingIngredient(handlers, ingredient, preferred);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, preferred);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(ServerPlayer player, Ingredient ingredient, ItemStack preferred) {
        if (player == null || ingredient == null || ingredient.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && ingredient.test(preferred)) {
            ItemStack preferredMatch = extractOneMatchingIngredientFromPlayer(player, ingredient, preferred, true);
            if (!preferredMatch.isEmpty()) {
                return preferredMatch;
            }
        }
        return extractOneMatchingIngredientFromPlayer(player, ingredient, ItemStack.EMPTY, false);
    }

    private static ItemStack extractOneMatchingIngredientFromPlayer(ServerPlayer player, Ingredient ingredient, ItemStack preferred,
            boolean requirePreferredMatch) {
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ingredient.test(current)) {
                continue;
            }
            if (requirePreferredMatch && !ItemStack.isSameItemSameTags(current, preferred)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty() && ingredient.test(extracted)) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void rollbackGridInserts(CraftingMenu craftingMenu, List<IItemHandler> handlers, ServerPlayer player,
            List<GridInsert> insertedThisPass) {
        for (int i = insertedThisPass.size() - 1; i >= 0; i--) {
            GridInsert insert = insertedThisPass.get(i);
            Slot grid = craftingMenu.getSlot(1 + insert.slotIndex());
            ItemStack current = grid.getItem();
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, insert.stack()) && current.getCount() > 0) {
                current.shrink(1);
                if (current.isEmpty()) {
                    grid.set(ItemStack.EMPTY);
                } else {
                    grid.set(current);
                }
                grid.setChanged();
            }
            storeToLinkedWithFallbackPreferExisting(handlers, player, insert.stack());
        }
    }

    private static Ingredient[] mapCraftingIngredients(CraftingRecipe recipe) {
        Ingredient[] mapped = new Ingredient[9];
        for (int i = 0; i < mapped.length; i++) {
            mapped[i] = Ingredient.EMPTY;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = Math.max(1, Math.min(3, shaped.getWidth()));
            int height = Math.max(1, Math.min(3, shaped.getHeight()));
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int src = y * width + x;
                    if (src < 0 || src >= ingredients.size()) {
                        continue;
                    }
                    mapped[y * 3 + x] = ingredients.get(src);
                }
            }
        } else {
            int count = Math.min(9, ingredients.size());
            for (int i = 0; i < count; i++) {
                mapped[i] = ingredients.get(i);
            }
        }
        return mapped;
    }

    private static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, int limit) {
        return extractMatchingFromLinked(handlers, targetItem, ItemStack.EMPTY, limit);
    }

    private static ItemStack extractMatchingFromLinked(List<IItemHandler> handlers, Item targetItem, ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        ItemStack out = ItemStack.EMPTY;
        for (IItemHandler handler : handlers) {
            if (remaining <= 0) {
                break;
            }
            ItemStack part = extractMatching(handler, targetItem, out.isEmpty() ? preferred : out, remaining);
            if (part.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameTags(out, part)) {
                out.grow(part.getCount());
            }
            remaining -= part.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerMainInventory(player, targetItem, ItemStack.EMPTY, limit);
    }

    private static ItemStack extractMatchingFromPlayerMainInventory(ServerPlayer player, Item targetItem, ItemStack preferred,
            int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end && remaining > 0; slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty() || current.getItem() != targetItem) {
                continue;
            }
            ItemStack expected = out.isEmpty() ? preferred : out;
            if (!expected.isEmpty() && !ItemStack.isSameItemSameTags(current, expected)) {
                continue;
            }
            int take = Math.min(remaining, current.getCount());
            ItemStack extracted = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(slot, current);
            }
            if (extracted.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(extracted, preferred)) {
                    player.getInventory().add(extracted);
                    continue;
                }
                out = extracted;
            } else if (ItemStack.isSameItemSameTags(out, extracted)) {
                out.grow(extracted.getCount());
            } else {
                player.getInventory().add(extracted);
                continue;
            }
            remaining -= extracted.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, int limit) {
        return extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, ItemStack.EMPTY, limit);
    }

    private static ItemStack extractMatchingFromPlayerHotbarForQuickDrop(ServerPlayer player, Item targetItem, ItemStack preferred,
            int limit) {
        if (player == null || targetItem == null) {
            return ItemStack.EMPTY;
        }
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = ItemStack.EMPTY;
        int selected = clampHotbarSlot(player.getInventory().selected);
        ItemStack selectedPart = extractMatchingFromPlayerSlot(player, targetItem, preferred, selected, remaining);
        out = mergeExtractedStacks(out, selectedPart);
        remaining -= selectedPart.getCount();

        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT && remaining > 0; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack part = extractMatchingFromPlayerSlot(player, targetItem, out.isEmpty() ? preferred : out, slot, remaining);
            out = mergeExtractedStacks(out, part);
            remaining -= part.getCount();
        }
        return out;
    }

    private static ItemStack extractMatchingFromPlayerSlot(ServerPlayer player, Item targetItem, ItemStack preferred, int slot,
            int limit) {
        if (player == null || targetItem == null || slot < 0 || limit <= 0) {
            return ItemStack.EMPTY;
        }
        if (slot >= player.getInventory().getContainerSize()) {
            return ItemStack.EMPTY;
        }

        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || current.getItem() != targetItem) {
            return ItemStack.EMPTY;
        }
        if (!preferred.isEmpty() && !ItemStack.isSameItemSameTags(current, preferred)) {
            return ItemStack.EMPTY;
        }
        int take = Math.min(limit, current.getCount());
        ItemStack extracted = current.split(take);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        return extracted.isEmpty() ? ItemStack.EMPTY : extracted;
    }

    private static ItemStack mergeExtractedStacks(ItemStack into, ItemStack addition) {
        if (addition == null || addition.isEmpty()) {
            return into;
        }
        if (into == null || into.isEmpty()) {
            return addition;
        }
        if (ItemStack.isSameItemSameTags(into, addition)) {
            into.grow(addition.getCount());
        }
        return into;
    }

    private static boolean movesLinkedQuickMoveToPlayerInventory(AbstractContainerMenu menu) {
        return menu instanceof InventoryMenu || (menu instanceof CraftingMenu && !(menu instanceof RtsCraftTerminalMenu));
    }

    private static ItemStack moveLinkedStackIntoOpenMenu(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return stack.copy();
        }

        ItemStack remain = stack.copy();
        for (int pass = 0; pass < 2 && !remain.isEmpty(); pass++) {
            boolean fillExisting = pass == 0;
            for (Slot slot : menu.slots) {
                if (slot == null || slot.container == player.getInventory() || !slot.isActive() || !slot.mayPlace(remain)) {
                    continue;
                }

                ItemStack inSlot = slot.getItem();
                if (fillExisting) {
                    if (inSlot.isEmpty() || !ItemStack.isSameItemSameTags(inSlot, remain)) {
                        continue;
                    }
                    int max = Math.min(slot.getMaxStackSize(remain), remain.getMaxStackSize());
                    int free = Math.max(0, max - inSlot.getCount());
                    if (free <= 0) {
                        continue;
                    }
                    int move = Math.min(free, remain.getCount());
                    if (move <= 0) {
                        continue;
                    }
                    inSlot.grow(move);
                    slot.setChanged();
                    remain.shrink(move);
                    continue;
                }

                if (!inSlot.isEmpty()) {
                    continue;
                }
                int move = Math.min(slot.getMaxStackSize(remain), remain.getCount());
                if (move <= 0) {
                    continue;
                }
                ItemStack placed = remain.copyWithCount(move);
                slot.set(placed);
                slot.setChanged();
                remain.shrink(move);
            }
        }
        return remain;
    }

    private static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        return extractMatchingFromNetwork(handlers, player, targetItem, ItemStack.EMPTY, limit);
    }

    private static ItemStack extractMatchingFromNetwork(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            ItemStack preferred, int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, preferred, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromPlayer = extractMatchingFromPlayerMainInventory(player, targetItem, out.isEmpty() ? preferred : out, remaining);
        if (fromPlayer.isEmpty()) {
            return out;
        }
        if (out.isEmpty()) {
            return fromPlayer;
        }
        if (ItemStack.isSameItemSameTags(out, fromPlayer)) {
            out.grow(fromPlayer.getCount());
        }
        return out;
    }

    private static ItemStack extractMatchingFromQuickDropSources(List<IItemHandler> handlers, ServerPlayer player, Item targetItem,
            int limit) {
        int remaining = Math.max(0, limit);
        if (remaining <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack out = extractMatchingFromLinked(handlers, targetItem, remaining);
        remaining -= out.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromHotbar = extractMatchingFromPlayerHotbarForQuickDrop(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromHotbar);
        remaining -= fromHotbar.getCount();
        if (remaining <= 0) {
            return out;
        }

        ItemStack fromMainInventory = extractMatchingFromPlayerMainInventory(player, targetItem, out, remaining);
        out = mergeExtractedStacks(out, fromMainInventory);
        return out;
    }

    private static void refundToLinked(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        storeToLinkedWithFallback(handlers, player, stack);
    }

    private static ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof LinkedItemHandlerView linkedView && linkedView.supportsAnySlotInsert()) {
            return linkedView.insertItemAnywhere(stack, false);
        }
        if (handler instanceof RtsAe2Compat.AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, false);
        }
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            remain = handler.insertItem(slot, remain, false);
        }
        return remain;
    }

    private static ItemStack storeToLinkedOnly(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }
        return remain;
    }

    private static OverflowOutcome storeToLinkedWithFallback(List<IItemHandler> handlers, ServerPlayer player, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandler(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    private static OverflowOutcome storeToLinkedWithFallbackPreferExisting(List<IItemHandler> handlers, ServerPlayer player,
            ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }

        int movedToInventory = 0;
        if (!remain.isEmpty()) {
            ItemStack invStack = remain.copy();
            int before = invStack.getCount();
            player.getInventory().add(invStack);
            movedToInventory = before - invStack.getCount();
            remain = invStack;
        }

        int dropped = 0;
        if (!remain.isEmpty()) {
            dropped = remain.getCount();
            player.drop(remain, false);
        }

        return new OverflowOutcome(movedToInventory, dropped);
    }

    private static ItemStack moveToPlayerInventoryOnly(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remain = stack.copy();
        player.getInventory().add(remain);
        return remain;
    }

    private static int[] snapshotPlayerMatchingCounts(ServerPlayer player, ItemStack prototype) {
        int size = player.getInventory().getContainerSize();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(stack, prototype)) {
                counts[i] = stack.getCount();
            }
        }
        return counts;
    }

    private static ItemStack[] snapshotCraftGridBlueprint(CraftingMenu menu) {
        ItemStack[] blueprint = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            Slot grid = menu.getSlot(1 + i);
            ItemStack stack = grid.getItem();
            blueprint[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        }
        return blueprint;
    }

    private static void refillCraftGridFromBlueprint(CraftingMenu menu, List<IItemHandler> handlers, ServerPlayer player,
            ItemStack[] blueprint, boolean fillAll, boolean includePlayerFallback) {
        if (blueprint == null || blueprint.length != 9) {
            return;
        }

        int maxPasses = fillAll ? 64 : 1;
        boolean changed = false;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean inserted = false;
            for (int i = 0; i < 9; i++) {
                ItemStack blueprintStack = blueprint[i];
                if (blueprintStack == null || blueprintStack.isEmpty()) {
                    continue;
                }
                Slot grid = menu.getSlot(1 + i);
                ItemStack current = grid.getItem();
                if (!current.isEmpty()) {
                    if (!ItemStack.isSameItemSameTags(current, blueprintStack)) {
                        continue;
                    }
                    if (current.getCount() >= current.getMaxStackSize()) {
                        continue;
                    }
                    ItemStack extracted = includePlayerFallback
                            ? extractOneMatchingPrototypeCombined(handlers, player, blueprintStack)
                            : extractOneMatchingPrototypeFromLinked(handlers, blueprintStack);
                    if (extracted.isEmpty()) {
                        continue;
                    }
                    current.grow(1);
                    grid.setChanged();
                    inserted = true;
                    changed = true;
                    continue;
                }

                ItemStack extracted = includePlayerFallback
                        ? extractOneMatchingPrototypeCombined(handlers, player, blueprintStack)
                        : extractOneMatchingPrototypeFromLinked(handlers, blueprintStack);
                if (extracted.isEmpty()) {
                    continue;
                }
                extracted.setCount(1);
                grid.set(extracted);
                grid.setChanged();
                inserted = true;
                changed = true;
            }
            if (!inserted) {
                break;
            }
            if (!fillAll) {
                break;
            }
        }
        if (changed) {
            refreshCraftingResult(menu);
        }
    }

    private static void refreshCraftingResult(CraftingMenu menu) {
        if (menu == null) {
            return;
        }
        CraftingContainer craftSlots = resolveCraftingContainer(menu);
        if (craftSlots != null) {
            menu.slotsChanged(craftSlots);
        }
    }

    private static CraftingContainer resolveCraftingContainer(CraftingMenu menu) {
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!CraftingContainer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object current = field.get(menu);
                    if (current instanceof CraftingContainer craftSlots) {
                        return craftSlots;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Fall back to the menu's default sync path if reflective access is blocked.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static ItemStack extractOneMatchingPrototypeCombined(List<IItemHandler> handlers, ServerPlayer player, ItemStack prototype) {
        ItemStack fromLinked = extractOneMatchingPrototypeFromLinked(handlers, prototype);
        if (!fromLinked.isEmpty()) {
            return fromLinked;
        }
        return extractOneMatchingPrototypeFromPlayer(player, prototype);
    }

    private static ItemStack extractOneMatchingPrototypeFromLinked(List<IItemHandler> handlers, ItemStack prototype) {
        if (prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, prototype)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty() && ItemStack.isSameItemSameTags(extracted, prototype)) {
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOneMatchingPrototypeFromPlayer(ServerPlayer player, ItemStack prototype) {
        if (player == null || prototype == null || prototype.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int start = getPlayerMainInventoryStart(player);
        int end = getPlayerMainInventoryEndExclusive(player);
        for (int i = start; i < end; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, prototype)) {
                continue;
            }
            ItemStack extracted = current.split(1);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (!extracted.isEmpty()) {
                return extracted;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack drainPlayerInventoryDelta(ServerPlayer player, ItemStack prototype, int[] before) {
        ItemStack out = ItemStack.EMPTY;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (!ItemStack.isSameItemSameTags(current, prototype)) {
                continue;
            }
            int previous = (before != null && i < before.length) ? before[i] : 0;
            int gained = Math.max(0, current.getCount() - previous);
            if (gained <= 0) {
                continue;
            }
            int take = Math.min(gained, current.getCount());
            ItemStack part = current.split(take);
            if (current.isEmpty()) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            } else {
                player.getInventory().setItem(i, current);
            }
            if (out.isEmpty()) {
                out = part;
            } else if (ItemStack.isSameItemSameTags(out, part)) {
                out.grow(part.getCount());
            } else {
                // Should not happen for one prototype, but keep behavior safe.
                player.getInventory().add(part);
            }
        }
        return out;
    }

    private static ItemStack insertToHandlerPreferExisting(IItemHandler handler, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (handler instanceof LinkedItemHandlerView linkedView && linkedView.supportsAnySlotInsert()) {
            return linkedView.insertItemAnywhere(stack, false);
        }
        if (handler instanceof RtsAe2Compat.AnySlotInsertItemHandler anySlot) {
            return anySlot.insertItemAnywhere(stack, false);
        }
        ItemStack remain = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            ItemStack slotStack = handler.getStackInSlot(slot);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameTags(slotStack, remain)) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        for (int slot = 0; slot < handler.getSlots() && !remain.isEmpty(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            remain = handler.insertItem(slot, remain, false);
        }
        return remain;
    }

    private static ItemStack storeToLinkedOnlyPreferExisting(List<IItemHandler> handlers, ItemStack stack) {
        ItemStack remain = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remain.isEmpty()) {
                break;
            }
            remain = insertToHandlerPreferExisting(handler, remain);
        }
        return remain;
    }

    private static ItemStack addToFunnelBuffer(Session session, ItemStack stack) {
        ItemStack remain = stack.copy();
        if (remain.isEmpty()) {
            return ItemStack.EMPTY;
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (remain.isEmpty()) {
                break;
            }
            if (buffered.isEmpty() || !ItemStack.isSameItemSameTags(buffered, remain)) {
                continue;
            }
            int free = Math.max(0, buffered.getMaxStackSize() - buffered.getCount());
            if (free <= 0) {
                continue;
            }
            int move = Math.min(free, remain.getCount());
            buffered.grow(move);
            remain.shrink(move);
        }

        while (!remain.isEmpty() && session.funnelBuffer.size() < FUNNEL_BUFFER_MAX_STACKS) {
            int move = Math.min(remain.getCount(), remain.getMaxStackSize());
            ItemStack chunk = remain.copy();
            chunk.setCount(move);
            session.funnelBuffer.add(chunk);
            remain.shrink(move);
        }
        return remain;
    }

    private static boolean flushFunnelBufferToDestinations(List<IItemHandler> handlers, ServerPlayer player, Session session) {
        if (session.funnelBuffer.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < session.funnelBuffer.size(); i++) {
            ItemStack buffered = session.funnelBuffer.get(i);
            if (buffered.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                remain = moveToPlayerInventoryOnly(player, remain);
            }
            if (remain.isEmpty()) {
                session.funnelBuffer.remove(i);
                i--;
                changed = true;
            } else if (remain.getCount() != buffered.getCount()) {
                session.funnelBuffer.set(i, remain);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean absorbDropsForFunnel(ServerPlayer player, BlockPos target, List<IItemHandler> handlers, Session session) {
        AABB box = new AABB(target).inflate(FUNNEL_RADIUS);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());

        int processedEntities = 0;
        int processedItems = 0;
        boolean changed = false;
        for (ItemEntity drop : drops) {
            if (processedEntities >= FUNNEL_MAX_ENTITIES_PER_TICK || processedItems >= FUNNEL_MAX_ITEMS_PER_TICK) {
                break;
            }
            processedEntities++;

            ItemStack worldStack = drop.getItem();
            if (worldStack.isEmpty()) {
                continue;
            }
            int remainingBudget = FUNNEL_MAX_ITEMS_PER_TICK - processedItems;
            int iterations = Math.min(worldStack.getCount(), remainingBudget);
            for (int i = 0; i < iterations; i++) {
                ItemStack one = worldStack.copy();
                one.setCount(1);
                ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, one);
                if (!remain.isEmpty()) {
                    remain = moveToPlayerInventoryOnly(player, remain);
                }
                if (!remain.isEmpty()) {
                    remain = addToFunnelBuffer(session, remain);
                }
                if (!remain.isEmpty()) {
                    break;
                }
                worldStack.shrink(1);
                processedItems++;
                changed = true;
                if (worldStack.isEmpty()) {
                    break;
                }
            }
            if (worldStack.isEmpty()) {
                drop.discard();
            } else {
                drop.setItem(worldStack);
            }
        }
        return changed;
    }

    private static void disableFunnelAndFlushBuffer(ServerPlayer player, Session session) {
        session.funnelEnabled = false;
        session.funnelTarget = null;
        session.funnelTickCooldown = 0;
        if (session.funnelBuffer.isEmpty()) {
            return;
        }
        sanitizeSessionDimension(player, session);
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler itemHandler : linked) {
            handlers.add(itemHandler.handler());
        }

        for (ItemStack buffered : session.funnelBuffer) {
            if (buffered.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnlyPreferExisting(handlers, buffered);
            if (!remain.isEmpty()) {
                storeToLinkedWithFallback(handlers, player, remain);
            }
        }
        session.funnelBuffer.clear();
    }

    private static void tickFunnel(ServerPlayer player, Session session) {
        if (!session.funnelEnabled || session.mode != BuilderMode.FUNNEL) {
            return;
        }
        if (session.funnelTickCooldown > 0) {
            session.funnelTickCooldown--;
            return;
        }
        session.funnelTickCooldown = FUNNEL_TICK_INTERVAL - 1;

        sanitizeSessionDimension(player, session);
        if (session.funnelTarget == null) {
            return;
        }
        if (!canAccessWorldTarget(player, session.funnelTarget)) {
            return;
        }
        if (!RtsCameraManager.isWithinActionRadius(player, session.funnelTarget)) {
            return;
        }

        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler linkedHandler : linked) {
            handlers.add(linkedHandler.handler());
        }

        boolean changed = flushFunnelBufferToDestinations(handlers, player, session);
        changed |= absorbDropsForFunnel(player, session.funnelTarget, handlers, session);
        if (changed) {
            requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            runQuestDetect(player, session, false);
        }
    }

    private static Map<String, Long> summarizeFunnelBuffer(Session session) {
        Map<String, Long> counts = new HashMap<>();
        for (ItemStack stack : session.funnelBuffer) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id == null) {
                continue;
            }
            mergeCount(counts, id.toString(), stack.getCount());
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        Map<String, Long> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : sorted) {
            ordered.put(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    private static boolean absorbNearbyDropsIntoLinked(ServerPlayer player, BlockPos pos, Session session) {
        if (!hasAnyStorage(player, session)) {
            return false;
        }
        List<LinkedHandler> linked = resolveLinkedHandlers(player, session);
        if (linked.isEmpty()) {
            return false;
        }
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler handler : linked) {
            handlers.add(handler.handler());
        }

        AABB box = new AABB(pos).inflate(1.25D);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = storeToLinkedOnly(handlers, original);
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    private static void sendStorageOverflowHint(ServerPlayer player, String context, OverflowOutcome overflow) {
        if (!overflow.hasOverflow()) {
            return;
        }
        String message;
        if (overflow.movedToInventory() > 0 && overflow.dropped() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory()
                    + " to inventory, dropped " + overflow.dropped() + ".";
        } else if (overflow.movedToInventory() > 0) {
            message = context + ": linked storage full, moved " + overflow.movedToInventory() + " to inventory.";
        } else {
            message = context + ": linked+inventory full, dropped " + overflow.dropped() + ".";
        }
        player.displayClientMessage(Component.literal(message), true);
    }

    /*
     * Thin recent-entry wrappers keep the existing crafting, transfer, mining,
     * and placement call sites stable. Later page-builder or transfer splits
     * can route callers directly to RtsStorageRecentEntries.
     */
    public static void recordCraftedOutput(ServerPlayer player, ItemStack crafted) {
        if (player == null || crafted == null || crafted.isEmpty()) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        RtsStorageRecentEntries.recordCraftedOutput(session, crafted);
    }

    private static void recordRecentItem(Session session, String itemId, byte kind, long amount) {
        RtsStorageRecentEntries.recordRecentItem(session, itemId, kind, amount);
    }

    private static void recordRecentFluid(Session session, String fluidId, byte kind, long amount, long capacity) {
        RtsStorageRecentEntries.recordRecentFluid(session, fluidId, kind, amount, capacity);
    }

    private static void runQuestDetect(ServerPlayer player, Session session, boolean force) {
        if (player == null || session == null) {
            return;
        }
        if (!RtsFtbCompat.isDetectAvailable()) {
            if (force) {
                sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE, 0, 0, 0);
            }
            return;
        }
        long now = player.serverLevel().getGameTime();
        if (!force && now < session.nextQuestDetectTick) {
            return;
        }
        session.nextQuestDetectTick = now + QUEST_DETECT_COOLDOWN_TICKS;
        if (force) {
            sendQuestDetectStatus(player, S2CRtsQuestDetectStatusPayload.PHASE_STARTED, 0, 0, 0);
        }
        RtsFtbCompat.QuestDetectResult result = RtsFtbCompat.detectNow(player);
        if (force) {
            byte phase = result.error()
                    ? S2CRtsQuestDetectStatusPayload.PHASE_ERROR
                    : result.available()
                            ? S2CRtsQuestDetectStatusPayload.PHASE_COMPLETE
                            : S2CRtsQuestDetectStatusPayload.PHASE_UNAVAILABLE;
            sendQuestDetectStatus(
                    player,
                    phase,
                    result.scannedTasks(),
                    result.scannedTasks(),
                    result.newlyCompletedTasks());
        }
    }

    private static void sendQuestDetectStatus(ServerPlayer player, byte phase, int scannedTasks, int totalTasks, int completedTasks) {
        PacketDistributor.sendToPlayer(
                player,
                new S2CRtsQuestDetectStatusPayload(
                        phase,
                        Math.max(0, scannedTasks),
                        Math.max(0, totalTasks),
                        Math.max(0, completedTasks)));
    }

    private static void playRemotePlacedBlockSound(ServerPlayer player, ServerLevel level, Session session, BlockPos pos,
            boolean quickBuild) {
        if (player == null || level == null || pos == null || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (quickBuild && session != null) {
            noteQuickBuildPlacement(session, pos, gameTime);
            if (session.lastQuickBuildPlaceSoundTick == gameTime) {
                return;
            }
            session.lastQuickBuildPlaceSoundTick = gameTime;
        }
        SoundType soundType = state.getSoundType(level, pos, player);
        sendDirectSound(
                player,
                soundType.getPlaceSound(),
                SoundSource.BLOCKS,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                (soundType.getVolume() + 1.0F) / 2.0F,
                soundType.getPitch() * 0.8F);
    }

    private static void noteQuickBuildPlacement(Session session, BlockPos pos, long gameTime) {
        session.quickBuildSoundPlacedCount++;
        session.quickBuildCompletionSoundTick = gameTime + QUICK_BUILD_COMPLETION_SOUND_DELAY_TICKS;
        session.quickBuildSoundX = pos.getX() + 0.5D;
        session.quickBuildSoundY = pos.getY() + 0.5D;
        session.quickBuildSoundZ = pos.getZ() + 0.5D;
    }

    private static void tickQuickBuildCompletionSound(ServerPlayer player, Session session) {
        if (player == null || session == null || session.quickBuildSoundPlacedCount <= 0) {
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        if (gameTime < session.quickBuildCompletionSoundTick) {
            return;
        }
        sendDirectSound(
                player,
                SoundEvents.NOTE_BLOCK_HARP.value(),
                SoundSource.PLAYERS,
                session.quickBuildSoundX,
                session.quickBuildSoundY,
                session.quickBuildSoundZ,
                0.35F,
                1.12F);
        session.quickBuildSoundPlacedCount = 0;
        session.quickBuildCompletionSoundTick = -1L;
        session.lastQuickBuildPlaceSoundTick = Long.MIN_VALUE;
    }

    private static void playRemoteUseSound(ServerPlayer player, ServerLevel level, Entity targetEntity, BlockPos pos,
            ItemStack stack) {
        if (player == null || level == null || stack == null || stack.isEmpty()) {
            return;
        }
        SoundEvent sound = selectRemoteUseSound(stack);
        if (sound == null) {
            return;
        }
        SoundSource source = targetEntity == null ? SoundSource.BLOCKS : SoundSource.PLAYERS;
        Vec3 at = targetEntity == null
                ? new Vec3(
                        pos == null ? player.getX() : pos.getX() + 0.5D,
                        pos == null ? player.getY() : pos.getY() + 0.5D,
                        pos == null ? player.getZ() : pos.getZ() + 0.5D)
                : targetEntity.getBoundingBox().getCenter();
        sendDirectSound(player, sound, source, at.x, at.y, at.z, 1.0F, 1.0F);
    }

    private static SoundEvent selectRemoteUseSound(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof HoeItem) {
            return SoundEvents.HOE_TILL;
        }
        if (item instanceof ShovelItem) {
            return SoundEvents.SHOVEL_FLATTEN;
        }
        if (item instanceof AxeItem) {
            return SoundEvents.AXE_STRIP;
        }
        if (item instanceof ShearsItem) {
            return SoundEvents.SHEEP_SHEAR;
        }
        if (item instanceof BoneMealItem) {
            return SoundEvents.BONE_MEAL_USE;
        }
        if (item == Items.HONEYCOMB) {
            return SoundEvents.HONEYCOMB_WAX_ON;
        }
        return null;
    }

    private static ItemStack createSoundStack(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId == null ? "" : itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private static void sendDirectSound(ServerPlayer player, SoundEvent sound, SoundSource source, double x, double y,
            double z, float volume, float pitch) {
        if (player == null || sound == null || sound == SoundEvents.EMPTY) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                source,
                x,
                y,
                z,
                volume,
                pitch,
                player.getRandom().nextLong()));
    }

    private static InteractionResult interactWithToolSlot(ServerPlayer player, ServerLevel level, Entity targetEntity,
            BlockHitResult blockHit, Vec3 hit, int toolSlot, RayContext rayContext) {
        int slot = clampHotbarSlot(toolSlot);
        int previousSelected = player.getInventory().selected;
        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        return withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            player.getInventory().selected = slot;
            try {
                if (targetEntity != null) {
                    return interactEntityWithMainHand(player, level, targetEntity, hit);
                }
                if (blockHit != null) {
                    // Alt interaction: prefer non-build interaction first.
                    InteractionResult primaryResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (primaryResult.consumesAction()) {
                        return primaryResult;
                    }
                    InteractionResult primaryUseResult = withTemporaryShiftKey(player, false, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                    if (primaryUseResult.consumesAction()) {
                        return primaryUseResult;
                    }
                    InteractionResult secondaryResult = withTemporaryShiftKey(player, true, () -> player.gameMode.useItemOn(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND,
                            blockHit));
                    if (secondaryResult.consumesAction()) {
                        return secondaryResult;
                    }
                    return withTemporaryShiftKey(player, true, () -> player.gameMode.useItem(
                            player,
                            level,
                            player.getMainHandItem(),
                            InteractionHand.MAIN_HAND));
                }
                return InteractionResult.PASS;
            } finally {
                player.getInventory().selected = previousSelected;
            }
                });
    }

    private static InteractionResult interactWithLinkedItem(ServerPlayer player, ServerLevel level, Session session,
            Entity targetEntity, BlockHitResult blockHit, Vec3 hit, String itemId, RayContext rayContext) {
        if (itemId == null || itemId.isBlank() || !hasAnyStorage(player, session)) {
            return InteractionResult.PASS;
        }

        List<LinkedHandler> activeLinked = resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return InteractionResult.PASS;
        }

        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return InteractionResult.PASS;
        }

        ItemStack extracted = extractOneFromNetwork(handlers, player, BuiltInRegistries.ITEM.get(id));
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        Vec3 interactionPos = resolveInteractionPosition(targetEntity, blockHit, hit);
        UseOnOutcome outcome = withTemporaryUseItemContext(
                player,
                interactionPos,
                hit,
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> {
            if (targetEntity != null) {
                return useItemOnEntityWithMainHand(player, level, extracted, targetEntity, hit);
            }
            // Alt interaction: normal item interaction first, build-like secondary interaction later.
            UseOnOutcome primaryOn = useItemOnWithMainHand(player, level, extracted, blockHit, false);
            if (primaryOn.result().consumesAction()) {
                return primaryOn;
            }
            ItemStack afterPrimaryOn = primaryOn.remainder().isEmpty() ? extracted.copy() : primaryOn.remainder().copy();

            UseOnOutcome primaryUse = useItemWithMainHand(player, level, afterPrimaryOn, false);
            if (primaryUse.result().consumesAction()) {
                return primaryUse;
            }
            ItemStack afterPrimaryUse = primaryUse.remainder().isEmpty() ? afterPrimaryOn : primaryUse.remainder().copy();

            UseOnOutcome secondaryOn = useItemOnWithMainHand(player, level, afterPrimaryUse, blockHit, true);
            if (secondaryOn.result().consumesAction()) {
                return secondaryOn;
            }
            ItemStack afterSecondaryOn = secondaryOn.remainder().isEmpty() ? afterPrimaryUse : secondaryOn.remainder().copy();
            return useItemWithMainHand(player, level, afterSecondaryOn, true);
                });
        if (!outcome.remainder().isEmpty()) {
            refundToLinked(handlers, player, outcome.remainder());
        }
        return outcome.result();
    }

    private static InteractionResult interactEntityWithMainHand(ServerPlayer player, ServerLevel level, Entity entity,
            Vec3 hit) {
        InteractionResult result = player.interactOn(entity, InteractionHand.MAIN_HAND);
        if (!result.consumesAction()) {
            Vec3 localHit = hit.subtract(entity.position());
            result = entity.interactAt(player, localHit, InteractionHand.MAIN_HAND);
        }
        if (!result.consumesAction()) {
            result = player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        }
        return result;
    }

    private static Vec3 resolveInteractionPosition(Entity targetEntity, BlockHitResult blockHit, Vec3 hit) {
        if (targetEntity != null) {
            Vec3 center = targetEntity.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(hit);
            if (delta.lengthSqr() < 1.0e-6D) {
                delta = new Vec3(0.0D, 0.0D, 1.0D);
            }
            Vec3 at = center.subtract(delta.normalize().scale(1.8D));
            return new Vec3(at.x, at.y + 0.2D, at.z);
        }
        if (blockHit != null) {
            Vec3 n = Vec3.atLowerCornerOf(blockHit.getDirection().getNormal());
            Vec3 at = blockHit.getLocation().subtract(n.scale(2.2D));
            return new Vec3(at.x, at.y + 1.1D, at.z);
        }
        return hit;
    }

    private static float[] yawPitchTo(Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double xz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(-d.x, d.z)));
        float pitch = (float) (-Math.toDegrees(Math.atan2(d.y, xz)));
        return new float[] { yaw, pitch };
    }

    private static RayContext parseRayContext(
            double originX, double originY, double originZ,
            double dirX, double dirY, double dirZ) {
        if (!Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                || !Double.isFinite(dirX) || !Double.isFinite(dirY) || !Double.isFinite(dirZ)) {
            return null;
        }
        Vec3 dir = new Vec3(dirX, dirY, dirZ);
        if (dir.lengthSqr() < 1.0e-6D) {
            return null;
        }
        return new RayContext(new Vec3(originX, originY, originZ), dir.normalize());
    }

    private static <T> T withTemporaryUseItemContext(ServerPlayer player, Vec3 fallbackPos, Vec3 fallbackLookAt,
            RayContext rayContext, double reach, Supplier<T> action) {
        if (rayContext == null) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        Vec3 rayDir = rayContext.dir();
        if (!Double.isFinite(rayDir.x) || !Double.isFinite(rayDir.y) || !Double.isFinite(rayDir.z)
                || rayDir.lengthSqr() < 1.0e-6D) {
            return withTemporaryInteractionPosition(player, fallbackPos, fallbackLookAt, action);
        }
        double clampedReach = Math.max(2.0D, Math.min(8.0D, reach));
        double offset = Math.max(0.5D, clampedReach - REMOTE_POV_EPSILON);
        Vec3 normalizedDir = rayDir.normalize();
        Vec3 virtualEye = fallbackLookAt.subtract(normalizedDir.scale(offset));
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 virtualFeet = new Vec3(virtualEye.x, virtualEye.y - eyeHeight, virtualEye.z);
        Vec3 lookAt = virtualEye.add(normalizedDir.scale(clampedReach));
        return withTemporaryInteractionPosition(player, virtualFeet, lookAt, action);
    }

    private static <T> T withTemporaryInteractionPosition(ServerPlayer player, Vec3 position, Vec3 lookAt, Supplier<T> action) {
        Vec3 prevPos = player.position();
        float prevYRot = player.getYRot();
        float prevXRot = player.getXRot();
        float prevYHeadRot = player.getYHeadRot();
        float prevYBodyRot = player.yBodyRot;

        player.setPos(position.x, position.y, position.z);
        double eyeHeight = player.getEyeHeight(player.getPose());
        Vec3 eyePos = new Vec3(position.x, position.y + eyeHeight, position.z);
        float[] look = yawPitchTo(eyePos, lookAt);
        player.setYRot(look[0]);
        player.setXRot(look[1]);
        player.setYHeadRot(look[0]);
        player.yBodyRot = look[0];
        try {
            return action.get();
        } finally {
            player.setPos(prevPos.x, prevPos.y, prevPos.z);
            player.setYRot(prevYRot);
            player.setXRot(prevXRot);
            player.setYHeadRot(prevYHeadRot);
            player.yBodyRot = prevYBodyRot;
        }
    }

    private static <T> T withTemporaryShiftKey(ServerPlayer player, boolean active, Supplier<T> action) {
        boolean previous = player.isShiftKeyDown();
        if (previous == active) {
            return action.get();
        }
        player.setShiftKeyDown(active);
        try {
            return action.get();
        } finally {
            player.setShiftKeyDown(previous);
        }
    }

    private static UseOnOutcome useItemOnWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            BlockHitResult hit, boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    hit));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static UseOnOutcome useItemWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            boolean forceSecondaryUse) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItem(
                    player,
                    level,
                    player.getMainHandItem(),
                    InteractionHand.MAIN_HAND));
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static UseOnOutcome useItemOnEntityWithMainHand(ServerPlayer player, ServerLevel level, ItemStack handStack,
            Entity entity, Vec3 hit) {
        ItemStack previousMainHand = player.getMainHandItem().copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, handStack);
        InteractionResult result;
        ItemStack remainder;
        try {
            result = interactEntityWithMainHand(player, level, entity, hit);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new UseOnOutcome(result, remainder);
    }

    private static void relaxOpenedMenuValidation(AbstractContainerMenu menu) {
        if (menu == null) {
            return;
        }
        Class<?> type = menu.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Class<?> fieldType = field.getType();

                    if (ContainerLevelAccess.class.isAssignableFrom(fieldType)) {
                        Object current = field.get(menu);
                        if (current instanceof ContainerLevelAccess access
                                && !(access instanceof RelaxedContainerLevelAccess)) {
                            field.set(menu, new RelaxedContainerLevelAccess(access));
                        } else if (current == null) {
                            field.set(menu, ContainerLevelAccess.NULL);
                        }
                        continue;
                    }

                    if (fieldType == Container.class) {
                        Object current = field.get(menu);
                        if (current instanceof Container delegate && !(delegate instanceof AlwaysValidContainer)) {
                            field.set(menu, new AlwaysValidContainer(delegate));
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // If a field is inaccessible/final in this runtime, keep default validation for that field.
                }
            }
            type = type.getSuperclass();
        }
    }

    private static void markRemoteMenuOpen(ServerPlayer player, Session session, AbstractContainerMenu menu, BlockPos pos) {
        if (menu == null) {
            return;
        }
        AbstractContainerMenu remoteMenu = RtsSophisticatedStorageCompat.wrapRemoteMenu(menu);
        if (player != null && player.containerMenu != remoteMenu) {
            player.containerMenu = remoteMenu;
        }
        if (session != null) {
            session.remoteMenuContainerId = remoteMenu.containerId;
            session.remoteMenuPos = pos == null ? null : pos.immutable();
        }
        relaxOpenedMenuValidation(remoteMenu);
        if (session != null && RtsSophisticatedStorageCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsSophisticatedStorageCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsSophisticatedStorageCompat.clearServerRemoteMenu(player);
        }
        if (session != null && RtsRemoteMenuCompat.isSupportedRemoteMenu(remoteMenu)) {
            RtsRemoteMenuCompat.markServerRemoteMenu(player, remoteMenu);
        } else {
            RtsRemoteMenuCompat.clearServerRemoteMenu(player);
        }
    }

    private static void clearRemoteMenuValidation(ServerPlayer player, Session session) {
        if (session != null) {
            session.remoteMenuContainerId = -1;
            session.remoteMenuPos = null;
        }
        RtsSophisticatedStorageCompat.clearServerRemoteMenu(player);
        RtsRemoteMenuCompat.clearServerRemoteMenu(player);
    }

    private static void closeTrackedRemoteMenu(ServerPlayer player, Session session) {
        if (player == null || session == null || session.remoteMenuContainerId < 0) {
            return;
        }
        if (player.containerMenu != null
                && player.containerMenu.containerId == session.remoteMenuContainerId
                && !(player.containerMenu instanceof InventoryMenu)) {
            player.closeContainer();
        }
        forceRemoteMenuClosedVisual(player, session.remoteMenuPos);
        session.remoteMenuContainerId = -1;
        session.remoteMenuPos = null;
    }

    private static void forceRemoteMenuClosedVisual(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || !(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        level.blockEvent(pos, state.getBlock(), 1, 0);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static void sendRemoteMenuOpenHint(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsRemoteMenuHintPayload(pos));
        if (!(player.level() instanceof ServerLevel level) || !level.hasChunkAt(pos)) {
            return;
        }
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            player.connection.send(ClientboundBlockEntityDataPacket.create(blockEntity));
        }
    }

    private static SyntheticBlockInteraction createGuiBindingInteraction(ServerPlayer player, BlockPos pos, Direction preferredFace) {
        Direction face = preferredFace == null ? resolveGuiBindingFace(player, pos) : preferredFace;
        Vec3 faceCenter = Vec3.atCenterOf(pos).add(
                face.getStepX() * 0.498D,
                face.getStepY() * 0.498D,
                face.getStepZ() * 0.498D);
        Vec3 eyePos = faceCenter.add(
                face.getStepX() * 2.2D,
                face.getStepY() * 2.2D,
                face.getStepZ() * 2.2D);
        double eyeHeight = player == null ? 1.62D : player.getEyeHeight(player.getPose());
        Vec3 interactionPos = new Vec3(eyePos.x, eyePos.y - eyeHeight, eyePos.z);
        return new SyntheticBlockInteraction(new BlockHitResult(faceCenter, face, pos, false), interactionPos);
    }

    private static Direction resolveGuiBindingFace(ServerPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 playerPos = player == null ? center : player.position();
        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static BlockPos detectPlacedPos(ServerLevel level, BlockPos clickedPos, BlockState beforeClicked, BlockPos adjacentPos,
            BlockState beforeAdjacent) {
        if (!level.hasChunkAt(clickedPos)) {
            return null;
        }
        BlockState afterClicked = level.getBlockState(clickedPos);
        if (!afterClicked.equals(beforeClicked) && !afterClicked.isAir()) {
            return clickedPos;
        }

        if (beforeAdjacent == null || !level.hasChunkAt(adjacentPos)) {
            return null;
        }
        BlockState afterAdjacent = level.getBlockState(adjacentPos);
        if (!afterAdjacent.equals(beforeAdjacent) && !afterAdjacent.isAir()) {
            return adjacentPos;
        }
        return null;
    }

    private static void rotatePlacedBlock(ServerLevel level, BlockPos pos, byte rotateSteps) {
        int turns = rotateSteps & 3;
        if (turns == 0 || !level.hasChunkAt(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        BlockState rotated = state;
        for (int i = 0; i < turns; i++) {
            rotated = rotated.rotate(Rotation.CLOCKWISE_90);
        }
        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
        }
    }

    private static void refundItem(IItemHandler handler, ServerPlayer player, ItemStack stack) {
        ItemStack remain = insertToHandler(handler, stack);
        if (!remain.isEmpty()) {
            player.drop(remain, false);
        }
    }

    // Thin wrappers keep existing manager call sites stable while linked
    // resolution moves behind a reviewable dependency boundary.
    private static IItemHandler findHandler(ServerLevel level, BlockPos pos, Direction side) {
        return RtsLinkedStorageResolver.findHandler(level, pos, side);
    }

    private static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        return RtsLinkedStorageResolver.findHandler(player, pos);
    }

    private static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        return RtsLinkedStorageResolver.findLinkedItemHandler(player, pos);
    }

    private static IFluidHandler findFluidHandler(ServerLevel level, BlockPos pos, Direction side) {
        return RtsLinkedStorageResolver.findFluidHandler(level, pos, side);
    }

    private static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        return RtsLinkedStorageResolver.findFluidHandler(player, pos);
    }

    private static String resolveDisplayName(ServerLevel level, BlockPos pos) {
        return RtsLinkedStorageResolver.resolveDisplayName(level, pos);
    }

    private static List<LinkedHandler> resolveLinkedHandlers(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
    }

    private static List<LinkedFluidHandler> resolveLinkedFluidHandlers(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
    }

    private static boolean canAccessWorldTarget(ServerPlayer player, BlockPos pos) {
        return RtsLinkedStorageResolver.canAccessWorldTarget(player, pos);
    }

    private static boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }

        if (level.mayInteract(player, pos)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos)) {
            return true;
        }

        // Fluid placement can target air blocks; also validate supporting block below.
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.hasChunkAt(below)) {
            return false;
        }
        return level.mayInteract(player, below)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos);
    }

    private static boolean hasAnyStorage(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.hasAnyStorage(player, session);
    }

    private static String buildAnyStorageSummary(ServerPlayer player, Session session) {
        return RtsLinkedStorageResolver.buildAnyStorageSummary(player, session);
    }

    private static void sanitizeSessionDimension(ServerPlayer player, Session session) {
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
    }

    private static String buildLinkedSummary(Session session) {
        return RtsLinkedStorageResolver.buildLinkedSummary(session);
    }

    static byte sanitizeLinkMode(byte linkMode) {
        return RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
    }

    private static boolean isExtractOnlyLink(Session session, LinkedStorageRef ref) {
        return RtsLinkedStorageResolver.isExtractOnlyLink(session, ref);
    }

    // Thin wrappers keep existing manager call sites stable for this small
    // extraction; the later page-builder split can remove them.
    private static List<String> buildQuickSlotPayload(Session session) {
        return RtsStorageUiPayloads.buildQuickSlotPayload(session, QUICK_SLOT_COUNT);
    }

    private static List<String> buildGuiBindingLabelPayload(Session session) {
        return RtsStorageUiPayloads.buildGuiBindingLabelPayload(session, GUI_BINDING_SLOT_COUNT);
    }

    private static List<String> buildGuiBindingItemIdPayload(Session session) {
        return RtsStorageUiPayloads.buildGuiBindingItemIdPayload(session, GUI_BINDING_SLOT_COUNT);
    }

    private static List<Long> toPackedPositions(ServerPlayer player, List<LinkedStorageRef> refs) {
        ResourceKey<Level> currentDimension = player.serverLevel().dimension();
        List<Long> packed = new ArrayList<>(refs.size());
        for (LinkedStorageRef ref : refs) {
            if (ref == null || ref.pos() == null || !currentDimension.equals(ref.dimension())) {
                continue;
            }
            packed.add(ref.pos().asLong());
        }
        return packed;
    }

    static ResourceKey<Level> parseDimensionKey(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    private static long getHandlerReportedCount(IItemHandler handler, int slot, ItemStack stack) {
        return sanitizeCount(RtsAe2Compat.getReportedCount(handler, slot, stack));
    }

    private static void mergeCount(Map<String, Long> counts, String key, long amount) {
        if (counts == null || key == null || key.isBlank()) {
            return;
        }
        long sanitized = sanitizeCount(amount);
        if (sanitized <= 0L) {
            return;
        }
        counts.merge(key, sanitized, RtsStorageManager::saturatedAdd);
    }

    private static long saturatedAdd(long a, long b) {
        long left = sanitizeCount(a);
        long right = sanitizeCount(b);
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long sanitizeCount(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value >= EFFECTIVELY_INFINITE_COUNT ? Long.MAX_VALUE : value;
    }

    private static long internalFluidCapacityMb(ServerPlayer player) {
        if (player == null) {
            return INTERNAL_FLUID_CAPACITY_MB;
        }
        return Math.max(0L, (long) RtsProgressionManager.getFluidCapacityBuckets(player) * FluidType.BUCKET_VOLUME);
    }

    private record Entry(ItemStack stack, String itemId, String namespace, String path, String label, long count) {
    }

    private record FluidEntry(String fluidId, String namespace, String path, long amount, long capacity) {
    }

    private record RecipeAvailability(boolean craftable, String missingSummary, int missingTotal) {
    }

    private record CraftableGroupEntry(CraftableCandidate primary, List<CraftableCandidate> options) {
        private static int compareForPanel(CraftableGroupEntry a, CraftableGroupEntry b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            return CraftableCandidate.compareForPanel(a.primary(), b.primary());
        }
    }

    private record CraftableCandidate(
            String recipeId,
            String resultItemId,
            int resultCount,
            String resultLabel,
            boolean craftable,
            String missingSummary,
            int missingTotal,
            String recipeSummary) {
        private boolean isPreferredOver(CraftableCandidate other) {
            if (other == null) {
                return true;
            }
            if (this.craftable != other.craftable) {
                return this.craftable;
            }
            if (this.missingTotal != other.missingTotal) {
                return this.missingTotal < other.missingTotal;
            }
            if (this.resultCount != other.resultCount) {
                return this.resultCount > other.resultCount;
            }
            return this.recipeId.compareToIgnoreCase(other.recipeId) < 0;
        }

        private static int compareForPanel(CraftableCandidate a, CraftableCandidate b) {
            if (a.craftable != b.craftable) {
                return a.craftable ? -1 : 1;
            }
            int byLabel = a.resultLabel.compareToIgnoreCase(b.resultLabel);
            if (byLabel != 0) {
                return byLabel;
            }
            return a.recipeId.compareToIgnoreCase(b.recipeId);
        }

        private static int compareForRecipeSelection(CraftableCandidate a, CraftableCandidate b) {
            if (a == null && b == null) {
                return 0;
            }
            if (a == null) {
                return 1;
            }
            if (b == null) {
                return -1;
            }
            if (a.isPreferredOver(b)) {
                return b.isPreferredOver(a) ? 0 : -1;
            }
            if (b.isPreferredOver(a)) {
                return 1;
            }
            return a.recipeId.compareToIgnoreCase(b.recipeId);
        }
    }

    private record CategorySelection(Type type, String namespace, String tabKey) {
        private static CategorySelection all() {
            return new CategorySelection(Type.ALL, "", "");
        }

        private static CategorySelection mod(String namespace) {
            return new CategorySelection(Type.MOD, namespace, "");
        }

        private static CategorySelection tab(String namespace, String tabKey) {
            return new CategorySelection(Type.TAB, namespace, tabKey);
        }

        private boolean isCreativeTab() {
            return this.type == Type.TAB;
        }

        private boolean matches(String namespace, Set<String> tabs) {
            return switch (this.type) {
                case ALL -> true;
                case MOD -> this.namespace.equals(namespace);
                case TAB -> this.namespace.equals(namespace) && tabs.contains(this.tabKey);
            };
        }

        private enum Type {
            ALL,
            MOD,
            TAB
        }
    }

    private static final class PreviewCraftingMenu extends AbstractContainerMenu {
        private PreviewCraftingMenu() {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return false;
        }
    }

    private record RayContext(Vec3 origin, Vec3 dir) {
    }

    private record SyntheticBlockInteraction(BlockHitResult hit, Vec3 interactionPos) {
    }

    private record UseOnOutcome(InteractionResult result, ItemStack remainder) {
    }

    private record ContainerDrainOutcome(FluidStack fluid, ItemStack remainder) {
        private static final ContainerDrainOutcome EMPTY = new ContainerDrainOutcome(FluidStack.EMPTY, ItemStack.EMPTY);

        private boolean isEmpty() {
            return this.fluid.isEmpty();
        }
    }

    static final class ToolLease {
        private static final ToolLease EMPTY = new ToolLease(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                null,
                -1,
                -1,
                "none");

        private final ItemStack original;
        private final ItemStack stack;
        private final IItemHandler linkedHandler;
        private final int linkedSlot;
        private final int playerSlot;
        private final String sourceDescription;

        private ToolLease(ItemStack original, ItemStack stack, IItemHandler linkedHandler, int linkedSlot, int playerSlot,
                String sourceDescription) {
            this.original = original == null || original.isEmpty() ? ItemStack.EMPTY : original.copy();
            this.stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
            this.linkedHandler = linkedHandler;
            this.linkedSlot = linkedSlot;
            this.playerSlot = playerSlot;
            this.sourceDescription = sourceDescription == null ? "unknown" : sourceDescription;
        }

        static ToolLease empty() {
            return EMPTY;
        }

        private static ToolLease playerSlot(int slot, ItemStack stack) {
            return new ToolLease(stack, stack, null, -1, slot, "player inventory slot " + slot);
        }

        private static ToolLease linkedSlot(IItemHandler handler, int slot, ItemStack stack) {
            return new ToolLease(stack, stack, handler, slot, -1, "linked storage slot " + slot);
        }

        private boolean isEmpty() {
            return this.stack.isEmpty();
        }

        private ItemStack stack() {
            return this.stack;
        }

        private ItemStack original() {
            return this.original;
        }

        private ToolLease withStack(ItemStack updatedStack) {
            if (this == EMPTY || updatedStack == null || updatedStack.isEmpty()) {
                return new ToolLease(this.original, ItemStack.EMPTY, this.linkedHandler, this.linkedSlot, this.playerSlot, this.sourceDescription);
            }
            return new ToolLease(this.original, updatedStack, this.linkedHandler, this.linkedSlot, this.playerSlot, this.sourceDescription);
        }

        private ItemStack returnToSource(ServerPlayer player) {
            if (this.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack remain = this.stack.copy();
            if (this.playerSlot >= 0) {
                remain = returnToPlayerSlot(player, this.playerSlot, remain);
            } else if (this.linkedHandler != null && this.linkedSlot >= 0) {
                remain = this.linkedHandler.insertItem(this.linkedSlot, remain, false);
            }
            return remain;
        }

        private String describeSource() {
            return this.sourceDescription;
        }

        private static ItemStack returnToPlayerSlot(ServerPlayer player, int slot, ItemStack stack) {
            if (player == null || stack == null || stack.isEmpty()
                    || slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return stack == null ? ItemStack.EMPTY : stack.copy();
            }
            ItemStack remain = stack.copy();
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, remain);
                player.getInventory().setChanged();
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(current, remain)) {
                int free = Math.max(0, current.getMaxStackSize() - current.getCount());
                if (free > 0) {
                    int moved = Math.min(free, remain.getCount());
                    current.grow(moved);
                    remain.shrink(moved);
                    player.getInventory().setItem(slot, current);
                    player.getInventory().setChanged();
                }
            }
            return remain;
        }
    }

    private record MiningDestroyOutcome(boolean broken, ItemStack remainder) {
    }

    private record OverflowOutcome(int movedToInventory, int dropped) {
        private static final OverflowOutcome EMPTY = new OverflowOutcome(0, 0);

        private OverflowOutcome merge(OverflowOutcome other) {
            return new OverflowOutcome(this.movedToInventory + other.movedToInventory, this.dropped + other.dropped);
        }

        private boolean hasOverflow() {
            return this.movedToInventory > 0 || this.dropped > 0;
        }
    }

    private record GridInsert(int slotIndex, ItemStack stack) {
    }

    private record ExtractedIngredient(ItemStack stack, boolean fromPlayer) {
    }

    private record CraftExecutionResult(
            boolean success,
            boolean storageFull,
            String resultItemId,
            int resultCount,
            Map<String, Integer> consumedCounts) {
        private static CraftExecutionResult failure(boolean storageFull) {
            return new CraftExecutionResult(false, storageFull, "", 0, Map.of());
        }
    }

    private static final class AlwaysValidContainer implements Container {
        private final Container delegate;

        private AlwaysValidContainer(Container delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getContainerSize() {
            return this.delegate.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return this.delegate.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return this.delegate.removeItem(slot, amount);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return this.delegate.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            this.delegate.setItem(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.delegate.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            this.delegate.setChanged();
        }

        @Override
        public boolean stillValid(net.minecraft.world.entity.player.Player player) {
            return true;
        }

        @Override
        public void startOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.startOpen(player);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.player.Player player) {
            this.delegate.stopOpen(player);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return this.delegate.canPlaceItem(slot, stack);
        }

        @Override
        public void clearContent() {
            this.delegate.clearContent();
        }
    }

    private static final class RelaxedContainerLevelAccess implements ContainerLevelAccess {
        private final ContainerLevelAccess delegate;

        private RelaxedContainerLevelAccess(ContainerLevelAccess delegate) {
            this.delegate = delegate == null ? ContainerLevelAccess.NULL : delegate;
        }

        @Override
        public <T> Optional<T> evaluate(BiFunction<Level, BlockPos, T> evaluator) {
            Optional<T> result = this.delegate.evaluate(evaluator);
            if (result.isPresent() && result.get() instanceof Boolean) {
                @SuppressWarnings("unchecked")
                T forcedTrue = (T) Boolean.TRUE;
                return Optional.of(forcedTrue);
            }
            return result;
        }

        @Override
        public void execute(BiConsumer<Level, BlockPos> consumer) {
            this.delegate.execute(consumer);
        }
    }

    static final class PlaceBatchJob {
        private final List<BlockPos> clickedPositions;
        private final Direction face;
        private final byte rotateSteps;
        private final boolean forcePlace;
        private final boolean skipIfOccupied;
        private final String itemId;
        private final double rayOriginX;
        private final double rayOriginY;
        private final double rayOriginZ;
        private final double rayDirX;
        private final double rayDirY;
        private final double rayDirZ;
        private int index;

        private PlaceBatchJob(List<BlockPos> clickedPositions, Direction face, byte rotateSteps, boolean forcePlace,
                boolean skipIfOccupied, String itemId, double rayOriginX, double rayOriginY, double rayOriginZ,
                double rayDirX, double rayDirY, double rayDirZ) {
            this.clickedPositions = clickedPositions;
            this.face = face;
            this.rotateSteps = rotateSteps;
            this.forcePlace = forcePlace;
            this.skipIfOccupied = skipIfOccupied;
            this.itemId = itemId;
            this.rayOriginX = rayOriginX;
            this.rayOriginY = rayOriginY;
            this.rayOriginZ = rayOriginZ;
            this.rayDirX = rayDirX;
            this.rayDirY = rayDirY;
            this.rayDirZ = rayDirZ;
        }

        private boolean hasNext() {
            return this.index < this.clickedPositions.size();
        }

        private BlockPos next() {
            return this.clickedPositions.get(this.index++);
        }

        private Direction face() {
            return this.face;
        }

        private byte rotateSteps() {
            return this.rotateSteps;
        }

        private boolean forcePlace() {
            return this.forcePlace;
        }

        private boolean skipIfOccupied() {
            return this.skipIfOccupied;
        }

        private String itemId() {
            return this.itemId;
        }

        private double rayOriginX() {
            return this.rayOriginX;
        }

        private double rayOriginY() {
            return this.rayOriginY;
        }

        private double rayOriginZ() {
            return this.rayOriginZ;
        }

        private double rayDirX() {
            return this.rayDirX;
        }

        private double rayDirY() {
            return this.rayDirY;
        }

        private double rayDirZ() {
            return this.rayDirZ;
        }
    }

    // Keep the old nested name as the local owner handle while the state fields
    // live in RtsStorageSession. This makes the first split behavior-neutral:
    // call sites still ask RtsStorageManager for a Session, and later PRs can
    // move persistence, linked-handler lookup, or mining state one boundary at a time.
    private static final class Session extends RtsStorageSession {
        private Session() {
            super();
        }
    }
}
