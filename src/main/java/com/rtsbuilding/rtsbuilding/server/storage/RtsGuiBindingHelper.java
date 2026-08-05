package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.storage.model.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumActionResult;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.WorldServer;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;

/**
 * GUI 绑定：设置绑定、远程打开、目标识别与图标回填。
 *
 * <p>1.12.2 没有 MenuProvider；实际打开必须走服务端的方块右键交互，才能保留模组自己的
 * 权限、事件和容器创建流程。1.7.10 没有通用菜单提供者，因此第三方机器必须由它自己的
 * {@code onBlockActivated} 打开，不能强行伪装成原版箱子容器。</p>
 */
final class RtsGuiBindingHelper {

    private RtsGuiBindingHelper() {
    }

    static RtsStorageBindings.UpdateResult setGuiBinding(EntityPlayerMP player, RtsStorageSession session,
            byte slotId, boolean clear, BlockPos pos, EnumFacing face, String itemIdHint) {
        if (player == null || session == null || !isValidGuiBindingSlot(slotId)) {
            return RtsStorageBindings.UpdateResult.none();
        }
        int slot = slotId;
        if (clear) {
            if (session.uiMemory.getGuiBinding(slot) == null) {
                return RtsStorageBindings.UpdateResult.none();
            }
            session.uiMemory.setGuiBinding(slot, null);
            return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
        }

        EnumFacing safeFace = face == null ? EnumFacing.UP : face;
        if (pos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
                || !RtsClaimProtectionService.canInteractBlock(
                        player, pos, safeFace, EnumHand.MAIN_HAND, null)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        WorldServer level = player.getServerForPlayer();
        if (!canBindGuiTarget(level, pos)) {
            sendStatus(player, "message.rtsbuilding.gui_binding.no_bindable_gui");
            return RtsStorageBindings.UpdateResult.none();
        }

        String label = resolveDisplayName(level, pos);
        String iconItemId = resolveGuiBindingIconItemId(level, pos, safeFace, itemIdHint, label);

        session.uiMemory.setGuiBinding(slot, new GuiBinding(
                pos.toImmutable(), level.provider.dimensionId, label, iconItemId, safeFace));
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, true);
    }

    static RtsStorageBindings.UpdateResult openGuiBinding(EntityPlayerMP player, RtsStorageSession session,
            byte slotId, double remotePovBlockReach) {
        return openGuiBinding(player, session, slotId, remotePovBlockReach, 0L);
    }

    static RtsStorageBindings.UpdateResult openGuiBinding(EntityPlayerMP player, RtsStorageSession session,
            byte slotId, double remotePovBlockReach, long traceId) {
        if (player == null || !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_GUI_BINDING)
                || session == null || !RtsCameraManager.isActive(player)
                || !isValidGuiBindingSlot(slotId)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        GuiBinding binding = session.uiMemory.getGuiBinding(slotId);
        if (binding == null || binding.pos() == null) {
            return RtsStorageBindings.UpdateResult.none();
        }
        if (player.dimension != binding.dimension()) {
            sendStatus(player, "message.rtsbuilding.gui_binding.other_dimension");
            return RtsStorageBindings.UpdateResult.none();
        }

        BlockPos pos = binding.pos();
        EnumFacing face = binding.face() == null ? EnumFacing.UP : binding.face();
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)
                || !RtsClaimProtectionService.canInteractBlock(
                        player, pos, face, EnumHand.MAIN_HAND, null)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        WorldServer level = player.getServerForPlayer();
        RtsRemoteMenuService.sendRemoteMenuOpenHint(player, pos, traceId);
        GuiBindingInteraction interaction = createGuiBindingInteraction(player, pos, face);
        Container before = player.openContainer;

        EnumActionResult result = interactWithBoundGui(
                player, level, interaction, false, remotePovBlockReach);
        if (markOpenedMenu(player, session, before, pos, traceId)) {
            return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
        }

        if (result != EnumActionResult.SUCCESS) {
            result = interactWithBoundGui(player, level, interaction, true, remotePovBlockReach);
            if (markOpenedMenu(player, session, before, pos, traceId)) {
                return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
            }
        }

        if (result != EnumActionResult.SUCCESS) {
            sendStatus(player, "message.rtsbuilding.gui_binding.open_failed");
        }
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
    }

    static boolean isValidGuiBindingSlot(int slot) {
        return slot >= 0 && slot < RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
    }

    static boolean canBindGuiTarget(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) {
            return false;
        }
        BlockState state = BlockState.fromWorld(level, pos);
        if (state == null || state.getBlock() == Blocks.air) {
            return false;
        }
        return com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos) != null
                || state.getBlock() == Blocks.crafting_table
                || state.getBlock() == Blocks.anvil;
    }

    static String resolveGuiBindingIconItemId(WorldServer level, BlockPos pos, EnumFacing face,
            String itemIdHint, String label) {
        if (level == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) {
            return "";
        }
        ResourceLocation hintKey = resourceLocation(itemIdHint);
        if (hintKey != null && RtsRegistries.ITEMS.containsKey(hintKey)) {
            return hintKey.toString();
        }

        BlockState state = BlockState.fromWorld(level, pos);
        if (state == null || state.getBlock() == Blocks.air) {
            return "";
        }
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == null || item == null) {
            return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label);
        }
        ResourceLocation id = RtsRegistries.ITEMS.getKey(item);
        return id == null ? RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label)
                : id.toString();
    }

    static boolean refreshMissingGuiBindingIcons(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null || com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player) == null) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < session.uiMemory.getGuiBindingCount(); i++) {
            GuiBinding binding = session.uiMemory.getGuiBinding(i);
            if (binding == null || binding.pos() == null || !isBlank(binding.itemId())) {
                continue;
            }
            WorldServer bindingLevel = com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getWorld(com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player), binding.dimension());
            if (bindingLevel == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(bindingLevel, binding.pos())) {
                continue;
            }

            String resolved = resolveGuiBindingIconItemId(
                    bindingLevel, binding.pos(), binding.face(), "", binding.label());
            if (isBlank(resolved)) {
                continue;
            }
            session.uiMemory.setGuiBinding(i, new GuiBinding(
                    binding.pos(), binding.dimension(), binding.label(), resolved, binding.face()));
            changed = true;
        }
        return changed;
    }

    /** 所有临时玩家状态都由 TemporaryContextSwitcher 的 finally 路径恢复。 */
    private static EnumActionResult interactWithBoundGui(EntityPlayerMP player, WorldServer level,
            GuiBindingInteraction interaction, boolean forceSecondaryUse, double remotePovBlockReach) {
        RayTraceResult hit = interaction.hit();
        float hitX = (float) (hit.hitVec.x - hit.getBlockPos().getX());
        float hitY = (float) (hit.hitVec.y - hit.getBlockPos().getY());
        float hitZ = (float) (hit.hitVec.z - hit.getBlockPos().getZ());
        return TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interaction.interactionPos(),
                hit.hitVec,
                remotePovBlockReach,
                () -> TemporaryContextSwitcher.withTemporaryMainHandItem(
                        player,
                        null,
                        () -> TemporaryContextSwitcher.withTemporaryShiftKey(
                                player,
                                forceSecondaryUse,
                                () -> EnumActionResult.fromLegacyBoolean(
                                        player.theItemInWorldManager.activateBlockOrUseItem(
                                                player, level, null,
                                                hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ(),
                                                hit.sideHit.getIndex(), hitX, hitY, hitZ)))));
    }

    private static boolean markOpenedMenu(EntityPlayerMP player, RtsStorageSession session,
            Container before, BlockPos pos) {
        return markOpenedMenu(player, session, before, pos, 0L);
    }

    private static boolean markOpenedMenu(EntityPlayerMP player, RtsStorageSession session,
            Container before, BlockPos pos, long traceId) {
        Container opened = player.openContainer;
        if (opened == null || opened == before) {
            return false;
        }
        RtsRemoteMenuService.markRemoteMenuOpen(player, session, opened, pos, traceId);
        return true;
    }

    private static GuiBindingInteraction createGuiBindingInteraction(
            EntityPlayerMP player, BlockPos pos, EnumFacing preferredFace) {
        EnumFacing face = preferredFace == null ? resolveGuiBindingFace(player, pos) : preferredFace;
        Vec3d faceCenter = center(pos).add(
                face.getXOffset() * 0.498D,
                face.getYOffset() * 0.498D,
                face.getZOffset() * 0.498D);
        Vec3d eyePos = faceCenter.add(
                face.getXOffset() * 2.2D,
                face.getYOffset() * 2.2D,
                face.getZOffset() * 2.2D);
        double eyeHeight = player == null ? 1.62D : player.getEyeHeight();
        Vec3d interactionPos = new Vec3d(eyePos.x, eyePos.y - eyeHeight, eyePos.z);
        return new GuiBindingInteraction(
                new RayTraceResult(faceCenter, face, pos), interactionPos);
    }

    private static EnumFacing resolveGuiBindingFace(EntityPlayerMP player, BlockPos pos) {
        Vec3d center = center(pos);
        Vec3d playerPos = player == null ? center : com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.position(player);
        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private static String resolveDisplayName(WorldServer level, BlockPos pos) {
        TileEntity tile = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
        if (tile instanceof IInventory) {
            String tileName = ((IInventory) tile).getInventoryName();
            if (!isBlank(tileName)) {
                return tileName;
            }
        }
        return BlockState.fromWorld(level, pos).getBlock().getLocalizedName();
    }

    private static Vec3d center(BlockPos pos) {
        return new Vec3d(pos).add(0.5D, 0.5D, 0.5D);
    }

    private static ResourceLocation resourceLocation(String value) {
        try {
            return isBlank(value) ? null : new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void sendStatus(EntityPlayerMP player, String key) {
        com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(player, new ChatComponentTranslation(key), true);
    }

    private static final class GuiBindingInteraction {
        private final RayTraceResult hit;
        private final Vec3d interactionPos;

        private GuiBindingInteraction(RayTraceResult hit, Vec3d interactionPos) {
            this.hit = hit;
            this.interactionPos = interactionPos;
        }

        private RayTraceResult hit() {
            return hit;
        }

        private Vec3d interactionPos() {
            return interactionPos;
        }
    }
}
