package com.rtsbuilding.rtsbuilding.client.compat.create;

import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsPolicy;
import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsRuntime;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * BuilderScreen 内 Create 0.5.x Value Settings 的客户端适配器。
 *
 * <p>玩家短按仍走 Create 的 onShortInteract；按住五个客户端 tick 后打开 Create 自己的
 * ValueSettingsScreen。只有本适配器打开的屏幕会改走 RTS 专用远程包，普通 Create
 * 屏幕完全保留原生保存路径。Create 缺失时本类不会硬加载任何 Create 类型。</p>
 */
public final class RtsCreateValueSettingsCompat {
    private static final int SCREEN_OPEN_HOLD_TICKS = 5;
    private static final String VALUE_SETTINGS_SCREEN_CLASS =
            "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen";

    private static PendingHold pendingHold;
    private static NativeScreenSession nativeScreenSession;
    private static boolean screenLookupAttempted;
    private static Constructor<?> valueSettingsScreenConstructor;
    private static Class<?> valueSettingsScreenClass;
    private static Method getClosestCoordinate;
    private static Class<?> valueSettingsRecordClass;
    private static Method valueSettingsRow;
    private static Method valueSettingsValue;

    private RtsCreateValueSettingsCompat() {
    }

    /** 仅在所有 RTS UI 路由之后，尝试接管世界区域的主操作按下。 */
    public static boolean handleWorldClick(
            BuilderScreen screen, double mouseX, double mouseY, int button) {
        boolean primary = CameraInputHandler.isPrimaryActionMouse(button);
        boolean worldArea = screen != null && screen.isWorldArea(mouseX, mouseY);
        if (!primary || !worldArea) {
            return false;
        }
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }

        RtsVanillaCursorHitBridge.publish(screen);
        BlockHitResult hit = screen.pickBlockHit();
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(
                        minecraft.level, hit, minecraft.player);
        if (!RtsCreateValueSettingsPolicy.shouldStartHold(
                primary, worldArea, candidate != null)) {
            return false;
        }

        pendingHold = new PendingHold(
                button,
                minecraft.level.dimension().location(),
                hit,
                candidate.blockEntity(),
                candidate.behaviour(),
                candidate.netId(),
                0);
        return true;
    }

    /**
     * 推进一次待定长按；目标位置、面或实际行为改变都会取消，释放前不误发旧目标。
     */
    public static void tick(BuilderScreen screen) {
        PendingHold pending = pendingHold;
        if (pending == null || screen == null) {
            return;
        }
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.screen != screen
                || minecraft.level == null || minecraft.player == null
                || !minecraft.level.dimension().location().equals(pending.dimension())) {
            pendingHold = null;
            return;
        }

        BlockHitResult currentHit = screen.pickBlockHit();
        if (!sameTarget(pending.hit(), currentHit)) {
            pendingHold = null;
            return;
        }
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(
                        minecraft.level, currentHit, minecraft.player);
        if (candidate == null
                || candidate.blockEntity() != pending.blockEntity()
                || candidate.behaviour() != pending.behaviour()
                || candidate.netId() != pending.behaviourNetId()) {
            pendingHold = null;
            return;
        }

        if (!isMouseButtonDown(minecraft, pending.mouseButton())) {
            sendShortInteraction(pending);
            pendingHold = null;
            return;
        }

        int heldTicks = pending.heldTicks() + 1;
        if (heldTicks < SCREEN_OPEN_HOLD_TICKS) {
            pendingHold = pending.withHeldTicks(heldTicks);
            return;
        }
        pendingHold = null;
        openNativeScreen(minecraft, currentHit, candidate);
    }

    /** 离开 BuilderScreen、关闭屏幕或退出 RTS 时只取消尚未打开的长按。 */
    public static void cancelPendingHold() {
        pendingHold = null;
    }

    /**
     * 由可选 mixin 在 Create 原生屏幕保存前提交 RTS 专用包。
     *
     * @return 是否属于 RTS 临时屏幕并应取消 Create 原生近距发送路径
     */
    public static boolean submitNativeScreenSave(Object screen, double mouseX, double mouseY) {
        NativeScreenSession session = nativeScreenSession;
        if (session == null || session.screen() != screen) {
            return false;
        }
        SelectedValue selected = readSelectedValue(screen, mouseX, mouseY);
        if (selected != null) {
            sendValueSettings(session, selected.row(), selected.value(), Screen.hasControlDown());
        }
        session.screen().onClose();
        return true;
    }

    /** 只清理由 RTS 打开的屏幕身份，不影响普通 Create 屏幕。 */
    public static void finishNativeScreen(Object screen) {
        if (nativeScreenSession != null && nativeScreenSession.screen() == screen) {
            nativeScreenSession = null;
        }
    }

    private static boolean sameTarget(BlockHitResult expected, BlockHitResult actual) {
        return expected != null
                && actual != null
                && expected.getBlockPos().equals(actual.getBlockPos())
                && expected.getDirection() == actual.getDirection();
    }

    private static boolean isMouseButtonDown(Minecraft minecraft, int button) {
        return minecraft.getWindow() != null
                && GLFW.glfwGetMouseButton(
                minecraft.getWindow().getWindow(), button) == GLFW.GLFW_PRESS;
    }

    private static void openNativeScreen(
            Minecraft minecraft,
            BlockHitResult hit,
            RtsCreateValueSettingsRuntime.Candidate candidate) {
        Object board = RtsCreateValueSettingsRuntime.createBoard(candidate, minecraft.player, hit);
        Object settings = RtsCreateValueSettingsRuntime.currentSettings(candidate);
        if (board == null || settings == null || !resolveScreenConstructor(board, settings)) {
            return;
        }
        try {
            Consumer<Object> onHover = value ->
                    RtsCreateValueSettingsRuntime.notifyHovered(candidate, value);
            Object nativeScreen = valueSettingsScreenConstructor.newInstance(
                    hit.getBlockPos(), board, settings, onHover, candidate.netId());
            if (nativeScreen instanceof Screen screen) {
                nativeScreenSession = new NativeScreenSession(
                        screen,
                        minecraft.level.dimension().location(),
                        hit.getBlockPos(),
                        hit.getDirection(),
                        hit.getLocation(),
                        candidate.netId());
                minecraft.setScreen(screen);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            nativeScreenSession = null;
        }
    }

    private static boolean resolveScreenConstructor(Object board, Object settings) {
        if (screenLookupAttempted) {
            return valueSettingsScreenConstructor != null;
        }
        screenLookupAttempted = true;
        try {
            valueSettingsScreenClass = Class.forName(VALUE_SETTINGS_SCREEN_CLASS);
            valueSettingsScreenConstructor = valueSettingsScreenClass.getConstructor(
                    BlockPos.class, board.getClass(), settings.getClass(), Consumer.class, int.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            valueSettingsScreenConstructor = null;
            return false;
        }
    }

    private static void sendShortInteraction(PendingHold pending) {
        BlockHitResult hit = pending.hit();
        PacketDistributor.sendToServer(new C2SRtsCreateValueSettingsPayload(
                pending.dimension(), hit.getBlockPos(), pending.behaviourNetId(), 0, 0, true,
                hit.getDirection(),
                hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                false));
    }

    private static void sendValueSettings(
            NativeScreenSession session, int row, int value, boolean ctrlDown) {
        PacketDistributor.sendToServer(new C2SRtsCreateValueSettingsPayload(
                session.dimension(), session.pos(), session.behaviourNetId(), row, value, false,
                session.face(),
                session.hitLocation().x,
                session.hitLocation().y,
                session.hitLocation().z,
                ctrlDown));
    }

    private static SelectedValue readSelectedValue(Object screen, double mouseX, double mouseY) {
        if (!resolveSelectedValueMethods(screen)) {
            return null;
        }
        try {
            Object settings = getClosestCoordinate.invoke(screen, (int) mouseX, (int) mouseY);
            Object row = valueSettingsRow.invoke(settings);
            Object value = valueSettingsValue.invoke(settings);
            if (row instanceof Number selectedRow && value instanceof Number selectedValue) {
                return new SelectedValue(selectedRow.intValue(), selectedValue.intValue());
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 小版本签名不符时不回退到 Create 受限的原生远距包。
        }
        return null;
    }

    private static boolean resolveSelectedValueMethods(Object screen) {
        if (screen == null || valueSettingsScreenClass == null
                || valueSettingsScreenClass != screen.getClass()) {
            return false;
        }
        if (getClosestCoordinate != null && valueSettingsRecordClass != null) {
            return true;
        }
        try {
            getClosestCoordinate = valueSettingsScreenClass.getMethod(
                    "getClosestCoordinate", int.class, int.class);
            valueSettingsRecordClass = getClosestCoordinate.getReturnType();
            valueSettingsRow = valueSettingsRecordClass.getMethod("row");
            valueSettingsValue = valueSettingsRecordClass.getMethod("value");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private record PendingHold(
            int mouseButton,
            ResourceLocation dimension,
            BlockHitResult hit,
            Object blockEntity,
            Object behaviour,
            int behaviourNetId,
            int heldTicks) {
        private PendingHold withHeldTicks(int ticks) {
            return new PendingHold(
                    mouseButton, dimension, hit, blockEntity, behaviour, behaviourNetId, ticks);
        }
    }

    private record NativeScreenSession(
            Screen screen,
            ResourceLocation dimension,
            BlockPos pos,
            Direction face,
            Vec3 hitLocation,
            int behaviourNetId) {
    }

    private record SelectedValue(int row, int value) {
    }
}
