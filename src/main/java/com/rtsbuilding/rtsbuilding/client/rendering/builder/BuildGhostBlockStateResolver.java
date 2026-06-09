package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves the block/entity preview source for single-block build ghosts.
 * This mirrors the server placement context closely enough for directional
 * blocks and rotation previews without making the client authoritative.
 */
public final class BuildGhostBlockStateResolver {
    private BuildGhostBlockStateResolver() {
    }

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
        if (state == null) {
            return null;
        }
        int rotateDegrees = controller.getPlaceRotateDegrees();
        return rotateDegrees == 0 ? state : applyRotation(state, rotateDegrees);
    }

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
        return ItemStack.EMPTY;
    }

    private static BlockState resolveStateWithCamera(Minecraft minecraft, BlockItem blockItem,
            ItemStack stack, BlockPos targetPos) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
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
            clickedFace = Direction.getNearest(-viewDir.x, -viewDir.y, -viewDir.z);
            adjacentPos = targetPos.relative(clickedFace.getOpposite());
            hitLocation = computeFallbackHitLocation(clickedFace, adjacentPos, targetCenter, cameraPos, viewDir);
        }

        BlockPlaceContext context = new BlockPlaceContext(
                minecraft.level, minecraft.player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(hitLocation, clickedFace, adjacentPos, false)) {
            @Override
            public Direction getHorizontalDirection() {
                return Direction.fromYRot(yawDeg);
            }

            @Override
            public Direction getNearestLookingDirection() {
                return clickedFace;
            }

            @Override
            public Direction getNearestLookingVerticalDirection() {
                return Direction.getNearest(0.0D, dy, 0.0D);
            }

            @Override
            public float getRotation() {
                return yawDeg;
            }
        };
        return blockItem.getBlock().getStateForPlacement(context);
    }

    private static Vec3 computeFallbackHitLocation(Direction face, BlockPos adjacentPos,
            Vec3 targetCenter, Vec3 cameraPos, Vec3 viewDir) {
        return switch (face) {
            case DOWN -> computePlaneHit(viewDir, cameraPos, adjacentPos.getY(), targetCenter.x, targetCenter.z, true, false);
            case UP -> computePlaneHit(viewDir, cameraPos, adjacentPos.getY() + 1.0D, targetCenter.x, targetCenter.z, true, false);
            case NORTH -> computePlaneHit(viewDir, cameraPos, adjacentPos.getZ(), targetCenter.x, targetCenter.y, false, true);
            case SOUTH -> computePlaneHit(viewDir, cameraPos, adjacentPos.getZ() + 1.0D, targetCenter.x, targetCenter.y, false, true);
            case WEST -> computePlaneHit(viewDir, cameraPos, adjacentPos.getX(), targetCenter.y, targetCenter.z, false, false);
            case EAST -> computePlaneHit(viewDir, cameraPos, adjacentPos.getX() + 1.0D, targetCenter.y, targetCenter.z, false, false);
        };
    }

    private static Vec3 computePlaneHit(Vec3 viewDir, Vec3 cameraPos, double planeCoord,
            double coord1, double coord2, boolean isVertical, boolean isZAxis) {
        double dirComponent = isVertical ? viewDir.y : (isZAxis ? viewDir.z : viewDir.x);
        if (dirComponent == 0.0D) {
            return isVertical ? new Vec3(coord1, planeCoord, coord2)
                    : (isZAxis ? new Vec3(coord1, coord2, planeCoord) : new Vec3(planeCoord, coord1, coord2));
        }
        double origin = isVertical ? cameraPos.y : (isZAxis ? cameraPos.z : cameraPos.x);
        double t = (planeCoord - origin) / dirComponent;
        double x = isVertical ? cameraPos.x + t * viewDir.x : (isZAxis ? coord1 : planeCoord);
        double y = isVertical ? planeCoord : (isZAxis ? coord2 : cameraPos.y + t * viewDir.y);
        double z = isVertical ? cameraPos.z + t * viewDir.z : (isZAxis ? planeCoord : coord2);
        return new Vec3(x, y, z);
    }

    private static BlockState applyRotation(BlockState state, int rotateDegrees) {
        int turns = (rotateDegrees / 90) & 3;
        BlockState rotated = state;
        for (int i = 0; i < turns; i++) {
            rotated = rotated.rotate(Rotation.CLOCKWISE_90);
        }
        return rotated;
    }
}
