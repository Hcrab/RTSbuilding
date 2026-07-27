package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsCommunityLinks;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.nio.file.Path;

/**
 * Forge 1.12.2 客户端入门提醒。
 *
 * <p>本类只管理连接级延迟、提示文本和本地隐藏命令；作用域归一化与磁盘状态仍由独立组件负责。
 * 它不参与服务端加载，也不会把单人世界的隐藏状态错误地扩散到其他存档。</p>
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
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
        if (minecraft.player != null) {
            minecraft.player.sendMessage(new TextComponentTranslation("chat.rtsbuilding.intro.dismissed"));
        }
        return 1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) {
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

        minecraft.player.sendMessage(new TextComponentTranslation("chat.rtsbuilding.intro.rts_key",
                styled(new TextComponentTranslation("key.rtsbuilding.toggle_rts"), TextFormatting.AQUA, null, null)));
        minecraft.player.sendMessage(styled(new TextComponentTranslation("chat.rtsbuilding.intro.version_warning",
                new TextComponentString(currentDisplayVersion()), new TextComponentString(STABLE_VERSION),
                link(RtsCommunityLinks.WEBSITE)), TextFormatting.GOLD, null, null));
        minecraft.player.sendMessage(styled(new TextComponentTranslation("chat.rtsbuilding.intro.feedback",
                link(RtsCommunityLinks.DISCORD_INVITE), link(RtsCommunityLinks.GITHUB_REPOSITORY),
                new TextComponentString(RtsCommunityLinks.QQ_GROUP)), TextFormatting.GRAY, null, null));

        ITextComponent dismiss = styled(new TextComponentTranslation("chat.rtsbuilding.intro.dismiss"),
                TextFormatting.YELLOW,
                new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + DISMISS_COMMAND),
                new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new TextComponentTranslation("chat.rtsbuilding.intro.dismiss.hover")));
        ITextComponent hint = styled(new TextComponentTranslation("chat.rtsbuilding.intro.config_hint"),
                TextFormatting.GRAY, null, null);
        hint.appendText(" ").appendSibling(dismiss);
        minecraft.player.sendMessage(hint);
    }

    private static ITextComponent link(String url) {
        return styled(new TextComponentString(url), TextFormatting.BLUE,
                new ClickEvent(ClickEvent.Action.OPEN_URL, url),
                new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(url)));
    }

    private static ITextComponent styled(ITextComponent component, TextFormatting color,
            ClickEvent click, HoverEvent hover) {
        Style style = new Style().setColor(color);
        if (click != null) {
            style.setClickEvent(click).setUnderlined(true);
        }
        if (hover != null) {
            style.setHoverEvent(hover);
        }
        component.setStyle(style);
        return component;
    }

    private static String currentDisplayVersion() {
        net.minecraftforge.fml.common.ModContainer container =
                Loader.instance().getIndexedModList().get(RtsbuildingMod.MODID);
        String version = container == null ? "unknown" : container.getVersion();
        int qualifier = version.indexOf('-');
        return qualifier > 0 ? version.substring(0, qualifier) : version;
    }

    private static String currentReminderKey(Minecraft minecraft) {
        if (minecraft == null) {
            return "";
        }
        if (minecraft.isSingleplayer() && minecraft.world != null && minecraft.world.getSaveHandler() != null) {
            Path worldRoot = minecraft.world.getSaveHandler().getWorldDirectory().toPath();
            return RtsIntroReminderScope.singleplayerKey(worldRoot);
        }
        ServerData server = minecraft.getCurrentServerData();
        return server == null ? "" : RtsIntroReminderScope.serverKey(server.serverIP);
    }

    private static final class DismissCommand extends CommandBase {
        @Override
        public String getName() {
            return DISMISS_COMMAND;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/" + DISMISS_COMMAND;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public void execute(net.minecraft.server.MinecraftServer server, ICommandSender sender,
                String[] args) throws CommandException {
            dismissIntroReminder();
        }
    }
}
