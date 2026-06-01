package com.rtsbuilding.rtsbuilding.client;

import com.mojang.brigadier.Command;
import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class RtsClientOnboardingReminder {
    private static final String DISMISS_COMMAND = "rtsbuilding_hide_intro";
    private static final int SHOW_DELAY_TICKS = 80;

    private static boolean shownThisConnection;
    private static int ticksUntilReminder = -1;

    private RtsClientOnboardingReminder() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(DISMISS_COMMAND).executes(context -> {
            RtsClientUiStateStore.dismissIntroReminder(currentReminderKey(Minecraft.getInstance()));
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("chat.rtsbuilding.intro.dismissed"), false);
            }
            return Command.SINGLE_SUCCESS;
        }));
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
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
        shownThisConnection = true;
        if (RtsClientUiStateStore.isIntroReminderDismissed(key)) {
            return;
        }

        minecraft.player.displayClientMessage(Component.translatable(
                "chat.rtsbuilding.intro.rts_key",
                Component.keybind("key.rtsbuilding.toggle_rts")).withStyle(ChatFormatting.AQUA), false);
        minecraft.player.displayClientMessage(Component.translatable("chat.rtsbuilding.intro.version_warning")
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

    private static Component githubComponent() {
        return Component.literal(RtsCommunityLinks.GITHUB_REPOSITORY).withStyle(style -> style
                .withColor(ChatFormatting.BLUE)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RtsCommunityLinks.GITHUB_REPOSITORY))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(RtsCommunityLinks.GITHUB_REPOSITORY))));
    }

    private static String currentReminderKey(Minecraft minecraft) {
        if (minecraft == null) {
            return "unknown";
        }
        if (minecraft.getCurrentServer() != null && minecraft.getCurrentServer().ip != null) {
            return "server:" + minecraft.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        }
        if (minecraft.getSingleplayerServer() != null && minecraft.getSingleplayerServer().getWorldData() != null) {
            String name = minecraft.getSingleplayerServer().getWorldData().getLevelName();
            if (name != null && !name.isBlank()) {
                return "singleplayer:" + name.trim().toLowerCase(Locale.ROOT);
            }
        }
        if (minecraft.level != null) {
            return "level:" + minecraft.level.dimension().location().toString().toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }
}
