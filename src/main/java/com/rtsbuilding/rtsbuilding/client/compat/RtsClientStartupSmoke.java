package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

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
public final class RtsClientStartupSmoke {
    private static final String ENABLE_PROPERTY = "rtsbuilding.clientStartupSmoke";
    private static final String REPORT_PROPERTY = "rtsbuilding.clientStartupSmokeReport";
    private static final String WORLD_DIRECTORY = "RTSBuildingClientSmoke";
    private static final int MAIN_MENU_STABLE_TICKS = 20;
    /** 必须超过入门提醒的 80 tick，才能覆盖最初的客户端崩溃路径。 */
    private static final int WORLD_STABLE_TICKS = 140;
    private static final int RTS_RENDER_TICKS = 60;
    private static final int MINING_PACKET_SETTLE_TICKS = 20;
    private static final int FINAL_STABLE_TICKS = 40;
    private static final int STAGE_TIMEOUT_TICKS = 20 * 30;
    private static final int TOTAL_TIMEOUT_TICKS = 20 * 120;

    private static final boolean ENABLED = Boolean.getBoolean(ENABLE_PROPERTY);
    private static final Path REPORT_PATH = resolveReportPath();

    private static Stage stage = Stage.WAIT_MAIN_MENU;
    private static int stageTicks;
    private static int totalTicks;
    private static int finalStableTicks;
    private static BlockPos emptyToolMinePos;
    private static BlockPos creativePrototypePlacedPos;
    private static boolean finished;

    private RtsClientStartupSmoke() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
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
                    || minecraft.theWorld != null || minecraft.thePlayer != null) {
                if (stageTicks > STAGE_TIMEOUT_TICKS) {
                    finish(minecraft, false, "main menu did not become ready");
                }
                return;
            }
            if (stageTicks < MAIN_MENU_STABLE_TICKS) return;
            append("MAIN_MENU_READY");
            verifyTranslation(minecraft, "screen.rtsbuilding.plugins");
            WorldSettings settings = new WorldSettings(
                    0x525453112L, GameType.CREATIVE, true, false, WorldType.DEFAULT);
            settings.enableCommands();
            minecraft.launchIntegratedServer(
                    WORLD_DIRECTORY, "RTSBuilding Client Smoke", settings);
            moveTo(Stage.WAIT_WORLD);
            return;
        }

        if (stage == Stage.WAIT_WORLD) {
            if (minecraft.theWorld == null || minecraft.thePlayer == null
                    || minecraft.thePlayer.sendQueue == null
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
            emptyToolMinePos = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                    .blockPosition(minecraft.thePlayer).down();
            RtsClientPacketGateway.sendMineStart(
                    emptyToolMinePos, EnumFacing.UP.getIndex(), 0, "", null, false, false);
            append("EMPTY_TOOL_MINE_START_SENT pos=" + emptyToolMinePos);
            moveTo(Stage.WAIT_EMPTY_TOOL_MINE_START);
            return;
        }

        if (stage == Stage.WAIT_EMPTY_TOOL_MINE_START) {
            if (!integratedServerHealthy(minecraft)) {
                finish(minecraft, false, "integrated server stopped after empty-tool mine start");
                return;
            }
            if (stageTicks < MINING_PACKET_SETTLE_TICKS) return;
            RtsClientPacketGateway.sendMineAbort(
                    emptyToolMinePos, EnumFacing.UP.getIndex(), 0);
            append("EMPTY_TOOL_MINE_ABORT_SENT pos=" + emptyToolMinePos);
            moveTo(Stage.WAIT_EMPTY_TOOL_MINE_ABORT);
            return;
        }

        if (stage == Stage.WAIT_EMPTY_TOOL_MINE_ABORT) {
            if (!integratedServerHealthy(minecraft)) {
                finish(minecraft, false, "integrated server stopped after empty-tool mine abort");
                return;
            }
            if (stageTicks < MINING_PACKET_SETTLE_TICKS) return;
            append("EMPTY_TOOL_MINING_ROUND_TRIP_OK");
            Vec3d hit = new Vec3d(
                    emptyToolMinePos.getX() + 0.5D,
                    emptyToolMinePos.getY() + 1.0D,
                    emptyToolMinePos.getZ() + 0.5D);
            Vec3d origin = new Vec3d(
                    minecraft.thePlayer.posX,
                    minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight(),
                    minecraft.thePlayer.posZ);
            Vec3d direction = hit.subtract(origin).normalize();
            RtsClientPacketGateway.sendInteractBlockEmptyHand(
                    new RayTraceResult(hit, EnumFacing.UP, emptyToolMinePos),
                    origin,
                    direction);
            append("EMPTY_HAND_INTERACTION_SENT pos=" + emptyToolMinePos);
            moveTo(Stage.WAIT_EMPTY_HAND_INTERACTION);
            return;
        }

        if (stage == Stage.WAIT_EMPTY_HAND_INTERACTION) {
            if (!integratedServerHealthy(minecraft)) {
                finish(minecraft, false, "integrated server stopped after empty-hand interaction");
                return;
            }
            if (stageTicks < MINING_PACKET_SETTLE_TICKS) return;
            append("EMPTY_HAND_INTERACTION_ROUND_TRIP_OK");
            BlockPos anchor = findCreativePlacementAnchor(minecraft);
            if (anchor == null) {
                finish(minecraft, false, "no nearby placement anchor for creative prototype probe");
                return;
            }
            creativePrototypePlacedPos = anchor.up();
            Vec3d hit = new Vec3d(
                    anchor.getX() + 0.5D,
                    anchor.getY() + 1.0D,
                    anchor.getZ() + 0.5D);
            Vec3d origin = new Vec3d(
                    minecraft.thePlayer.posX,
                    minecraft.thePlayer.posY + minecraft.thePlayer.getEyeHeight(),
                    minecraft.thePlayer.posZ);
            RtsClientPacketGateway.sendInteractBlockWithPinnedItem(
                    new RayTraceResult(hit, EnumFacing.UP, anchor),
                    "minecraft:wool",
                    new ItemStack(Blocks.wool, 1, 14),
                    origin,
                    hit.subtract(origin).normalize());
            append("CREATIVE_PINNED_PROTOTYPE_SENT target=" + creativePrototypePlacedPos + " metadata=14");
            moveTo(Stage.WAIT_CREATIVE_PINNED_PROTOTYPE);
            return;
        }

        if (stage == Stage.WAIT_CREATIVE_PINNED_PROTOTYPE) {
            if (!integratedServerHealthy(minecraft)) {
                finish(minecraft, false, "integrated server stopped after creative pinned-item interaction");
                return;
            }
            if (stageTicks < MINING_PACKET_SETTLE_TICKS) return;
            int x = creativePrototypePlacedPos.getX();
            int y = creativePrototypePlacedPos.getY();
            int z = creativePrototypePlacedPos.getZ();
            if (minecraft.theWorld.getBlock(x, y, z) != Blocks.wool
                    || minecraft.theWorld.getBlockMetadata(x, y, z) != 14) {
                finish(minecraft, false,
                        "creative pinned-item prototype was not preserved at " + creativePrototypePlacedPos);
                return;
            }
            append("CREATIVE_PINNED_PROTOTYPE_ROUND_TRIP_OK metadata=14");
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

    private static boolean integratedServerHealthy(Minecraft minecraft) {
        return minecraft != null && minecraft.getIntegratedServer() != null
                && minecraft.getIntegratedServer().isServerRunning();
    }

    /** 在玩家附近寻找一个上方为空气的实体方块，避免探针依赖固定出生地地形。 */
    private static BlockPos findCreativePlacementAnchor(Minecraft minecraft) {
        BlockPos center = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat
                .blockPosition(minecraft.thePlayer);
        for (int radius = 3; radius <= 8; radius++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 2; dy >= -6; dy--) {
                    BlockPos anchor = center.add(radius, dy, dz);
                    BlockPos above = anchor.up();
                    if (!minecraft.theWorld.isAirBlock(
                            anchor.getX(), anchor.getY(), anchor.getZ())
                            && minecraft.theWorld.isAirBlock(
                            above.getX(), above.getY(), above.getZ())) {
                        return anchor;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 1.7.10 按 en_US / zh_CN 形式精确寻找 .lang；文件名沿用现代小写格式时，
     * {@link I18n#format(String, Object...)} 会原样返回 key。把这项检查放进真客户端，
     * 可以同时覆盖资源是否进 JAR、语言管理器是否加载以及最终 UI 翻译调用三层边界。
     */
    private static void verifyTranslation(Minecraft minecraft, String key) {
        String translated = I18n.format(key);
        if (translated == null || translated.trim().isEmpty() || key.equals(translated)) {
            finish(minecraft, false, "i18n unresolved key=" + key);
            throw new IllegalStateException("I18n unresolved: " + key);
        }
        append("I18N_OK key=" + key + " value=" + translated);
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
        WAIT_EMPTY_TOOL_MINE_START,
        WAIT_EMPTY_TOOL_MINE_ABORT,
        WAIT_EMPTY_HAND_INTERACTION,
        WAIT_CREATIVE_PINNED_PROTOTYPE,
        OBSERVE_RTS_RENDER,
        WAIT_RTS_OFF
    }
}
