package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 真实客户端启动烟测的游戏内驱动器。
 *
 * <p>它只在 Gradle {@code runClientSmoke} 显式设置系统属性时启用。驱动器会从主菜单
 * 创建隔离单人世界，跨过入门提醒的延迟窗口，开关一次 RTS 相机并继续运行若干帧，
 * 最后写出机器可判定的报告并正常关闭客户端。普通发布包虽然包含这个类，但没有属性
 * 时不会创建目录、注册额外命令或改变任何玩家行为。</p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class RtsClientStartupSmoke {
    private static final String ENABLE_PROPERTY = "rtsbuilding.clientStartupSmoke";
    private static final String REPORT_PROPERTY = "rtsbuilding.clientStartupSmokeReport";
    private static final String WORLD_DIRECTORY = "RTSBuildingClientSmoke";
    private static final int MAIN_MENU_STABLE_TICKS = 20;
    /** 必须超过入门提醒的 80 tick，才能覆盖最初的客户端崩溃路径。 */
    private static final int WORLD_STABLE_TICKS = 140;
    private static final int RTS_RENDER_TICKS = 60;
    private static final int FINAL_STABLE_TICKS = 40;
    private static final int STAGE_TIMEOUT_TICKS = 20 * 30;
    private static final int TOTAL_TIMEOUT_TICKS = 20 * 120;

    private static final boolean ENABLED = Boolean.getBoolean(ENABLE_PROPERTY);
    private static final Path REPORT_PATH = resolveReportPath();

    private static Stage stage = Stage.WAIT_MAIN_MENU;
    private static int stageTicks;
    private static int totalTicks;
    private static int finalStableTicks;
    private static boolean finished;

    private RtsClientStartupSmoke() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        try {
            totalTicks++;
            stageTicks++;
            if (totalTicks > TOTAL_TIMEOUT_TICKS) {
                finish(minecraft, false, "total timeout at stage=" + stage);
                return;
            }
            tickStage(minecraft);
        } catch (Throwable failure) {
            finish(minecraft, false,
                    failure.getClass().getName() + ": " + safeMessage(failure));
        }
    }

    private static void tickStage(Minecraft minecraft) {
        if (stage == Stage.WAIT_MAIN_MENU) {
            if (!(minecraft.currentScreen instanceof GuiMainMenu)
                    || minecraft.world != null || minecraft.player != null) {
                if (stageTicks > STAGE_TIMEOUT_TICKS) {
                    finish(minecraft, false, "main menu did not become ready");
                }
                return;
            }
            if (stageTicks < MAIN_MENU_STABLE_TICKS) return;
            append("MAIN_MENU_READY");
            WorldSettings settings = new WorldSettings(
                    0x525453112L, GameType.CREATIVE, true, false, WorldType.DEFAULT);
            settings.enableCommands();
            minecraft.launchIntegratedServer(
                    WORLD_DIRECTORY, "RTSBuilding Client Smoke", settings);
            moveTo(Stage.WAIT_WORLD);
            return;
        }

        if (stage == Stage.WAIT_WORLD) {
            if (minecraft.world == null || minecraft.player == null
                    || minecraft.player.connection == null
                    || minecraft.getIntegratedServer() == null) {
                failStageTimeout(minecraft, "integrated client world did not become ready");
                return;
            }
            if (stageTicks == 1) {
                append("WORLD_READY folder=" + minecraft.getIntegratedServer().getFolderName());
            }
            if (stageTicks < WORLD_STABLE_TICKS) return;
            RtsClientPacketGateway.sendToggleCamera(
                    ClientRtsController.get().isStartCameraAtPlayerHead());
            append("RTS_ENABLE_SENT");
            moveTo(Stage.WAIT_RTS_ON);
            return;
        }

        if (stage == Stage.WAIT_RTS_ON) {
            if (!ClientRtsController.get().isEnabled()) {
                failStageTimeout(minecraft, "RTS enable acknowledgement timed out");
                return;
            }
            append("RTS_ENABLED");
            moveTo(Stage.OBSERVE_RTS_RENDER);
            return;
        }

        if (stage == Stage.OBSERVE_RTS_RENDER) {
            if (!ClientRtsController.get().isEnabled()) {
                finish(minecraft, false, "RTS disabled unexpectedly during render observation");
                return;
            }
            if (stageTicks < RTS_RENDER_TICKS) return;
            RtsClientPacketGateway.sendToggleCamera(false);
            append("RTS_DISABLE_SENT");
            moveTo(Stage.WAIT_RTS_OFF);
            return;
        }

        if (stage == Stage.WAIT_RTS_OFF) {
            if (ClientRtsController.get().isEnabled()) {
                failStageTimeout(minecraft, "RTS disable acknowledgement timed out");
                return;
            }
            finalStableTicks++;
            if (finalStableTicks >= FINAL_STABLE_TICKS) {
                finish(minecraft, true,
                        "worldTicks>=" + WORLD_STABLE_TICKS
                                + " rtsRenderTicks=" + RTS_RENDER_TICKS);
            }
        }
    }

    private static void failStageTimeout(Minecraft minecraft, String message) {
        if (stageTicks > STAGE_TIMEOUT_TICKS) finish(minecraft, false, message);
    }

    private static void moveTo(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    private static void finish(Minecraft minecraft, boolean success, String detail) {
        if (finished) return;
        finished = true;
        String status = success ? "PASS" : "FAIL";
        append(status + " " + detail);
        System.out.println("RTS_112_CLIENT_SMOKE " + status + " " + detail);
        if (minecraft != null) minecraft.shutdown();
    }

    private static Path resolveReportPath() {
        if (!ENABLED) return null;
        String configured = System.getProperty(REPORT_PROPERTY, "").trim();
        if (configured.isEmpty()) {
            throw new IllegalStateException(REPORT_PROPERTY + " must be set for client smoke");
        }
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private static void append(String line) {
        if (REPORT_PATH == null) return;
        try {
            Path parent = REPORT_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(REPORT_PATH,
                    (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to write client smoke report", failure);
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? "" : failure.getMessage();
    }

    private enum Stage {
        WAIT_MAIN_MENU,
        WAIT_WORLD,
        WAIT_RTS_ON,
        OBSERVE_RTS_RENDER,
        WAIT_RTS_OFF
    }
}
