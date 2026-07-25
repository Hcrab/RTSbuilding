package com.rtsbuilding.rtsbuilding.client.screen.guide;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.util.Locale;

/**
 * 将随版本发布的完整 Markdown 教程与当前客户端状态装配成 AI 的隐藏知识库。
 *
 * <p>游戏内问答与“复制提示词与教程”读取同一份教程资源。若资源包异常缺失 Markdown，
 * 才回退到生产 UI 使用的 i18n 教程目录。本类不发送网络请求；它只生成有长度上限的纯文本。
 */
public final class RtsAiKnowledgeBase {
    private static final int MAX_KNOWLEDGE_CHARS = 40_000;

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
            appendInfo(text, "Forge 版本", modVersion("forge"));
            appendInfo(text, "语言", language);
            appendInfo(text, "当前 RTS 模式", localizedMode(controller == null ? null : controller.getMode()));
            text.append("\n## 随当前版本发布的教程\n");
        } else {
            text.append("## Current game information\n");
            appendInfo(text, "RTSBuilding version", modVersion(RtsbuildingMod.MODID));
            appendInfo(text, "Minecraft version", SharedConstants.getCurrentVersion().getName());
            appendInfo(text, "Forge version", modVersion("forge"));
            appendInfo(text, "Language", language);
            appendInfo(text, "Current RTS mode", localizedMode(controller == null ? null : controller.getMode()));
            text.append("\n## Tutorials bundled with this version\n");
        }

        String tutorial = RtsAiHelpClipboard.readTutorial(minecraft, chinese);
        if (tutorial != null && !tutorial.isBlank()) {
            text.append('\n').append(tutorial.strip()).append('\n');
        } else {
            appendGuide(text, GuideTypes.GuideContext.TOP);
            appendGuide(text, GuideTypes.GuideContext.BOTTOM);
            appendGuide(text, GuideTypes.GuideContext.SETTINGS);
        }

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

    private static void appendGuide(StringBuilder text, GuideTypes.GuideContext context) {
        String titleKey = switch (context) {
            case TOP -> "screen.rtsbuilding.guide.top.title";
            case BOTTOM -> "screen.rtsbuilding.guide.bottom.title";
            case SETTINGS -> "screen.rtsbuilding.guide.settings.title";
        };
        text.append("\n### ").append(Component.translatable(titleKey).getString()).append('\n');
        for (GuideTypes.GuideTopic topic : guideTopics(context)) {
            text.append("\n#### ")
                    .append(Component.translatable(topic.titleKey()).getString())
                    .append('\n');
            for (String lineKey : topic.lineKeys()) {
                text.append("- ").append(Component.translatable(lineKey).getString()).append('\n');
            }
        }
    }

    /**
     * Markdown 教程资源缺失时，回退到 Forge 1.20.1 已有的完整教程目录。
     *
     * <p>这只是资源异常的薄适配；正常路径仍与主线共用随版本发布的 Markdown。
     */
    private static GuideTypes.GuideTopic[] guideTopics(GuideTypes.GuideContext context) {
        return switch (context) {
            case TOP -> new GuideTypes.GuideTopic[]{
                    topic("screen.rtsbuilding.guide.top.interact.title", "screen.rtsbuilding.guide.top.interact.1",
                            "screen.rtsbuilding.guide.top.interact.2", "screen.rtsbuilding.guide.top.interact.3",
                            "screen.rtsbuilding.guide.top.interact.4"),
                    topic("screen.rtsbuilding.guide.top.camera.title", "screen.rtsbuilding.guide.top.camera.1",
                            "screen.rtsbuilding.guide.top.camera.2", "screen.rtsbuilding.guide.top.camera.3",
                            "screen.rtsbuilding.guide.top.camera.4"),
                    topic("screen.rtsbuilding.guide.top.link.title", "screen.rtsbuilding.guide.top.link.1",
                            "screen.rtsbuilding.guide.top.link.2"),
                    topic("screen.rtsbuilding.guide.top.funnel.title", "screen.rtsbuilding.guide.top.funnel.1",
                            "screen.rtsbuilding.guide.top.funnel.2"),
                    topic("screen.rtsbuilding.guide.top.rotate.title", "screen.rtsbuilding.guide.top.rotate.1"),
                    topic("screen.rtsbuilding.guide.top.build.title", "screen.rtsbuilding.guide.top.build.1",
                            "screen.rtsbuilding.guide.top.build.2", "screen.rtsbuilding.guide.top.build.3"),
                    topic("screen.rtsbuilding.guide.top.ultimine.title", "screen.rtsbuilding.guide.top.ultimine.1",
                            "screen.rtsbuilding.guide.top.ultimine.2"),
                    topic("screen.rtsbuilding.guide.top.chunk.title", "screen.rtsbuilding.guide.top.chunk.1")
            };
            case BOTTOM -> new GuideTypes.GuideTopic[]{
                    topic("screen.rtsbuilding.guide.bottom.sort.title", "screen.rtsbuilding.guide.bottom.sort.1",
                            "screen.rtsbuilding.guide.bottom.sort.2", "screen.rtsbuilding.guide.bottom.sort.3",
                            "screen.rtsbuilding.guide.bottom.sort.4"),
                    topic("screen.rtsbuilding.guide.bottom.remote.title", "screen.rtsbuilding.guide.bottom.remote.1",
                            "screen.rtsbuilding.guide.bottom.remote.2", "screen.rtsbuilding.guide.bottom.remote.3"),
                    topic("screen.rtsbuilding.guide.bottom.main.title", "screen.rtsbuilding.guide.bottom.main.1",
                            "screen.rtsbuilding.guide.bottom.main.2", "screen.rtsbuilding.guide.bottom.main.3",
                            "screen.rtsbuilding.guide.bottom.main.4"),
                    topic("screen.rtsbuilding.guide.bottom.recent_pins.title",
                            "screen.rtsbuilding.guide.bottom.recent_pins.1",
                            "screen.rtsbuilding.guide.bottom.recent_pins.2",
                            "screen.rtsbuilding.guide.bottom.recent_pins.3"),
                    topic("screen.rtsbuilding.guide.bottom.craft_panel.title",
                            "screen.rtsbuilding.guide.bottom.craft_panel.1",
                            "screen.rtsbuilding.guide.bottom.craft_panel.2")
            };
            case SETTINGS -> new GuideTypes.GuideTopic[]{
                    topic("screen.rtsbuilding.guide.settings.sensitivity.title",
                            "screen.rtsbuilding.guide.settings.sensitivity.1",
                            "screen.rtsbuilding.guide.settings.sensitivity.2"),
                    topic("screen.rtsbuilding.guide.settings.ui_scale.title",
                            "screen.rtsbuilding.guide.settings.ui_scale.1",
                            "screen.rtsbuilding.guide.settings.ui_scale.2"),
                    topic("screen.rtsbuilding.guide.settings.autostore.title",
                            "screen.rtsbuilding.guide.settings.autostore.1",
                            "screen.rtsbuilding.guide.settings.autostore.2"),
                    topic("screen.rtsbuilding.guide.settings.placed_recovery.title",
                            "screen.rtsbuilding.guide.settings.placed_recovery.1",
                            "screen.rtsbuilding.guide.settings.placed_recovery.2"),
                    topic("screen.rtsbuilding.guide.settings.config.title",
                            "screen.rtsbuilding.guide.settings.config.1",
                            "screen.rtsbuilding.guide.settings.config.2")
            };
        };
    }

    private static GuideTypes.GuideTopic topic(String titleKey, String... lines) {
        return new GuideTypes.GuideTopic(GuideTypes.GuideIcon.GRID, titleKey, lines);
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
