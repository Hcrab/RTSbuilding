package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.placement.PlacementStatePreset;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 1.12 单方块幽灵状态解析器。
 *
 * <p>这里直接走 {@link Block#getStateForPlacement}，不会把楼梯、门、床等方块静默
 * 退化为 defaultState。命中点优先取客户端真实方块射线；射线未命中时在目标方块
 * 中心构造一个与相机朝向一致的等价点击面。</p>
 */
public final class BuildGhostBlockStateResolver {
    private BuildGhostBlockStateResolver() {}

    public static IBlockState resolve(Minecraft minecraft, BlockPos targetPos) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack stack = resolveGhostItemStack(minecraft, controller);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBlock)) return null;
        ItemBlock item = (ItemBlock) stack.getItem();
        IBlockState state;
        if (targetPos == null) {
            state = item.getBlock().getDefaultState();
        } else {
            state = resolveStateWithCamera(minecraft, item, stack, targetPos);
            if (state == null) return null;
        }
        state = applyRotation(state, controller.getPlaceRotateDegrees());
        return PlacementStatePreset.apply(state, controller.getPlacementStatePreset());
    }

    private static ItemStack resolveGhostItemStack(Minecraft minecraft, ClientRtsController controller) {
        ItemStack preview = controller.getSelectedItemPreview();
        if (!preview.isEmpty() && preview.getItem() instanceof ItemBlock) return preview;
        if (minecraft != null && minecraft.player != null) {
            ItemStack hand = minecraft.player.getHeldItemMainhand();
            if (!hand.isEmpty() && hand.getItem() instanceof ItemBlock) return hand;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack resolveSpawnEggStack(Minecraft minecraft) {
        ItemStack preview = ClientRtsController.get().getSelectedItemPreview();
        if (!preview.isEmpty() && preview.getItem() instanceof ItemMonsterPlacer) return preview;
        if (minecraft != null && minecraft.player != null) {
            ItemStack hand = minecraft.player.getHeldItemMainhand();
            if (!hand.isEmpty() && hand.getItem() instanceof ItemMonsterPlacer) return hand;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack resolveEndCrystalStack(Minecraft minecraft) {
        ItemStack preview = ClientRtsController.get().getSelectedItemPreview();
        if (!preview.isEmpty() && preview.getItem() == Items.END_CRYSTAL) return preview;
        if (minecraft != null && minecraft.player != null) {
            ItemStack hand = minecraft.player.getHeldItemMainhand();
            if (!hand.isEmpty() && hand.getItem() == Items.END_CRYSTAL) return hand;
        }
        return ItemStack.EMPTY;
    }

    public static IBlockState resolveStateWithCamera(Minecraft minecraft, ItemBlock item,
            ItemStack stack, BlockPos targetPos) {
        if (minecraft == null || minecraft.player == null || minecraft.world == null) return null;
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null) camera = minecraft.player;
        float partial = minecraft.getRenderPartialTicks();
        Vec3d eye = camera.getPositionEyes(partial);
        Vec3d direction = camera.getLook(partial).normalize();
        RayTraceResult hit = minecraft.world.rayTraceBlocks(eye, eye.add(direction.scale(128.0D)),
                false, false, false);
        EnumFacing face;
        Vec3d location;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && hit.sideHit != null) {
            face = hit.sideHit;
            location = hit.hitVec;
        } else {
            face = EnumFacing.getFacingFromVector((float) -direction.x, (float) -direction.y,
                    (float) -direction.z);
            location = new Vec3d(targetPos).add(new Vec3d(0.5D, 0.5D, 0.5D));
        }
        float hitX = clampHit(location.x - targetPos.getX());
        float hitY = clampHit(location.y - targetPos.getY());
        float hitZ = clampHit(location.z - targetPos.getZ());
        EntityPlayer player = minecraft.player;
        int metadata = item.getMetadata(stack.getMetadata());
        return item.getBlock().getStateForPlacement(minecraft.world, targetPos, face,
                hitX, hitY, hitZ, metadata, player, EnumHand.MAIN_HAND);
    }

    private static float clampHit(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    static float placementYawFromRay(Vec3d direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public static IBlockState applyRotation(IBlockState state, int rotateDegrees) {
        if (state == null) return null;
        int turns = (rotateDegrees / 90) & 3;
        for (int i = 0; i < turns; i++) state = state.withRotation(Rotation.CLOCKWISE_90);
        return state;
    }

    /** 保留旧调用形状，1.12 的旋转不需要 World/BlockPos 参数。 */
    public static IBlockState applyRotation(IBlockState state, int rotateDegrees, World world, BlockPos pos) {
        return applyRotation(state, rotateDegrees);
    }
}
