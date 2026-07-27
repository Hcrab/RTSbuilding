package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.client.compat.RtsClientOnboardingReminder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Set;

/**
 * Forge 1.12 客户端 bootstrap，同时是可由 mcmod.info 的 guiFactory 指向的配置工厂。
 * 配置屏幕仍由 screen 包拥有，本类只保留真实、可调用的 1.12 接入边界。
 */
@SideOnly(Side.CLIENT)
public final class RtsClientBootstrap implements IModGuiFactory {
    private static final String CONFIG_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsModConfigScreen";

    public RtsClientBootstrap() {
    }

    /** 客户端 proxy 可在 preInit 直接调用；重复调用安全。 */
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
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        try {
            Class<?> screenType = Class.forName(CONFIG_SCREEN);
            Constructor<?> constructor = screenType.getConstructor(GuiScreen.class);
            Object screen = constructor.newInstance(parentScreen);
            if (!(screen instanceof GuiScreen)) {
                throw new IllegalStateException(CONFIG_SCREEN + " 不是 Forge 1.12 GuiScreen");
            }
            return (GuiScreen) screen;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法创建 RTSBuilding 1.12 配置界面", failure);
        }
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
