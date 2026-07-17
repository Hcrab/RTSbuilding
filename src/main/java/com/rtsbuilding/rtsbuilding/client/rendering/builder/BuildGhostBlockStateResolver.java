package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * BlockState resolver for single-block ghost previews.
 * <p>
 * Resolves the correct BlockState for ghost rendering by simulating
 * the server-side {@link net.minecraft.world.level.block.Block#getStateForPlacement(BlockPlaceContext)}
 * logic based on the player's held item and camera direction.
 * <p>
 * Supports rotation and multi-block block orientation (doors, beds, etc.).
 */
public final class BuildGhostBlockStateResolver {

    private BuildGhostBlockStateResolver() {
    }

    /**
     * Resolves the expected BlockState for a placement position.
     *
     * @param minecraft Minecraft client instance
     * @param targetPos Target placement position, may be null
     * @return Resolved BlockState, or null if resolution fails
     */
    public static BlockState resolve(Minecraft minecraft, BlockPos targetPos) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemStack = resolveGhostItemStack(minecraft, controller);
        if (itemStack == null || !(itemStack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        if (targetPos == null) {
            return blockItem.getBlock().defaultBlockState();
        }
        BlockState state = resolveStateWithCamera(minecraft, blockItem, itemStack, targetPos);
        if (state == null) return null;
        int rotateDegrees = controller.getPlaceRotateDegrees();
        if (rotateDegrees != 0) {
            state = applyRotation(state, rotateDegrees, minecraft.level, targetPos);
        }
        return state;
    }

    /**
     * Determines the item source for ghost rendering.
     */
    private static ItemStack resolveGhostItemStack(Minecraft minecraft, ClientRtsController controller) {
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof BlockItem) {
            return itemPreview;
        }
        if (minecraft != null && minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem) {
                return mainHand;
            }
        }
        return null;
    }

    /**
     * Resolves the currently held spawn egg item stack.
     *
     * @param minecraft Minecraft client instance
     * @return The spawn egg ItemStack, or {@link ItemStack#EMPTY} if not holding one
     */
    public static ItemStack resolveSpawnEggStack(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof SpawnEggItem) {
            return itemPreview;
        }
        if (minecraft != null && minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof SpawnEggItem) {
                return mainHand;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolves the currently held end crystal item stack.
     *
     * @param minecraft Minecraft client instance
     * @return The end crystal ItemStack, or {@link ItemStack#EMPTY} if not holding one
     */
    public static ItemStack resolveEndCrystalStack(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof EndCrystalItem) {
            return itemPreview;
        }
        if (minecraft != null && minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof EndCrystalItem) {
                return mainHand;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Simulates BlockPlaceContext using the client camera direction, matching server-side placement logic.
     */
    public static BlockState resolveStateWithCamera(Minecraft minecraft, BlockItem blockItem,
            ItemStack stack, BlockPos targetPos) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return null;

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);

        double dx = targetCenter.x - cameraPos.x;
        double dy = targetCenter.y - cameraPos.y;
        double dz = targetCenter.z - cameraPos.z;
        float yawDeg = (float) Math.toDegrees(Mth.atan2(-dx, dz));

        Vec3 viewDir = RaycastHelper.computeCursorRayDirection(minecraft);
        Vec3 rayEnd = cameraPos.add(viewDir.scale(128.0D));
        BlockHitResult actualHit = RaycastHelper.raycastBlockFromCursor(minecraft, cameraPos, rayEnd, false);

        Direction clickedFace;
        BlockPos adjacentPos;
        Vec3 hitLocation;

        if (actualHit != null) {
            clickedFace = actualHit.getDirection();
            adjacentPos = actualHit.getBlockPos();
            hitLocation = actualHit.getLocation();
        } else {
            clickedFace = Direction.getApproximateNearest(-viewDir.x, -viewDir.y, -viewDir.z);
            adjacentPos = targetPos.relative(clickedFace.getOpposite());
            hitLocation = computeFallbackHitLocation(clickedFace, adjacentPos, targetCenter, cameraPos, viewDir);
        }

        BlockPlaceContext context = new BlockPlaceContext(
                minecraft.level, minecraft.player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(hitLocation, clickedFace, adjacentPos, false)) {
            @Override
            public @NotNull Direction getHorizontalDirection() { return Direction.fromYRot(yawDeg); }
            @Override
            public @NotNull Direction getNearestLookingDirection() { return clickedFace; }
            @Override
            public @NotNull Direction getNearestLookingVerticalDirection() { return Direction.getApproximateNearest(0.0, dy, 0.0); }
            @Override
            public float getRotation() { return yawDeg; }
        };
        return blockItem.getBlock().getStateForPlacement(context);
    }

    /**
     * Computes the fallback hit location via ray-plane intersection when the ray misses all blocks.
     */
    private static Vec3 computeFallbackHitLocation(Direction face, BlockPos adjacentPos,
            Vec3 targetCenter, Vec3 cameraPos, Vec3 viewDir) {
        return switch (face) {
            case DOWN -> computePlaneHit(viewDir, cameraPos, adjacentPos.getY(), targetCenter.x, targetCenter.z, true, false);
            case UP -> computePlaneHit(viewDir, cameraPos, adjacentPos.getY() + 1.0, targetCenter.x, targetCenter.z, true, false);
            case NORTH -> computePlaneHit(viewDir, cameraPos, adjacentPos.getZ(), targetCenter.x, targetCenter.y, false, true);
            case SOUTH -> computePlaneHit(viewDir, cameraPos, adjacentPos.getZ() + 1.0, targetCenter.x, targetCenter.y, false, true);
            case WEST -> computePlaneHit(viewDir, cameraPos, adjacentPos.getX(), targetCenter.y, targetCenter.z, false, false);
            case EAST -> computePlaneHit(viewDir, cameraPos, adjacentPos.getX() + 1.0, targetCenter.y, targetCenter.z, false, false);
            default -> targetCenter;
        };
    }

    private static Vec3 computePlaneHit(Vec3 viewDir, Vec3 cameraPos, double planeCoord,
            double coord1, double coord2, boolean isVertical, boolean isZAxis) {
        double dirComponent = isVertical ? viewDir.y : (isZAxis ? viewDir.z : viewDir.x);
        if (dirComponent == 0.0) {
            return isVertical ? new Vec3(coord1, planeCoord, coord2)
                    : (isZAxis ? new Vec3(coord1, coord2, planeCoord) : new Vec3(planeCoord, coord1, coord2));
        }
        double t = (planeCoord - (isVertical ? cameraPos.y : (isZAxis ? cameraPos.z : cameraPos.x))) / dirComponent;
        double x = isVertical ? cameraPos.x + t * viewDir.x : (isZAxis ? coord1 : planeCoord);
        double y = isVertical ? planeCoord : (isZAxis ? coord2 : cameraPos.y + t * viewDir.y);
        double z = isVertical ? cameraPos.z + t * viewDir.z : (isZAxis ? planeCoord : coord2);
        return new Vec3(x, y, z);
    }

    /**
     * Applies 90° stepping rotation to a BlockState.
     */
    public static BlockState applyRotation(BlockState state, int rotateDegrees, LevelAccessor level, BlockPos pos) {
        int turns = (rotateDegrees / 90) & 3;
        if (turns == 0) return state;
        BlockState rotated = state;
        for (int i = 0; i < turns; i++) {
            rotated = rotated.rotate(level, pos, Rotation.CLOCKWISE_90);
        }
        return rotated;
    }
}
