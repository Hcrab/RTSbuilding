package com.rtsbuilding.rtsbuilding.client.compat.create;

import com.rtsbuilding.rtsbuilding.client.compat.RtsVanillaCursorHitBridge;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsPolicy;
import com.rtsbuilding.rtsbuilding.compat.create.RtsCreateValueSettingsRuntime;
import com.rtsbuilding.rtsbuilding.network.create.C2SRtsCreateValueSettingsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * BuilderScreen 内 Create Value Settings 的客户端适配器。
 *
 * <p>它负责把 RTS 自由光标上的主键长按转换成 Create 原生 ValueSettingsScreen，并在短按时发送保留
 * onShortInteract 语义的专用包。它不识别具体 Create 方块、不改变 Create 的 board/格式逻辑，也不承担
 * 服务端权限或区块加载校验。Create 缺失、版本签名变化或反射失败时，本类安静返回 false，让既有 RTS
 * 世界右键继续执行。</p>
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

    /**
     * 尝试接管一个 BuilderScreen 世界区域内的主操作鼠标点击。
     *
     * <p>调用方已经先处理窗口、面板、确认框等 UI 路由；这里仍重复 world-area 和主键门禁，避免
     * Value Settings 穿透 RTS UI、破坏键、旋转键或其它鼠标按键。</p>
     */
    public static boolean handleWorldClick(BuilderScreen screen, double mouseX, double mouseY, int button) {
        if (screen == null || !CameraInputHandler.isPrimaryActionMouse(button)
                || !screen.isWorldArea(mouseX, mouseY)) {
            return false;
        }
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return false;
        }

        RtsVanillaCursorHitBridge.publish(screen);
        BlockHitResult hit = screen.pickBlockHit();
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(minecraft.level, hit, minecraft.player);
        if (!RtsCreateValueSettingsPolicy.shouldStartHold(true, true, candidate != null)) {
            return false;
        }

        pendingHold = new PendingHold(button, hit, candidate.netId(), 0);
        return true;
    }

    /**
     * 在 BuilderScreen 生命周期内推进一次待定长按。
     *
     * <p>到第五个客户端 tick 才反射构造 Create 自己的 ValueSettingsScreen；提前松开则走同一个
     * ValueSettingsBehaviour.onShortInteract 服务端语义。目标改变、行为失效或离开 BuilderScreen 时取消，
     * 与 Create 原生 handler 的取消边界保持一致。</p>
     */
    public static void tick(BuilderScreen screen) {
        PendingHold pending = pendingHold;
        if (pending == null || screen == null) {
            return;
        }
        Minecraft minecraft = screen.getMinecraft();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            pendingHold = null;
            return;
        }
        if (!isMouseButtonDown(minecraft, pending.mouseButton())) {
            sendShortInteraction(pending);
            pendingHold = null;
            return;
        }

        BlockHitResult currentHit = screen.pickBlockHit();
        if (currentHit == null || !pending.hit().getBlockPos().equals(currentHit.getBlockPos())) {
            pendingHold = null;
            return;
        }
        RtsCreateValueSettingsRuntime.Candidate candidate =
                RtsCreateValueSettingsRuntime.findEligible(minecraft.level, currentHit, minecraft.player);
        if (candidate == null || candidate.netId() != pending.behaviourNetId()) {
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

    /** 离开 BuilderScreen 时只取消尚未打开的长按，不碰其它 Create 屏幕。 */
    public static void cancelPendingHold() {
        pendingHold = null;
    }

    /**
     * 由可选 mixin 在 Create 原生屏幕保存前提交 RTS 专用包。
     *
     * <p>只有 identity 与 RTS 临时会话一致的屏幕会返回 {@code true} 并取消原方法；普通 Create
     * 屏幕立即返回 {@code false}，由 Create 完整执行自己的 saveAndClose 与 NetworkHelper 发包路径。</p>
     */
    public static boolean submitNativeScreenSave(Object screen, double mouseX, double mouseY) {
        NativeScreenSession session = nativeScreenSession;
        if (session == null || session.screen() != screen) {
            return false;
        }

        SelectedValue value = readSelectedValue(screen, mouseX, mouseY);
        if (value != null) {
            sendValueSettings(session, value.row(), value.value(),
                    com.rtsbuilding.rtsbuilding.client.input.RtsModifierKeys.isControlDown());
        }
        session.screen().onClose();
        return true;
    }

    /** 由可选 mixin 在 ValueSettingsScreen 关闭时清掉该屏幕的临时路由身份。 */
    public static void finishNativeScreen(Object screen) {
        if (nativeScreenSession != null && nativeScreenSession.screen() == screen) {
            nativeScreenSession = null;
        }
    }

    private static boolean isMouseButtonDown(Minecraft minecraft, int button) {
        return minecraft.getWindow() != null
                && GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), button) == GLFW.GLFW_PRESS;
    }

    private static void openNativeScreen(
            Minecraft minecraft, BlockHitResult hit, RtsCreateValueSettingsRuntime.Candidate candidate) {
        Object board = RtsCreateValueSettingsRuntime.createBoard(candidate, minecraft.player, hit);
        Object settings = RtsCreateValueSettingsRuntime.currentSettings(candidate);
        if (board == null || settings == null || !resolveScreenConstructor(board, settings)) {
            return;
        }
        try {
            Consumer<Object> onHover = setting -> RtsCreateValueSettingsRuntime.notifyHovered(candidate, setting);
            Object nativeScreen = valueSettingsScreenConstructor.newInstance(
                    hit.getBlockPos(), board, settings, onHover, candidate.netId());
            if (nativeScreen instanceof Screen screen) {
                nativeScreenSession = new NativeScreenSession(
                        screen, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), candidate.netId());
                minecraft.setScreen(screen);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // 反射屏幕构造失败时不替代普通 RTS 交互，也不保留半开的会话状态。
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
        RtsClientNetworkBridge.send(new C2SRtsCreateValueSettingsPayload(
                hit.getBlockPos(), pending.behaviourNetId(), 0, 0, true,
                hit.getDirection(), hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, false));
    }

    private static void sendValueSettings(NativeScreenSession session, int row, int value, boolean ctrlDown) {
        RtsClientNetworkBridge.send(new C2SRtsCreateValueSettingsPayload(
                session.pos(), session.behaviourNetId(), row, value, false,
                session.face(), session.hitLocation().x, session.hitLocation().y, session.hitLocation().z, ctrlDown));
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
            // Create 小版本签名变化时安全关闭 RTS 临时屏幕，不回退到受限的原生远距包。
        }
        return null;
    }

    private static boolean resolveSelectedValueMethods(Object screen) {
        if (screen == null || valueSettingsScreenClass == null || valueSettingsScreenClass != screen.getClass()) {
            return false;
        }
        if (getClosestCoordinate != null && valueSettingsRecordClass != null) {
            return true;
        }
        try {
            getClosestCoordinate = valueSettingsScreenClass.getMethod("getClosestCoordinate", int.class, int.class);
            valueSettingsRecordClass = getClosestCoordinate.getReturnType();
            valueSettingsRow = valueSettingsRecordClass.getMethod("row");
            valueSettingsValue = valueSettingsRecordClass.getMethod("value");
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private record PendingHold(int mouseButton, BlockHitResult hit, int behaviourNetId, int heldTicks) {
        private PendingHold withHeldTicks(int ticks) {
            return new PendingHold(mouseButton, hit, behaviourNetId, ticks);
        }
    }

    private record NativeScreenSession(
            Screen screen, BlockPos pos, Direction face, net.minecraft.world.phys.Vec3 hitLocation, int behaviourNetId) {
    }

    private record SelectedValue(int row, int value) {
    }
}
