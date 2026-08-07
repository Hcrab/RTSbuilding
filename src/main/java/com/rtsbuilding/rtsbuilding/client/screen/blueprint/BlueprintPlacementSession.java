package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.network.blueprint.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintMaterialInspector.hasEnoughMaterials;

/**
 * 蓝图放置会话的唯一 owner：负责旋转、固定锚点、虚影生成、微调与网络提交。
 *
 * <p>它不扫描蓝图库、不处理捕获区，也不绘制任何控件。蓝图条目由仓储 owner 注入，
 * 这样切换条目时旋转默认值和固定虚影只存在一条状态路径。</p>
 */
final class BlueprintPlacementSession {
    private final Supplier<BlueprintEntry> selectedEntry;
    private final BlueprintLibraryRepository.StatusSink status;
    private int yRotationSteps;
    private int xRotationSteps;
    private int zRotationSteps;
    private BlockPos pinnedAnchor;

    BlueprintPlacementSession(Supplier<BlueprintEntry> selectedEntry,
            BlueprintLibraryRepository.StatusSink status) {
        this.selectedEntry = selectedEntry;
        this.status = status;
    }

    void onSelectionChanged(BlueprintEntry entry) {
        pinnedAnchor = null;
        RotationPreset preset = entry == null
                ? null
                : BlueprintRotationDefaults.rotationFor(entry.fileName());
        yRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.y());
        xRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.x());
        zRotationSteps = preset == null ? 0 : BlueprintTransform.normalizeSteps(preset.z());
    }

    boolean hasSelection() {
        BlueprintEntry entry = selectedEntry.get();
        return entry != null && entry.error().isBlank();
    }

    int yRotationSteps() {
        return yRotationSteps;
    }

    int xRotationSteps() {
        return xRotationSteps;
    }

    int zRotationSteps() {
        return zRotationSteps;
    }

    BlockPos pinnedAnchor() {
        return pinnedAnchor;
    }

    boolean hasPinnedPreview() {
        return pinnedAnchor != null && hasSelection();
    }

    boolean pin(BlockPos anchor) {
        if (!Config.areBlueprintsEnabled()) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        if (!hasSelection() || anchor == null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_selection", "");
            return false;
        }
        pinnedAnchor = anchor.immutable();
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.preview_pinned", shortPos(pinnedAnchor));
        return true;
    }

    BlockPos anchorForCursorTarget(BlockPos cursorTarget) {
        return BlueprintPlacementPreviewFactory.anchorForCursorTarget(
                selectedEntry.get(), cursorTarget,
                yRotationSteps, xRotationSteps, zRotationSteps);
    }

    BlueprintGhostPreview createGhostPreview(BlockPos anchor, int requestedYRotation,
            ClientRtsController controller) {
        BlueprintEntry entry = selectedEntry.get();
        if (!Config.areBlueprintsEnabled() || anchor == null
                || entry == null || !entry.error().isBlank()) {
            return BlueprintGhostPreview.EMPTY;
        }
        return BlueprintPlacementPreviewFactory.create(
                entry, anchor, requestedYRotation, xRotationSteps, zRotationSteps,
                Config.maxBlueprintBlocks(), hasEnoughMaterials(entry, controller));
    }

    boolean place(BlockPos anchor, int yRotation, int xRotation, int zRotation) {
        if (!Config.areBlueprintsEnabled()) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.disabled", "");
            return true;
        }
        BlueprintEntry entry = selectedEntry.get();
        if (entry == null || !entry.error().isBlank()) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_selection", "");
            return false;
        }
        BlueprintLibraryFileOperations.UploadReadResult upload =
                BlueprintLibraryFileOperations.readForUpload(
                        entry, C2SBlueprintPlacePayload.MAX_FILE_BYTES);
        if (upload.tooLarge()) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.too_large", "");
            return true;
        }
        if (!upload.succeeded()) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.read_failed", upload.errorDetail());
            return true;
        }
        C2SBlueprintPlacePayload payload = new C2SBlueprintPlacePayload(
                UUID.randomUUID(), entry.fileName(), upload.data(), anchor,
                (byte) BlueprintTransform.normalizeSteps(yRotation),
                (byte) BlueprintTransform.normalizeSteps(xRotation),
                (byte) BlueprintTransform.normalizeSteps(zRotation));
        RtsClientNetworkBridge.send(payload);
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.uploading", entry.name());
        pinnedAnchor = null;
        return true;
    }

    boolean confirmPinnedPreview() {
        if (pinnedAnchor == null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        return place(pinnedAnchor, yRotationSteps, xRotationSteps, zRotationSteps);
    }

    boolean rotateY(int step) {
        if (!requireSelection()) return true;
        yRotationSteps = BlueprintTransform.normalizeSteps(yRotationSteps + step);
        rememberRotation();
        return true;
    }

    boolean rotateX(int step) {
        if (!requireSelection()) return true;
        xRotationSteps = BlueprintTransform.normalizeSteps(xRotationSteps + step);
        rememberRotation();
        return true;
    }

    boolean rotateZ(int step) {
        if (!requireSelection()) return true;
        zRotationSteps = BlueprintTransform.normalizeSteps(zRotationSteps + step);
        rememberRotation();
        return true;
    }

    void resetRotation() {
        if (!requireSelection()) return;
        yRotationSteps = 0;
        xRotationSteps = 0;
        zRotationSteps = 0;
        rememberRotation();
    }

    boolean nudge(int dx, int dy, int dz, ClientRtsController controller) {
        if (pinnedAnchor == null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        BlockPos next = clampAnchor(pinnedAnchor.offset(dx, dy, dz), controller);
        if (next.equals(pinnedAnchor)) {
            status.set(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.nudge_blocked", "");
            return true;
        }
        pinnedAnchor = next.immutable();
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.nudged", shortPos(pinnedAnchor));
        return true;
    }

    boolean setPinnedAnchor(BlockPos anchor, ClientRtsController controller) {
        if (anchor == null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.no_preview", "");
            return true;
        }
        pinnedAnchor = clampAnchor(anchor, controller).immutable();
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.nudged", shortPos(pinnedAnchor));
        return true;
    }

    boolean nudgeRelative(int rightSteps, int forwardSteps, int upSteps,
            ClientRtsController controller) {
        Direction forward = currentHorizontalFacingDirection();
        Direction right = rightOf(forward);
        int dx = forward.getStepX() * forwardSteps + right.getStepX() * rightSteps;
        int dz = forward.getStepZ() * forwardSteps + right.getStepZ() * rightSteps;
        return nudge(dx, upSteps, dz, controller);
    }

    void clear() {
        pinnedAnchor = null;
        yRotationSteps = 0;
        xRotationSteps = 0;
        zRotationSteps = 0;
    }

    void clearPinnedAnchor() {
        pinnedAnchor = null;
    }

    private boolean requireSelection() {
        if (hasSelection()) return true;
        status.set(S2CBlueprintStatusPayload.ERROR,
                "screen.rtsbuilding.blueprints.status.no_selection", "");
        return false;
    }

    private void rememberRotation() {
        BlueprintEntry entry = selectedEntry.get();
        if (entry == null || !entry.error().isBlank()) return;
        IOException error = BlueprintRotationDefaults.remember(
                entry.fileName(), yRotationSteps, xRotationSteps, zRotationSteps);
        if (error != null) {
            status.set(S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed", error.getMessage());
            return;
        }
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.rotated", "");
    }

    private static Direction currentHorizontalFacingDirection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameRenderer != null) {
            return Direction.fromYRot(minecraft.gameRenderer.getMainCamera().yRot());
        }
        if (minecraft != null && minecraft.getCameraEntity() != null) {
            return Direction.fromYRot(minecraft.getCameraEntity().getYRot());
        }
        if (minecraft != null && minecraft.player != null) {
            return Direction.fromYRot(minecraft.player.getYRot());
        }
        return Direction.SOUTH;
    }

    private static Direction rightOf(Direction forward) {
        return switch (forward) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    private static BlockPos clampAnchor(BlockPos pos, ClientRtsController controller) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            y = Mth.clamp(y, level.getMinY(), level.getMaxY() - 1);
        }
        if (controller != null && controller.hasBounds()) {
            double halfExtent = controller.getMaxRadius() + 8.0D;
            int minX = Mth.ceil(controller.getAnchorX() - halfExtent - 0.5D);
            int maxX = Mth.floor(controller.getAnchorX() + halfExtent - 0.5D);
            int minZ = Mth.ceil(controller.getAnchorZ() - halfExtent - 0.5D);
            int maxZ = Mth.floor(controller.getAnchorZ() + halfExtent - 0.5D);
            if (minX <= maxX) x = Mth.clamp(x, minX, maxX);
            if (minZ <= maxZ) z = Mth.clamp(z, minZ, maxZ);
        }
        return new BlockPos(x, y, z);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
