package com.rtsbuilding.rtsbuilding.client.rendering.util;


import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/**
 * 灏勭嚎妫€娴嬭緟鍔╁伐鍏风被
 * 鎻愪緵榧犳爣灏勭嚎璁＄畻鍜屾柟鍧?瀹炰綋妫€娴嬪姛鑳?
 */
public final class RaycastHelper {

    /**
     * 绉佹湁鏋勯€犲嚱鏁帮紝闃叉瀹炰緥鍖?
     */
    private RaycastHelper() {
    }

    /**
     * 浠庣浉鏈轰綅缃悜榧犳爣鏂瑰悜鍙戝皠灏勭嚎锛屾娴嬪懡涓殑鏂瑰潡
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @param camPos 鐩告満璧峰浣嶇疆
     * @param to 灏勭嚎缁堢偣浣嶇疆
     * @param includeFluidSource 鏄惁鍖呭惈娴佷綋婧愭柟鍧?
     * @return 鏂瑰潡鍛戒腑缁撴灉锛屾湭鍛戒腑鍒欒繑鍥瀗ull
     */
    public static BlockHitResult raycastBlockFromCursor(Minecraft minecraft, Vec3 camPos, Vec3 to,
            boolean includeFluidSource) {
        ClipContext.Fluid fluidMode = includeFluidSource ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        HitResult hit = null;
        if (minecraft.getCameraEntity() != null) {
            if (minecraft.level != null) {
                hit = minecraft.level.clip(new ClipContext(camPos, to, ClipContext.Block.OUTLINE, fluidMode,
                        minecraft.getCameraEntity()));
            }
        }
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        return null;
    }

    /**
     * 浠庣浉鏈轰綅缃悜榧犳爣鏂瑰悜鍙戝皠灏勭嚎锛屾娴嬪懡涓殑瀹炰綋
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @param camPos 鐩告満璧峰浣嶇疆
     * @param to 灏勭嚎缁堢偣浣嶇疆
     * @param viewDir 瑙嗙嚎鏂瑰悜鍚戦噺
     * @param reach 灏勭嚎鏈€澶ц窛绂?
     * @return 瀹炰綋鍛戒腑缁撴灉锛屾湭鍛戒腑鍒欒繑鍥瀗ull
     */
    public static EntityHitResult raycastEntityFromCursor(Minecraft minecraft, Vec3 camPos, Vec3 to, Vec3 viewDir,
            double reach) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }

        // 鏋勫缓鎼滅储鑼冨洿锛氫互鐩告満涓轰腑蹇冿紝娌胯绾挎柟鍚戞墿灞?
        AABB search = cameraEntity.getBoundingBox().expandTowards(viewDir.scale(reach)).inflate(1.0D);

        // 鎵ц瀹炰綋灏勭嚎妫€娴?
        return ProjectileUtil.getEntityHitResult(
                cameraEntity,
                camPos,
                to,
                search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != minecraft.player,
                reach * reach);
    }

    /**
     * 璁＄畻榧犳爣鍏夋爣瀵瑰簲鐨勫皠绾挎柟鍚戝悜閲?
     * 鑰冭檻FOV銆佺獥鍙ｅ昂瀵搞€佺浉鏈烘湞鍚戠瓑鍥犵礌
     *
     * @param minecraft Minecraft瀹㈡埛绔疄渚?
     * @return 褰掍竴鍖栫殑灏勭嚎鏂瑰悜鍚戦噺
     */
    public static Vec3 computeCursorRayDirection(Minecraft minecraft) {
        // 鑾峰彇榧犳爣灞忓箷鍧愭爣
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        double width = Math.max(1.0D, minecraft.getWindow().getScreenWidth());
        double height = Math.max(1.0D, minecraft.getWindow().getScreenHeight());

        // 杞崲涓篘DC锛堝綊涓€鍖栬澶囧潗鏍囷級锛岃寖鍥碵-1, 1]
        double nx = (mouseX / width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / height) * 2.0D;

        // 鑾峰彇鐩告満鏈濆悜瑙掑害
        float yawDeg = minecraft.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = minecraft.gameRenderer.getMainCamera().getXRot();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        // 璁＄畻鍓嶅悜鍚戦噺锛堢浉鏈烘鍓嶆柟锛?
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();

        // 璁＄畻鍙冲悜鍚戦噺
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();

        // 璁＄畻涓婂悜鍚戦噺锛堝弶涔橈級
        Vec3 up = forward.cross(right).normalize();

        // 璁＄畻FOV鐩稿叧鐨勭缉鏀惧洜瀛?
        double fovY = Math.toRadians(minecraft.options.fov().get());
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / height);

        // 缁勫悎鏈€缁堝皠绾挎柟鍚戯細鍓嶅悜 + 姘村钩鍋忕Щ + 鍨傜洿鍋忕Щ
        // 娉ㄦ剰锛氬綋鍓峺aw鍩哄悜閲忎骇鐢熺殑鏄乏鍚戦噺锛屽洜姝ら渶瑕佸弽杞琗 NDC浠ヤ繚鎸佸睆骞曞彸渚у搴斿皠绾垮彸渚?
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }
}
