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
 * 单方块放置虚影的 BlockState 解析器。
 * <p>
 * 负责根据玩家当前手持物品和摄像机方向，
 * 模拟服务端 {@link net.minecraft.world.level.block.Block#getStateForPlacement(BlockPlaceContext)}
 * 的逻辑，解析出虚影渲染所需的正确 BlockState。
 * <p>
 * 支持旋转和多方块方块（门、床等）的方向推断。
 */
public final class BuildGhostBlockStateResolver {

    private BuildGhostBlockStateResolver() {
    }

    /**
     * 解析放置位置的预期 BlockState。
     *
     * @param minecraft Minecraft 客户端实例
     * @param targetPos 目标放置位置，可为 null
     * @return 解析出的 BlockState，如果无法解析则返回 null
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
     * 确定用于虚影渲染的物品来源。
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
     * 解析当前手持的刷怪蛋物品栈。
     *
     * @param minecraft Minecraft 客户端实例
     * @return 如果是刷怪蛋则返回对应的 ItemStack，否则返回 {@link ItemStack#EMPTY}
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
     * 解析当前手持的末地水晶物品栈。
     *
     * @param minecraft Minecraft 客户端实例
     * @return 如果是末地水晶则返回对应的 ItemStack，否则返回 {@link ItemStack#EMPTY}
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
     * 使用客户端摄像机方向模拟 BlockPlaceContext，匹配服务端放置逻辑。
     */
    public static BlockState resolveStateWithCamera(Minecraft minecraft, BlockItem blockItem,
            ItemStack stack, BlockPos targetPos) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return null;

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
            public @NotNull Direction getHorizontalDirection() { return Direction.fromYRot(yawDeg); }
            @Override
            public @NotNull Direction getNearestLookingDirection() { return clickedFace; }
            @Override
            public @NotNull Direction getNearestLookingVerticalDirection() { return Direction.getNearest(0.0, dy, 0.0); }
            @Override
            public float getRotation() { return yawDeg; }
        };
        return blockItem.getBlock().getStateForPlacement(context);
    }

    /**
     * 当射线未命中任何方块时，通过射线-平面求交计算命中位置。
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
     * 对 BlockState 应用 90° 步进旋转。
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
