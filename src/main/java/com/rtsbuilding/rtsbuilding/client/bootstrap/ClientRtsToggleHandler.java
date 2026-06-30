package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * RTS 模式开关按键处理器——监听 {@link RtsKeyMappings#TOGGLE_RTS_KEY} 按键并切换 RTS 模式。
 *
 * <p>当玩家按下默认 G 键时，检测当前 RTS 相机状态：
 * <ul>
 *   <li>如果已激活 → 发送关闭请求</li>
 *   <li>如果未激活 → 发送开启请求</li>
 * </ul>
 * 实际开关由服务端 {@link com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager} 处理。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientRtsToggleHandler {

    private ClientRtsToggleHandler() {}

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        // 检查 RTS 开关按键是否在本次 tick 中被按下
        if (!RtsKeyMappings.TOGGLE_RTS_KEY.consumeClick()) {
            return;
        }

        RtsClientKernel kernel = RtsClientKernel.get();
        if (!kernel.isInitialized()) return;

        // 获取相机模块状态，判断 RTS 模式是否已激活
        CameraModule cam = kernel.module(CameraModule.class);
        boolean currentlyEnabled = cam != null && cam.getState().isEnabled();

        // 发送切换请求到服务端（true = 从玩家头部位置开启，false = 关闭）
        RtsClientPacketGateway.sendToggleCamera(!currentlyEnabled);
    }
}
