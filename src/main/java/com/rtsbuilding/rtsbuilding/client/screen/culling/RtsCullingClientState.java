package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.network.culling.C2SRtsRequestCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.culling.C2SRtsSaveCullingStatePayload;
import com.rtsbuilding.rtsbuilding.network.culling.RtsCullingBoxSnapshot;
import com.rtsbuilding.rtsbuilding.network.culling.S2CRtsCullingStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 范围剔除的客户端全局状态桥。
 *
 * <p>Mixin 和世界渲染器不能持有 BuilderScreen 实例，因此通过这里查询当前
 * 打开的 RTS 页面。它只转发只读判断，不主动创建或修改剔除区域。
 */
public final class RtsCullingClientState {
    private static final RtsCullingManager PERSISTENT_MANAGER = new RtsCullingManager();
    // Embeddium 在后台网格线程读取隐藏状态，必须安全发布当前管理器。
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

    /**
     * 切换服务器、存档或客户端世界时清空世界坐标状态，避免上一世界的剔除盒污染下一世界。
     */
    public static void resetForWorldChange() {
        activeManager = null;
        PERSISTENT_MANAGER.clearWorldState();
    }

    /** 打开 RTS 界面时请求当前存档、当前维度自己的剔除记录。 */
    public static void requestCurrentWorldState() {
        PERSISTENT_MANAGER.clearWorldState();
        PacketDistributor.sendToServer(new C2SRtsRequestCullingStatePayload());
    }

    /** 应用服务端按玩家与维度返回的剔除记录。 */
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
                boxes, PERSISTENT_MANAGER.revealedBlocks()));
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
