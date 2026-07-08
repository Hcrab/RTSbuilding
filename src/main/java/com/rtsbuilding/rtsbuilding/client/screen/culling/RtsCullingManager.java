package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * State machine for the range-culling editor.
 *
 * <p>This class owns box lists, edit phases, selection/hover state, and render refresh. It does not draw UI buttons
 * or send server packets. The current feature is client-side visibility plus ray passthrough; future save/sync work
 * can add explicit data export here without changing the interaction layer.
 */
public final class RtsCullingManager {
    private static final double RAY_DISTANCE = 128.0D;
    private static final double RAY_SKIP_EPSILON = 0.05D;
    private static final int DEFAULT_HEIGHT = 0;
    private static final int FAST_SCROLL_STEP = 4;
    private static final int MAX_HEIGHT_OFFSET = 255;
    private static final long RESIZE_ANIMATION_MS = 90L;

    private final RtsBoxHandleInteraction handleInteraction = new RtsBoxHandleInteraction();
    private final List<RtsCullingBox> boxes = new CopyOnWriteArrayList<>();
    private final Set<BlockPos> revealedBlocks = ConcurrentHashMap.newKeySet();
    private boolean managementMode;
    private int nextId = 1;
    private int selectedId = -1;
    private int hoveredId = -1;
    private BlockPos firstCorner;
    private BlockPos secondCorner;
    private int previewHeight = DEFAULT_HEIGHT;
    private Phase phase = Phase.IDLE;
    private int animatedBoxId = -1;
    private AABB animatedStartAabb;
    private AABB animatedEndAabb;
    private long animatedStartMillis;

    public boolean isManagementMode() {
        return managementMode;
    }

    public boolean hasWorldCullBoxes() {
        return !boxes.isEmpty();
    }

    public List<RtsCullingBox> boxes() {
        return List.copyOf(boxes);
    }

    public Optional<RtsCullingBox> selectedBox() {
        return boxById(selectedId);
    }

    public int selectedId() {
        return selectedId;
    }

    public int hoveredId() {
        return hoveredId;
    }

    public Direction hoveredHandleDirection() {
        return handleInteraction.hoveredDirection();
    }

    public Direction activeHandleDirection() {
        return handleInteraction.activeDirection();
    }

    public Phase phase() {
        return phase;
    }

    public int previewHeight() {
        return previewHeight;
    }

    public RtsCullingBox previewBox() {
        if (firstCorner == null) {
            return null;
        }
        BlockPos second = secondCorner == null ? firstCorner : secondCorner;
        return RtsCullingBox.fromDiagonal(0, firstCorner, second, previewHeight);
    }

    public AABB renderAabb(RtsCullingBox box) {
        if (box == null) {
            return null;
        }
        if (box.id() != animatedBoxId || animatedStartAabb == null || animatedEndAabb == null) {
            return box.asAabb();
        }
        long now = System.currentTimeMillis();
        double raw = Mth.clamp((double) (now - animatedStartMillis) / (double) RESIZE_ANIMATION_MS,
                0.0D, 1.0D);
        if (raw >= 1.0D) {
            clearResizeAnimation();
            return box.asAabb();
        }
        double eased = 1.0D - Math.pow(1.0D - raw, 3.0D);
        return lerpAabb(animatedStartAabb, animatedEndAabb, eased);
    }

    public void toggleManagementMode() {
        if (managementMode) {
            closeManagementMode();
        } else {
            setManagementMode(true);
        }
    }

    /**
     * 关闭剔除管理页。
     *
     * <p>如果玩家已经完成“两点 + 高度”的完整草稿，关闭管理页等同于把它落成正式剔除盒；
     * 未完成的半截草稿仍然会被取消。这样不会改变 Enter 的明确确认语义，但能避免玩家框好区域后
     * 点顶部栏关闭时把完整区域静默丢掉。
     */
    public void closeManagementMode() {
        if (!managementMode) {
            return;
        }
        if (hasCompleteDraft()) {
            confirmDraft();
        }
        setManagementMode(false);
    }

    public void setManagementMode(boolean active) {
        if (this.managementMode == active) {
            return;
        }
        this.managementMode = active;
        this.hoveredId = -1;
        this.handleInteraction.clear();
        clearResizeAnimation();
        cancelDraft();
        markAllBoxesDirty();
    }

    public void updateHover(Vec3 origin, Vec3 direction) {
        if (!managementMode) {
            this.hoveredId = -1;
            this.handleInteraction.clear();
            return;
        }
        RtsCullingBox selected = selectedBox().orElse(null);
        this.handleInteraction.updateHover(selected, origin, direction, phase == Phase.IDLE);
        if (phase == Phase.IDLE
                && selected != null
                && (this.handleInteraction.hoveredDirection() != null || this.handleInteraction.activeDirection() != null)) {
            this.hoveredId = selectedId;
            return;
        }
        this.hoveredId = nearestHit(origin, direction)
                .map(hit -> hit.box().id())
                .orElse(-1);
    }

    public boolean handleWorldAction(BlockHitResult hit, Vec3 origin, Vec3 direction) {
        if (!managementMode) {
            return false;
        }
        if (phase == Phase.IDLE) {
            Optional<RtsCullingBox> selected = selectedBox();
            if (selected.isPresent()) {
                RtsBoxHandleInteraction.ClickResult handleClick =
                        this.handleInteraction.clickHandle(selected.get(), origin, direction);
                if (handleClick.handled()) {
                    this.hoveredId = selectedId;
                    return true;
                }
            }
        }
        Optional<RtsCullingBox.RayHit> boxHit = nearestHit(origin, direction);
        if (boxHit.isPresent() && phase == Phase.IDLE) {
            this.selectedId = boxHit.get().box().id();
            this.handleInteraction.clear();
            return true;
        }
        if (phase == Phase.IDLE && selectHoveredBoxIfPresent()) {
            return true;
        }
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return true;
        }
        RtsCullingBox oldPreview = previewBox();
        BlockPos pos = hit.getBlockPos();
        switch (phase) {
            case IDLE -> {
                this.firstCorner = pos;
                this.secondCorner = null;
                this.previewHeight = DEFAULT_HEIGHT;
                this.selectedId = -1;
                this.handleInteraction.clear();
                this.phase = Phase.NEED_SECOND;
            }
            case NEED_SECOND -> {
                this.secondCorner = new BlockPos(pos.getX(), firstCorner.getY(), pos.getZ());
                this.phase = Phase.NEED_HEIGHT;
                markBoxDirty(oldPreview);
                markBoxDirty(previewBox());
            }
            case NEED_HEIGHT -> {
                this.secondCorner = new BlockPos(pos.getX(), firstCorner.getY(), pos.getZ());
                markBoxDirty(oldPreview);
                markBoxDirty(previewBox());
            }
        }
        return true;
    }

    public boolean handleScroll(double scrollY, boolean fast) {
        if (!managementMode) {
            return false;
        }
        if (phase == Phase.IDLE) {
            return this.handleInteraction.handleScroll(scrollY, fast, this::adjustSelectedFromHandle);
        }
        int delta = scrollY > 0.0D ? 1 : -1;
        if (fast) {
            delta *= FAST_SCROLL_STEP;
        }
        RtsCullingBox oldPreview = previewBox();
        this.previewHeight = Mth.clamp(this.previewHeight + delta, -MAX_HEIGHT_OFFSET, MAX_HEIGHT_OFFSET);
        markBoxDirty(oldPreview);
        markBoxDirty(previewBox());
        return true;
    }

    public boolean handleActiveHandleDrag(double dragX, double dragY, double axisX, double axisY) {
        if (!managementMode || phase != Phase.IDLE) {
            return false;
        }
        return this.handleInteraction.handleDrag(dragX, dragY, axisX, axisY, this::adjustSelectedFromHandle);
    }

    public boolean releaseActiveHandleIfDragged() {
        return this.handleInteraction.releaseActiveHandleIfDragged();
    }

    public boolean cancelDraftIfActive() {
        if (!managementMode || phase == Phase.IDLE) {
            return false;
        }
        cancelDraft();
        return true;
    }

    public boolean hasCompleteDraft() {
        return phase == Phase.NEED_HEIGHT && firstCorner != null && secondCorner != null;
    }

    public boolean handleKey(int keyCode, int scanCode, int modifiers) {
        if (!managementMode) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            return confirmDraft();
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            return deleteSelected();
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.handleInteraction.releaseActiveHandle()) {
                return true;
            } else if (phase != Phase.IDLE) {
                cancelDraft();
            } else {
                setManagementMode(false);
            }
            return true;
        }
        return false;
    }

    public boolean confirmDraft() {
        if (phase == Phase.IDLE || firstCorner == null) {
            return true;
        }
        BlockPos second = secondCorner == null ? firstCorner : secondCorner;
        RtsCullingBox box = RtsCullingBox.fromDiagonal(nextId++, firstCorner, second, previewHeight);
        this.boxes.add(box);
        this.selectedId = box.id();
        cancelDraft();
        markBoxDirty(box);
        return true;
    }

    public boolean deleteSelected() {
        if (selectedId < 0) {
            return true;
        }
        int deleting = selectedId;
        Optional<RtsCullingBox> removed = boxById(deleting);
        boxes.removeIf(box -> box.id() == deleting);
        selectedId = -1;
        hoveredId = -1;
        handleInteraction.clear();
        if (animatedBoxId == deleting) {
            clearResizeAnimation();
        }
        removed.ifPresent(this::markBoxDirty);
        return true;
    }

    public void adjustSelectedDimension(Direction.Axis axis, int delta) {
        if (selectedId < 0 || delta == 0) {
            return;
        }
        for (int i = 0; i < boxes.size(); i++) {
            RtsCullingBox box = boxes.get(i);
            if (box.id() != selectedId) {
                continue;
            }
            RtsCullingBox resized = box.resize(axis, delta);
            startResizeAnimation(box, resized);
            boxes.set(i, resized);
            markBoxDirty(box);
            markBoxDirty(resized);
            return;
        }
    }

    public boolean adjustSelectedFromHandle(Direction direction, int delta) {
        if (selectedId < 0 || direction == null || delta == 0) {
            return false;
        }
        for (int i = 0; i < boxes.size(); i++) {
            RtsCullingBox box = boxes.get(i);
            if (box.id() != selectedId) {
                continue;
            }
            RtsCullingBox resized = box.resizeFromHandle(direction, delta);
            startResizeAnimation(box, resized);
            boxes.set(i, resized);
            markBoxDirty(box);
            markBoxDirty(resized);
            return true;
        }
        return false;
    }

    public boolean shouldCullWorldBlock(BlockPos pos) {
        RtsCullingBox preview = activePreviewBox();
        if ((!hasWorldCullBoxes() && preview == null) || pos == null) {
            return false;
        }
        BlockPos immutable = pos.immutable();
        if (revealedBlocks.contains(immutable)) {
            return false;
        }
        if (boxes.stream().anyMatch(box -> box.contains(immutable))) {
            return true;
        }
        return preview != null && preview.contains(immutable);
    }

    public double distanceAfterCulledBlock(Vec3 origin, Vec3 direction, BlockPos pos, double maxDistance) {
        RtsCullingBox preview = activePreviewBox();
        if ((!hasWorldCullBoxes() && preview == null) || origin == null || direction == null || pos == null) {
            return -1.0D;
        }
        double best = distanceAfterCulledBlockIn(boxes, origin, direction, pos, maxDistance);
        if (preview != null && preview.contains(pos)) {
            RtsCullingBox.RayHit previewHit = preview.rayHit(origin, direction, maxDistance);
            if (previewHit != null) {
                double previewDistance = Mth.clamp(previewHit.exitDistance() + RAY_SKIP_EPSILON, 0.0D, maxDistance);
                best = best < 0.0D ? previewDistance : Math.min(best, previewDistance);
            }
        }
        return best;
    }

    public void revealWorldBlock(BlockPos pos) {
        if (pos == null) {
            return;
        }
        BlockPos immutable = pos.immutable();
        if (!isInsideAnyCullVolume(immutable)) {
            return;
        }
        if (revealedBlocks.add(immutable)) {
            markBlockDirty(immutable);
        }
    }

    public void refreshWorldCullRendering() {
        markAllBoxesDirty();
        markBoxDirty(activePreviewBox());
        revealedBlocks.forEach(this::markBlockDirty);
    }

    private double distanceAfterCulledBlockIn(List<RtsCullingBox> candidates, Vec3 origin, Vec3 direction,
            BlockPos pos, double maxDistance) {
        return candidates.stream()
                .filter(box -> box.contains(pos))
                .map(box -> box.rayHit(origin, direction, maxDistance))
                .filter(hit -> hit != null)
                .map(hit -> Mth.clamp(hit.exitDistance() + RAY_SKIP_EPSILON, 0.0D, maxDistance))
                .min(Double::compareTo)
                .orElse(-1.0D);
    }

    private boolean isInsideAnyCullVolume(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        if (boxes.stream().anyMatch(box -> box.contains(pos))) {
            return true;
        }
        RtsCullingBox preview = activePreviewBox();
        return preview != null && preview.contains(pos);
    }

    private RtsCullingBox activePreviewBox() {
        return managementMode && hasCompleteDraft() ? previewBox() : null;
    }

    private Optional<RtsCullingBox> boxById(int id) {
        return boxes.stream().filter(box -> box.id() == id).findFirst();
    }

    private Optional<RtsCullingBox.RayHit> nearestHit(Vec3 origin, Vec3 direction) {
        if (origin == null || direction == null) {
            return Optional.empty();
        }
        return boxes.stream()
                .map(box -> box.rayHit(origin, direction, RAY_DISTANCE))
                .filter(hit -> hit != null)
                .min(Comparator.comparingDouble(RtsCullingBox.RayHit::enterDistance));
    }

    private boolean selectHoveredBoxIfPresent() {
        if (hoveredId < 0) {
            return false;
        }
        Optional<RtsCullingBox> hovered = boxById(hoveredId);
        if (hovered.isEmpty()) {
            hoveredId = -1;
            return false;
        }
        selectedId = hovered.get().id();
        handleInteraction.clear();
        return true;
    }

    private void cancelDraft() {
        this.firstCorner = null;
        this.secondCorner = null;
        this.previewHeight = DEFAULT_HEIGHT;
        this.phase = Phase.IDLE;
        this.handleInteraction.clear();
    }

    private void markAllBoxesDirty() {
        boxes.forEach(this::markBoxDirty);
    }

    private void markBoxDirty(RtsCullingBox box) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null || box == null) {
            return;
        }
        mc.levelRenderer.setBlocksDirty(
                box.min().getX() - 1, box.min().getY() - 1, box.min().getZ() - 1,
                box.max().getX() + 1, box.max().getY() + 1, box.max().getZ() + 1);
    }

    private void markBlockDirty(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.levelRenderer == null || pos == null) {
            return;
        }
        mc.levelRenderer.setBlocksDirty(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private void startResizeAnimation(RtsCullingBox from, RtsCullingBox to) {
        if (from == null || to == null || from.equals(to)) {
            return;
        }
        long now = System.currentTimeMillis();
        AABB visualStart = from.id() == animatedBoxId && animatedStartAabb != null && animatedEndAabb != null
                ? currentAnimatedAabb(now)
                : from.asAabb();
        animatedBoxId = to.id();
        animatedStartAabb = visualStart;
        animatedEndAabb = to.asAabb();
        animatedStartMillis = now;
    }

    private AABB currentAnimatedAabb(long now) {
        double raw = Mth.clamp((double) (now - animatedStartMillis) / (double) RESIZE_ANIMATION_MS,
                0.0D, 1.0D);
        double eased = 1.0D - Math.pow(1.0D - raw, 3.0D);
        return lerpAabb(animatedStartAabb, animatedEndAabb, eased);
    }

    private void clearResizeAnimation() {
        animatedBoxId = -1;
        animatedStartAabb = null;
        animatedEndAabb = null;
        animatedStartMillis = 0L;
    }

    private static AABB lerpAabb(AABB from, AABB to, double amount) {
        return new AABB(
                Mth.lerp(amount, from.minX, to.minX),
                Mth.lerp(amount, from.minY, to.minY),
                Mth.lerp(amount, from.minZ, to.minZ),
                Mth.lerp(amount, from.maxX, to.maxX),
                Mth.lerp(amount, from.maxY, to.maxY),
                Mth.lerp(amount, from.maxZ, to.maxZ));
    }

    public enum Phase {
        IDLE,
        NEED_SECOND,
        NEED_HEIGHT
    }
}
