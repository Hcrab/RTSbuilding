package com.rtsbuilding.rtsbuilding.client.rendering.overlay;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsOwnedBufferUploader;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsGlStateRestorer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsCursorRay;

import com.google.common.base.Predicate;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingRayClipper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDoublePlant;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.render.BufferBuilder;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import com.rtsbuilding.rtsbuilding.platform.render.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import com.rtsbuilding.rtsbuilding.platform.render.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * 1.12 鼠标世界目标高亮。
 *
 * <p>深度与穿透几何分别写入本类私有缓冲；所有兼容入口中的调用方缓冲都不会
 * 被结束或上传。方块/实体择近、范围限制、近黄远橙、命中面雾层和多方块合并
 * 与主线语义保持一致。</p>
 */
public final class InteractionTargetRenderer {
    private static final double INFLATE = 0.03D;
    private static final double LINE_OFFSET = 0.01D;
    private static final double MAX_REACH = 128.0D;
    private static final double NEAR_DISTANCE = 10.0D;
    private static final double FAR_DISTANCE = 20.0D;
    private static final double FOG_OFFSET = 0.005D;
    private static final float FOG_NEAR = 0.045F;
    private static final float FOG_FAR = 0.50F;
    private static final float NO_DEPTH_ALPHA = 0.32F;
    private static final float NO_DEPTH_FOG_NEAR = 0.025F;
    private static final float NO_DEPTH_FOG_FAR = 0.18F;
    private static final float BREATH_SPEED = 0.2F;
    private static final float BREATH_MIN = 0.7F;
    private static final BufferBuilder DEPTH_BUFFER = new BufferBuilder(512 * 1024);
    private static final BufferBuilder NO_DEPTH_BUFFER = new BufferBuilder(512 * 1024);
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    private InteractionTargetRenderer() {
    }

    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller) {
        if (minecraft == null || controller == null || minecraft.theWorld == null
                || minecraft.renderViewEntity == null || isRotateCaptured(controller)
                || isInteractionBlockedByUi(minecraft)) return;

        Entity camera = minecraft.renderViewEntity;
        RtsCursorRay.Snapshot cursorRay = RtsCursorRay.capture(minecraft);
        Vec3d origin = cursorRay.origin();
        Vec3d direction = cursorRay.direction();
        Vec3d end = origin.add(direction.scale(MAX_REACH));
        RayTraceResult blockHit = RtsCullingRayClipper.clip(origin, direction, MAX_REACH,
                new RtsCullingRayClipper.BlockClip() {
                    @Override public RayTraceResult clip(Vec3d start, Vec3d finish) {
                        return RayTraceResult.trace(minecraft.theWorld, start, finish, false, false, false);
                    }
                }, new RtsCullingRayClipper.CullingQuery() {
                    @Override public boolean shouldCull(BlockPos pos) {
                        return RtsCullingClientState.shouldCull(pos);
                    }
                    @Override public double distanceAfterCulledBlock(Vec3d rayOrigin, Vec3d rayDirection,
                            BlockPos pos, double maxDistance) {
                        return RtsCullingClientState.distanceAfterCulledBlock(
                                rayOrigin, rayDirection, pos, maxDistance);
                    }
                });
        EntityHit entityHit = raycastEntity(minecraft.theWorld, camera, origin, end, direction);
        double blockDistanceSq = blockHit == null || blockHit.hitVec == null
                ? Double.MAX_VALUE : origin.squareDistanceTo(blockHit.hitVec);
        double entityDistanceSq = entityHit == null
                ? Double.MAX_VALUE : origin.squareDistanceTo(entityHit.hit);

        RenderManager manager = net.minecraft.client.renderer.entity.RenderManager.instance;
        beginBuffers(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        try {
            float breath = breathFactor();
            if (entityHit != null && InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(
                    entityDistanceSq, blockDistanceSq,
                    isWithinBounds(controller, new BlockPos(entityHit.entity)))) {
                appendEntity(entityHit.entity, Math.sqrt(entityDistanceSq), breath);
            } else if (blockHit != null && blockHit.typeOfHit == RayTraceResult.Type.BLOCK
                    && blockHit.getBlockPos() != null && isWithinBounds(controller, blockHit.getBlockPos())) {
                appendBlock(minecraft.theWorld, blockHit.getBlockPos(), blockHit.sideHit,
                        Math.sqrt(blockDistanceSq), breath);
            }
            drawOwnedBuffers();
        } catch (RuntimeException exception) {
            discardOwnedBuffers();
            throw exception;
        } finally {
            resetTranslations();
        }
    }

    /** 迁移期兼容入口：调用方缓冲的生命周期完全归调用方。 */
    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller,
            BufferBuilder callerDepthBuffer, BufferBuilder callerNoDepthBuffer) {
        renderHoveredInteractionTarget(minecraft, controller);
    }

    private static void appendEntity(Entity entity, double distance, float breath) {
        AxisAlignedBB box = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(entity.boundingBox).grow(INFLATE);
        float r = 0.50F * breath, g = 0.80F * breath, b = 1.00F * breath;
        appendCornerBrackets(DEPTH_BUFFER, box, r, g, b, 1.0F, distance, 1.0D);
        appendCornerBrackets(NO_DEPTH_BUFFER, box, r, g, b, NO_DEPTH_ALPHA, distance, 1.0D);
    }

    private static void appendBlock(World world, BlockPos pos, EnumFacing face,
            double distance, float breath) {
        AxisAlignedBB bounds = computeWorldBounds(world, pos);
        if (bounds == null) return;
        float near = 1.0F - smoothstep(NEAR_DISTANCE, FAR_DISTANCE, distance);
        float far = 1.0F - near;
        float r = (1.000F * near + 0.965F * far) * breath;
        float g = (0.900F * near + 0.608F * far) * breath;
        float b = (0.130F * near + 0.192F * far) * breath;
        AxisAlignedBB expanded = bounds.grow(LINE_OFFSET);
        appendCornerBrackets(DEPTH_BUFFER, expanded, r, g, b, 1.0F, distance, 1.0D);
        appendCornerBrackets(NO_DEPTH_BUFFER, expanded, r, g, b, NO_DEPTH_ALPHA, distance, 1.0D);
        appendFace(DEPTH_BUFFER, bounds, face, r, g, b, FOG_NEAR * near + FOG_FAR * far);
        appendFace(NO_DEPTH_BUFFER, bounds, face, r, g, b,
                NO_DEPTH_FOG_NEAR * near + NO_DEPTH_FOG_FAR * far);
    }

    static void appendCornerBrackets(BufferBuilder buffer, AxisAlignedBB box,
            float r, float g, float b, float alpha, double distance, double thicknessMultiplier) {
        double t = 0.04D * Math.max(0.25D, thicknessMultiplier)
                * Math.max(1.0D, distance / 16.0D) * 0.5D;
        appendHorizontalRing(buffer, box.minX, box.minZ, box.maxX, box.maxZ,
                box.minY, r, g, b, alpha, t);
        appendHorizontalRing(buffer, box.minX, box.minZ, box.maxX, box.maxZ,
                box.maxY, r, g, b, alpha, t);
        appendSegment(buffer, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ,
                r, g, b, alpha, 1, t);
        appendSegment(buffer, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ,
                r, g, b, alpha, 1, t);
        appendSegment(buffer, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ,
                r, g, b, alpha, 1, t);
        appendSegment(buffer, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ,
                r, g, b, alpha, 1, t);
    }

    private static void appendHorizontalRing(BufferBuilder buffer, double minX, double minZ,
            double maxX, double maxZ, double y, float r, float g, float b, float a, double t) {
        appendSegment(buffer, minX, y, minZ, maxX, y, minZ, r, g, b, a, 0, t);
        appendSegment(buffer, maxX, y, minZ, maxX, y, maxZ, r, g, b, a, 2, t);
        appendSegment(buffer, maxX, y, maxZ, minX, y, maxZ, r, g, b, a, 0, t);
        appendSegment(buffer, minX, y, maxZ, minX, y, minZ, r, g, b, a, 2, t);
    }

    private static void appendSegment(BufferBuilder buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float r, float g, float b, float a, int axis, double t) {
        if (axis == 0) {
            quad(buffer, x1,y1-t,z1, x1,y1+t,z1, x2,y2+t,z2, x2,y2-t,z2, r,g,b,a);
            quad(buffer, x1,y1,z1-t, x1,y1,z1+t, x2,y2,z2+t, x2,y2,z2-t, r,g,b,a);
        } else if (axis == 1) {
            quad(buffer, x1,y1,z1-t, x1,y1,z1+t, x2,y2,z2+t, x2,y2,z2-t, r,g,b,a);
            quad(buffer, x1-t,y1,z1, x1+t,y1,z1, x2+t,y2,z2, x2-t,y2,z2, r,g,b,a);
        } else {
            quad(buffer, x1-t,y1,z1, x1+t,y1,z1, x2+t,y2,z2, x2-t,y2,z2, r,g,b,a);
            quad(buffer, x1,y1-t,z1, x1,y1+t,z1, x2,y2+t,z2, x2,y2-t,z2, r,g,b,a);
        }
    }

    static void quad(BufferBuilder buffer,
            double x1,double y1,double z1,double x2,double y2,double z2,
            double x3,double y3,double z3,double x4,double y4,double z4,
            float r,float g,float b,float a) {
        vertex(buffer,x1,y1,z1,r,g,b,a); vertex(buffer,x2,y2,z2,r,g,b,a);
        vertex(buffer,x3,y3,z3,r,g,b,a); vertex(buffer,x4,y4,z4,r,g,b,a);
    }

    private static void vertex(BufferBuilder buffer, double x, double y, double z,
            float r, float g, float b, float a) {
        buffer.pos(x, y, z).color(r, g, b, a).endVertex();
    }

    private static void appendFace(BufferBuilder buffer, AxisAlignedBB box, EnumFacing face,
            float r, float g, float b, float a) {
        if (face == null) return;
        double x1=box.minX,x2=box.maxX,y1=box.minY,y2=box.maxY,z1=box.minZ,z2=box.maxZ,o=FOG_OFFSET;
        switch (face) {
            case DOWN: quad(buffer,x1,y1-o,z1,x2,y1-o,z1,x2,y1-o,z2,x1,y1-o,z2,r,g,b,a); break;
            case UP: quad(buffer,x1,y2+o,z1,x1,y2+o,z2,x2,y2+o,z2,x2,y2+o,z1,r,g,b,a); break;
            case NORTH: quad(buffer,x1,y1,z1-o,x2,y1,z1-o,x2,y2,z1-o,x1,y2,z1-o,r,g,b,a); break;
            case SOUTH: quad(buffer,x1,y1,z2+o,x1,y2,z2+o,x2,y2,z2+o,x2,y1,z2+o,r,g,b,a); break;
            case WEST: quad(buffer,x1-o,y1,z1,x1-o,y2,z1,x1-o,y2,z2,x1-o,y1,z2,r,g,b,a); break;
            case EAST: quad(buffer,x2+o,y1,z1,x2+o,y1,z2,x2+o,y2,z2,x2+o,y2,z1,r,g,b,a); break;
        }
    }

    private static AxisAlignedBB computeWorldBounds(World world, BlockPos start) {
        BlockState initial = BlockState.fromWorld(world, start);
        Block block = initial.getBlock();
        if (!(block instanceof BlockBed) && !(block instanceof BlockDoublePlant)) {
            return selectedBounds(world, start);
        }
        Queue<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> visited = new HashSet<BlockPos>();
        queue.add(start); visited.add(start);
        AxisAlignedBB merged = null;
        while (!queue.isEmpty()) {
            BlockPos current = queue.remove();
            BlockState state = BlockState.fromWorld(world, current);
            AxisAlignedBB one = selectedBounds(world, current);
            if (one != null) merged = merged == null ? one : merged.union(one);
            EnumFacing direction = connectedDirection(state);
            if (direction != null) {
                BlockPos next = current.offset(direction);
                if (visited.add(next) && BlockState.fromWorld(world, next).getBlock() == block) queue.add(next);
            }
        }
        return merged;
    }

    private static EnumFacing connectedDirection(BlockState state) {
        if (state.getBlock() instanceof BlockDoublePlant) {
            return BlockDoublePlant.func_149887_c(state.getMetadata())
                    ? EnumFacing.DOWN : EnumFacing.UP;
        }
        if (state.getBlock() instanceof BlockBed) {
            EnumFacing facing;
            switch (net.minecraft.block.BlockDirectional.getDirection(state.getMetadata())) {
                case 0: facing = EnumFacing.SOUTH; break;
                case 1: facing = EnumFacing.WEST; break;
                case 2: facing = EnumFacing.NORTH; break;
                default: facing = EnumFacing.EAST; break;
            }
            return BlockBed.isBlockHeadOfBed(state.getMetadata())
                    ? facing.getOpposite() : facing;
        }
        return null;
    }

    private static AxisAlignedBB selectedBounds(World world, BlockPos pos) {
        BlockState state = BlockState.fromWorld(world, pos);
        if (state.getMaterial().isReplaceable() && state.getBlock() == net.minecraft.init.Blocks.air) return null;
        return AxisAlignedBB.fromNative(state.getBlock().getSelectedBoundingBoxFromPool(
                world, pos.getX(), pos.getY(), pos.getZ()));
    }

    private static EntityHit raycastEntity(final World world, final Entity camera,
            Vec3d origin, Vec3d end, Vec3d direction) {
        AxisAlignedBB search = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(camera.boundingBox).expand(
                direction.x * MAX_REACH, direction.y * MAX_REACH, direction.z * MAX_REACH).grow(1.0D);
        List<Entity> entities = com.rtsbuilding.rtsbuilding.platform.entity.EntityCompat.getEntitiesExcluding(
                world, camera, search, new Predicate<Entity>() {
            @Override public boolean apply(Entity entity) {
                return entity != null && !entity.isDead && entity.canBeCollidedWith()
                        && entity != Minecraft.getMinecraft().thePlayer;
            }
        });
        EntityHit best = null;
        double bestDistance = MAX_REACH * MAX_REACH;
        for (Entity entity : entities) {
            AxisAlignedBB bounds = com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB.fromNative(entity.boundingBox).grow(entity.getCollisionBorderSize());
            RayTraceResult hit = bounds.calculateIntercept(origin, end);
            if (bounds.contains(origin)) {
                if (bestDistance >= 0.0D) { best = new EntityHit(entity, origin); bestDistance = 0.0D; }
            } else if (hit != null && hit.hitVec != null) {
                double distance = origin.squareDistanceTo(hit.hitVec);
                if (distance < bestDistance) { best = new EntityHit(entity, hit.hitVec); bestDistance = distance; }
            }
        }
        return best;
    }

    private static boolean isWithinBounds(ClientRtsController controller, BlockPos pos) {
        if (!controller.hasBounds()) return true;
        int minX=(int)Math.floor(controller.getAnchorX()-controller.getMaxRadius());
        int maxX=(int)Math.ceil(controller.getAnchorX()+controller.getMaxRadius())-1;
        int minZ=(int)Math.floor(controller.getAnchorZ()-controller.getMaxRadius());
        int maxZ=(int)Math.ceil(controller.getAnchorZ()+controller.getMaxRadius())-1;
        return pos.getX()>=minX && pos.getX()<=maxX && pos.getZ()>=minZ && pos.getZ()<=maxZ;
    }

    private static boolean isRotateCaptured(ClientRtsController controller) {
        try {
            Method method = controller.getClass().getMethod("isRotateCaptured");
            return Boolean.TRUE.equals(method.invoke(controller));
        } catch (ReflectiveOperationException ignored) { return false; }
    }

    private static boolean isInteractionBlockedByUi(Minecraft minecraft) {
        Object screen = minecraft.currentScreen;
        if (screen == null || !screen.getClass().getName().endsWith("BuilderScreen")) return false;
        try {
            Method mouseXMethod=screen.getClass().getMethod("getCurrentMouseX");
            Method mouseYMethod=screen.getClass().getMethod("getCurrentMouseY");
            double x=((Number)mouseXMethod.invoke(screen)).doubleValue();
            double y=((Number)mouseYMethod.invoke(screen)).doubleValue();
            Method worldArea=screen.getClass().getMethod("isWorldArea",double.class,double.class);
            if (!Boolean.TRUE.equals(worldArea.invoke(screen,x,y))) return true;

            Object layer=screen.getClass().getMethod("getFloatingWindowLayer").invoke(screen);
            Object windows=layer.getClass().getMethod("frontToBackWindows").invoke(layer);
            if(windows instanceof Iterable){
                for(Object window:(Iterable<?>)windows){
                    boolean visible=Boolean.TRUE.equals(window.getClass().getMethod("isVisibleWindow").invoke(window));
                    boolean inside=Boolean.TRUE.equals(window.getClass().getMethod(
                            "isInsideWindow",double.class,double.class).invoke(window,x,y));
                    if(visible&&inside)return true;
                }
            }

            Object shapeController=screen.getClass().getMethod("getShapeController").invoke(screen);
            Object session=shapeController.getClass().getMethod("getShapeBuildSession").invoke(shapeController);
            if(session!=null){
                String phase=String.valueOf(session.getClass().getMethod("phase").invoke(session));
                if("READY_CONFIRM".equals(phase))return true;
                boolean quickBuild=Boolean.TRUE.equals(screen.getClass().getMethod("isQuickBuildOpen").invoke(screen));
                if(quickBuild&&("NEED_SECOND_POINT".equals(phase)||"NEED_THIRD_POINT".equals(phase)))return true;
            }
            return false;
        } catch (ReflectiveOperationException ignored) {
            // 独立界面尚未完成迁移时保守阻断，避免点击 GUI 却高亮世界。
            return true;
        }
    }

    private static float breathFactor() {
        double phase=System.currentTimeMillis()/1000.0D*BREATH_SPEED*2.0D*Math.PI;
        return (float)((Math.sin(phase)+1.0D)*0.5D*(1.0F-BREATH_MIN)+BREATH_MIN);
    }

    static float smoothstep(double edge0, double edge1, double value) {
        double t=Math.max(0.0D,Math.min(1.0D,(value-edge0)/(edge1-edge0)));
        return (float)(t*t*(3.0D-2.0D*t));
    }

    private static void beginBuffers(double x, double y, double z) {
        DEPTH_BUFFER.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        DEPTH_BUFFER.setTranslation(x,y,z);
        try { NO_DEPTH_BUFFER.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR); NO_DEPTH_BUFFER.setTranslation(x,y,z); }
        catch (RuntimeException exception) { discard(DEPTH_BUFFER); throw exception; }
    }

    private static void drawOwnedBuffers() {
        GlSnapshot state=GlSnapshot.capture();
        try {
            setupCommon(); GlStateManager.enableDepth(); GlStateManager.depthMask(false);
            GlStateManager.enablePolygonOffset(); GlStateManager.doPolygonOffset(-1.0F,-1.0F);
            uploadOrReset(DEPTH_BUFFER);
            GlStateManager.disablePolygonOffset(); GlStateManager.disableDepth();
            uploadOrReset(NO_DEPTH_BUFFER);
        } finally { resetTranslations(); state.restore(); }
    }

    private static void setupCommon() {
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,GlStateManager.DestFactor.ZERO);
        GlStateManager.disableTexture2D(); GlStateManager.disableCull();
    }

    private static void uploadOrReset(BufferBuilder buffer) {
        if(buffer.getVertexCount()>0) RtsOwnedBufferUploader.draw(buffer); else discard(buffer);
    }
    private static void discardOwnedBuffers(){discard(DEPTH_BUFFER);discard(NO_DEPTH_BUFFER);resetTranslations();}
    private static void discard(BufferBuilder buffer){try{buffer.finishDrawing();}catch(IllegalStateException ignored){}buffer.reset();}
    private static void resetTranslations(){DEPTH_BUFFER.setTranslation(0,0,0);NO_DEPTH_BUFFER.setTranslation(0,0,0);}

    private static final class EntityHit { final Entity entity; final Vec3d hit; EntityHit(Entity e,Vec3d h){entity=e;hit=h;} }

    private static final class GlSnapshot {
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND),texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                polygon=GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL);
        final boolean depthMask=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final float lineWidth=GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        final float polygonFactor=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR),polygonUnits=GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS);
        final int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        static GlSnapshot capture(){return new GlSnapshot();}
        void restore(){GlStateManager.tryBlendFuncSeparate(sr,dr,sa,da);set(GL11.GL_BLEND,blend);set(GL11.GL_TEXTURE_2D,texture);
            set(GL11.GL_CULL_FACE,cull);set(GL11.GL_DEPTH_TEST,depth);set(GL11.GL_POLYGON_OFFSET_FILL,polygon);
            GlStateManager.doPolygonOffset(polygonFactor,polygonUnits);GlStateManager.depthMask(depthMask);
            GlStateManager.glLineWidth(lineWidth);GlStateManager.resetColor();}
        static void set(int cap,boolean on){RtsGlStateRestorer.restoreCapability(cap,on);}
    }
}
