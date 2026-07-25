package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.network.culling.C2SRtsRequestCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.culling.C2SRtsSaveCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import com.rtsbuilding.rtsbuilding.network.culling.S2CRtsCullingStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 范围剔除的客户端当前世界桥。
 *
 * <p>Mixin 和世界渲染器不能持有 BuilderScreen，因此从这里读取当前管理器。持久化身份由服务端
 * 当前存档和当前维度决定；这里仅保存当前已连接世界的渲染副本。</p>
 */
public final class RtsCullingClientState {
    private static final RtsCullingManager PERSISTENT_MANAGER = new RtsCullingManager();
    private static volatile RtsCullingManager activeManager;

    private RtsCullingClientState() {
    }

    static {
        PERSISTENT_MANAGER.setStateChangeListener(RtsCullingClientState::saveCurrentWorldState);
    }

    public static RtsCullingManager persistentManager() {
        return PERSISTENT_MANAGER;
    }

    public static void setActiveManager(RtsCullingManager manager) {
        activeManager = manager;
        if (manager != null) {
            manager.refreshWorldCullRendering();
        }
    }

    public static void clearActiveManager(RtsCullingManager manager) {
        if (activeManager == manager) {
            activeManager = null;
            // 先停止剔除，再按盒子范围重建网格，让普通视角立即恢复真实方块。
            manager.refreshWorldCullRendering();
        }
    }

    public static RtsCullingManager activeManager() {
        return activeManager;
    }

    /** 切换服务器、存档或维度时清空旧世界坐标，防止跨存档剔除。 */
    public static void resetForWorldChange() {
        activeManager = null;
        PERSISTENT_MANAGER.clearWorldState();
    }

    /** 打开 RTS 时从当前服务器、当前存档、当前维度请求剔除记录。 */
    public static void requestCurrentWorldState() {
        PERSISTENT_MANAGER.clearWorldState();
        PacketDistributor.sendToServer(new C2SRtsRequestCullingStatePayload());
    }

    /** 应用服务端当前存档、当前维度的权威快照。 */
    public static void applyCurrentWorldState(S2CRtsCullingStatePayload payload) {
        if (payload == null) {
            PERSISTENT_MANAGER.clearWorldState();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String currentDimension = minecraft.level == null
                ? ""
                : minecraft.level.dimension().location().toString();
        if (!currentDimension.equals(payload.dimension())) {
            return;
        }
        List<RtsCullingBox> boxes = new ArrayList<>(payload.boxes().size());
        int id = 1;
        for (RtsCullingBoxSnapshot box : payload.boxes()) {
            boxes.add(new RtsCullingBox(id++, box.min(), box.max()));
        }
        PERSISTENT_MANAGER.replaceWorldState(boxes, payload.revealed());
    }

    private static void saveCurrentWorldState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        List<RtsCullingBoxSnapshot> boxes = PERSISTENT_MANAGER.boxes().stream()
                .map(box -> new RtsCullingBoxSnapshot(box.min(), box.max()))
                .toList();
        PacketDistributor.sendToServer(new C2SRtsSaveCullingStatePayload(
                minecraft.level.dimension().location().toString(),
                boxes,
                PERSISTENT_MANAGER.revealedBlocks()));
    }

    public static boolean shouldCull(BlockPos pos) {
        return activeManager != null && activeManager.shouldCullWorldBlock(pos);
    }

    public static void revealLikelyPlacement(BlockPos clickedPos, Direction face) {
        if (activeManager == null) {
            return;
        }
        activeManager.revealWorldBlock(clickedPos);
        if (clickedPos != null && face != null) {
            activeManager.revealWorldBlock(clickedPos.relative(face));
        }
    }

    public static double distanceAfterCulledBlock(Vec3 origin, Vec3 direction, BlockPos pos, double maxDistance) {
        if (activeManager == null) {
            return -1.0D;
        }
        return activeManager.distanceAfterCulledBlock(origin, direction, pos, maxDistance);
    }
}
