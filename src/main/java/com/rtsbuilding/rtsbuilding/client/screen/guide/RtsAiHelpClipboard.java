package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeVersion;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * 把客户端运行状态与随 JAR 发布的教程装配成 AI 求助文本，并写入系统剪贴板。
 *
 * <p>这里不拥有教程 UI，也不会发起网络请求。首版入口完全离线工作，玩家可自行选择
 * ChatGPT、Gemini 或其他 AI；教程文本随模组版本固定，避免把隐私或 API 密钥交给模组。
 */
public final class RtsAiHelpClipboard {
    private static final ResourceLocation CHINESE_TUTORIAL =
            new ResourceLocation(RtsbuildingMod.MODID, "tutorial/ai_help_zh_cn.md");
    private static final ResourceLocation ENGLISH_TUTORIAL =
            new ResourceLocation(RtsbuildingMod.MODID, "tutorial/ai_help_en_us.md");

    private RtsAiHelpClipboard() {
    }

    public static boolean copy(ClientRtsController controller) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String language = minecraft.getLanguageManager().getCurrentLanguage().getLanguageCode();
        boolean chinese = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("zh_");
        String tutorial = readTutorial(minecraft, chinese);
        if (tutorial == null) {
            return false;
        }
        Path loaderGameDir = com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.gameDir(minecraft).toPath();
        RtsLatestLogExcerpt.Result logs = RtsLatestLogExcerpt.readFirstAvailable(
                loaderGameDir.resolve("logs").resolve("latest.log"),
                com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.gameDir(minecraft).toPath().resolve("logs").resolve("latest.log"),
                Paths.get("").toAbsolutePath().resolve("logs").resolve("latest.log"));

        String prompt = RtsAiHelpPrompt.compose(
                chinese,
                modVersion(RtsbuildingMod.MODID),
                cpw.mods.fml.common.Loader.MC_VERSION,
                ForgeVersion.getVersion(),
                language,
                localizedMode(controller == null ? null : controller.getMode()),
                tutorial,
                logs.latestLines(),
                logs.rtsLines(),
                logs.available());
        GuiScreen.setClipboardString(prompt);
        return true;
    }

    private static String readTutorial(Minecraft minecraft, ResourceLocation id) {
        try {
            IResource resource = minecraft.getResourceManager().getResource(id);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 读取与当前语言匹配的正式 Markdown 教程，供复制入口和游戏内 AI 共用。
     *
     * <p>包级可见是刻意的：教程资源的选择只保留一个实现，避免两条求助链路逐渐使用不同资料。</p>
     */
    static String readTutorial(Minecraft minecraft, boolean chinese) {
        return readTutorial(minecraft, chinese ? CHINESE_TUTORIAL : ENGLISH_TUTORIAL);
    }

    private static String modVersion(String modId) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(modId);
            return container == null ? "unknown" : container.getVersion();
        } catch (RuntimeException | LinkageError ignored) {
            return "unknown";
        }
    }

    private static String localizedMode(BuilderMode mode) {
        if (mode == null) {
            return I18n.format("screen.rtsbuilding.mode.idle");
        }
        String key;
        switch (mode) {
            case INTERACT: key = "screen.rtsbuilding.mode.interact"; break;
            case LINK_STORAGE: key = "screen.rtsbuilding.mode.link_storage"; break;
            case FUNNEL: key = "screen.rtsbuilding.mode.funnel"; break;
            case ROTATE: key = "screen.rtsbuilding.mode.rotate"; break;
            case SELECT_PAN: key = "screen.rtsbuilding.mode.camera"; break;
            case OFF: default: key = "screen.rtsbuilding.mode.idle"; break;
        }
        return I18n.format(key);
    }
}
