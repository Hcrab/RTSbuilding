package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Renders corner-bracket highlights around all linked storage blocks (chests, barrels,
 * etc.) that the player has registered via the storage-binding UI.
 *
 * <p>Two visual modes exist:
 * <ul>
 *   <li><b>Bidirectional</b> (default) &ndash; blue brackets {@code (0.24, 0.55, 1.0)}
 *   <li><b>Extract-only</b> &ndash; pink brackets {@code (1.0, 0.30, 0.82)}
 * </ul>
 *
 * <p>Double chests are automatically detected via {@link ChestBlock} properties and
 * rendered as a single merged bounding box spanning both halves.
 *
 * <p>A smooth expand/contract animation plays when a storage is linked or unlinked:
 * the bounding box grows from / shrinks toward the block centre over ~300 ms, giving
 * the visual impression that the 8 corner anchor points are being "released" or "drawn in".
 *
 * <p>All methods are static; this class is never instantiated.
 */
public final class StorageRenderer {
    private StorageRenderer() {
    }

    /** Small outward offset applied to bracket vertices to prevent z-fighting with world geometry. */
    private static final double LINE_OFFSET = 0.002D;

    /** Duration of the expand (bind) / contract (unbind) animation in milliseconds. */
    private static final long ANIM_DURATION_MS = 300L;

    // ── Per-position animation state ──────────────────────────────────────────

    private static final Map<BlockPos, StorageAnim> anims = new HashMap<>();
    private static Set<BlockPos> prevPositions = Collections.emptySet();
    private static boolean initialised = false;

    /**
     * Tracks the lifecycle of one linked-storage highlight.
     * <ul>
     *   <li>{@link Phase#BINDING}   &ndash; newly added, expanding from centre
     *   <li>{@link Phase#BOUND}     &ndash; fully visible
     *   <li>{@link Phase#UNBINDING} &ndash; removed from the list, contracting to centre
     * </ul>
     */
    private static final class StorageAnim {
        enum Phase { BINDING, BOUND, UNBINDING }

        Phase phase;
        long startTime;

        /** Frozen (or live-updated) world-space bounding box. */
        AABB bounds;
        /** Frozen (or live-updated) target highlight colour. */
        float red, green, blue;

        // ── Colour-transition fields ───────────────────────────────────────

        /** Colour at the moment the transition started. */
        float prevRed, prevGreen, prevBlue;
        /** Timestamp when the current colour transition began; -1 means no transition. */
        long colorTransitionStart = -1L;
        /** Whether the target colours have been written at least once. */
        boolean colorsSet = false;

        StorageAnim(Phase phase, long now) {
            this.phase = phase;
            this.startTime = now;
        }

        /** Normalised progress in [0, 1]. */
        float progress(long now) {
            return Math.min(1.0F, (float) (now - startTime) / (float) ANIM_DURATION_MS);
        }

        // ── Colour interpolation methods ───────────────────────────────────

        /** Whether a colour transition is currently active. */
        boolean isColorTransitioning(long now) {
            return colorTransitionStart >= 0
                    && (now - colorTransitionStart) < ANIM_DURATION_MS;
        }

        /** Interpolated red component: blends from {@link #prevRed} to {@link #red}. */
        float getRenderRed(long now) {
            return lerpColor(prevRed, red, now);
        }

        /** Interpolated green component: blends from {@link #prevGreen} to {@link #green}. */
        float getRenderGreen(long now) {
            return lerpColor(prevGreen, green, now);
        }

        /** Interpolated blue component: blends from {@link #prevBlue} to {@link #blue}. */
        float getRenderBlue(long now) {
            return lerpColor(prevBlue, blue, now);
        }

        private float lerpColor(float from, float to, long now) {
            if (!isColorTransitioning(now)) return to;
            float t = Math.min(1.0F, (float) (now - colorTransitionStart) / (float) ANIM_DURATION_MS);
            float s = 1.0F - (float) Math.pow(1.0 - t, 3); // cubic ease-out
            return from + (to - from) * s;
        }
    }

    // ── Public entry-point ────────────────────────────────────────────────────

    /**
     * Renders corner brackets (and a translucent fog layer) for every linked storage,
     * with expand/contract animations for newly added or removed entries.
     *
     * @param minecraft   the Minecraft client instance
     * @param controller  the client-side RTS controller (source of linked-storage data)
     * @param poseStack   current transformation stack (already translated to camera space)
     * @param lineBuffer  {@link VertexConsumer} for the bracket-quad render type
     */
    public static void renderLinkedStorages(Minecraft minecraft, ClientRtsController controller, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        if (minecraft.level == null) {
            return;
        }

        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();
        long now = System.currentTimeMillis();

        List<ClientRtsController.LinkedStorageEntry> entries = controller.getLinkedStorageEntries();

        // ── 1. Detect additions / removals ─────────────────────────────────

        Set<BlockPos> currPositions = new HashSet<>();
        for (ClientRtsController.LinkedStorageEntry e : entries) {
            if (e.worldAvailable() && e.pos() != null) currPositions.add(e.pos());
        }

        if (!initialised) {
            // First frame: snapshot everything as BOUND, no animations.
            for (BlockPos p : currPositions) {
                if (minecraft.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                    BlockState st = minecraft.level.getBlockState(p);
                    if (!st.isAir()) {
                        StorageAnim a = new StorageAnim(StorageAnim.Phase.BOUND, now);
                        a.bounds = computeStorageBounds(minecraft.level, p, st);
                        anims.put(p, a);
                    }
                }
            }
            prevPositions = new HashSet<>(currPositions);
            initialised = true;
        } else {
            // Removals → start UNBINDING.
            for (BlockPos p : prevPositions) {
                if (!currPositions.contains(p)) {
                    StorageAnim existing = anims.get(p);
                    if (existing != null && existing.bounds != null) {
                        StorageAnim ub = new StorageAnim(StorageAnim.Phase.UNBINDING, now);
                        ub.bounds = existing.bounds;
                        ub.red = existing.red;
                        ub.green = existing.green;
                        ub.blue = existing.blue;
                        anims.put(p, ub);
                    }
                }
            }

            // Additions → start BINDING (or restart if still UNBINDING).
            for (ClientRtsController.LinkedStorageEntry e : entries) {
                if (!e.worldAvailable()) continue;
                BlockPos p = e.pos();
                if (p == null || prevPositions.contains(p)) continue;
                StorageAnim existing = anims.get(p);
                if (existing != null && existing.phase == StorageAnim.Phase.UNBINDING) {
                    // Re-added while the previous unbind was still running:
                    // replace with a fresh bind animation so it expands again from the centre.
                    anims.put(p, new StorageAnim(StorageAnim.Phase.BINDING, now));
                } else if (!anims.containsKey(p)) {
                    anims.put(p, new StorageAnim(StorageAnim.Phase.BINDING, now));
                }
            }

            prevPositions = new HashSet<>(currPositions);
        }

        // ── 2. Advance animation states ─────────────────────────────────────

        for (Iterator<Map.Entry<BlockPos, StorageAnim>> it = anims.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, StorageAnim> e = it.next();
            BlockPos p = e.getKey();
            StorageAnim a = e.getValue();

            switch (a.phase) {
                case BINDING:
                    // Keep snapshot up-to-date while the entry is still linked.
                    if (currPositions.contains(p) && minecraft.level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) {
                        BlockState st = minecraft.level.getBlockState(p);
                        if (!st.isAir()) {
                            a.bounds = computeStorageBounds(minecraft.level, p, st);
                        }
                    }
                    if (a.progress(now) >= 1.0F) {
                        a.phase = StorageAnim.Phase.BOUND;
                    }
                    break;
                case UNBINDING:
                    if (a.progress(now) >= 1.0F) {
                        it.remove(); // animation finished
                    }
                    break;
                case BOUND:
                    if (!currPositions.contains(p)) {
                        it.remove(); // stale entry
                    }
                    break;
            }
        }

        // ── 3. Render currently linked entries (BINDING / BOUND) ────────────

        for (ClientRtsController.LinkedStorageEntry entry : entries) {
            if (!entry.worldAvailable()) {
                continue;
            }
            BlockPos pos = entry.pos();
            if (pos == null || !minecraft.level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
                continue;
            }

            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            // Compute the world-space bounding box, merging double-chest halves if applicable.
            AABB fullBounds = computeStorageBounds(minecraft.level, pos, state);

            // Determine target bracket colour: pink for extract-only, blue for bidirectional.
            boolean extractOnly = entry.mode() == C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY;
            float targetRed = extractOnly ? 1.00F : 0.24F;
            float targetGreen = extractOnly ? 0.30F : 0.55F;
            float targetBlue = extractOnly ? 0.82F : 1.00F;

            // Manage colour-transition animation when the mode switches.
            StorageAnim a = anims.get(pos);
            if (a != null) {
                if (a.colorsSet
                        && (Math.abs(a.red - targetRed) > 0.01F
                        || Math.abs(a.green - targetGreen) > 0.01F
                        || Math.abs(a.blue - targetBlue) > 0.01F)) {
                    // Mode changed: snapshot the currently displayed colour as the
                    // start of the transition, then mark the transition start.
                    a.prevRed = a.getRenderRed(now);
                    a.prevGreen = a.getRenderGreen(now);
                    a.prevBlue = a.getRenderBlue(now);
                    a.colorTransitionStart = now;
                } else if (!a.colorsSet) {
                    // First assignment: just store the target without a transition.
                    a.prevRed = a.red = targetRed;
                    a.prevGreen = a.green = targetGreen;
                    a.prevBlue = a.blue = targetBlue;
                    a.colorTransitionStart = now;
                    a.colorsSet = true;
                }
                // Update the final (target) colour for the snapshot & future transitions.
                a.red = targetRed;
                a.green = targetGreen;
                a.blue = targetBlue;
            }

            // Use the potentially interpolated colour for rendering.
            float renderRed   = a != null ? a.getRenderRed(now)   : targetRed;
            float renderGreen = a != null ? a.getRenderGreen(now) : targetGreen;
            float renderBlue  = a != null ? a.getRenderBlue(now)  : targetBlue;

            // Compute the animated render bounds.
            AABB renderBounds = getAnimatedBounds(pos, fullBounds, now);

            // Distance from camera to the centre of the storage bounds (used for thickness scaling).
            double distance = cameraPos.distanceTo(renderBounds.getCenter());

            // Render a subtle translucent fog layer on all six faces of the bounding box.
            renderFogFaces(lineBuffer, poseStack, renderBounds, renderRed, renderGreen, renderBlue);

            // Render corner brackets with a small outward offset to avoid z-fighting.
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, lineBuffer,
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    renderRed, renderGreen, renderBlue, distance);
        }

        // ── 4. Render entries currently in UNBINDING animation ──────────────

        for (Map.Entry<BlockPos, StorageAnim> ae : anims.entrySet()) {
            StorageAnim a = ae.getValue();
            if (a.phase != StorageAnim.Phase.UNBINDING) continue;

            // Progress goes 0→1; t goes 1→0 so the box shrinks toward the centre.
            float t = 1.0F - a.progress(now);
            AABB renderBounds = expandBounds(a.bounds, t);

            double distance = cameraPos.distanceTo(renderBounds.getCenter());

            renderFogFaces(lineBuffer, poseStack, renderBounds, a.red, a.green, a.blue);
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, lineBuffer,
                    renderBounds.minX - LINE_OFFSET, renderBounds.minY - LINE_OFFSET, renderBounds.minZ - LINE_OFFSET,
                    renderBounds.maxX + LINE_OFFSET, renderBounds.maxY + LINE_OFFSET, renderBounds.maxZ + LINE_OFFSET,
                    a.red, a.green, a.blue, distance);
        }
    }

    // ── Animation helpers ─────────────────────────────────────────────────────

    /**
     * Returns the animated AABB for a linked entry at {@code pos}.
     * <ul>
     *   <li>{@link StorageAnim.Phase#BINDING} &ndash; expands from the block centre
     *       to {@code fullBounds} over the animation duration.
     *   <li>No animation or {@link StorageAnim.Phase#BOUND} &ndash; returns
     *       {@code fullBounds} unchanged.
     * </ul>
     */
    private static AABB getAnimatedBounds(BlockPos pos, AABB fullBounds, long now) {
        StorageAnim a = anims.get(pos);
        if (a == null || a.phase != StorageAnim.Phase.BINDING) return fullBounds;
        return expandBounds(fullBounds, a.progress(now));
    }

    /**
     * Performs a cubic ease-out expansion of {@code bounds} from its centre point.
     *
     * @param bounds the full (target) bounding box
     * @param t      interpolation parameter in [0, 1]; 0 = centre point, 1 = full size
     * @return the interpolated AABB
     */
    private static AABB expandBounds(AABB bounds, float t) {
        float clamped = Math.min(1.0F, Math.max(0.0F, t));
        // Cubic ease-out for a smooth, natural-looking expansion.
        double s = 1.0 - Math.pow(1.0 - clamped, 3);
        double cx = (bounds.minX + bounds.maxX) * 0.5;
        double cy = (bounds.minY + bounds.maxY) * 0.5;
        double cz = (bounds.minZ + bounds.maxZ) * 0.5;
        return new AABB(
                cx + (bounds.minX - cx) * s,
                cy + (bounds.minY - cy) * s,
                cz + (bounds.minZ - cz) * s,
                cx + (bounds.maxX - cx) * s,
                cy + (bounds.maxY - cy) * s,
                cz + (bounds.maxZ - cz) * s
        );
    }

    // ── Fog rendering ────────────────────────────────────────────────────────

    /**
     * Renders a subtle translucent fog layer on all six faces of the given bounding box,
     * creating a soft coloured glow around linked storage blocks.
     *
     * @param consumer  vertex consumer
     * @param poseStack current transformation stack
     * @param bounds    the world-space bounding box
     * @param r         red   colour component [0, 1]
     * @param g         green colour component [0, 1]
     * @param b         blue  colour component [0, 1]
     */
    private static void renderFogFaces(VertexConsumer consumer, PoseStack poseStack,
            AABB bounds, float r, float g, float b) {
        float alpha = 0.10F;

        double x1 = bounds.minX;
        double x2 = bounds.maxX;
        double y1 = bounds.minY;
        double y2 = bounds.maxY;
        double z1 = bounds.minZ;
        double z2 = bounds.maxZ;

        // Six faces: -X (west), +X (east), -Y (bottom), +Y (top), -Z (north), +Z (south)
        RenderingUtil.quad(consumer, poseStack, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, alpha);
        RenderingUtil.quad(consumer, poseStack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, alpha);
        RenderingUtil.quad(consumer, poseStack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, alpha);
        RenderingUtil.quad(consumer, poseStack, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, alpha);
        RenderingUtil.quad(consumer, poseStack, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, alpha);
        RenderingUtil.quad(consumer, poseStack, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, alpha);
    }

    // ── AABB computation ─────────────────────────────────────────────────────

    /**
     * Computes the world-space axis-aligned bounding box for the storage at the given
     * position. If the block is part of a double chest ({@link ChestType#LEFT} or
     * {@link ChestType#RIGHT}), the returned AABB spans both halves.
     *
     * @param level the world
     * @param pos   the targeted block position
     * @param state the block state at {@code pos}
     * @return the merged world-space AABB (always a valid, non-empty box)
     */
    private static AABB computeStorageBounds(Level level, BlockPos pos, BlockState state) {
        // Detect double chest: if the block is a chest and its type is not SINGLE,
        // locate the connected half and merge both into a single bounding box.
        if (state.getBlock() instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                Direction connectedDir = ChestBlock.getConnectedDirection(state);
                BlockPos connectedPos = pos.relative(connectedDir);
                if (level.hasChunk(connectedPos.getX() >> 4, connectedPos.getZ() >> 4)) {
                    BlockState connectedState = level.getBlockState(connectedPos);
                    if (!connectedState.isAir() && connectedState.getBlock() instanceof ChestBlock) {
                        double minX = Math.min(pos.getX(), connectedPos.getX());
                        double minY = Math.min(pos.getY(), connectedPos.getY());
                        double minZ = Math.min(pos.getZ(), connectedPos.getZ());
                        double maxX = Math.max(pos.getX(), connectedPos.getX()) + 1;
                        double maxY = Math.max(pos.getY(), connectedPos.getY()) + 1;
                        double maxZ = Math.max(pos.getZ(), connectedPos.getZ()) + 1;
                        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                    }
                }
            }
        }

        // Default: a single 1x1x1 block centred on the given position.
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return new AABB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }
}
