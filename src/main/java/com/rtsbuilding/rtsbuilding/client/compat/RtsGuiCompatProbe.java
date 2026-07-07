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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsGuiCompatProbe {
    private static final int SCREENLESS_MENU_TICK_LIMIT = 8;
    private static final int AUTO_WORLD_READY_DELAY = 80;
    private static final int AUTO_SETUP_DELAY = 40;
    private static final int AUTO_EXIT_DELAY = 40;
    private static final int AUTO_TIMEOUT_TICKS = 20 * 120;
    private static final int REQUIRED_STABLE_TICKS = resolveInt("rtsbuilding.guiCompatStableTicks",
            "RTSBUILDING_GUI_COMPAT_STABLE_TICKS", 40);
    private static final int TARGET_SEARCH_RADIUS = resolveInt("rtsbuilding.guiCompatTargetSearchRadius",
            "RTSBUILDING_GUI_COMPAT_TARGET_SEARCH_RADIUS", 32);

    private static final Path REPORT_PATH = resolveReportPath();
    private static final String CASE_ID = resolveConfig("rtsbuilding.guiCompatCaseId",
            "RTSBUILDING_GUI_COMPAT_CASE_ID", "vanilla_chest");
    private static final String TARGET_BLOCK = resolveConfig("rtsbuilding.guiCompatTargetBlock",
            "RTSBUILDING_GUI_COMPAT_TARGET_BLOCK", "minecraft:chest");
    private static final String EXPECTED_MENU_REGEX = resolveConfig("rtsbuilding.guiCompatExpectedMenuRegex",
            "RTSBUILDING_GUI_COMPAT_EXPECTED_MENU_REGEX", defaultExpectedMenuRegex(CASE_ID));
    private static final String EXPECTED_SCREEN_REGEX = resolveConfig("rtsbuilding.guiCompatExpectedScreenRegex",
            "RTSBUILDING_GUI_COMPAT_EXPECTED_SCREEN_REGEX", "");
    private static final String SETUP_COMMAND = stripLeadingSlash(resolveConfig("rtsbuilding.guiCompatSetupCommand",
            "RTSBUILDING_GUI_COMPAT_SETUP_COMMAND", "rtsbuilding_gui_compat_setup vanilla_chest"));
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
    private static int screenlessMenuTicks;
    private static SmokeRun activeRun;
    private static AutoRun autoRun = AUTO_RUN && REPORT_PATH != null ? new AutoRun(CASE_ID) : null;

    private RtsGuiCompatProbe() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        if (REPORT_PATH == null) {
            return;
        }
        event.getDispatcher().register(Commands.literal("rtsbuilding_gui_compat_run")
                .executes(context -> startFromCommand(CASE_ID))
                .then(Commands.argument("caseId", StringArgumentType.word())
                        .executes(context -> startFromCommand(StringArgumentType.getString(context, "caseId")))));
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (REPORT_PATH == null) {
            return;
        }

        tick++;
        Minecraft minecraft = Minecraft.getInstance();
        String screenClass = currentScreenClass(minecraft);
        String screenTitle = currentScreenTitle(minecraft);
        String menuClass = currentMenuClass(minecraft);
        int containerId = currentContainerId(minecraft);

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
                        "Menu exists but no client screen stayed open.");
            }
        } else {
            screenlessMenuTicks = 0;
        }
    }

    private static int startFromCommand(String requestedCaseId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            writeRow("run-start", "FAIL", "", "", "", -1, "Client world or player is not ready.");
            return 0;
        }
        closeStaleBuilderScreen(minecraft);

        BlockHitResult hit = resolveTargetHit(minecraft);
        if (hit == null) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft), "No target block found.");
            return 0;
        }

        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        String targetBlock = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (!TARGET_BLOCK.isBlank() && !TARGET_BLOCK.equals(targetBlock)) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft),
                    "Target mismatch: expected=" + TARGET_BLOCK + " actual=" + targetBlock);
            return 0;
        }

        Vec3 origin = minecraft.player.getEyePosition();
        Vec3 rayDir = hit.getLocation().subtract(origin);
        rayDir = rayDir.lengthSqr() < 1.0E-6D ? minecraft.player.getLookAngle() : rayDir.normalize();

        activeRun = new SmokeRun(requestedCaseId == null || requestedCaseId.isBlank() ? CASE_ID : requestedCaseId,
                targetBlock, hit, origin, rayDir);
        writeRow("run-start", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                currentMenuClass(minecraft), currentContainerId(minecraft),
                "pos=" + hit.getBlockPos().toShortString() + " block=" + targetBlock);
        return Command.SINGLE_SUCCESS;
    }

    private static void closeStaleBuilderScreen(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        if (screen != null && screen.getClass().getName().equals(
                "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen")) {
            minecraft.setScreen(null);
            writeRow("screen-close-stale-rts", "INFO", "", "",
                    currentMenuClass(minecraft), currentContainerId(minecraft),
                    "Closed stale RTS BuilderScreen before starting the GUI compat probe.");
        }
    }

    private static BlockHitResult resolveTargetHit(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            if (TARGET_BLOCK.isBlank() || matchesTargetBlock(minecraft, hit.getBlockPos())) {
                return hit;
            }
        }
        return findNearestTargetHit(minecraft);
    }

    private static boolean matchesTargetBlock(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return TARGET_BLOCK.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static BlockHitResult findNearestTargetHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || TARGET_BLOCK.isBlank()) {
            return null;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = Math.max(1, TARGET_SEARCH_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-radius, -3, -radius),
                playerPos.offset(radius, 5, radius))) {
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
        return new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false);
    }

    private static void tickActiveRun(Minecraft minecraft, String screenClass, String screenTitle,
            String menuClass, int containerId) {
        if (activeRun == null) {
            return;
        }

        activeRun.totalTicks++;
        activeRun.stageTicks++;
        if (activeRun.totalTicks > AUTO_TIMEOUT_TICKS) {
            finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId, "Probe timed out.");
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (activeRun.stage == SmokeStage.START) {
            if (!controller.isEnabled()) {
                if (!activeRun.toggleSent) {
                    RtsClientPacketGateway.sendToggleCamera(controller.isStartCameraAtPlayerHead());
                    activeRun.toggleSent = true;
                    writeRow("run-toggle-rts", "INFO", screenClass, screenTitle, menuClass, containerId,
                            "Waiting for RTS mode.");
                }
                return;
            }
            controller.selectEmptyHand();
            activeRun.stage = SmokeStage.SEND_INTERACT;
            activeRun.stageTicks = 0;
            return;
        }

        if (activeRun.stage == SmokeStage.SEND_INTERACT) {
            if (activeRun.stageTicks < 2) {
                return;
            }
            controller.interactEmpty(activeRun.hit, activeRun.rayOrigin, activeRun.rayDir);
            activeRun.stage = SmokeStage.OBSERVE;
            activeRun.stageTicks = 0;
            writeRow("run-interact", "INFO", screenClass, screenTitle, menuClass, containerId,
                    "Sent RTS empty-hand right-click.");
            return;
        }

        if (activeRun.stage == SmokeStage.OBSERVE) {
            AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
            boolean hasMenu = menu != null && menu.containerId != 0;
            boolean hasScreen = minecraft.screen != null;
            boolean menuMatches = hasMenu && matchesRegex(menu.getClass().getName(), EXPECTED_MENU_REGEX);
            boolean screenMatches = hasScreen && matchesRegex(screenClass, EXPECTED_SCREEN_REGEX);
            if (hasMenu && !menuMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected menu: " + menu.getClass().getName() + " expected=" + EXPECTED_MENU_REGEX);
                return;
            }
            if (hasMenu && hasScreen && !screenMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected screen: " + screenClass + " expected=" + EXPECTED_SCREEN_REGEX);
                return;
            }
            if (hasMenu && hasScreen && menuMatches && screenMatches) {
                activeRun.stableTicks++;
                activeRun.sawMenu = true;
                if (activeRun.stableTicks >= REQUIRED_STABLE_TICKS) {
                    finishActiveRun("PASS", screenClass, screenTitle, menuClass, containerId,
                            "Expected menu and screen stayed open for " + REQUIRED_STABLE_TICKS + " ticks.");
                }
                return;
            }
            if (activeRun.sawMenu && !hasScreen) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Screen closed before " + REQUIRED_STABLE_TICKS + " stable ticks.");
                return;
            }
            if (activeRun.stageTicks > 120) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Expected menu did not open within 120 ticks after interaction.");
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
                        "Timed out waiting for a playable world.");
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
                    currentMenuClass(minecraft), currentContainerId(minecraft), "Stopping client.");
            minecraft.stop();
        }
    }

    private static void recordTransition(String screenClass, String screenTitle, String menuClass, int containerId) {
        if (!lastScreenClass.equals(screenClass)) {
            writeRow(screenClass.isEmpty() ? "screen-close" : "screen-open", "INFO",
                    screenClass, screenTitle, menuClass, containerId, "");
        }
        if (!lastMenuClass.equals(menuClass) || lastContainerId != containerId) {
            writeRow(menuClass.isEmpty() ? "menu-close" : "menu-open", "INFO",
                    screenClass, screenTitle, menuClass, containerId, "");
        }
        lastScreenClass = screenClass;
        lastScreenTitle = screenTitle;
        lastMenuClass = menuClass;
        lastContainerId = containerId;
    }

    private static void finishActiveRun(String status, String screenClass, String screenTitle,
            String menuClass, int containerId, String note) {
        writeRow("run-finish", status, screenClass, screenTitle, menuClass, containerId, note);
        activeRun = null;
    }

    private static String currentScreenClass(Minecraft minecraft) {
        Screen screen = minecraft == null ? null : minecraft.screen;
        return screen == null ? "" : screen.getClass().getName();
    }

    private static String currentScreenTitle(Minecraft minecraft) {
        Screen screen = minecraft == null ? null : minecraft.screen;
        return screen == null || screen.getTitle() == null ? "" : screen.getTitle().getString();
    }

    private static String currentMenuClass(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.player.containerMenu == null
                || minecraft.player.containerMenu.containerId == 0) {
            return "";
        }
        return minecraft.player.containerMenu.getClass().getName();
    }

    private static int currentContainerId(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.player.containerMenu == null) {
            return -1;
        }
        return minecraft.player.containerMenu.containerId;
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

    private static String resolveConfig(String propertyName, String environmentName, String fallback) {
        String configured = System.getProperty(propertyName);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(environmentName);
        }
        return configured == null || configured.isBlank() ? fallback : configured;
    }

    private static boolean resolveBoolean(String propertyName, String environmentName) {
        String configured = resolveConfig(propertyName, environmentName, "");
        return "1".equals(configured) || "true".equalsIgnoreCase(configured) || "yes".equalsIgnoreCase(configured);
    }

    private static int resolveInt(String propertyName, String environmentName, int fallback) {
        String configured = resolveConfig(propertyName, environmentName, "");
        if (configured.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(configured));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripLeadingSlash(String command) {
        String stripped = command == null ? "" : command.trim();
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    private static String defaultExpectedMenuRegex(String caseId) {
        if ("vanilla_chest".equals(caseId)) {
            return "net\\.minecraft\\.world\\.inventory\\.ChestMenu";
        }
        return "";
    }

    private static boolean matchesRegex(String value, String regex) {
        if (regex == null || regex.isBlank()) {
            return true;
        }
        return value != null && value.matches(regex);
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
    }

    private static String currentCaseId() {
        return activeRun == null ? CASE_ID : activeRun.caseId;
    }

    private static String currentTargetBlock() {
        return activeRun == null ? TARGET_BLOCK : activeRun.targetBlock;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
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
        private int totalTicks;
        private int stageTicks;
        private int stableTicks;
        private boolean toggleSent;
        private boolean sawMenu;

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
