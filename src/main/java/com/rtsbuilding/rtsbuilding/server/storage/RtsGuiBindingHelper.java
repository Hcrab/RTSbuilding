package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsMiningService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
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
 * GUI 绑定：设置绑定、远程打开、菜单提供者解析、图标解析。
 * <p>包私有——仅供 {@link RtsStorageBindings} 内部委托。
 */
final class RtsGuiBindingHelper {

    private RtsGuiBindingHelper() {
    }

    // ======================================================================
    //  设置绑定
    // ======================================================================

    static RtsStorageBindings.UpdateResult setGuiBinding(ServerPlayer player, RtsStorageSession session,
            byte slotId, boolean clear, BlockPos pos, Direction face, String itemIdHint) {
        if (player == null || session == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        if (clear) {
            if (session.guiBindings[slot] == null) {
                return RtsStorageBindings.UpdateResult.none();
            }
            session.guiBindings[slot] = null;
            return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
        }

        if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        ServerLevel level = player.serverLevel();
        MenuProvider provider = resolveBindableMenuProvider(level, pos);
        if (!canBindGuiTarget(level, pos)) {
            player.displayClientMessage(Component.literal("Target has no bindable GUI."), true);
            return RtsStorageBindings.UpdateResult.none();
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
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
    }

    // ======================================================================
    //  远程打开绑定
    // ======================================================================

    static RtsStorageBindings.UpdateResult openGuiBinding(ServerPlayer player, RtsStorageSession session,
            byte slotId, double remotePovBlockReach) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)) {
            return RtsStorageBindings.UpdateResult.none();
        }
        if (session == null || !RtsCameraManager.isActive(player)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        int slot = slotId;
        if (!isValidGuiBindingSlot(slot)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        GuiBinding binding = session.guiBindings[slot];
        if (binding == null || binding.pos() == null || binding.dimension() == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        if (!player.serverLevel().dimension().equals(binding.dimension())) {
            player.displayClientMessage(Component.literal("Bound GUI is in another dimension."), true);
            return RtsStorageBindings.UpdateResult.none();
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, binding.pos())) {
            return RtsStorageBindings.UpdateResult.none();
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = binding.pos();
        RtsRemoteMenuService.sendRemoteMenuOpenHint(player, pos);
        GuiBindingInteraction interaction = createGuiBindingInteraction(player, pos, binding.face());
        BlockHitResult hit = interaction.hit();
        Vec3 hitLocation = hit.getLocation();
        Vec3 interactionPos = interaction.interactionPos();

        AbstractContainerMenu menuBeforeInteract = player.containerMenu;
        InteractionResult interactResult = interactWithBoundGui(player, level, interactionPos, hitLocation, hit, false, remotePovBlockReach);
        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterInteract, pos);
            return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
        }

        if (!interactResult.consumesAction()) {
            interactResult = interactWithBoundGui(player, level, interactionPos, hitLocation, hit, true, remotePovBlockReach);
            AbstractContainerMenu menuAfterSecondaryInteract = player.containerMenu;
            if (menuAfterSecondaryInteract != menuBeforeInteract) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterSecondaryInteract, pos);
                return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
            }
        }

        if (!interactResult.consumesAction()) {
            MenuProvider provider = resolveBindableMenuProvider(level, pos);
            if (provider == null) {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
                return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
            }
            player.openMenu(provider);
            if (player.containerMenu != null && player.containerMenu != menuBeforeInteract) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, player.containerMenu, pos);
            } else {
                player.displayClientMessage(Component.literal("Bound target did not open a GUI."), true);
            }
        }
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
    }

    // ======================================================================
    //  查询
    // ======================================================================

    static boolean isValidGuiBindingSlot(int slot) {
        return slot >= 0 && slot < RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
    }

    static boolean canBindGuiTarget(ServerLevel level, BlockPos pos) {
        if (resolveBindableMenuProvider(level, pos) != null) {
            return true;
        }
        return level != null && pos != null && level.hasChunkAt(pos) && level.getBlockEntity(pos) != null;
    }

    static MenuProvider resolveBindableMenuProvider(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider != null) {
            return provider;
        }
        return level.getBlockEntity(pos) instanceof MenuProvider menuProvider ? menuProvider : null;
    }

    static String resolveGuiBindingIconItemId(ServerLevel level, BlockPos pos, Direction face, String itemIdHint, String label) {
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
            return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label);
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            return id.toString();
        }
        return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label);
    }

    // ======================================================================
    //  图标回填
    // ======================================================================

    static boolean refreshMissingGuiBindingIcons(ServerPlayer player, RtsStorageSession session) {
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

    // ======================================================================
    //  GUI 交互辅助
    // ======================================================================

    private static InteractionResult interactWithBoundGui(ServerPlayer player, ServerLevel level, Vec3 interactionPos,
            Vec3 hitLocation, BlockHitResult hit, boolean forceSecondaryUse, double remotePovBlockReach) {
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hitLocation,
                remotePovBlockReach,
                () -> RtsMiningService.withTemporaryMainHandItem(
                        player,
                        ItemStack.EMPTY,
                        () -> TemporaryContextSwitcher.withTemporaryShiftKey(player, forceSecondaryUse, () -> player.gameMode.useItemOn(
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

    private record GuiBindingInteraction(BlockHitResult hit, Vec3 interactionPos) {
    }
}

