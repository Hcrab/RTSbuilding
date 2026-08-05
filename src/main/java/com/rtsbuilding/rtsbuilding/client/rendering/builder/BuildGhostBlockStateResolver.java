package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.placement.PlacementStatePreset;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import com.rtsbuilding.rtsbuilding.platform.block.Rotation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
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

    public static BlockState resolve(Minecraft minecraft, BlockPos targetPos) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack stack = resolveGhostItemStack(minecraft, controller);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) || !(stack.getItem() instanceof ItemBlock)) return null;
        ItemBlock item = (ItemBlock) stack.getItem();
        BlockState state;
        if (targetPos == null) {
            state = BlockState.defaultState(net.minecraft.block.Block.getBlockFromItem(item));
        } else {
            state = resolveStateWithCamera(minecraft, item, stack, targetPos);
            if (state == null) return null;
        }
        state = applyRotation(state, controller.getPlaceRotateDegrees());
        return PlacementStatePreset.apply(state, controller.getPlacementStatePreset());
    }

    private static ItemStack resolveGhostItemStack(Minecraft minecraft, ClientRtsController controller) {
        ItemStack preview = controller.getSelectedItemPreview();
        if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview) && preview.getItem() instanceof ItemBlock) return preview;
        if (minecraft != null && minecraft.thePlayer != null) {
            ItemStack hand = minecraft.thePlayer.getHeldItem();
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(hand) && hand.getItem() instanceof ItemBlock) return hand;
        }
        return null;
    }

    public static ItemStack resolveSpawnEggStack(Minecraft minecraft) {
        ItemStack preview = ClientRtsController.get().getSelectedItemPreview();
        if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview) && preview.getItem() instanceof ItemMonsterPlacer) return preview;
        if (minecraft != null && minecraft.thePlayer != null) {
            ItemStack hand = minecraft.thePlayer.getHeldItem();
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(hand) && hand.getItem() instanceof ItemMonsterPlacer) return hand;
        }
        return null;
    }

    public static ItemStack resolveEndCrystalStack(Minecraft minecraft) {
        // 末影水晶物品在 1.9 才加入；1.7.10 只有实体，不能从玩家选择物解析预览。
        return null;
    }

    public static BlockState resolveStateWithCamera(Minecraft minecraft, ItemBlock item,
            ItemStack stack, BlockPos targetPos) {
        if (minecraft == null || minecraft.thePlayer == null || minecraft.theWorld == null) return null;
        Entity camera = minecraft.renderViewEntity;
        if (camera == null) camera = minecraft.thePlayer;
        float partial = com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.renderPartialTicks(minecraft);
        Vec3d eye = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.positionEyes(camera, partial);
        Vec3d direction = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                .look(camera, partial).normalize();
        RayTraceResult hit = RayTraceResult.trace(
                minecraft.theWorld, eye, eye.add(direction.scale(128.0D)), false, false, false);
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
        EntityPlayer player = minecraft.thePlayer;
        return BlockState.forPlacement(item, stack, minecraft.theWorld, targetPos, face,
                hitX, hitY, hitZ);
    }

    private static float clampHit(double value) {
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    static float placementYawFromRay(Vec3d direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
    }

    public static BlockState applyRotation(BlockState state, int rotateDegrees) {
        if (state == null) return null;
        int turns = (rotateDegrees / 90) & 3;
        for (int i = 0; i < turns; i++) state = state.withRotation(Rotation.CLOCKWISE_90);
        return state;
    }

    /** 保留旧调用形状，1.12 的旋转不需要 World/BlockPos 参数。 */
    public static BlockState applyRotation(BlockState state, int rotateDegrees, World world, BlockPos pos) {
        return applyRotation(state, rotateDegrees);
    }
}
