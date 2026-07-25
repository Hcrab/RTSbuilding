package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiCatalog;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiTopic;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * 将随版本发布的教程目录与当前客户端状态装配成 AI 的隐藏知识库。
 *
 * <p>教程正文直接来自生产 UI 使用的 i18n 键，避免聊天窗口维护第二份容易过期的操作说明。
 * 本类不发送网络请求；它只生成有长度上限的纯文本。
 */
public final class RtsAiKnowledgeBase {
    private static final int MAX_KNOWLEDGE_CHARS = 24_000;

    private RtsAiKnowledgeBase() {
    }

    public static String build(ClientRtsController controller) {
        Minecraft minecraft = Minecraft.getInstance();
        String language = minecraft.getLanguageManager().getSelected();
        boolean chinese = language != null && language.toLowerCase(Locale.ROOT).startsWith("zh_");
        StringBuilder text = new StringBuilder(12_000);

        if (chinese) {
            text.append("## 当前游戏信息\n");
            appendInfo(text, "RTSBuilding 版本", modVersion(RtsbuildingMod.MODID));
            appendInfo(text, "Minecraft 版本", SharedConstants.getCurrentVersion().getName());
            appendInfo(text, "NeoForge 版本", modVersion("neoforge"));
            appendInfo(text, "语言", language);
            appendInfo(text, "当前 RTS 模式", localizedMode(controller == null ? null : controller.getMode()));
            text.append("\n## 随当前版本发布的教程\n");
        } else {
            text.append("## Current game information\n");
            appendInfo(text, "RTSBuilding version", modVersion(RtsbuildingMod.MODID));
            appendInfo(text, "Minecraft version", SharedConstants.getCurrentVersion().getName());
            appendInfo(text, "NeoForge version", modVersion("neoforge"));
            appendInfo(text, "Language", language);
            appendInfo(text, "Current RTS mode", localizedMode(controller == null ? null : controller.getMode()));
            text.append("\n## Tutorials bundled with this version\n");
        }

        appendGuide(text, GuideUiContext.TOP);
        appendGuide(text, GuideUiContext.BOTTOM);
        appendGuide(text, GuideUiContext.SETTINGS);

        text.append(chinese ? "\n## 继续求助\n" : "\n## More help\n");
        appendInfo(text, "Website", RtsCommunityLinks.WEBSITE);
        appendInfo(text, "GitHub", RtsCommunityLinks.GITHUB_REPOSITORY);
        appendInfo(text, "Discord", RtsCommunityLinks.DISCORD_INVITE);
        appendInfo(text, "QQ", RtsCommunityLinks.QQ_GROUP);

        if (text.length() > MAX_KNOWLEDGE_CHARS) {
            return text.substring(0, MAX_KNOWLEDGE_CHARS);
        }
        return text.toString();
    }

    /**
     * 复制给外部 AI 时附带玩家此前明确要求的诊断尾部；游戏内普通问答不上传日志。
     */
    public static String buildForClipboard(ClientRtsController controller, boolean chinese) {
        Minecraft minecraft = Minecraft.getInstance();
        StringBuilder text = new StringBuilder(build(controller));
        RtsLatestLogExcerpt.Result logs = RtsLatestLogExcerpt.read(
                minecraft.gameDirectory.toPath().resolve("logs").resolve("latest.log"));
        if (!logs.available()) {
            text.append(chinese
                    ? "\n\n## 诊断日志\nlatest.log 当前不可用。"
                    : "\n\n## Diagnostic logs\nlatest.log is currently unavailable.");
            return text.toString();
        }
        text.append(chinese
                ? "\n\n## latest.log 最后 200 行\n"
                : "\n\n## Last 200 lines of latest.log\n");
        text.append(logs.latestLines().isBlank() ? "(none)" : logs.latestLines());
        text.append(chinese
                ? "\n\n## 最近 50 行 RTSBuilding 相关日志\n"
                : "\n\n## Latest 50 RTSBuilding-related log lines\n");
        text.append(logs.rtsLines().isBlank() ? "(none)" : logs.rtsLines());
        return text.toString();
    }

    private static void appendGuide(StringBuilder text, GuideUiContext context) {
        text.append("\n### ")
                .append(Component.translatable(GuideUiCatalog.titleKey(context)).getString())
                .append('\n');
        for (GuideUiTopic topic : GuideUiCatalog.topics(context)) {
            text.append("\n#### ")
                    .append(Component.translatable(topic.titleKey).getString())
                    .append('\n');
            for (String lineKey : topic.lineKeys) {
                text.append("- ").append(Component.translatable(lineKey).getString()).append('\n');
            }
        }
    }

    private static void appendInfo(StringBuilder text, String label, String value) {
        text.append("- ").append(label).append(": ")
                .append(value == null || value.isBlank() ? "unknown" : value.strip())
                .append('\n');
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
