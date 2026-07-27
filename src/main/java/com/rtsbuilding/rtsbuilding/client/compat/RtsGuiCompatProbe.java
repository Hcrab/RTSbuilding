package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Forge 1.12 GUI 兼容自动探针。启用报告路径后，它仍会记录屏幕/容器转换，
 * 支持客户端命令手动运行，也可按环境变量自动布置、交互、稳定性判定和退出。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = "rtsbuilding", value = Side.CLIENT)
public final class RtsGuiCompatProbe {
    private static final Logger LOGGER = Logger.getLogger("rtsbuilding");
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
    private static final String EXPECTED_MENU_REGEX = resolveConfig(
            "rtsbuilding.guiCompatExpectedMenuRegex",
            "RTSBUILDING_GUI_COMPAT_EXPECTED_MENU_REGEX", defaultExpectedMenuRegex(CASE_ID));
    private static final String EXPECTED_SCREEN_REGEX = resolveConfig(
            "rtsbuilding.guiCompatExpectedScreenRegex",
            "RTSBUILDING_GUI_COMPAT_EXPECTED_SCREEN_REGEX", "");
    private static final String SETUP_COMMAND = stripLeadingSlash(resolveConfig(
            "rtsbuilding.guiCompatSetupCommand", "RTSBUILDING_GUI_COMPAT_SETUP_COMMAND",
            "rtsbuilding_gui_compat_setup vanilla_chest"));
    private static final boolean AUTO_RUN = resolveBoolean("rtsbuilding.guiCompatAutoRun",
            "RTSBUILDING_GUI_COMPAT_AUTO_RUN");
    private static final boolean AUTO_EXIT = resolveBoolean("rtsbuilding.guiCompatAutoExit",
            "RTSBUILDING_GUI_COMPAT_AUTO_EXIT");

    private static long tick;
    private static boolean headerWritten;
    private static boolean commandRegistered;
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (REPORT_PATH == null || event.phase != TickEvent.Phase.END) return;
        ensureClientCommandRegistered();

        tick++;
        Minecraft minecraft = Minecraft.getMinecraft();
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
                writeRow("screenless-menu", "FAIL", screenClass, screenTitle, menuClass,
                        containerId, "Menu exists but no client screen stayed open.");
            }
        } else {
            screenlessMenuTicks = 0;
        }
    }

    private static void ensureClientCommandRegistered() {
        if (commandRegistered) return;
        ClientCommandHandler.instance.registerCommand(new ProbeCommand());
        commandRegistered = true;
    }

    private static int startFromCommand(String requestedCaseId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.world == null) {
            writeRow("run-start", "FAIL", "", "", "", -1,
                    "Client world or player is not ready.");
            return 0;
        }
        closeStaleBuilderScreen(minecraft);

        RayTraceResult hit = resolveTargetHit(minecraft);
        if (hit == null) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft),
                    currentScreenTitle(minecraft), currentMenuClass(minecraft),
                    currentContainerId(minecraft), "No target block found.");
            return 0;
        }

        IBlockState state = minecraft.world.getBlockState(hit.getBlockPos());
        String targetBlock = registryName(state.getBlock());
        if (!isBlank(TARGET_BLOCK) && !TARGET_BLOCK.equals(targetBlock)) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft),
                    currentScreenTitle(minecraft), currentMenuClass(minecraft),
                    currentContainerId(minecraft),
                    "Target mismatch: expected=" + TARGET_BLOCK + " actual=" + targetBlock);
            return 0;
        }

        Vec3d origin = minecraft.player.getPositionEyes(1.0F);
        Vec3d rayDir = hit.hitVec.subtract(origin);
        rayDir = rayDir.lengthSquared() < 1.0E-6D
                ? minecraft.player.getLookVec() : rayDir.normalize();

        String runCase = isBlank(requestedCaseId) ? CASE_ID : requestedCaseId;
        activeRun = new SmokeRun(runCase, targetBlock, hit, origin, rayDir);
        writeRow("run-start", "INFO", currentScreenClass(minecraft),
                currentScreenTitle(minecraft), currentMenuClass(minecraft),
                currentContainerId(minecraft),
                "pos=" + hit.getBlockPos() + " block=" + targetBlock);
        return 1;
    }

    private static void closeStaleBuilderScreen(Minecraft minecraft) {
        GuiScreen screen = minecraft.currentScreen;
        if (screen != null && screen.getClass().getName().equals(
                "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen")) {
            minecraft.displayGuiScreen(null);
            writeRow("screen-close-stale-rts", "INFO", "", "",
                    currentMenuClass(minecraft), currentContainerId(minecraft),
                    "Closed stale RTS BuilderScreen before starting the GUI compat probe.");
        }
    }

    private static RayTraceResult resolveTargetHit(Minecraft minecraft) {
        RayTraceResult hit = minecraft.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                && (isBlank(TARGET_BLOCK) || matchesTargetBlock(minecraft, hit.getBlockPos()))) {
            return hit;
        }
        return findNearestTargetHit(minecraft);
    }

    private static boolean matchesTargetBlock(Minecraft minecraft, BlockPos pos) {
        if (minecraft.world == null) return false;
        return TARGET_BLOCK.equals(registryName(minecraft.world.getBlockState(pos).getBlock()));
    }

    private static RayTraceResult findNearestTargetHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.world == null || isBlank(TARGET_BLOCK)) return null;
        BlockPos playerPos = minecraft.player.getPosition();
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = Math.max(1, TARGET_SEARCH_RADIUS);
        BlockPos min = playerPos.add(-radius, -3, -radius);
        BlockPos max = playerPos.add(radius, 5, radius);
        for (BlockPos.MutableBlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            if (!matchesTargetBlock(minecraft, pos)) continue;
            double distance = pos.distanceSq(playerPos);
            if (distance < bestDistance) {
                nearest = pos.toImmutable();
                bestDistance = distance;
            }
        }
        if (nearest == null) return null;
        Vec3d center = new Vec3d(nearest.getX() + 0.5D, nearest.getY() + 0.5D,
                nearest.getZ() + 0.5D);
        return new RayTraceResult(center, EnumFacing.UP, nearest);
    }

    private static void tickActiveRun(Minecraft minecraft, String screenClass,
                                      String screenTitle, String menuClass, int containerId) {
        if (activeRun == null) return;
        activeRun.totalTicks++;
        activeRun.stageTicks++;
        if (activeRun.totalTicks > AUTO_TIMEOUT_TICKS) {
            finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                    "Probe timed out.");
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (activeRun.stage == SmokeStage.START) {
            if (!controller.isEnabled()) {
                if (!activeRun.toggleSent) {
                    RtsClientPacketGateway.sendToggleCamera(controller.isStartCameraAtPlayerHead());
                    activeRun.toggleSent = true;
                    writeRow("run-toggle-rts", "INFO", screenClass, screenTitle, menuClass,
                            containerId, "Waiting for RTS mode.");
                }
                return;
            }
            controller.selectEmptyHand();
            activeRun.stage = SmokeStage.SEND_INTERACT;
            activeRun.stageTicks = 0;
            return;
        }

        if (activeRun.stage == SmokeStage.SEND_INTERACT) {
            if (activeRun.stageTicks < 2) return;
            if (!invokeInteractEmpty(controller, activeRun)) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "No compatible 1.12 interactEmpty entry point was available.");
                return;
            }
            activeRun.stage = SmokeStage.OBSERVE;
            activeRun.stageTicks = 0;
            writeRow("run-interact", "INFO", screenClass, screenTitle, menuClass, containerId,
                    "Sent RTS empty-hand right-click.");
            return;
        }

        if (activeRun.stage == SmokeStage.OBSERVE) {
            Container menu = minecraft.player == null ? null : minecraft.player.openContainer;
            boolean hasMenu = menu != null && menu.windowId != 0;
            boolean hasScreen = minecraft.currentScreen != null;
            boolean menuMatches = hasMenu && matchesRegex(menu.getClass().getName(), EXPECTED_MENU_REGEX);
            boolean screenMatches = hasScreen && matchesRegex(screenClass, EXPECTED_SCREEN_REGEX);
            if (hasMenu && !menuMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected menu: " + menu.getClass().getName()
                                + " expected=" + EXPECTED_MENU_REGEX);
                return;
            }
            if (hasMenu && hasScreen && !screenMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected screen: " + screenClass
                                + " expected=" + EXPECTED_SCREEN_REGEX);
                return;
            }
            if (hasMenu && hasScreen && menuMatches && screenMatches) {
                activeRun.stableTicks++;
                activeRun.sawMenu = true;
                if (activeRun.stableTicks >= REQUIRED_STABLE_TICKS) {
                    finishActiveRun("PASS", screenClass, screenTitle, menuClass, containerId,
                            "Expected menu and screen stayed open for "
                                    + REQUIRED_STABLE_TICKS + " ticks.");
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

    /** 兼容 controller 完成 1.12 迁移前后的边界，并且失败时显式写入探针结果。 */
    private static boolean invokeInteractEmpty(ClientRtsController controller, SmokeRun run) {
        try {
            Method method = controller.getClass().getMethod("interactEmpty",
                    RayTraceResult.class, Vec3d.class, Vec3d.class);
            method.invoke(controller, run.hit, run.rayOrigin, run.rayDir);
            return true;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            LOGGER.log(Level.WARNING, "Unable to invoke RTS 1.12 empty-hand interaction", failure);
            return false;
        }
    }

    private static void tickAutoRun(Minecraft minecraft, String screenClass,
                                    String screenTitle, String menuClass, int containerId) {
        if (autoRun == null || autoRun.finished) return;
        autoRun.totalTicks++;
        if (minecraft.player == null || minecraft.world == null || minecraft.player.connection == null) {
            if (autoRun.totalTicks > AUTO_TIMEOUT_TICKS) {
                writeRow("auto-timeout", "FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Timed out waiting for a playable world.");
                finishAutoRun(minecraft);
            }
            return;
        }

        autoRun.stageTicks++;
        if (autoRun.stage == AutoStage.WAIT_WORLD) {
            if (autoRun.stageTicks < AUTO_WORLD_READY_DELAY) return;
            if (!isBlank(SETUP_COMMAND)) {
                minecraft.player.sendChatMessage("/" + SETUP_COMMAND);
                writeRow("auto-setup-command", "INFO", screenClass, screenTitle, menuClass,
                        containerId, "/" + SETUP_COMMAND);
                autoRun.stage = AutoStage.WAIT_SETUP;
                autoRun.stageTicks = 0;
                return;
            }
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.WAIT_SETUP) {
            if (autoRun.stageTicks < AUTO_SETUP_DELAY) return;
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.START_PROBE) {
            int result = startFromCommand(autoRun.caseId);
            autoRun.stage = AutoStage.WAIT_FINISH;
            autoRun.stageTicks = 0;
            if (result != 1 || activeRun == null) finishAutoRun(minecraft);
            return;
        }

        if (autoRun.stage == AutoStage.WAIT_FINISH && activeRun == null
                && autoRun.stageTicks >= AUTO_EXIT_DELAY) {
            finishAutoRun(minecraft);
        }
    }

    private static void finishAutoRun(Minecraft minecraft) {
        if (autoRun == null || autoRun.finished) return;
        autoRun.finished = true;
        if (AUTO_EXIT) {
            writeRow("auto-exit", "INFO", currentScreenClass(minecraft),
                    currentScreenTitle(minecraft), currentMenuClass(minecraft),
                    currentContainerId(minecraft), "Stopping client.");
            minecraft.shutdown();
        }
    }

    private static void recordTransition(String screenClass, String screenTitle,
                                         String menuClass, int containerId) {
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
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        return screen == null ? "" : screen.getClass().getName();
    }

    /** 1.12 GuiScreen 无统一标题接口，优先读取其 IInventory 显示名。 */
    private static String currentScreenTitle(Minecraft minecraft) {
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        if (screen == null) return "";
        Class<?> type = screen.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!IInventory.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object inventory = field.get(screen);
                    if (inventory instanceof IInventory) {
                        ITextComponent title = ((IInventory) inventory).getDisplayName();
                        if (title != null) return title.getUnformattedText();
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) {
                    // 标题只用于诊断，不因可选 GUI 的封装字段不可访问而中止探针。
                }
            }
            type = type.getSuperclass();
        }
        return "";
    }

    private static String currentMenuClass(Minecraft minecraft) {
        Container menu = minecraft == null || minecraft.player == null
                ? null : minecraft.player.openContainer;
        return menu == null || menu.windowId == 0 ? "" : menu.getClass().getName();
    }

    private static int currentContainerId(Minecraft minecraft) {
        Container menu = minecraft == null || minecraft.player == null
                ? null : minecraft.player.openContainer;
        return menu == null ? -1 : menu.windowId;
    }

    private static Path resolveReportPath() {
        String configured = System.getProperty("rtsbuilding.guiCompatProbeReport");
        if (isBlank(configured)) configured = System.getenv("RTSBUILDING_GUI_COMPAT_PROBE_REPORT");
        if (isBlank(configured)) return null;
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private static String resolveConfig(String propertyName, String environmentName,
                                        String fallback) {
        String configured = System.getProperty(propertyName);
        if (isBlank(configured)) configured = System.getenv(environmentName);
        return isBlank(configured) ? fallback : configured;
    }

    private static boolean resolveBoolean(String propertyName, String environmentName) {
        String configured = resolveConfig(propertyName, environmentName, "");
        return "1".equals(configured) || "true".equalsIgnoreCase(configured)
                || "yes".equalsIgnoreCase(configured);
    }

    private static int resolveInt(String propertyName, String environmentName, int fallback) {
        String configured = resolveConfig(propertyName, environmentName, "");
        if (isBlank(configured)) return fallback;
        try {
            return Math.max(1, Integer.parseInt(configured));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stripLeadingSlash(String command) {
        String stripped = command == null ? "" : command.trim();
        while (stripped.startsWith("/")) stripped = stripped.substring(1);
        return stripped;
    }

    private static String defaultExpectedMenuRegex(String caseId) {
        return "vanilla_chest".equals(caseId)
                ? "net\\.minecraft\\.inventory\\.ContainerChest" : "";
    }

    private static boolean matchesRegex(String value, String regex) {
        return isBlank(regex) || value != null && value.matches(regex);
    }

    private static String registryName(Block block) {
        return Block.REGISTRY.getNameForObject(block) == null
                ? "" : Block.REGISTRY.getNameForObject(block).toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void writeRow(String event, String status, String screenClass,
                                 String screenTitle, String menuClass, int containerId,
                                 String note) {
        if (REPORT_PATH == null) return;
        try {
            Path parent = REPORT_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            if (!headerWritten && !Files.exists(REPORT_PATH)) {
                appendUtf8("timestamp\tcaseId\ttargetBlock\ttick\tevent\tstatus\t"
                        + "screenClass\tscreenTitle\tmenuClass\tcontainerId\tnote\r\n");
            }
            headerWritten = true;
            appendUtf8(System.currentTimeMillis()
                    + "\t" + escape(currentCaseId())
                    + "\t" + escape(currentTargetBlock())
                    + "\t" + tick
                    + "\t" + escape(event)
                    + "\t" + escape(status)
                    + "\t" + escape(screenClass)
                    + "\t" + escape(screenTitle)
                    + "\t" + escape(menuClass)
                    + "\t" + containerId
                    + "\t" + escape(note) + "\r\n");
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "Failed to write RTS GUI compat probe report: "
                    + REPORT_PATH, exception);
        }
    }

    private static void appendUtf8(String text) throws IOException {
        Files.write(REPORT_PATH, text.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String currentCaseId() {
        return activeRun == null ? CASE_ID : activeRun.caseId;
    }

    private static String currentTargetBlock() {
        return activeRun == null ? TARGET_BLOCK : activeRun.targetBlock;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ')
                .replace('\n', ' ');
    }

    private enum SmokeStage { START, SEND_INTERACT, OBSERVE }
    private enum AutoStage { WAIT_WORLD, WAIT_SETUP, START_PROBE, WAIT_FINISH }

    private static final class SmokeRun {
        private final String caseId;
        private final String targetBlock;
        private final RayTraceResult hit;
        private final Vec3d rayOrigin;
        private final Vec3d rayDir;
        private SmokeStage stage = SmokeStage.START;
        private int totalTicks;
        private int stageTicks;
        private int stableTicks;
        private boolean toggleSent;
        private boolean sawMenu;

        private SmokeRun(String caseId, String targetBlock, RayTraceResult hit,
                         Vec3d rayOrigin, Vec3d rayDir) {
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

    /** Forge 1.12 客户端命令替代 Brigadier 注册事件。 */
    private static final class ProbeCommand extends CommandBase {
        @Override public String getName() { return "rtsbuilding_gui_compat_run"; }
        @Override public String getUsage(ICommandSender sender) {
            return "/rtsbuilding_gui_compat_run [caseId]";
        }
        @Override public int getRequiredPermissionLevel() { return 0; }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args)
                throws CommandException {
            String caseId = args.length == 0 ? CASE_ID : args[0];
            if (startFromCommand(caseId) != 1) {
                throw new CommandException("RTS GUI compatibility probe could not start");
            }
        }
    }
}
