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
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.RtsSyntheticHandOutputRecovery;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * GUI 绑定：设置绑定、远程打开、目标识别与图标回填。
 *
 * <p>1.12.2 没有 MenuProvider；实际打开必须走服务端的方块右键交互，才能保留模组自己的
 * 权限、事件和容器创建流程。仅在方块没有消费交互时，才对实现 IInteractionObject 的方块实体
 * 使用原版 displayGui 兜底。</p>
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
                        player, pos, safeFace, EnumHand.MAIN_HAND, ItemStack.EMPTY)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        WorldServer level = player.getServerWorld();
        if (!canBindGuiTarget(level, pos)) {
            sendStatus(player, "message.rtsbuilding.gui_binding.no_bindable_gui");
            return RtsStorageBindings.UpdateResult.none();
        }

        IInteractionObject provider = resolveBindableInteractionObject(level, pos);
        String label = provider == null || provider.getDisplayName() == null
                ? "" : provider.getDisplayName().getUnformattedText();
        if (isBlank(label)) {
            label = resolveDisplayName(level, pos);
        }
        String iconItemId = resolveGuiBindingIconItemId(level, pos, safeFace, itemIdHint, label);

        session.uiMemory.setGuiBinding(slot, new GuiBinding(
                pos.toImmutable(), level.provider.getDimension(), label, iconItemId, safeFace));
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
                        player, pos, face, EnumHand.MAIN_HAND, ItemStack.EMPTY)) {
            return RtsStorageBindings.UpdateResult.none();
        }

        WorldServer level = player.getServerWorld();
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
            IInteractionObject provider = resolveBindableInteractionObject(level, pos);
            if (provider != null) {
                player.displayGui(provider);
                if (markOpenedMenu(player, session, before, pos, traceId)) {
                    return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
                }
            }
            sendStatus(player, "message.rtsbuilding.gui_binding.open_failed");
        }
        return RtsStorageBindings.UpdateResult.refreshCurrent(session, false);
    }

    static boolean isValidGuiBindingSlot(int slot) {
        return slot >= 0 && slot < RtsStorageBindings.GUI_BINDING_SLOT_COUNT;
    }

    static boolean canBindGuiTarget(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !level.isBlockLoaded(pos)) {
            return false;
        }
        IBlockState state = level.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.AIR) {
            return false;
        }
        return level.getTileEntity(pos) != null
                || state.getBlock() == Blocks.CRAFTING_TABLE
                || state.getBlock() == Blocks.ANVIL;
    }

    static IInteractionObject resolveBindableInteractionObject(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !level.isBlockLoaded(pos)) {
            return null;
        }
        TileEntity tile = level.getTileEntity(pos);
        return tile instanceof IInteractionObject ? (IInteractionObject) tile : null;
    }

    static String resolveGuiBindingIconItemId(WorldServer level, BlockPos pos, EnumFacing face,
            String itemIdHint, String label) {
        if (level == null || pos == null || !level.isBlockLoaded(pos)) {
            return "";
        }
        ResourceLocation hintKey = resourceLocation(itemIdHint);
        if (hintKey != null && ForgeRegistries.ITEMS.containsKey(hintKey)) {
            return hintKey.toString();
        }

        IBlockState state = level.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.AIR) {
            return "";
        }
        Item item = Item.getItemFromBlock(state.getBlock());
        if (item == null || item == Items.AIR) {
            return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label);
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, label)
                : id.toString();
    }

    static boolean refreshMissingGuiBindingIcons(EntityPlayerMP player, RtsStorageSession session) {
        if (player == null || session == null || player.getServer() == null) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < session.uiMemory.getGuiBindingCount(); i++) {
            GuiBinding binding = session.uiMemory.getGuiBinding(i);
            if (binding == null || binding.pos() == null || !isBlank(binding.itemId())) {
                continue;
            }
            WorldServer bindingLevel = player.getServer().getWorld(binding.dimension());
            if (bindingLevel == null || !bindingLevel.isBlockLoaded(binding.pos())) {
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
        TemporaryContextSwitcher.UseOnOutcome outcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interaction.interactionPos(),
                hit.hitVec,
                remotePovBlockReach,
                () -> InteractionHelper.useItemOnWithMainHand(
                        player, level, ItemStack.EMPTY, hit, forceSecondaryUse));
        return RtsSyntheticHandOutputRecovery.recoverToPlayer(player, outcome);
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
        Vec3d playerPos = player == null ? center : player.getPositionVector();
        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? EnumFacing.EAST : EnumFacing.WEST;
        }
        return dz >= 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
    }

    private static String resolveDisplayName(WorldServer level, BlockPos pos) {
        TileEntity tile = level.getTileEntity(pos);
        if (tile != null && tile.getDisplayName() != null) {
            String tileName = tile.getDisplayName().getUnformattedText();
            if (!isBlank(tileName)) {
                return tileName;
            }
        }
        return level.getBlockState(pos).getBlock().getLocalizedName();
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
        player.sendStatusMessage(new TextComponentTranslation(key), true);
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
