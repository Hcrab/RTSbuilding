package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.client.compat.RtsClientOnboardingReminder;
import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Collections;
import java.util.Set;

/** 1.7.10 客户端引导与模组列表配置界面工厂。 */
@SideOnly(Side.CLIENT)
public final class RtsClientBootstrap implements IModGuiFactory {
    private static final String CONFIG_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsModConfigScreen";

    public static void registerClient() {
        RtsClientOnboardingReminder.registerClientCommand();
        try {
            Class<?> events = Class.forName(
                    "com.rtsbuilding.rtsbuilding.client.bootstrap.RtsClientModEvents");
            events.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("注册 RTSBuilding 客户端生命周期失败", failure);
        }
    }

    @Override
    public void initialize(Minecraft minecraftInstance) {
        registerClient();
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        try {
            return Class.forName(CONFIG_SCREEN).asSubclass(GuiScreen.class);
        } catch (ReflectiveOperationException | ClassCastException failure) {
            throw new IllegalStateException("无法加载 RTSBuilding 1.7.10 配置界面", failure);
        }
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }
}
