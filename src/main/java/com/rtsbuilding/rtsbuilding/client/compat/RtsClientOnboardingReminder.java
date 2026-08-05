package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraftforge.client.ClientCommandHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * Forge 1.12.2 客户端入门提醒。
 *
 * <p>本类只管理连接级延迟、提示文本和本地隐藏命令；作用域归一化与磁盘状态仍由独立组件负责。
 * 它不参与服务端加载，也不会把单人世界的隐藏状态错误地扩散到其他存档。</p>
 */
public final class RtsClientOnboardingReminder {
    private static final String DISMISS_COMMAND = "rtsbuilding_hide_intro";
    private static final String STABLE_VERSION = "1.1.5-patch4";
    private static final int SHOW_DELAY_TICKS = 80;

    private static boolean commandRegistered;
    private static boolean shownThisConnection;
    private static int ticksUntilReminder = -1;

    private RtsClientOnboardingReminder() {
    }

    /** 由客户端引导层调用一次，注册不发送到服务端的本地隐藏命令。 */
    public static synchronized void registerClientCommand() {
        if (commandRegistered) {
            return;
        }
        ClientCommandHandler.instance.registerCommand(new DismissCommand());
        commandRegistered = true;
    }

    private static int dismissIntroReminder() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String key = currentReminderKey(minecraft);
        if (key.trim().isEmpty()) {
            return 0;
        }
        RtsClientUiStateStore.dismissIntroReminder(key);
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentTranslation("chat.rtsbuilding.intro.dismissed"));
        }
        return 1;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.theWorld == null) {
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
        if (key.trim().isEmpty()) {
            ticksUntilReminder = SHOW_DELAY_TICKS;
            return;
        }
        shownThisConnection = true;
        if (RtsClientUiStateStore.isIntroReminderDismissed(key)) {
            return;
        }

        minecraft.thePlayer.addChatMessage(new ChatComponentTranslation("chat.rtsbuilding.intro.rts_key",
                styled(new ChatComponentTranslation("key.rtsbuilding.toggle_rts"), EnumChatFormatting.AQUA, null, null)));
        minecraft.thePlayer.addChatMessage(styled(new ChatComponentTranslation("chat.rtsbuilding.intro.version_warning",
                new ChatComponentText(currentDisplayVersion()), new ChatComponentText(STABLE_VERSION),
                link(RtsCommunityLinks.WEBSITE)), EnumChatFormatting.GOLD, null, null));
        minecraft.thePlayer.addChatMessage(styled(new ChatComponentTranslation("chat.rtsbuilding.intro.feedback",
                link(RtsCommunityLinks.DISCORD_INVITE), link(RtsCommunityLinks.GITHUB_REPOSITORY),
                new ChatComponentText(RtsCommunityLinks.QQ_GROUP)), EnumChatFormatting.GRAY, null, null));

        IChatComponent dismiss = styled(new ChatComponentTranslation("chat.rtsbuilding.intro.dismiss"),
                EnumChatFormatting.YELLOW,
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + DISMISS_COMMAND),
                new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentTranslation("chat.rtsbuilding.intro.dismiss.hover")));
        IChatComponent hint = styled(new ChatComponentTranslation("chat.rtsbuilding.intro.config_hint"),
                EnumChatFormatting.GRAY, null, null);
        hint.appendText(" ").appendSibling(dismiss);
        minecraft.thePlayer.addChatMessage(hint);
    }

    private static IChatComponent link(String url) {
        return styled(new ChatComponentText(url), EnumChatFormatting.BLUE,
                new ClickEvent(ClickEvent.Action.OPEN_URL, url),
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ChatComponentText(url)));
    }

    private static IChatComponent styled(IChatComponent component, EnumChatFormatting color,
            ClickEvent click, HoverEvent hover) {
        ChatStyle style = new ChatStyle().setColor(color);
        if (click != null) {
            style.setChatClickEvent(click).setUnderlined(true);
        }
        if (hover != null) {
            style.setChatHoverEvent(hover);
        }
        component.setChatStyle(style);
        return component;
    }

    private static String currentDisplayVersion() {
        cpw.mods.fml.common.ModContainer container =
                Loader.instance().getIndexedModList().get(RtsbuildingMod.MODID);
        String version = container == null ? "unknown" : container.getVersion();
        int qualifier = version.indexOf('-');
        return qualifier > 0 ? version.substring(0, qualifier) : version;
    }

    private static String currentReminderKey(Minecraft minecraft) {
        if (minecraft == null) {
            return "";
        }
        if (minecraft.isSingleplayer()) {
            IntegratedServer server = minecraft.getIntegratedServer();
            if (server == null || com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.gameDir(minecraft) == null) {
                return "";
            }
            return RtsIntroReminderScope.singleplayerKey(
                    com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.gameDir(minecraft).toPath(), server.getFolderName());
        }
        ServerData server = com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.currentServerData(minecraft);
        return server == null ? "" : RtsIntroReminderScope.serverKey(server.serverIP);
    }

    private static final class DismissCommand extends CommandBase {
        @Override
        public String getCommandName() {
            return DISMISS_COMMAND;
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/" + DISMISS_COMMAND;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            dismissIntroReminder();
        }
    }
}
