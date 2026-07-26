package com.rtsbuilding.rtsbuilding.client.compat;


import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.mojang.brigadier.Command;
import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsClientOnboardingReminder {
    private static final String DISMISS_COMMAND = "rtsbuilding_hide_intro";
    private static final int SHOW_DELAY_TICKS = 80;

    private static boolean shownThisConnection;
    private static int ticksUntilReminder = -1;

    private RtsClientOnboardingReminder() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(DISMISS_COMMAND).executes(context -> dismissIntroReminder()));
        event.getDispatcher().register(Commands.literal("rtsbuilding")
                .then(Commands.literal("hide")
                        .then(Commands.literal("intro").executes(context -> dismissIntroReminder()))));
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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

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
                Component.literal(currentModVersion()),
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

    /** 从当前 Forge ModContainer 读取版本，避免语言文件与实际发布包漂移。 */
    private static String currentModVersion() {
        return ModList.get().getModContainerById(RtsbuildingMod.MODID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    private static Component websiteComponent() {
        return Component.literal(RtsCommunityLinks.WEBSITE).withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RtsCommunityLinks.WEBSITE))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        Component.literal(RtsCommunityLinks.WEBSITE))));
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
