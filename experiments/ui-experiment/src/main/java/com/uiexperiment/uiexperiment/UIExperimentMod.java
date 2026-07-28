package com.uiexperiment.uiexperiment;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

@Mod("uiexperiment")
public class UIExperimentMod {

    public static final String MODID = "uiexperiment";

    public static ShaderInstance roundedRectShader;
    public static ShaderInstance triangleShader;
    private static KeyMapping openTestKey;

    public UIExperimentMod(IEventBus modBus) {
        modBus.addListener(ModClientEvents::onRegisterShaders);
        modBus.addListener(ModClientEvents::onRegisterKeyMappings);
    }

    public static class ModClientEvents {
        public static void onRegisterShaders(RegisterShadersEvent event) {
            try {
                event.registerShader(
                        new ShaderInstance(
                                event.getResourceProvider(),
                                ResourceLocation.fromNamespaceAndPath(MODID, "rounded_rect"),
                                DefaultVertexFormat.POSITION_TEX_COLOR
                        ),
                        shader -> roundedRectShader = shader
                );
                event.registerShader(
                        new ShaderInstance(
                                event.getResourceProvider(),
                                ResourceLocation.fromNamespaceAndPath(MODID, "triangle"),
                                DefaultVertexFormat.POSITION_TEX_COLOR
                        ),
                        shader -> triangleShader = shader
                );
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to load shader", e);
            }
        }

        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            openTestKey = new KeyMapping(
                    "key.uiexperiment.open_test",
                    GLFW.GLFW_KEY_U,
                    "key.categories.uiexperiment"
            );
            event.register(openTestKey);
        }
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (openTestKey != null && openTestKey.consumeClick()) {
                Minecraft.getInstance().setScreen(new TestScreen());
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            if (event.getScreen() instanceof TitleScreen) {
                int y = event.getScreen().height / 4 + 168;
                event.addListener(Button.builder(
                        Component.literal("SDF Test"),
                        btn -> Minecraft.getInstance().setScreen(new TestScreen())
                ).bounds(event.getScreen().width / 2 + 104, y, 40, 20).build());
            }
        }
    }
}
