package com.rtsbuilding.rtsbuilding.client.compat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
    private static final int AUTO_WORLD_READY_DELAY = 10;
    private static final int AUTO_PLAYER_POSITION_STABLE_TICKS = 20;
    private static final int DEFAULT_AUTO_SETUP_DELAY = 40;
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
    private static final Path SUITE_PATH = resolveOptionalPath("rtsbuilding.guiCompatSuite",
            "RTSBUILDING_GUI_COMPAT_SUITE");
    private static final ResolvedSuite RESOLVED_SUITE = resolveSuite();
    private static final RtsGuiCompatSuiteLoader.RtsGuiCompatSuite SUITE = RESOLVED_SUITE.suite();
    private static final RtsGuiCompatProbeReport REPORT = new RtsGuiCompatProbeReport(
            REPORT_PATH,
            SUITE.suiteId(),
            resolveConfig("rtsbuilding.guiCompatBaselineSha", "RTSBUILDING_GUI_COMPAT_BASELINE_SHA", "unknown"),
            resolveConfig("rtsbuilding.guiCompatManifestHash", "RTSBUILDING_GUI_COMPAT_MANIFEST_HASH", "unknown"));

    private static long tick;
    private static boolean configErrorReported;
    private static String lastScreenClass = "";
    private static String lastScreenTitle = "";
    private static String lastMenuClass = "";
    private static int lastContainerId = -1;
    private static int screenlessMenuTicks;
    private static boolean respawnRequested;
    private static SmokeRun activeRun;
    private static RtsGuiCompatCase currentCase = SUITE.cases().getFirst();
    private static AutoRun autoRun = AUTO_RUN && REPORT_PATH != null && RESOLVED_SUITE.error().isBlank()
            ? new AutoRun(REPORT.resumeIndex(SUITE.cases().size()))
            : null;

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
        if (!RESOLVED_SUITE.error().isBlank() && !configErrorReported) {
            configErrorReported = true;
            writeRow("suite-load", "SKIP_SETUP", "", "", "", -1, RESOLVED_SUITE.error());
        }
        Minecraft minecraft = Minecraft.getInstance();
        recoverProbePlayerFromDeath(minecraft);
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
        RtsGuiCompatCase requestedCase = findCase(requestedCaseId);
        if (requestedCase == null) {
            writeRow("run-start", "SKIP_SETUP", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft),
                    "Unknown suite case: " + requestedCaseId);
            return 0;
        }
        currentCase = requestedCase;
        if (minecraft.player == null || minecraft.level == null) {
            writeRow("run-start", "FAIL", "", "", "", -1, "Client world or player is not ready.");
            return 0;
        }
        closeStaleBuilderScreen(minecraft);

        TargetResolution target = resolveTargetHit(minecraft);
        if (target == null) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft), "No target block found.");
            return 0;
        }

        BlockHitResult hit = applyCaseHitGeometry(target.hit(), currentCase);
        if (!target.trustedServerSetup() && !currentCase.blockId().isBlank()
                && !currentCase.blockId().equals(target.observedBlock())) {
            writeRow("run-start", "FAIL", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                    currentMenuClass(minecraft), currentContainerId(minecraft),
                    "Target mismatch: expected=" + currentCase.blockId() + " actual=" + target.observedBlock());
            return 0;
        }

        Vec3 origin = minecraft.player.getEyePosition();
        Vec3 rayDir = hit.getLocation().subtract(origin);
        rayDir = rayDir.lengthSqr() < 1.0E-6D ? minecraft.player.getLookAngle() : rayDir.normalize();

        activeRun = new SmokeRun(currentCase, hit, origin, rayDir);
        writeRow("run-start", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                currentMenuClass(minecraft), currentContainerId(minecraft),
                "pos=" + hit.getBlockPos().toShortString() + " block=" + currentCase.blockId()
                        + " clientObserved=" + target.observedBlock()
                        + " clientChunkLoaded=" + target.clientChunkLoaded()
                        + " trustedServerSetup=" + target.trustedServerSetup());
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

    private static TargetResolution resolveTargetHit(Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            if (currentCase.blockId().isBlank() || matchesTargetBlock(minecraft, hit.getBlockPos())) {
                return observedTarget(minecraft, hit);
            }
        }
        if (minecraft.player != null) {
            BlockPos expected = minecraft.player.blockPosition().offset(0, 0, currentCase.distance());
            if (matchesTargetBlock(minecraft, expected)) {
                return observedTarget(minecraft,
                        new BlockHitResult(Vec3.atCenterOf(expected), Direction.UP, expected, false));
            }
        }
        TargetResolution nearest = findNearestTargetHit(minecraft);
        if (nearest != null) {
            return nearest;
        }

        // 自动套件刚刚由服务端探针命令完成了精确布置。远距离区块未发送给客户端时，
        // 客户端看到空气是正常现象；仍应按已确认坐标发包，真正验证服务端远程交互链路。
        if (autoRun != null && minecraft.player != null && !currentSetupCommand().isBlank()) {
            BlockPos expected = minecraft.player.blockPosition().offset(0, 0, currentCase.distance());
            boolean loaded = minecraft.level != null && minecraft.level.hasChunkAt(expected);
            String observed = loaded ? blockIdAt(minecraft, expected) : "<unloaded>";
            BlockHitResult blindHit = new BlockHitResult(
                    Vec3.atCenterOf(expected), Direction.UP, expected, false);
            return new TargetResolution(blindHit, observed, loaded, true);
        }
        return null;
    }

    /**
     * 将测试清单里的点击面与方块内偏移应用到服务端真实交互射线。
     * 默认仍命中方块中心；只有 Pipez 抽取臂等按局部碰撞体分派菜单的方块才需要覆盖。
     */
    private static BlockHitResult applyCaseHitGeometry(BlockHitResult source, RtsGuiCompatCase guiCase) {
        BlockPos pos = source.getBlockPos();
        Vec3 location = Vec3.atCenterOf(pos).add(
                guiCase.hitOffsetX(), guiCase.hitOffsetY(), guiCase.hitOffsetZ());
        Direction face = Direction.valueOf(guiCase.hitFace());
        return new BlockHitResult(location, face, pos, false);
    }

    private static void recoverProbePlayerFromDeath(Minecraft minecraft) {
        if (!AUTO_RUN || minecraft.player == null) {
            respawnRequested = false;
            return;
        }
        if (!(minecraft.screen instanceof DeathScreen)) {
            respawnRequested = false;
            return;
        }
        if (respawnRequested) {
            return;
        }
        respawnRequested = true;
        minecraft.player.respawn();
        if (autoRun != null && !autoRun.finished) {
            // 重生包发出后客户端玩家对象会先恢复，坐标随后才同步到出生点。放弃当前的
            // 临时运行并重试同一 case，必须重新经过坐标稳定门，不能把环境死亡记成兼容失败。
            activeRun = null;
            autoRun.caseCompleted = false;
            autoRun.caseTicks = 0;
            autoRun.stageTicks = 0;
            autoRun.stage = AutoStage.WAIT_WORLD;
            autoRun.worldStability.reset();
        }
        writeRow("auto-respawn", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                currentMenuClass(minecraft), currentContainerId(minecraft),
                "Respawn requested so hostile mobs cannot stall the isolated GUI probe.");
    }

    private static TargetResolution observedTarget(Minecraft minecraft, BlockHitResult hit) {
        boolean loaded = minecraft.level != null && minecraft.level.hasChunkAt(hit.getBlockPos());
        return new TargetResolution(hit, loaded ? blockIdAt(minecraft, hit.getBlockPos()) : "<unloaded>",
                loaded, false);
    }

    private static String blockIdAt(Minecraft minecraft, BlockPos pos) {
        BlockState state = minecraft.level.getBlockState(pos);
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static boolean matchesTargetBlock(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level == null) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return currentCase.blockId().equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static TargetResolution findNearestTargetHit(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || currentCase.blockId().isBlank()) {
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
        return observedTarget(minecraft,
                new BlockHitResult(Vec3.atCenterOf(nearest), Direction.UP, nearest, false));
    }

    private record TargetResolution(BlockHitResult hit, String observedBlock,
            boolean clientChunkLoaded, boolean trustedServerSetup) {
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
            if (activeRun.guiCase.interactionItemId().isBlank()) {
                controller.interactEmpty(activeRun.hit, activeRun.rayOrigin, activeRun.rayDir);
            } else {
                RtsClientPacketGateway.sendInteractBlockWithToolSlot(
                        activeRun.hit, 0, activeRun.rayOrigin, activeRun.rayDir, false);
            }
            activeRun.stage = SmokeStage.OBSERVE;
            activeRun.stageTicks = 0;
            writeRow("run-interact", "INFO", screenClass, screenTitle, menuClass, containerId,
                    activeRun.guiCase.interactionItemId().isBlank()
                            ? "Sent RTS empty-hand right-click."
                            : "Sent RTS tool-slot right-click with " + activeRun.guiCase.interactionItemId() + ".");
            return;
        }

        if (activeRun.stage == SmokeStage.OBSERVE) {
            AbstractContainerMenu menu = minecraft.player == null ? null : minecraft.player.containerMenu;
            boolean hasMenu = menu != null && menu.containerId != 0;
            boolean hasScreen = minecraft.screen != null;
            boolean hasContainerScreen = minecraft.screen instanceof AbstractContainerScreen<?>;
            boolean menuMatches = hasMenu && matchesRegex(menu.getClass().getName(), activeRun.guiCase.expectedMenuRegex());
            boolean screenMatches = hasScreen && matchesRegex(screenClass, activeRun.guiCase.expectedScreenRegex());
            if (hasMenu && !menuMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected menu: " + menu.getClass().getName()
                                + " expected=" + activeRun.guiCase.expectedMenuRegex());
                return;
            }
            if (hasMenu && hasScreen && !screenMatches) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Unexpected screen: " + screenClass
                                + " expected=" + activeRun.guiCase.expectedScreenRegex());
                return;
            }
            if (hasMenu && hasScreen && !hasContainerScreen) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Menu remained open behind a non-container screen: " + screenClass);
                return;
            }
            if (hasMenu && hasContainerScreen && menuMatches && screenMatches) {
                activeRun.stableTicks++;
                activeRun.sawMenu = true;
                if ("VANILLA_INTERACTION".equals(activeRun.guiCase.depth())
                        && !activeRun.interactionPassed
                        && activeRun.stableTicks >= 5) {
                    if (activeRun.interactionDriver == null) {
                        activeRun.interactionDriver = new RtsGuiCompatVanillaInteractionDriver(activeRun.guiCase);
                    }
                    RtsGuiCompatVanillaInteractionDriver.TickResult interaction =
                            activeRun.interactionDriver.tick(minecraft, menu);
                    if (interaction.outcome() == RtsGuiCompatVanillaInteractionDriver.Outcome.FAIL) {
                        finishActiveRun("INTERACTION_FAIL", screenClass, screenTitle, menuClass, containerId,
                                interaction.note());
                        return;
                    }
                    if (interaction.outcome() == RtsGuiCompatVanillaInteractionDriver.Outcome.PASS) {
                        activeRun.interactionPassed = true;
                        activeRun.interactionNote = interaction.note();
                        writeRow("run-interaction-pass", "INFO", screenClass, screenTitle, menuClass, containerId,
                                interaction.note());
                    }
                }
                boolean interactionReady = !"VANILLA_INTERACTION".equals(activeRun.guiCase.depth())
                        || activeRun.interactionPassed;
                if (activeRun.stableTicks >= SUITE.stableTicks() && interactionReady) {
                    String status = activeRun.guiCase.discoveryOnly() ? "DISCOVERED" : "PASS";
                    finishActiveRun(status, screenClass, screenTitle, menuClass, containerId,
                            "Expected menu and screen stayed open for " + SUITE.stableTicks()
                                    + " ticks; actualMenu=" + menuClass + " actualScreen=" + screenClass
                                    + (activeRun.interactionNote.isBlank()
                                            ? "" : "; interaction=" + activeRun.interactionNote));
                }
                return;
            }
            if (activeRun.sawMenu && !hasScreen) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Screen closed before " + SUITE.stableTicks() + " stable ticks.");
                return;
            }
            if (activeRun.stageTicks > SUITE.openTimeoutTicks()) {
                finishActiveRun("FAIL", screenClass, screenTitle, menuClass, containerId,
                        "Expected menu did not open within " + SUITE.openTimeoutTicks()
                                + " ticks after interaction.");
            }
        }
    }

    private static void tickAutoRun(Minecraft minecraft, String screenClass, String screenTitle,
            String menuClass, int containerId) {
        if (autoRun == null || autoRun.finished) {
            return;
        }
        autoRun.caseTicks++;

        boolean playable = minecraft.player != null
                && minecraft.level != null
                && minecraft.player.connection != null
                && minecraft.player.isAlive()
                && !(minecraft.screen instanceof DeathScreen);
        if (!playable) {
            autoRun.worldStability.reset();
            if (autoRun.caseTicks > AUTO_TIMEOUT_TICKS) {
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
            if (!autoRun.worldStability.tick(true, minecraft.player.blockPosition())) {
                return;
            }
            if (autoRun.caseIndex >= SUITE.cases().size()) {
                finishAutoRun(minecraft);
                return;
            }
            writeRow("auto-world-stable", "INFO", screenClass, screenTitle, menuClass, containerId,
                    "Player position stayed at " + minecraft.player.blockPosition().toShortString()
                            + " for " + autoRun.worldStability.stableTicks()
                            + " ticks before setup.");
            autoRun.stage = AutoStage.PREPARE_CASE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.PREPARE_CASE) {
            currentCase = SUITE.cases().get(autoRun.caseIndex);
            autoRun.caseCompleted = false;
            autoRun.caseTicks = 0;
            String setupCommand = currentSetupCommand();
            if (!setupCommand.isBlank()) {
                minecraft.player.connection.sendCommand(setupCommand);
                writeRow("auto-setup-command", "INFO", screenClass, screenTitle, menuClass, containerId,
                        "/" + setupCommand);
                autoRun.stage = AutoStage.WAIT_SETUP;
                autoRun.stageTicks = 0;
                return;
            }
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.WAIT_SETUP) {
            int waitTicks = currentCase == null
                    ? DEFAULT_AUTO_SETUP_DELAY
                    : currentCase.setupWaitTicks();
            if (autoRun.stageTicks < waitTicks) {
                return;
            }
            autoRun.stage = AutoStage.START_PROBE;
            autoRun.stageTicks = 0;
        }

        if (autoRun.stage == AutoStage.START_PROBE) {
            int result = startFromCommand(currentCase.id());
            autoRun.stage = AutoStage.WAIT_FINISH;
            autoRun.stageTicks = 0;
            if (result != Command.SINGLE_SUCCESS || activeRun == null) {
                completeAutoCase("SKIP_SETUP");
            }
            return;
        }

        if (autoRun.stage == AutoStage.WAIT_FINISH) {
            if (activeRun == null && autoRun.caseCompleted) {
                if (autoRun.stageTicks >= AUTO_EXIT_DELAY) {
                    closeProbeMenu(minecraft);
                    autoRun.caseIndex++;
                    autoRun.stage = AutoStage.PREPARE_CASE;
                    autoRun.stageTicks = 0;
                    autoRun.caseTicks = 0;
                    if (autoRun.caseIndex >= SUITE.cases().size()) {
                        finishAutoRun(minecraft);
                    }
                }
            }
        }
    }

    private static void completeAutoCase(String status) {
        if (autoRun == null || autoRun.caseCompleted) {
            return;
        }
        autoRun.caseCompleted = true;
        REPORT.markCompleted(autoRun.caseIndex, currentCase, status);
    }

    private static void closeProbeMenu(Minecraft minecraft) {
        if (minecraft != null && minecraft.player != null && minecraft.player.containerMenu != null
                && minecraft.player.containerMenu.containerId != 0) {
            RtsClientPacketGateway.sendCloseRemoteMenu();
            minecraft.player.closeContainer();
        }
        if (minecraft != null && minecraft.screen != null
                && !(minecraft.screen.getClass().getName().equals(
                        "com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen"))) {
            minecraft.setScreen(null);
        }
    }

    private static void finishAutoRun(Minecraft minecraft) {
        if (autoRun == null || autoRun.finished) {
            return;
        }
        autoRun.finished = true;
        writeRow("suite-finish", "INFO", currentScreenClass(minecraft), currentScreenTitle(minecraft),
                currentMenuClass(minecraft), currentContainerId(minecraft),
                "Completed " + Math.min(autoRun.caseIndex, SUITE.cases().size())
                        + "/" + SUITE.cases().size() + " cases.");
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
        completeAutoCase(status);
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
        return resolveOptionalPath("rtsbuilding.guiCompatProbeReport", "RTSBUILDING_GUI_COMPAT_PROBE_REPORT");
    }

    private static Path resolveOptionalPath(String propertyName, String environmentName) {
        String configured = resolveConfig(propertyName, environmentName, "");
        return configured.isBlank() ? null : Path.of(configured).toAbsolutePath().normalize();
    }

    private static ResolvedSuite resolveSuite() {
        RtsGuiCompatCase fallbackCase = new RtsGuiCompatCase(
                CASE_ID,
                TARGET_BLOCK,
                resolveInt("rtsbuilding.guiCompatTargetDistance", "RTSBUILDING_GUI_COMPAT_TARGET_DISTANCE", 20),
                "OPEN_STABLE",
                "single_block",
                DEFAULT_AUTO_SETUP_DELAY,
                "",
                "UP",
                0.0D,
                0.0D,
                0.0D,
                EXPECTED_MENU_REGEX,
                EXPECTED_SCREEN_REGEX);
        if (SUITE_PATH == null) {
            return new ResolvedSuite(
                    RtsGuiCompatSuiteLoader.single(fallbackCase, REQUIRED_STABLE_TICKS, 120), "");
        }
        try {
            return new ResolvedSuite(RtsGuiCompatSuiteLoader.load(SUITE_PATH), "");
        } catch (RuntimeException | java.io.IOException exception) {
            RtsbuildingMod.LOGGER.error("Failed to load RTS GUI compat suite {}; automatic probing is disabled.",
                    SUITE_PATH, exception);
            return new ResolvedSuite(
                    RtsGuiCompatSuiteLoader.single(fallbackCase, REQUIRED_STABLE_TICKS, 120),
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
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
        if ("DISCOVER_THEN_LOCK".equals(regex)) {
            return value != null && !value.isBlank();
        }
        return value != null && value.matches(regex);
    }

    private static RtsGuiCompatCase findCase(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return currentCase;
        }
        return SUITE.cases().stream()
                .filter(guiCase -> guiCase.id().equals(caseId))
                .findFirst()
                .orElse(null);
    }

    private static String currentSetupCommand() {
        if (SUITE_PATH == null && !SETUP_COMMAND.isBlank()) {
            return SETUP_COMMAND;
        }
        return currentCase.setupCommand();
    }

    private static void writeRow(String event, String status, String screenClass, String screenTitle,
            String menuClass, int containerId, String note) {
        RtsGuiCompatCase reportCase = activeRun == null ? currentCase : activeRun.guiCase;
        REPORT.append(tick, reportCase, event, status, screenClass, screenTitle, menuClass, containerId, note);
    }

    private enum SmokeStage {
        START,
        SEND_INTERACT,
        OBSERVE
    }

    private enum AutoStage {
        WAIT_WORLD,
        PREPARE_CASE,
        WAIT_SETUP,
        START_PROBE,
        WAIT_FINISH
    }

    private static final class SmokeRun {
        private final RtsGuiCompatCase guiCase;
        private final BlockHitResult hit;
        private final Vec3 rayOrigin;
        private final Vec3 rayDir;
        private SmokeStage stage = SmokeStage.START;
        private int totalTicks;
        private int stageTicks;
        private int stableTicks;
        private boolean toggleSent;
        private boolean sawMenu;
        private boolean interactionPassed;
        private String interactionNote = "";
        private RtsGuiCompatVanillaInteractionDriver interactionDriver;

        private SmokeRun(RtsGuiCompatCase guiCase, BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
            this.guiCase = guiCase;
            this.hit = hit;
            this.rayOrigin = rayOrigin;
            this.rayDir = rayDir;
        }
    }

    private static final class AutoRun {
        private int caseIndex;
        private AutoStage stage = AutoStage.WAIT_WORLD;
        private final RtsGuiCompatWorldStabilityGate worldStability =
                new RtsGuiCompatWorldStabilityGate(AUTO_PLAYER_POSITION_STABLE_TICKS);
        private int caseTicks;
        private int stageTicks;
        private boolean caseCompleted;
        private boolean finished;

        private AutoRun(int caseIndex) {
            this.caseIndex = Math.max(0, caseIndex);
        }
    }

    private record ResolvedSuite(
            RtsGuiCompatSuiteLoader.RtsGuiCompatSuite suite,
            String error) {
    }
}
