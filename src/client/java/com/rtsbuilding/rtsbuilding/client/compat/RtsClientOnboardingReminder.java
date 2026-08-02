package com.rtsbuilding.rtsbuilding.client.compat;


import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.storage.LevelResource;
import net.fabricmc.loader.api.FabricLoader;

public final class RtsClientOnboardingReminder {
    private static final String DISMISS_COMMAND = "rtsbuilding_hide_intro";
    private static final String STABLE_VERSION = "1.1.6";
    private static final int SHOW_DELAY_TICKS = 80;

    private static boolean shownThisConnection;
    private static int ticksUntilReminder = -1;

    private RtsClientOnboardingReminder() {
    }

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal(DISMISS_COMMAND)
                    .executes(context -> dismissIntroReminder()));
            dispatcher.register(ClientCommandManager.literal("rtsbuilding")
                    .then(ClientCommandManager.literal("hide")
                            .then(ClientCommandManager.literal("intro")
                                    .executes(context -> dismissIntroReminder()))));
        });
    }

    private static int dismissIntroReminder() {
        Minecraft minecraft = Minecraft.getInstance();
        String key = currentReminderKey(minecraft);
        if (key.isBlank()) {
            return 0;
        }
        RtsClientUiStateStore.dismissIntroReminder(key);
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("chat.rtsbuilding.intro.dismissed"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    public static void onClientTickPost() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            shownThisConnection = false;
            ticksUntilReminder = -1;
            return;
        }

        if (shownThisConnection) {
            return;
        }
        if (ticksUntilReminder < 0) {
            ticksUntilReminder = SHOW_DELAY_TICKS;
        }
        if (ticksUntilReminder-- > 0) {
            return;
        }

        String key = currentReminderKey(minecraft);
        if (key.isBlank()) {
            // 服务器/存档身份尚未稳定时稍后重试，避免把提醒错误地记到维度或全局键上。
            ticksUntilReminder = SHOW_DELAY_TICKS;
            return;
        }
        shownThisConnection = true;
        if (RtsClientUiStateStore.isIntroReminderDismissed(key)) {
            return;
        }

        minecraft.player.displayClientMessage(Component.translatable(
                "chat.rtsbuilding.intro.rts_key",
                Component.keybind("key.rtsbuilding.toggle_rts")).withStyle(ChatFormatting.AQUA), false);
        minecraft.player.displayClientMessage(Component.translatable(
                "chat.rtsbuilding.intro.version_warning",
                Component.literal(currentDisplayVersion()),
                Component.literal(STABLE_VERSION),
                websiteComponent())
                .withStyle(ChatFormatting.GOLD), false);
        minecraft.player.displayClientMessage(Component.translatable(
                "chat.rtsbuilding.intro.feedback",
                discordComponent(),
                githubComponent(),
                Component.literal(RtsCommunityLinks.QQ_GROUP).withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, RtsCommunityLinks.QQ_GROUP))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.rtsbuilding.intro.copy_qq")))))
                .withStyle(ChatFormatting.GRAY), false);
        minecraft.player.displayClientMessage(Component.translatable("chat.rtsbuilding.intro.config_hint")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" "))
                .append(Component.translatable("chat.rtsbuilding.intro.dismiss").withStyle(style -> style
                        .withColor(ChatFormatting.YELLOW)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + DISMISS_COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.rtsbuilding.intro.dismiss.hover"))))), false);
    }

    private static Component discordComponent() {
        return Component.literal(RtsCommunityLinks.DISCORD_INVITE).withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RtsCommunityLinks.DISCORD_INVITE))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(RtsCommunityLinks.DISCORD_INVITE))));
    }

    /**
     * 从实际加载的 ModContainer 读取当前版本系列。
     *
     * <p>入门提醒面向普通玩家，只展示 {@code 1.1.6} 这一公开版本系列；
     * Patch/Pilot 构建自身仍保留完整限定符的 JAR 元数据，便于日志诊断。
     */
    private static String currentDisplayVersion() {
        return FabricLoader.getInstance()
                .getModContainer(RtsbuildingMod.MODID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static Component websiteComponent() {
        return Component.literal(RtsCommunityLinks.WEBSITE).withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RtsCommunityLinks.WEBSITE))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(RtsCommunityLinks.WEBSITE))));
    }

    private static Component githubComponent() {
        return Component.literal(RtsCommunityLinks.GITHUB_REPOSITORY).withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RtsCommunityLinks.GITHUB_REPOSITORY))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(RtsCommunityLinks.GITHUB_REPOSITORY))));
    }

    private static String currentReminderKey(Minecraft minecraft) {
        if (minecraft == null) {
            return "";
        }
        if (minecraft.getSingleplayerServer() != null) {
            return RtsIntroReminderScope.singleplayerKey(
                    minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT));
        }
        if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null) {
            return RtsIntroReminderScope.serverKey(minecraft.getCurrentServer().ip);
        }
        return "";
    }
}
