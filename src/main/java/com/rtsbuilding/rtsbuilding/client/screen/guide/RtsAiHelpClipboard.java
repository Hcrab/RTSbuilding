package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        boolean chinese = language != null && language.toLowerCase(java.util.Locale.ROOT).startsWith("zh_");
        String tutorial = readTutorial(minecraft, chinese);
        if (tutorial == null) {
            return false;
        }
        Path loaderGameDir = FMLPaths.GAMEDIR.get();
        RtsLatestLogExcerpt.Result logs = RtsLatestLogExcerpt.readFirstAvailable(
                loaderGameDir.resolve("logs").resolve("latest.log"),
                minecraft.gameDirectory.toPath().resolve("logs").resolve("latest.log"),
                Path.of("").toAbsolutePath().resolve("logs").resolve("latest.log"));

        String prompt = RtsAiHelpPrompt.compose(
                chinese,
                modVersion(RtsbuildingMod.MODID),
                SharedConstants.getCurrentVersion().getName(),
                modVersion("forge"),
                language,
                localizedMode(controller == null ? null : controller.getMode()),
                tutorial,
                logs.latestLines(),
                logs.rtsLines(),
                logs.available());
        minecraft.keyboardHandler.setClipboard(prompt);
        return true;
    }

    private static String readTutorial(Minecraft minecraft, ResourceLocation id) {
        try {
            return minecraft.getResourceManager().getResource(id)
                    .map(resource -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                resource.open(), StandardCharsets.UTF_8))) {
                            return reader.lines().collect(Collectors.joining("\n"));
                        } catch (IOException ignored) {
                            return null;
                        }
                    })
                    .orElse(null);
        } catch (RuntimeException ignored) {
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
            return ModList.get().getModContainerById(modId)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (RuntimeException | LinkageError ignored) {
            return "unknown";
        }
    }

    private static String localizedMode(BuilderMode mode) {
        if (mode == null) {
            return Component.translatable("screen.rtsbuilding.mode.idle").getString();
        }
        String key = switch (mode) {
            case INTERACT -> "screen.rtsbuilding.mode.interact";
            case LINK_STORAGE -> "screen.rtsbuilding.mode.link_storage";
            case FUNNEL -> "screen.rtsbuilding.mode.funnel";
            case ROTATE -> "screen.rtsbuilding.mode.rotate";
            case SELECT_PAN -> "screen.rtsbuilding.mode.camera";
            case OFF -> "screen.rtsbuilding.mode.idle";
        };
        return Component.translatable(key).getString();
    }
}
