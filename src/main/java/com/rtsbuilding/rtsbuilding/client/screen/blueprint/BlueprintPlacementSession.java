package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.blueprint.transform.BlueprintTransform;
import com.rtsbuilding.rtsbuilding.network.blueprint.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

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
        return entry != null && entry.error().trim().isEmpty();
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
        pinnedAnchor = anchor.toImmutable();
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
                || entry == null || !entry.error().trim().isEmpty()) {
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
        if (entry == null || !entry.error().trim().isEmpty()) {
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
        RtsPayloadRegistrar.sendToServer(payload);
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
        BlockPos next = clampAnchor(pinnedAnchor.add(dx, dy, dz), controller);
        if (next.equals(pinnedAnchor)) {
            status.set(S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.nudge_blocked", "");
            return true;
        }
        pinnedAnchor = next.toImmutable();
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
        pinnedAnchor = clampAnchor(anchor, controller).toImmutable();
        status.set(S2CBlueprintStatusPayload.INFO,
                "screen.rtsbuilding.blueprints.status.nudged", shortPos(pinnedAnchor));
        return true;
    }

    boolean nudgeRelative(int rightSteps, int forwardSteps, int upSteps,
            ClientRtsController controller) {
        EnumFacing forward = currentHorizontalFacingDirection();
        EnumFacing right = rightOf(forward);
        int dx = forward.getXOffset() * forwardSteps + right.getXOffset() * rightSteps;
        int dz = forward.getZOffset() * forwardSteps + right.getZOffset() * rightSteps;
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
        if (entry == null || !entry.error().trim().isEmpty()) return;
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

    private static EnumFacing currentHorizontalFacingDirection() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft == null ? null : minecraft.getRenderViewEntity();
        if (camera != null) return EnumFacing.fromAngle(camera.rotationYaw);
        if (minecraft != null && minecraft.player != null) return EnumFacing.fromAngle(minecraft.player.rotationYaw);
        return EnumFacing.SOUTH;
    }

    private static EnumFacing rightOf(EnumFacing forward) {
        return forward.getAxis().isHorizontal() ? forward.rotateY() : EnumFacing.WEST;
    }

    private static BlockPos clampAnchor(BlockPos pos, ClientRtsController controller) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        y = MathHelper.clamp(y, 0, 255);
        if (controller != null && controller.hasBounds()) {
            double halfExtent = controller.getMaxRadius() + 8.0D;
            int minX = MathHelper.ceil(controller.getAnchorX() - halfExtent - 0.5D);
            int maxX = MathHelper.floor(controller.getAnchorX() + halfExtent - 0.5D);
            int minZ = MathHelper.ceil(controller.getAnchorZ() - halfExtent - 0.5D);
            int maxZ = MathHelper.floor(controller.getAnchorZ() + halfExtent - 0.5D);
            if (minX <= maxX) x = MathHelper.clamp(x, minX, maxX);
            if (minZ <= maxZ) z = MathHelper.clamp(z, minZ, maxZ);
        }
        return new BlockPos(x, y, z);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }
}
