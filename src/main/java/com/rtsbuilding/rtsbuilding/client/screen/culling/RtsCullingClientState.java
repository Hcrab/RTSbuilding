package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
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
        // 只有正式盒子、尺寸和显式可见方块会触发此回调；草稿不污染服务器记录。
        PERSISTENT_MANAGER.setStateChangeListener(RtsCullingClientState::saveCurrentWorldState);
    }

    public static RtsCullingManager persistentManager() {
        return PERSISTENT_MANAGER;
    }

    public static void setActiveManager(RtsCullingManager manager) {
        activeManager = manager;
        if (manager != null) {
            manager.refreshWorldCullRendering();
            // BuilderScreen 初始化后在这里发布当前管理器，因而可在不耦合屏幕生命周期的前提下请求本维度记录。
            requestCurrentWorldState();
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

    /** 切换存档或服务器时丢弃旧世界坐标，防止剔除状态串到新世界。 */
    public static void resetForWorldChange() {
        activeManager = null;
        PERSISTENT_MANAGER.clearWorldState();
    }

    /** 打开 RTS 时请求服务端为当前玩家、当前维度保存的状态。 */
    public static void requestCurrentWorldState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        PERSISTENT_MANAGER.replaceWorldState(List.of(), List.of());
        RtsClientNetworkBridge.send(new C2SRtsRequestCullingStatePayload());
    }

    /** 仅接受服务端返回的当前维度快照，过期的跨维度响应保持让行。 */
    public static void applyCurrentWorldState(S2CRtsCullingStatePayload payload) {
        if (payload == null) {
            PERSISTENT_MANAGER.replaceWorldState(List.of(), List.of());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String currentDimension = minecraft.level == null
                ? ""
                : minecraft.level.dimension().identifier().toString();
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

    private static void saveCurrentWorldState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getConnection() == null) {
            return;
        }
        List<RtsCullingBoxSnapshot> boxes = PERSISTENT_MANAGER.boxes().stream()
                .map(box -> new RtsCullingBoxSnapshot(box.min(), box.max()))
                .toList();
        RtsClientNetworkBridge.send(new C2SRtsSaveCullingStatePayload(
                minecraft.level.dimension().identifier().toString(),
                boxes,
                PERSISTENT_MANAGER.revealedBlocks()));
    }
}
