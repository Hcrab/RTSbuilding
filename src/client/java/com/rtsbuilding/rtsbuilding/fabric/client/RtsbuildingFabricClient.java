package com.rtsbuilding.rtsbuilding.fabric.client;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientModEvents;
import com.rtsbuilding.rtsbuilding.client.compat.RtsClientOnboardingReminder;
import com.rtsbuilding.rtsbuilding.client.compat.RtsGuiCompatProbe;
import com.rtsbuilding.rtsbuilding.client.input.ClientInputHandler;
import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsPluginInventoryScreenEvents;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsVisualOverlayRenderer;
import com.rtsbuilding.rtsbuilding.network.RtsFabricClientNetworking;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginItem;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/** Fabric 1.21.1 客户端唯一装配入口。 */
public final class RtsbuildingFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("rts_building");
        Config.CLIENT_SPEC.load(configDirectory.resolve("rtsbuilding-client.json"));

        RtsPluginItem.installControlDownSupplier(Screen::hasControlDown);
        RtsFabricClientNetworking.register();
        ClientKeyMappings.register();
        RtsClientModEvents.initialize();
        RtsClientOnboardingReminder.initialize();
        RtsGuiCompatProbe.initialize();

        ClientTickEvents.START_CLIENT_TICK.register(client -> ClientInputHandler.onClientTickPre());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientInputHandler.onClientTickPost();
            RtsClientOnboardingReminder.onClientTickPost();
            RtsGuiCompatProbe.onClientTickPost();
        });
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> RtsClientInputGate.onClientLoggingIn());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> RtsClientInputGate.onClientLoggingOut());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(RtsVisualOverlayRenderer::onRenderLevel);
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            RtsFabricScreenInput.register(screen);
            RtsPluginInventoryScreenEvents.onInventoryInit(new RtsScreenEvent.Init.Post(
                    screen, widget -> Screens.getButtons(screen).add(widget)));
            ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, partialTick) ->
                    RtsClientInputGate.onScreenRenderPost(new RtsScreenEvent.Render.Post(
                            current, graphics, mouseX, mouseY, partialTick)));
        });
    }
}
