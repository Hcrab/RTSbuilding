package com.rtsbuilding.rtsbuilding.client.compat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.20.1 本地 GUI 兼容烟测探针。
 *
 * <p>它只在设置了 {@code rtsbuilding.guiCompatProbeReport} 系统属性或
 * {@code RTSBUILDING_GUI_COMPAT_PROBE_REPORT} 环境变量时启用。探针不改写 RTS
 * 交互逻辑，只记录客户端 screen / menu 的生命周期，并在主动运行时通过真实 RTS 空手右键链路触发目标方块。
 * 这样可以把“机器 GUI 一闪又关闭”这类现象转成可对比的 TSV 证据。</p>
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsGuiCompatProbe {
    private static final int FLASH_CLOSE_TICK_LIMIT = 12;
    private static final int SCREENLESS_MENU_TICK_LIMIT = 8;
    private static final int AUTO_WORLD_READY_DELAY = 80;
    private static final int AUTO_SETUP_DELAY = 100;
    private static final int AUTO_EXIT_DELAY = 40;
    private static final int AUTO_TIMEOUT_TICKS = 20 * 90;

    private static final Path REPORT_PATH = resolveReportPath();
    private static final String CASE_ID = resolveConfig("rtsbuilding.guiCompatCaseId",
            "RTSBUILDING_GUI_COMPAT_CASE_ID");
    private static final String TARGET_BLOCK = resolveConfig("rtsbuilding.guiCompatTargetBlock",
            "RTSBUILDING_GUI_COMPAT_TARGET_BLOCK");
    private static final String SETUP_COMMAND = stripLeadingSlash(resolveConfig("rtsbuilding.guiCompatSetupCommand",
            "RTSBUILDING_GUI_COMPAT_SETUP_COMMAND"));
    private static final boolean AUTO_RUN = resolveBoolean("rtsbuilding.guiCompatAutoRun",
            "RTSBUILDING_GUI_COMPAT_AUTO_RUN");
    private static final boolean AUTO_EXIT = resolveBoolean("rtsbuilding.guiCompatAutoExit",
            "RTSBUILDING_GUI_COMPAT_AUTO_EXIT");

    private static long tick;
    private static boolean headerWritten;
    private static String lastScreenClass = "";
    private static String lastScreenTitle = "";
    private static String lastMenuClass = "";
    private static int lastContainerId = -1;
    private static long lastScreenOpenTick = -1;
    private static int screenlessMenuTicks;
    private static SmokeRun activeRun;
    private static AutoRun autoRun = AUTO_RUN && REPORT_PATH != null ? new AutoRun(resolveFallbackCaseId()) : null;

    private RtsGuiCompatProbe() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rtsbuilding_gui_compat_run")
                .executes(context -> startFromCommand(resolveFallbackCaseId()))
                .then(Commands.argument("caseId", StringArgumentType.word())
                        .executes(context -> startFromCommand(StringArgumentType.getString(context, "caseId")))));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || REPORT_PATH == null) {
            return;
        }

        tick++;
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        String screenClass = screen == null ? "" : screen.getClass().getName();
        String screenTitle = screen == null || screen.getTitle() == null ? "" : screen.getTitle().getString();

        AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
        int containerId = menu == null ? -1 : menu.containerId;
        String menuClass = menu == null || containerId == 0 ? "" : menu.getClass().getName();

        if (!screenClass.equals(lastScreenClass) || !screenTitle.equals(lastScreenTitle)
                || !menuClass.equals(lastMenuClass) || containerId != lastContainerId) {
            recordTransition(screenClass, screenTitle, menuClass, containerId);
        }

        tickActiveRun(minecraft, screenClass, screenTitle, menuClass, containerId);
        tickAutoRun(minecraft, screenClass, screenTitle, menuClass, containerId);

        if (screenClass.isEmpty() && !menuClass.isEmpty()) {
            screenlessMenuTicks++;
            if (screenlessMenuTicks == SCREENLESS_MENU_TICK_LIMIT) {
                writeRow("screenless-menu", "FAIL", screenClass, screenTitle, menuClass, containerId,
                        "服务端菜单存在但客户端 screen 长时间为空，符合 GUI 一闪后丢失的症状。");
            }
        } else {
            screenlessMenuTicks = 0;
        }
    }

    private static int startFromCommand(String requestedCaseId) {
        if (REPORT_PATH == null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(
                    "RTS GUI compat probe is disabled. Launch with RTSBUILDING_GUI_COMPAT_PROBE_REPORT first."), false);
            return 0;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            writeRow("run-start", "FAIL", "", "", "", -1, "客户端世界或玩家尚未就绪。");
            return 0;
        }
        BlockHitResult hit = resolveTargetHit(minecraft);
        if (hit == null) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft), "未找到可测试目标方块。");
            minecraft.player.displayClientMessage(Component.literal("RTS GUI compat: no target block found."), false);
            return 0;
        }

        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        String targetBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!TARGET_BLOCK.isBlank() && !TARGET_BLOCK.equals(targetBlock)) {
            String note = "目标方块不匹配，expected=" + TARGET_BLOCK + " actual=" + targetBlock;
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft), note);
            minecraft.player.displayClientMessage(Component.literal("RTS GUI compat: " + note), false);
            return 0;
        }
        Vec3 origin = minecraft.player.getEyePosition();
        Vec3 rayDir = hit.getLocation().subtract(origin);
        if (rayDir.lengthSqr() < 1.0E-6D) {
            rayDir = minecraft.player.getLookAngle();
        } else {
            rayDir = rayDir.normalize();
        }

        activeRun = new SmokeRun(
                requestedCaseId == null || requestedCaseId.isBlank() ? resolveFallbackCaseId() : requestedCaseId,
                targetBlock,
                hit,
                origin,
                rayDir);
        writeRow("run-start", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                currentMenuClass(minecraft), currentContainerId(minecraft),
                "pos=" + hit.getBlockPos().toShortString() + " block=" + targetBlock);
        minecraft.player.displayClientMessage(Component.literal("RTS GUI compat: started " + activeRun.caseId
                + " on " + targetBlock), false);
        return Command.SINGLE_SUCCESS;
    }

    private static BlockHitResult resolveTargetHit(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            if (TARGET_BLOCK.isBlank() || matchesTargetBlock(minecraft, hit.getBlockPos())) {
                return hit;
            }
        }
        if (!TARGET_BLOCK.isBlank()) {
            return findNearestTargetHit(minecraft);
        }
        return null;
    }

    private static boolean matchesTargetBlock(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        String targetBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return TARGET_BLOCK.equals(targetBlock);
    }

    private static BlockHitResult findNearestTargetHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            return null;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-8, -3, -8), playerPos.offset(8, 5, 8))) {
            if (!matchesTargetBlock(minecraft, pos)) {
                continue;
            }
            double distance = pos.distSqr(playerPos);
            if (distance < bestDistance) {
                nearest = pos.immutable();
                bestDistance = distance;
            }
        }
        if (nearest == null) {
            return null;
        }
        Vec3 hitLocation = Vec3.atCenterOf(nearest);
        return new BlockHitResult(hitLocation, Direction.UP, nearest, false);
    }

    private static void tickActiveRun(Minecraft minecraft, String screenClass, String screenTitle,
            String menuClass, int containerId) {
        if (activeRun == null) {
            return;
        }
        activeRun.ticks++;
        ClientRtsController controller = ClientRtsController.get();

        if (activeRun.stage == SmokeStage.START) {
            if (!controller.isEnabled()) {
                if (!activeRun.toggleSent) {
                    RtsClientPacketGateway.sendToggleCamera(controller.isStartCameraAtPlayerHead());
                    activeRun.toggleSent = true;
                    writeRow("run-toggle-rts", "INFO", screenClass, screenTitle, menuClass, containerId,
                            "等待服务器打开 RTS 模式。");
                }
                if (activeRun.ticks > 100) {
                    finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                            "100 tick 内未进入 RTS 模式。");
                }
                return;
            }
            controller.selectEmptyHand();
            activeRun.stage = SmokeStage.SEND_INTERACT;
            activeRun.stageTicks = 0;
        }

        if (activeRun.stage == SmokeStage.SEND_INTERACT) {
            activeRun.stageTicks++;
            if (activeRun.stageTicks < 2) {
                return;
            }
            controller.interactEmpty(activeRun.hit, activeRun.rayOrigin, activeRun.rayDir);
            activeRun.stage = SmokeStage.OBSERVE;
            activeRun.stageTicks = 0;
            writeRow("run-interact", "INFO", screenClass, screenTitle, menuClass, containerId,
                    "已通过 RTS 空手右键链路触发目标方块。");
            return;
        }

        if (activeRun.stage == SmokeStage.OBSERVE) {
            activeRun.stageTicks++;
            if (isExternalScreen(screenClass)) {
                activeRun.sawExternalScreen = true;
                activeRun.lastExternalScreen = screenClass;
            }
            if (activeRun.stageTicks >= 80) {
                if (activeRun.failed) {
                    finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId, activeRun.failureNote);
                } else if (activeRun.warned) {
                    finishActiveRun("WARN", screenClass, screenTitle, menuClass, containerId, activeRun.warningNote);
                } else if (activeRun.sawExternalScreen && isExternalScreen(screenClass)) {
                    finishActiveRun("PASS", screenClass, screenTitle, menuClass, containerId,
                            "外部 GUI 保持打开，screen=" + activeRun.lastExternalScreen);
                } else if (!menuClass.isEmpty() && screenClass.isEmpty()) {
                    finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                            "服务端菜单存在但客户端 screen 为空。");
                } else {
                    finishActiveRun("WARN", screenClass, screenTitle, menuClass, containerId,
                            "未观察到稳定的外部 GUI。");
                }
            }
        }
    }

    private static void tickAutoRun(Minecraft minecraft, String screenClass, String screenTitle,
            String menuClass, int containerId) {
        if (autoRun == null || autoRun.finished) {
            return;
        }
        autoRun.totalTicks++;

        if (minecraft.player == null || minecraft.level == null || minecraft.player.connection == null) {
            if (autoRun.totalTicks > AUTO_TIMEOUT_TICKS) {
                writeRow("auto-timeout", "FAIL", screenClass, screenTitle, menuClass, containerId,
                        "自动探针等待客户端进入世界超时。");
                finishAutoRun(minecraft);
            }
            return;
        }

        autoRun.stageTicks++;
        if (autoRun.stage == AutoStage.WAIT_WORLD) {
            if (autoRun.stageTicks < AUTO_WORLD_READY_DELAY) {
                return;
            }
            if (!SETUP_COMMAND.isBlank()) {
                minecraft.player.connection.sendCommand(SETUP_COMMAND);
                writeRow("auto-setup-command", "INFO", screenClass, screenTitle, menuClass, containerId,
                        "/" + SETUP_COMMAND);
                autoRun.stage = AutoStage.WAIT_SETUP;
                autoRun.stageTicks = 0;
                return;
            }
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.WAIT_SETUP) {
            if (autoRun.stageTicks < AUTO_SETUP_DELAY) {
                return;
            }
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.START_PROBE) {
            int result = startFromCommand(autoRun.caseId);
            autoRun.stage = AutoStage.WAIT_FINISH;
            autoRun.stageTicks = 0;
            if (result != Command.SINGLE_SUCCESS || activeRun == null) {
                finishAutoRun(minecraft);
            }
            return;
        }

        if (autoRun.stage == AutoStage.WAIT_FINISH) {
            if (activeRun == null) {
                if (autoRun.stageTicks >= AUTO_EXIT_DELAY) {
                    finishAutoRun(minecraft);
                }
                return;
            }
            if (autoRun.stageTicks > AUTO_TIMEOUT_TICKS) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "自动探针等待 run-finish 超时。");
                finishAutoRun(minecraft);
            }
        }
    }

    private static void finishAutoRun(Minecraft minecraft) {
        if (autoRun == null || autoRun.finished) {
            return;
        }
        autoRun.finished = true;
        if (AUTO_EXIT) {
            writeRow("auto-exit", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft), "自动探针结束，关闭客户端。");
            minecraft.stop();
        }
    }

    private static void recordTransition(String screenClass, String screenTitle, String menuClass, int containerId) {
        if (lastScreenClass.isEmpty() && !screenClass.isEmpty()) {
            lastScreenOpenTick = tick;
            writeRow("screen-open", "INFO", screenClass, screenTitle, menuClass, containerId, "");
        } else if (!lastScreenClass.isEmpty() && screenClass.isEmpty()) {
            long lifetime = lastScreenOpenTick < 0 ? -1 : tick - lastScreenOpenTick;
            String status = isInternalRtsScreen(lastScreenClass) || lifetime < 0 || lifetime > FLASH_CLOSE_TICK_LIMIT
                    ? "INFO" : "WARN";
            String note = lifetime < 0 ? "" : "screen 持续 " + lifetime + " tick 后关闭。";
            writeRow("screen-close", status, lastScreenClass, lastScreenTitle, menuClass, containerId, note);
        } else if (!lastScreenClass.isEmpty() && !screenClass.equals(lastScreenClass)) {
            long lifetime = lastScreenOpenTick < 0 ? -1 : tick - lastScreenOpenTick;
            String status = isInternalRtsScreen(lastScreenClass) || lifetime < 0 || lifetime > FLASH_CLOSE_TICK_LIMIT
                    ? "INFO" : "WARN";
            String note = lifetime < 0 ? "" : "screen 持续 " + lifetime + " tick 后切换到 " + screenClass + "。";
            writeRow("screen-change", status, screenClass, screenTitle, menuClass, containerId, note);
            lastScreenOpenTick = tick;
        }

        if (!menuClass.equals(lastMenuClass) || containerId != lastContainerId) {
            String event = menuClass.isEmpty() ? "menu-close" : "menu-open";
            writeRow(event, "INFO", screenClass, screenTitle, menuClass, containerId, "");
        }

        lastScreenClass = screenClass;
        lastScreenTitle = screenTitle;
        lastMenuClass = menuClass;
        lastContainerId = containerId;
    }

    private static boolean isInternalRtsScreen(String screenClass) {
        return screenClass.startsWith("com.rtsbuilding.rtsbuilding.client.screen.");
    }

    private static boolean isExternalScreen(String screenClass) {
        return screenClass != null && !screenClass.isEmpty() && !isInternalRtsScreen(screenClass)
                && !screenClass.equals("net.minecraft.client.gui.screens.ChatScreen");
    }

    private static void finishActiveRun(String status, String screenClass, String screenTitle,
            String menuClass, int containerId, String note) {
        SmokeRun completed = activeRun;
        if (completed == null) {
            return;
        }
        writeRow("run-finish", status, screenClass, screenTitle, menuClass, containerId, note);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal("RTS GUI compat: " + completed.caseId
                    + " -> " + status + " (" + note + ")"), false);
        }
        activeRun = null;
    }

    private static String currentScreenClass(Minecraft minecraft) {
        return minecraft.screen == null ? "" : minecraft.screen.getClass().getName();
    }

    private static String currentScreenTitle(Minecraft minecraft) {
        return minecraft.screen == null || minecraft.screen.getTitle() == null ? "" : minecraft.screen.getTitle().getString();
    }

    private static String currentMenuClass(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.containerMenu == null
                || minecraft.player.containerMenu.containerId == 0) {
            return "";
        }
        return minecraft.player.containerMenu.getClass().getName();
    }

    private static int currentContainerId(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.player.containerMenu == null) {
            return -1;
        }
        return minecraft.player.containerMenu.containerId;
    }

    private static String resolveFallbackCaseId() {
        return CASE_ID == null || CASE_ID.isBlank() ? "manual" : CASE_ID;
    }

    private static Path resolveReportPath() {
        String configured = System.getProperty("rtsbuilding.guiCompatProbeReport");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("RTSBUILDING_GUI_COMPAT_PROBE_REPORT");
        }
        if (configured == null || configured.isBlank()) {
            return null;
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static String resolveConfig(String propertyName, String environmentName) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        return configured == null ? "" : configured;
    }

    private static boolean resolveBoolean(String propertyName, String environmentName) {
        String configured = resolveConfig(propertyName, environmentName);
        return "1".equals(configured) || "true".equalsIgnoreCase(configured) || "yes".equalsIgnoreCase(configured);
    }

    private static String stripLeadingSlash(String command) {
        if (command == null) {
            return "";
        }
        String stripped = command.trim();
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    private static void writeRow(String event, String status, String screenClass, String screenTitle,
            String menuClass, int containerId, String note) {
        if (REPORT_PATH == null) {
            return;
        }
        try {
            Path parent = REPORT_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!headerWritten && !Files.exists(REPORT_PATH)) {
                Files.writeString(REPORT_PATH,
                        "timestamp\tcaseId\ttargetBlock\ttick\tevent\tstatus\tscreenClass\tscreenTitle\tmenuClass\tcontainerId\tnote\r\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            headerWritten = true;
            String row = System.currentTimeMillis()
                    + "\t" + escape(currentCaseId())
                    + "\t" + escape(currentTargetBlock())
                    + "\t" + tick
                    + "\t" + escape(event)
                    + "\t" + escape(status)
                    + "\t" + escape(screenClass)
                    + "\t" + escape(screenTitle)
                    + "\t" + escape(menuClass)
                    + "\t" + containerId
                    + "\t" + escape(note)
                    + "\r\n";
            Files.writeString(REPORT_PATH, row, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            RtsbuildingMod.LOGGER.warn("Failed to write RTS GUI compat probe report: {}", REPORT_PATH, exception);
        }

        if (activeRun != null && activeRun.stage == SmokeStage.OBSERVE) {
            if ("FAIL".equals(status)) {
                activeRun.failed = true;
                activeRun.failureNote = note == null || note.isBlank() ? event : note;
            } else if ("WARN".equals(status)) {
                activeRun.warned = true;
                activeRun.warningNote = note == null || note.isBlank() ? event : note;
            }
        }
    }

    private static String currentCaseId() {
        return activeRun == null ? CASE_ID : activeRun.caseId;
    }

    private static String currentTargetBlock() {
        return activeRun == null ? TARGET_BLOCK : activeRun.targetBlock;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private enum SmokeStage {
        START,
        SEND_INTERACT,
        OBSERVE
    }

    private enum AutoStage {
        WAIT_WORLD,
        WAIT_SETUP,
        START_PROBE,
        WAIT_FINISH
    }

    private static final class SmokeRun {
        private final String caseId;
        private final String targetBlock;
        private final BlockHitResult hit;
        private final Vec3 rayOrigin;
        private final Vec3 rayDir;
        private SmokeStage stage = SmokeStage.START;
        private int ticks;
        private int stageTicks;
        private boolean toggleSent;
        private boolean sawExternalScreen;
        private boolean warned;
        private boolean failed;
        private String lastExternalScreen = "";
        private String warningNote = "";
        private String failureNote = "";

        private SmokeRun(String caseId, String targetBlock, BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
            this.caseId = caseId;
            this.targetBlock = targetBlock;
            this.hit = hit;
            this.rayOrigin = rayOrigin;
            this.rayDir = rayDir;
        }
    }

    private static final class AutoRun {
        private final String caseId;
        private AutoStage stage = AutoStage.WAIT_WORLD;
        private int totalTicks;
        private int stageTicks;
        private boolean finished;

        private AutoRun(String caseId) {
            this.caseId = caseId;
        }
    }
}
