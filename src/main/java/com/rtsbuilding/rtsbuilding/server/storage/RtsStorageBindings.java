package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Owns the player binding edge of an RTS storage session.
 *
 * <p>This helper decides which storage refs, external GUI targets, quick-slot
 * item ids, and builder mode values are stored on the player's RTS session. It
 * deliberately does not read or build the full storage page, aggregate storage
 * contents, move items, transfer fluids, craft, mine, place blocks, or persist
 * NBT. {@link RtsStorageManager} keeps packet/page refresh and persistence
 * wrappers so existing network handlers do not need to know about this split.
 *
 * <p>Linked storage capability probing and access checks still come from
 * {@link RtsLinkedStorageResolver}; this class only applies the resulting
 * binding state to the session. Remote GUI opening still reuses manager-owned
 * temporary interaction helpers because those are shared with placement and
 * direct interact flows.
 */
public final class RtsStorageBindings {
    private RtsStorageBindings() {
    }

    /**
     * Stores the requested builder mode and reports whether leaving funnel mode
     * requires the manager to flush the funnel buffer and refresh the page.
     */
    public static boolean setMode(RtsStorageSession session, BuilderMode mode) {
        if (session == null) {
            return false;
        }
        session.mode = mode;
        return mode != BuilderMode.FUNNEL && session.funnelEnabled;
    }

    /**
     * Toggles or retargets a linked storage ref while preserving the existing
     * extract-only mode behavior. A target with no item or fluid endpoint still
     * asks the UI to return to page zero without saving session data.
     */
    public static UpdateResult linkStorage(ServerPlayer player, RtsStorageSession session, BlockPos pos, byte linkMode) {
        if (player == null || session == null || pos == null) {
            return UpdateResult.none();
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        Object itemHandler = RtsLinkedCapabilities.findLinkedItemHandler(player, pos);
        Object fluidHandler = RtsLinkedCapabilities.findFluidHandler(player, pos);
        if (itemHandler == null && fluidHandler == null) {
            return UpdateResult.refreshFirst(false);
        }

        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        if (session.linkedStorages.contains(ref)) {
            byte existingMode = session.linkedModes.getOrDefault(ref, RtsStorageManager.LINK_MODE_BIDIRECTIONAL);
            if (existingMode == normalizedMode) {
                session.linkedStorages.remove(ref);
                session.linkedNames.remove(ref);
                session.linkedModes.remove(ref);
                session.linkedPriorities.remove(ref);
            } else {
                session.linkedModes.put(ref, normalizedMode);
                session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
            }
        } else {
            session.linkedStorages.add(ref);
            session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
            session.linkedModes.put(ref, normalizedMode);
            session.linkedPriorities.put(ref, 0);
        }
        return UpdateResult.refreshFirst(true);
    }

    /**
     * Updates settings for an existing linked storage row. This is intentionally
     * not a link/create operation: the detail panel can edit mode and AE-style
     * priority, but the server still requires the ref to already belong to the
     * player's session.
     */
    public static UpdateResult updateLinkedStorageSettings(ServerPlayer player, RtsStorageSession session,
            BlockPos pos, byte linkMode, int priority) {
        if (player == null || session == null || pos == null) {
            return UpdateResult.none();
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        LinkedStorageRef ref = new LinkedStorageRef(player.serverLevel().dimension(), pos.immutable());
        if (!session.linkedStorages.contains(ref)) {
            return UpdateResult.none();
        }
        byte normalizedMode = RtsLinkedStorageResolver.sanitizeLinkMode(linkMode);
        int normalizedPriority = RtsStorageManager.sanitizeLinkedStoragePriority(priority);
        byte oldMode = session.linkedModes.getOrDefault(ref, RtsStorageManager.LINK_MODE_BIDIRECTIONAL);
        int oldPriority = session.linkedPriorities.getOrDefault(ref, 0);
        if (oldMode == normalizedMode && oldPriority == normalizedPriority) {
            return UpdateResult.none();
        }
        session.linkedModes.put(ref, normalizedMode);
        session.linkedPriorities.put(ref, normalizedPriority);
        session.linkedNames.put(ref, RtsLinkedStorageResolver.resolveDisplayName(player.serverLevel(), ref.pos()));
        return UpdateResult.refreshCurrent(session, true);
    }

    /**
     * Updates one fixed quick-slot cell. Blank/null item ids clear the slot;
     * nonblank ids must parse to a registered item before the session changes.
     */
    public static UpdateResult setQuickSlot(RtsStorageSession session, byte slotId, String itemId) {
        if (session == null) {
            return UpdateResult.none();
        }
        int slot = slotId;
        if (!isValidQuickSlotIndex(slot)) {
            return UpdateResult.none();
        }

        String normalized = "";
        if (itemId != null && !itemId.isBlank()) {
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                return UpdateResult.none();
            }
            normalized = itemId;
        }

        if (normalized.equals(session.quickSlotItemIds[slot])) {
            return UpdateResult.none();
        }

        session.quickSlotItemIds[slot] = normalized;
        return UpdateResult.refreshCurrent(session, true);
    }

    /**
     * Binds or clears one external GUI slot. Clear operations do not require the
     * remote-GUI feature gate; new bindings keep the previous block-label,
     * AE2 icon fallback, normal container, and block-entity target behavior.
     */
    public static UpdateResult setGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, boolean clear,
            BlockPos pos, Direction face, String itemIdHint) {
        if (player == null || session == null) {
            return UpdateResult.none();
        }
        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return UpdateResult.none();
        }

        if (clear) {
            if (session.guiBindings[slot] == null) {
                return UpdateResult.none();
            }
            session.guiBindings[slot] = null;
            return UpdateResult.refreshCurrent(session, true);
        }

        if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return UpdateResult.none();
        }

        ServerLevel level = player.serverLevel();
        MenuProvider provider = resolveBindableMenuProvider(level, pos);
        if (!canBindGuiTarget(level, pos)) {
            player.displayClientMessage(Component.literal("Target has no bindable GUI."), true);
            return UpdateResult.none();
        }

        String label = provider == null || provider.getDisplayName() == null ? "" : provider.getDisplayName().getString();
        if (label.isBlank()) {
            label = RtsLinkedStorageResolver.resolveDisplayName(level, pos);
        }
        String iconItemId = resolveGuiBindingIconItemId(level, pos, face, itemIdHint, label);

        session.guiBindings[slot] = new GuiBinding(
                pos.immutable(),
                level.dimension(),
                label,
                iconItemId,
                face);
        return UpdateResult.refreshCurrent(session, true);
    }

    /**
     * Reopens a saved GUI binding from RTS camera mode. The session slot,
     * dimension, access, direct block interaction, secondary-use retry, and
     * menu-provider fallback order must stay aligned with the old manager path.
     */
    public static UpdateResult openGuiBinding(ServerPlayer player, RtsStorageSession session, byte slotId, double remotePovBlockReach) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) {
            return UpdateResult.none();
        }
        if (session == null || !RtsCameraManager.isActive(player)) {
            return UpdateResult.none();
        }

        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return UpdateResult.none();
        }

        GuiBinding binding = session.guiBindings[slot];
        if (binding == null || binding.pos() == null || binding.dimension() == null) {
            return UpdateResult.none();
        }
        if (!player.serverLevel().dimension().equals(binding.dimension())) {
            player.displayClientMessage(Component.literal("Bound GUI is in another dimension."), true);
            return UpdateResult.none();
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, binding.pos())) {
            return UpdateResult.none();
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = binding.pos();
        RtsStorageManager.sendRemoteMenuOpenHint(player, pos);
        GuiBindingInteraction interaction = createGuiBindingInteraction(player, pos, binding.face());
        BlockHitResult hit = interaction.hit();
        Vec3 hitLocation = hit.getLocation();
        Vec3 interactionPos = interaction.interactionPos();

        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        InteractionResult interactResult = interactWithBoundGui(player, level, interactionPos, hitLocation, hit, false, remotePovBlockReach);
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            RtsStorageManager.markRemoteMenuOpen(player, session, menuAfterInteract, pos);
            return UpdateResult.refreshCurrent(session, false);
        }

        if (!interactResult.consumesAction()) {
            interactResult = interactWithBoundGui(player, level, interactionPos, hitLocation, hit, true, remotePovBlockReach);
            AbstractContainerMenu menuAfterSecondaryInteract = player.containerMenu;
            if (menuAfterSecondaryInteract != menuBeforeInteract) {
                RtsStorageManager.markRemoteMenuOpen(player, session, menuAfterSecondaryInteract, pos);
                return UpdateResult.refreshCurrent(session, false);
            }
        }

        if (!interactResult.consumesAction()) {
            MenuProvider provider = resolveBindableMenuProvider(level, pos);
            if (provider == null) {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
                return UpdateResult.refreshCurrent(session, false);
            }
            player.openMenu(provider);
            if (player.containerMenu != null && player.containerMenu != menuBeforeInteract) {
                RtsStorageManager.markRemoteMenuOpen(player, session, player.containerMenu, pos);
            } else {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
            }
        }
        return UpdateResult.refreshCurrent(session, false);
    }

    public static boolean isValidQuickSlotIndex(int slot) {
        return slot >= 0 && slot < RtsStorageManager.QUICK_SLOT_COUNT;
    }

    public static boolean isValidGuiBindingSlot(int slot) {
        return slot >= 0 && slot < RtsStorageManager.GUI_BINDING_SLOT_COUNT;
    }

    public static boolean canBindGuiTarget(ServerLevel level, BlockPos pos) {
        if (resolveBindableMenuProvider(level, pos) != null) {
            return true;
        }
        return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) != null;
    }

    public static MenuProvider resolveBindableMenuProvider(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider != null) {
            return provider;
        }
        return level.getBlockEntity(pos) instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    public static String resolveGuiBindingIconItemId(ServerLevel level, BlockPos pos, Direction face, String itemIdHint, String label) {
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

    /**
     * Backfills older GUI bindings that predate item-id icons. Only empty icon
     * cells are changed, and the caller decides when to persist the session.
     */
    public static boolean refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || player.server == null) {
            return false;
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
        return changed;
    }

    private static InteractionResult interactWithBoundGui(ServerPlayer player, ServerLevel level, Vec3 interactionPos,
            Vec3 hitLocation, BlockHitResult hit, boolean forceSecondaryUse, double remotePovBlockReach) {
        return RtsStorageManager.withTemporaryUseItemContext(
                player,
                interactionPos,
                hitLocation,
                remotePovBlockReach,
                () -> RtsStorageManager.withTemporaryMainHandItem(
                        player,
                        ItemStack.EMPTY,
                        () -> RtsStorageManager.withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
                                player,
                                level,
                                ItemStack.EMPTY,
                                InteractionHand.MAIN_HAND,
                                hit))));
    }

    private static GuiBindingInteraction createGuiBindingInteraction(ServerPlayer player, BlockPos pos, Direction preferredFace) {
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
        return new GuiBindingInteraction(new BlockHitResult(faceCenter, face, pos, false), interactionPos);
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

    public record UpdateResult(boolean saveSession, boolean refreshPage, int page) {
        private static final UpdateResult NONE = new UpdateResult(false, false, 0);

        static UpdateResult none() {
            return NONE;
        }

        static UpdateResult refreshFirst(boolean saveSession) {
            return new UpdateResult(saveSession, true, 0);
        }

        static UpdateResult refreshCurrent(RtsStorageSession session, boolean saveSession) {
            return new UpdateResult(saveSession, true, session == null ? 0 : session.page);
        }
    }

    private record GuiBindingInteraction(BlockHitResult hit, Vec3 interactionPos) {
    }
}
