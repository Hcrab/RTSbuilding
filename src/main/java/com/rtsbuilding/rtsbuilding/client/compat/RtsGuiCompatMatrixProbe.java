package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import com.rtsbuilding.rtsbuilding.compat.RtsGuiCompatMatrixSync;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multiblock Madness 等大型 1.12.2 整合包的真实 GUI 兼容矩阵。
 *
 * <p>它使用生产 RTS 发包路径，而不是直接调用方块方法：每个候选先在 3 格处验证
 * “确实能打开 GUI”，再把同一方块放到 120 格处验证窗口能持续保活。没有近距 GUI
 * 的方块记为 SKIP，交给需要结构/物品/人工前置的清单；近距成功而远距失败才是明确
 * 的 RTS 兼容回归。</p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = "rtsbuilding", value = Side.CLIENT)
public final class RtsGuiCompatMatrixProbe {
    private static final String BUILDER_SCREEN =
            "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen";
    private static final String WORLD_DIRECTORY = "RTSBuildingMmGuiMatrix";
    private static final int MAIN_MENU_STABLE_TICKS = 30;
    private static final int WORLD_READY_TICKS = 180;
    private static final int SETUP_WAIT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixSetupWaitTicks", 2, 2, 40);
    private static final int OPEN_TIMEOUT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixOpenTimeoutTicks", 8, 4, 120);
    private static final int CLOSE_WAIT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixCloseWaitTicks", 2, 1, 20);
    private static final int SERVER_ACK_TIMEOUT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixServerAckTimeoutTicks", 400, 40, 1200);
    private static final int CLIENT_SETUP_SYNC_TIMEOUT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixClientSetupSyncTimeoutTicks", 40, 5, 200);
    private static final int MENU_SCREEN_TIMEOUT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixMenuScreenTimeoutTicks", 60, 10, 400);
    private static final int GUI_STABILITY_TIMEOUT_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixGuiStabilityTimeoutTicks", 100, 20, 600);
    private static final int NEAR_STABLE_TICKS = 6;

    private static final Path REPORT_PATH = resolvePath("rtsbuilding.guiCompatMatrixReport",
            "RTSBUILDING_GUI_COMPAT_MATRIX_REPORT");
    private static final Path FAR_MINING_STORAGE_REPORT = resolvePath(
            "rtsbuilding.farMiningStorageSmokeReport", "RTSBUILDING_FAR_MINING_STORAGE_SMOKE_REPORT");
    private static final int NEAR_DISTANCE = resolveInt("rtsbuilding.guiCompatMatrixNearDistance", 3, 2, 16);
    private static final int FAR_DISTANCE = resolveInt("rtsbuilding.guiCompatMatrixFarDistance", 120, 17, 128);
    private static final int FAR_STABLE_TICKS = resolveInt(
            "rtsbuilding.guiCompatMatrixStableTicks", 20, 5, 200);
    private static final int CANDIDATE_LIMIT = resolveInt(
            "rtsbuilding.guiCompatMatrixLimit", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
    private static final boolean AUTO_EXIT = resolveBoolean("rtsbuilding.guiCompatMatrixAutoExit", true);
    private static final Set<String> NAMESPACE_FILTER = resolveNamespaces();
    private static final Set<String> BLOCK_FILTER = resolveBlockIds();

    private static MatrixRun run = REPORT_PATH == null ? null : new MatrixRun(REPORT_PATH);

    private RtsGuiCompatMatrixProbe() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || run == null || run.finished) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            run.tick(minecraft);
        } catch (RuntimeException | LinkageError failure) {
            run.recordInfrastructureFailure(minecraft, failure);
        }
    }

    private static final class MatrixRun {
        private final RtsGuiCompatMatrixReport report;
        private final RtsGuiCompatMatrixReport.ResumeState resume;
        private Phase phase = Phase.WAIT_WORLD;
        private Phase phaseAfterEnable = Phase.DISCOVER;
        private List<RtsGuiCompatCandidateCatalog.Candidate> candidates = new ArrayList<>();
        private RtsGuiCompatCandidateCatalog.Candidate candidate;
        private int candidateIndex = -1;
        private int phaseTicks;
        private int mainMenuTicks;
        private String launchScreen = "";
        private int stableTicks;
        private int guiCandidates;
        private int passed;
        private int failed;
        private int skipped;
        private int previousCrashes;
        private boolean worldLaunchSent;
        private boolean toggleSent;
        private boolean sawExternalScreen;
        private boolean sawMenu;
        private boolean setupAckObserved;
        private boolean interactionAckObserved;
        private boolean finished;
        private BlockPos targetPos;
        private long setupBaseline;
        private long interactionBaseline;
        private int setupAckTicks;
        private int interactionAckTicks;
        private String stableScreen = "";
        private String stableMenu = "";
        private RtsGuiCompatMatrixReport.Observation near = RtsGuiCompatMatrixReport.Observation.EMPTY;

        MatrixRun(Path reportPath) {
            this.report = new RtsGuiCompatMatrixReport(reportPath);
            this.resume = report.readResumeState();
        }

        void tick(Minecraft minecraft) {
            if (!hasPlayableWorld(minecraft)) {
                tickWorldLaunch(minecraft);
                return;
            }
            phaseTicks++;
            switch (phase) {
                case WAIT_WORLD:
                    if (phaseTicks >= WORLD_READY_TICKS) moveTo(Phase.ENABLE_RTS);
                    break;
                case ENABLE_RTS:
                    ensureRtsEnabled(minecraft);
                    break;
                case DISCOVER:
                    discoverCandidates();
                    moveTo(Phase.NEXT_CANDIDATE);
                    break;
                case NEXT_CANDIDATE:
                    startNextCandidate(minecraft);
                    break;
                case WAIT_NEAR_SETUP:
                    if (phaseTicks >= SETUP_WAIT_TICKS) validateAndMoveToInteraction(minecraft, true);
                    break;
                case SEND_NEAR:
                    sendInteraction(minecraft, true);
                    break;
                case OBSERVE_NEAR:
                    observe(minecraft, true);
                    break;
                case CLOSE_NEAR:
                    if (phaseTicks == 1) closeExternalScreen(minecraft);
                    if (phaseTicks >= CLOSE_WAIT_TICKS) prepare(minecraft, false);
                    break;
                case WAIT_FAR_SETUP:
                    if (phaseTicks >= SETUP_WAIT_TICKS) validateAndMoveToInteraction(minecraft, false);
                    break;
                case SEND_FAR:
                    sendInteraction(minecraft, false);
                    break;
                case OBSERVE_FAR:
                    observe(minecraft, false);
                    break;
                case CLOSE_FAR:
                    if (phaseTicks == 1) closeExternalScreen(minecraft);
                    if (phaseTicks >= CLOSE_WAIT_TICKS) moveTo(Phase.NEXT_CANDIDATE);
                    break;
                case START_FAR_MINING_STORAGE:
                    startFarMiningStorageSmoke(minecraft);
                    break;
                case WAIT_FAR_MINING_STORAGE:
                    waitFarMiningStorageSmoke(minecraft);
                    break;
                case FINISH:
                    finish(minecraft);
                    break;
                default:
                    break;
            }
        }

        private void tickWorldLaunch(Minecraft minecraft) {
            if (phase != Phase.WAIT_WORLD || worldLaunchSent) return;
            // 大型整合包经常用 FancyMenu 等组件彻底替换 GuiMainMenu。只在 FML 已完整
            // 到达 AVAILABLE 后接受稳定的非空菜单，既兼容换皮主菜单，也不会在模组加载中抢跑。
            GuiScreen screen = minecraft.currentScreen;
            if (!Loader.instance().hasReachedState(LoaderState.AVAILABLE) || screen == null) {
                mainMenuTicks = 0;
                launchScreen = "";
                return;
            }
            String screenName = screen.getClass().getName();
            if (!screenName.equals(launchScreen)) {
                launchScreen = screenName;
                mainMenuTicks = 0;
                return;
            }
            mainMenuTicks++;
            if (mainMenuTicks >= MAIN_MENU_STABLE_TICKS) {
                RtsbuildingMod.LOGGER.info("RTS_GUI_MATRIX launching world from screen={}", screenName);
                WorldSettings settings = new WorldSettings(
                        0x5254534d41545258L, GameType.CREATIVE, true, false, WorldType.FLAT);
                settings.enableCommands();
                minecraft.launchIntegratedServer(WORLD_DIRECTORY,
                        "RTSBuilding MM GUI Matrix", settings);
                worldLaunchSent = true;
            }
        }

        private void ensureRtsEnabled(Minecraft minecraft) {
            ClientRtsController controller = ClientRtsController.get();
            if (controller.isEnabled()) {
                controller.selectEmptyHand();
                moveTo(phaseAfterEnable);
                return;
            }
            if (!toggleSent) {
                RtsClientPacketGateway.sendToggleCamera(controller.isStartCameraAtPlayerHead());
                toggleSent = true;
            }
            if (phaseTicks > 200) {
                throw new IllegalStateException("RTS mode did not enable within 200 ticks");
            }
        }

        private void discoverCandidates() {
            List<RtsGuiCompatCandidateCatalog.Candidate> discovered =
                    RtsGuiCompatCandidateCatalog.discover();
            for (RtsGuiCompatCandidateCatalog.Candidate one : discovered) {
                if (!NAMESPACE_FILTER.isEmpty() && !NAMESPACE_FILTER.contains(one.namespace())) continue;
                if (!BLOCK_FILTER.isEmpty() && !BLOCK_FILTER.contains(one.blockId())) continue;
                candidates.add(one);
                if (candidates.size() >= CANDIDATE_LIMIT) break;
            }
            RtsbuildingMod.LOGGER.info("RTS_GUI_MATRIX discovered={} resumed={} interrupted={}",
                    candidates.size(), resume.completed.size(), resume.interrupted.size());
        }

        private void startNextCandidate(Minecraft minecraft) {
            while (++candidateIndex < candidates.size()) {
                candidate = candidates.get(candidateIndex);
                if (resume.completed.contains(candidate.key())) continue;
                if (resume.interrupted.contains(candidate.key())) {
                    previousCrashes++;
                    failed++;
                    report.result(candidateIndex + 1, candidates.size(), candidate,
                            NEAR_DISTANCE,
                            new RtsGuiCompatMatrixReport.Observation(
                                    "INTERRUPTED_PREVIOUS_RUN", "", ""),
                            FAR_DISTANCE, RtsGuiCompatMatrixReport.Observation.EMPTY,
                            "上次进程在 BEGIN 后中断；保留为需要单独复现的候选。");
                    continue;
                }
                report.begin(candidateIndex + 1, candidates.size(), candidate,
                        NEAR_DISTANCE, FAR_DISTANCE);
                near = RtsGuiCompatMatrixReport.Observation.EMPTY;
                prepare(minecraft, true);
                return;
            }
            moveTo(FAR_MINING_STORAGE_REPORT == null
                    ? Phase.FINISH : Phase.START_FAR_MINING_STORAGE);
        }

        private void startFarMiningStorageSmoke(Minecraft minecraft) {
            minecraft.player.sendChatMessage("/rtsbuilding_far_mining_storage_smoke");
            moveTo(Phase.WAIT_FAR_MINING_STORAGE);
        }

        private void waitFarMiningStorageSmoke(Minecraft minecraft) {
            if (FAR_MINING_STORAGE_REPORT != null && Files.isRegularFile(FAR_MINING_STORAGE_REPORT)) {
                try {
                    List<String> lines = Files.readAllLines(FAR_MINING_STORAGE_REPORT, StandardCharsets.UTF_8);
                    String result = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
                    if (result.startsWith("PASS\t")) {
                        RtsbuildingMod.LOGGER.info("RTS_GUI_MATRIX FAR_MINING_STORAGE {}", result);
                    } else {
                        failed++;
                        RtsbuildingMod.LOGGER.error("RTS_GUI_MATRIX FAR_MINING_STORAGE {}", result);
                    }
                    moveTo(Phase.FINISH);
                    return;
                } catch (IOException failure) {
                    failed++;
                    RtsbuildingMod.LOGGER.error("Unable to read far mining storage smoke report", failure);
                    moveTo(Phase.FINISH);
                    return;
                }
            }
            if (phaseTicks > 200) {
                failed++;
                RtsbuildingMod.LOGGER.error("RTS_GUI_MATRIX FAR_MINING_STORAGE timed out");
                moveTo(Phase.FINISH);
            }
        }

        private void prepare(Minecraft minecraft, boolean nearPhase) {
            closeExternalScreen(minecraft);
            int distance = nearPhase ? NEAR_DISTANCE : FAR_DISTANCE;
            targetPos = minecraft.player.getPosition().add(0, 0, distance);
            setupAckObserved = false;
            setupAckTicks = 0;
            setupBaseline = RtsGuiCompatMatrixSync.setupSequence();
            minecraft.player.sendChatMessage("/rtsbuilding_gui_compat_setup matrix "
                    + candidate.blockId() + " " + distance + " " + candidate.meta()
                    + " " + targetPos.getX() + " " + targetPos.getY() + " " + targetPos.getZ());
            moveTo(nearPhase ? Phase.WAIT_NEAR_SETUP : Phase.WAIT_FAR_SETUP);
        }

        private void validateAndMoveToInteraction(Minecraft minecraft, boolean nearPhase) {
            if (!setupAckObserved) {
                boolean acknowledged = RtsGuiCompatMatrixSync.isSetupAcknowledgedAfter(
                        setupBaseline, targetPos, candidate.blockId(), candidate.meta());
                if (acknowledged) {
                    String setupFailure = RtsGuiCompatMatrixSync.setupFailureAfter(
                            setupBaseline, targetPos, candidate.blockId(), candidate.meta());
                    if (!setupFailure.isEmpty()) {
                        finishCandidate(new RtsGuiCompatMatrixReport.Observation(
                                        "SETUP_REJECTED", "", ""), nearPhase,
                                "服务端拒绝矩阵方块状态：" + setupFailure);
                        return;
                    }
                    setupAckObserved = true;
                } else {
                    if (phaseTicks >= SERVER_ACK_TIMEOUT_TICKS) {
                        finishCandidate(new RtsGuiCompatMatrixReport.Observation(
                                        "SETUP_ACK_TIMEOUT", "", ""), nearPhase,
                                "服务端未在超时前确认矩阵方块布置。目标=" + targetPos);
                    }
                    return;
                }
            }
            setupAckTicks++;
            IBlockState state = minecraft.world.getBlockState(targetPos);
            String actual = registryName(state.getBlock());
            TileEntity clientTile = minecraft.world.getTileEntity(targetPos);
            boolean tileReady = !candidate.tileEntity() || clientTile != null;
            if (!candidate.blockId().equals(actual) || !tileReady) {
                if (setupAckTicks >= CLIENT_SETUP_SYNC_TIMEOUT_TICKS) {
                    finishCandidate(new RtsGuiCompatMatrixReport.Observation(
                                    "SETUP_FAILED", "", ""),
                            nearPhase, "pos=" + targetPos + " expected=" + candidate.blockId()
                                    + " actual=" + actual + " expectedTile=" + candidate.tileEntity()
                                    + " actualTile=" + (clientTile == null ? "null"
                                            : clientTile.getClass().getName())
                                    + " afterServerAck=true");
                }
                return;
            }
            moveTo(nearPhase ? Phase.SEND_NEAR : Phase.SEND_FAR);
        }

        private void sendInteraction(Minecraft minecraft, boolean nearPhase) {
            ClientRtsController controller = ClientRtsController.get();
            if (!controller.isEnabled()) {
                phaseAfterEnable = nearPhase ? Phase.SEND_NEAR : Phase.SEND_FAR;
                toggleSent = false;
                moveTo(Phase.ENABLE_RTS);
                return;
            }
            controller.selectEmptyHand();
            Vec3d hit = new Vec3d(targetPos.getX() + 0.5D,
                    targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
            Vec3d origin = minecraft.player.getPositionEyes(1.0F);
            Vec3d direction = hit.subtract(origin).normalize();
            resetObservation();
            interactionBaseline = RtsGuiCompatMatrixSync.interactionSequence();
            controller.interactEmpty(new RayTraceResult(hit, EnumFacing.UP, targetPos), origin, direction);
            moveTo(nearPhase ? Phase.OBSERVE_NEAR : Phase.OBSERVE_FAR);
        }

        private void observe(Minecraft minecraft, boolean nearPhase) {
            if (!interactionAckObserved) {
                boolean acknowledged = RtsGuiCompatMatrixSync.isInteractionAcknowledgedAfter(
                        interactionBaseline, targetPos);
                if (acknowledged) {
                    String interactionFailure = RtsGuiCompatMatrixSync.interactionFailureAfter(
                            interactionBaseline, targetPos);
                    if (!interactionFailure.isEmpty()) {
                        finishCandidate(new RtsGuiCompatMatrixReport.Observation(
                                        "INTERACTION_REJECTED", "", ""), nearPhase,
                                "第三方方块拒绝孤立矩阵交互：" + interactionFailure);
                        return;
                    }
                    interactionAckObserved = true;
                } else {
                    if (phaseTicks >= SERVER_ACK_TIMEOUT_TICKS) {
                        finishCandidate(new RtsGuiCompatMatrixReport.Observation(
                                        "INTERACTION_ACK_TIMEOUT", "", ""), nearPhase,
                                "服务端未在超时前确认生产 RTS 交互。目标=" + targetPos);
                    }
                    return;
                }
            }
            interactionAckTicks++;
            String screen = externalScreenClass(minecraft.currentScreen);
            String menu = currentMenuClass(minecraft);
            boolean hasScreen = !screen.isEmpty();
            boolean hasMenu = !menu.isEmpty();
            sawExternalScreen |= hasScreen;
            sawMenu |= hasMenu;

            if (hasScreen && screen.equals(stableScreen) && menu.equals(stableMenu)) {
                stableTicks++;
            } else if (hasScreen) {
                stableScreen = screen;
                stableMenu = menu;
                stableTicks = 1;
            } else {
                stableTicks = 0;
            }

            int required = nearPhase ? NEAR_STABLE_TICKS : FAR_STABLE_TICKS;
            if (stableTicks >= required) {
                RtsGuiCompatMatrixReport.Observation success =
                        new RtsGuiCompatMatrixReport.Observation("OPEN_STABLE", screen, menu);
                if (nearPhase) {
                    near = success;
                    guiCandidates++;
                    moveTo(Phase.CLOSE_NEAR);
                } else {
                    passed++;
                    report.result(candidateIndex + 1, candidates.size(), candidate,
                            NEAR_DISTANCE, near, FAR_DISTANCE, success,
                            "近距与远距均通过生产 RTS 交互路径。");
                    moveTo(Phase.CLOSE_FAR);
                }
                return;
            }

            if ((sawExternalScreen || sawMenu) && !hasScreen && phaseTicks > 3) {
                RtsGuiCompatMatrixReport.Observation closed =
                        new RtsGuiCompatMatrixReport.Observation("CLOSED_EARLY", stableScreen, stableMenu);
                finishCandidate(closed, nearPhase,
                        "GUI/container opened but closed before the stability threshold.");
                return;
            }
            // 完全没有菜单/屏幕时快速判空；一旦服务端菜单已经出现，就给大型模组的
            // S2C 建屏幕留独立预算。屏幕首次出现后，稳定计时也不能再复用“首屏等待”预算。
            boolean noVisibleResponseTimedOut = !sawMenu && !sawExternalScreen
                    && interactionAckTicks >= OPEN_TIMEOUT_TICKS;
            boolean menuWithoutScreenTimedOut = sawMenu && !sawExternalScreen
                    && interactionAckTicks >= MENU_SCREEN_TIMEOUT_TICKS;
            boolean unstableScreenTimedOut = sawExternalScreen
                    && interactionAckTicks >= GUI_STABILITY_TIMEOUT_TICKS;
            if (noVisibleResponseTimedOut || menuWithoutScreenTimedOut || unstableScreenTimedOut) {
                RtsGuiCompatMatrixReport.Observation timeout =
                        new RtsGuiCompatMatrixReport.Observation(
                                sawExternalScreen ? "SCREEN_UNSTABLE"
                                        : sawMenu ? "SCREEN_MISSING" : "NO_GUI_OR_PREREQUISITE",
                                stableScreen, stableMenu);
                finishCandidate(timeout, nearPhase,
                        sawExternalScreen ? "Client screen appeared but never reached the stability threshold."
                                : sawMenu ? "Container existed without a stable client screen."
                                        : "Default isolated block did not open a GUI at near distance.");
            }
        }

        private void finishCandidate(RtsGuiCompatMatrixReport.Observation outcome,
                boolean nearPhase, String note) {
            if (nearPhase) {
                if ("NO_GUI_OR_PREREQUISITE".equals(outcome.outcome)) skipped++;
                else failed++;
                report.result(candidateIndex + 1, candidates.size(), candidate,
                        NEAR_DISTANCE, outcome, FAR_DISTANCE,
                        RtsGuiCompatMatrixReport.Observation.EMPTY, note);
                moveTo(Phase.CLOSE_FAR);
            } else {
                failed++;
                report.result(candidateIndex + 1, candidates.size(), candidate,
                        NEAR_DISTANCE, near, FAR_DISTANCE, outcome, note);
                moveTo(Phase.CLOSE_FAR);
            }
        }

        private void closeExternalScreen(Minecraft minecraft) {
            if (minecraft.player != null && minecraft.player.openContainer != null
                    && minecraft.player.openContainer.windowId != 0) {
                minecraft.player.closeScreen();
            } else if (minecraft.currentScreen != null
                    && !BUILDER_SCREEN.equals(minecraft.currentScreen.getClass().getName())) {
                minecraft.displayGuiScreen(null);
            }
            RtsRemoteMenuCompat.clearClientRemoteMenu();
        }

        private void resetObservation() {
            stableTicks = 0;
            sawExternalScreen = false;
            sawMenu = false;
            interactionAckObserved = false;
            interactionAckTicks = 0;
            stableScreen = "";
            stableMenu = "";
        }

        private void finish(Minecraft minecraft) {
            report.summary(candidates.size(), guiCandidates, passed, failed, skipped, previousCrashes);
            RtsbuildingMod.LOGGER.info(
                    "RTS_GUI_MATRIX COMPLETE candidates={} gui={} passed={} failed={} skipped={} interrupted={}",
                    candidates.size(), guiCandidates, passed, failed, skipped, previousCrashes);
            finished = true;
            if (AUTO_EXIT) minecraft.shutdown();
        }

        private void recordInfrastructureFailure(Minecraft minecraft, Throwable failure) {
            RtsbuildingMod.LOGGER.error("RTS GUI matrix infrastructure failure", failure);
            if (candidate != null && candidateIndex >= 0 && candidateIndex < candidates.size()) {
                failed++;
                report.result(candidateIndex + 1, candidates.size(), candidate,
                        NEAR_DISTANCE,
                        new RtsGuiCompatMatrixReport.Observation("PROBE_EXCEPTION", "", ""),
                        FAR_DISTANCE, RtsGuiCompatMatrixReport.Observation.EMPTY,
                        failure.getClass().getName() + ": " + String.valueOf(failure.getMessage()));
                closeExternalScreen(minecraft);
                moveTo(Phase.NEXT_CANDIDATE);
            } else {
                finished = true;
                if (AUTO_EXIT) minecraft.shutdown();
            }
        }

        private void moveTo(Phase next) {
            phase = next;
            phaseTicks = 0;
        }
    }

    private enum Phase {
        WAIT_WORLD,
        ENABLE_RTS,
        DISCOVER,
        NEXT_CANDIDATE,
        WAIT_NEAR_SETUP,
        SEND_NEAR,
        OBSERVE_NEAR,
        CLOSE_NEAR,
        WAIT_FAR_SETUP,
        SEND_FAR,
        OBSERVE_FAR,
        CLOSE_FAR,
        START_FAR_MINING_STORAGE,
        WAIT_FAR_MINING_STORAGE,
        FINISH
    }

    private static boolean hasPlayableWorld(Minecraft minecraft) {
        return minecraft != null && minecraft.player != null && minecraft.world != null
                && minecraft.player.connection != null;
    }

    private static String externalScreenClass(GuiScreen screen) {
        if (screen == null) return "";
        String name = screen.getClass().getName();
        return BUILDER_SCREEN.equals(name) ? "" : name;
    }

    private static String currentMenuClass(Minecraft minecraft) {
        Container menu = minecraft == null || minecraft.player == null
                ? null : minecraft.player.openContainer;
        return menu == null || menu.windowId == 0 ? "" : menu.getClass().getName();
    }

    private static String registryName(Block block) {
        return block == null || block.getRegistryName() == null ? "" : block.getRegistryName().toString();
    }

    private static Path resolvePath(String property, String environment) {
        String value = System.getProperty(property);
        if (isBlank(value)) value = System.getenv(environment);
        return isBlank(value) ? null : Paths.get(value).toAbsolutePath().normalize();
    }

    private static int resolveInt(String property, int fallback, int min, int max) {
        String value = System.getProperty(property);
        if (isBlank(value)) return fallback;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(value)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean resolveBoolean(String property, boolean fallback) {
        String value = System.getProperty(property);
        if (isBlank(value)) return fallback;
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static Set<String> resolveNamespaces() {
        return resolveStringSet("rtsbuilding.guiCompatMatrixNamespaces");
    }

    private static Set<String> resolveBlockIds() {
        return resolveStringSet("rtsbuilding.guiCompatMatrixBlocks");
    }

    private static Set<String> resolveStringSet(String property) {
        String value = System.getProperty(property, "");
        Set<String> result = new HashSet<String>();
        for (String part : value.split(",")) {
            String normalized = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalized.isEmpty()) result.add(normalized);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
