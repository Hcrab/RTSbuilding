package com.rtsbuilding.rtsbuilding.client.screen.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.MIDDLE_CLICK_DRAG_THRESHOLD;

/**
 * Handles RTS camera and input interaction state management.
 * <p>
 * Manages mouse dragging (right-click rotation, middle-click pan),
 * mining actions, keyboard camera control, and keyboard pan-drag states.
 * All state is used in BuilderScreen event methods; this class stores and
 * manages these states and provides helper methods for input detection
 * and action execution.
 * <p>
 * Default mouse bindings:
 * <ul>
 *   <li>Right-click drag -> camera rotation</li>
 *   <li>Middle-click drag -> camera pan (movement)</li>
 *   <li>Middle-click without drag -> pick block for placement</li>
 * </ul>
 */
public final class CameraInputHandler {
    private BuilderScreen screen;
    private ClientRtsController controller;

    // ======================== Mouse/Camera state ========================

    /** Whether right-click drag is active */
    private boolean rightPressActive = false;
    /** Mouse button that triggered right-click drag */
    private int rightPressButton = -1;
    /** Whether current right press can trigger primary action */
    private boolean rightPressCanPrimary = false;
    /** Whether current right press can trigger rotation */
    private boolean rightPressCanRotate = false;
    /** Whether rotation drag has occurred (distinguishes click vs drag) */
    private boolean rightDragRotated = false;
    /** Accumulated right-click drag distance */
    private double rightDragDistance = 0.0D;

    /** Whether middle-click drag is active */
    private boolean middlePressActive = false;
    /** Mouse button that triggered middle-click drag */
    private int middlePressButton = -1;
    /** Whether current middle press can pan */
    private boolean middlePressCanPan = false;
    /** Whether current middle press can pick blocks */
    private boolean middlePressCanPick = false;
    /** Accumulated middle-click drag distance */
    private double middleDragDistance = 0.0D;

    /** Keyboard pan-drag - last mouse X (for delta calculation) */
    private double keyboardPanLastMouseX = Double.NaN;
    /** Keyboard pan-drag - last mouse Y */
    private double keyboardPanLastMouseY = Double.NaN;

    /** Whether left-click mining is active */
    private boolean leftMiningActive = false;
    /** Mouse button that activated mining (-1 for keyboard-triggered) */
    private int activeMiningMouseButton = -1;
    /** Whether mining was triggered by keyboard */
    private boolean activeMiningKeyboard = false;

    /** Camera up action held state */
    private boolean cameraUpActionHeld = false;
    /** Camera down action held state */
    private boolean cameraDownActionHeld = false;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ======================== 静态输入辅助方法 ========================

    public static boolean isPrimaryActionMouse(int button) {
        return ClientKeyMappings.ACTION_PRIMARY.matchesMouse(button);
    }

    public static boolean isBreakActionMouse(int button) {
        return ClientKeyMappings.ACTION_BREAK.matchesMouse(button);
    }

    public static boolean isRotateDragActionMouse(int button) {
        return ClientKeyMappings.CAMERA_ROTATE_DRAG.matchesMouse(button);
    }

    public static boolean isPanDragActionMouse(int button) {
        return ClientKeyMappings.CAMERA_PAN_DRAG.matchesMouse(button);
    }

    public static boolean isKeyboardPanDragActionHeld() {
        InputConstants.Key key = ClientKeyMappings.CAMERA_PAN_DRAG.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && ClientKeyMappings.CAMERA_PAN_DRAG.isDown();
    }

    public static boolean isPickBlockActionMouse(int button) {
        return ClientKeyMappings.PICK_BLOCK.matchesMouse(button);
    }

    public static boolean canStartBreakActionOnMouse(int button) {
        return !isPrimaryActionMouse(button)
                && !isRotateDragActionMouse(button)
                && !isPanDragActionMouse(button)
                && !isPickBlockActionMouse(button);
    }

    // ======================== 镜头/输入状态查询 ========================

    public boolean isCameraUpActionHeld() {
        return this.cameraUpActionHeld || ClientKeyMappings.CAMERA_UP.isDown();
    }

    public boolean isCameraDownActionHeld() {
        return this.cameraDownActionHeld || ClientKeyMappings.CAMERA_DOWN.isDown();
    }

    public boolean isLeftMiningActive() {
        return this.leftMiningActive;
    }

    public boolean isRightPressActive() {
        return this.rightPressActive;
    }

    public int getRightPressButton() {
        return this.rightPressButton;
    }

    public boolean isRightPressCanPrimary() {
        return this.rightPressCanPrimary;
    }

    public boolean isRightDragRotated() {
        return this.rightDragRotated;
    }

    public boolean isMiddlePressActive() {
        return this.middlePressActive;
    }

    public int getMiddlePressButton() {
        return this.middlePressButton;
    }

    public boolean isMiddlePressCanPick() {
        return this.middlePressCanPick;
    }

    public double getMiddleDragDistance() {
        return this.middleDragDistance;
    }

    // ======================== 右键拖拽状态管理 ========================

    public void beginRightPress(double mouseX, double mouseY, int button, boolean primaryMouse, boolean rotateMouse) {
        this.rightPressActive = true;
        this.rightPressButton = button;
        this.rightPressCanPrimary = primaryMouse;
        this.rightPressCanRotate = rotateMouse;
        this.rightDragRotated = false;
        this.rightDragDistance = 0.0D;
    }

    public boolean isRightDragActive(int button) {
        return this.rightPressActive && button == this.rightPressButton;
    }

    /**
     * Handles right/middle-click drag.
     * <ul>
     *   <li>Right button -> camera rotation by default</li>
     *   <li>Middle button -> camera pan by default</li>
     * </ul>
     * The specific action is determined dynamically via {@link CameraInputHandler#isPanDragActionMouse(int)}
     * and {@link CameraInputHandler#isRotateDragActionMouse(int)}.
     */
    public boolean handleRightDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.rightPressActive
                && button == this.rightPressButton
                && screen.isWorldArea(mouseX, mouseY)
                && !isAltDown()) {
            this.rightDragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (this.rightDragDistance > 1.5D) {
                this.rightDragRotated = true;
            }
            if (CameraInputHandler.isPanDragActionMouse(button)) {
                // Default middle button: camera pan (movement)
                this.controller.queuePanDrag(dragX, dragY);
            } else if (this.rightPressCanRotate) {
                // Default right button: camera rotation
                this.controller.queueRotateDrag(dragX, dragY);
            }
            return true;
        }
        return false;
    }

    /**
     * Ends right/middle-click press.
     * <ul>
     *   <li>If rotation/pan occurred during drag, returns false (no primary action).</li>
     *   <li>If middle-click (pick block) without drag, triggers {@link #tryPickHoveredBlockForPlacement()}.</li>
     *   <li>Otherwise returns true iff a primary action should be triggered at the release position.</li>
     * </ul>
     */
    public boolean endRightPress(double mouseX, double mouseY, int button) {
        if (!this.rightPressActive || button != this.rightPressButton) {
            return false;
        }
        boolean canPrimary = this.rightPressCanPrimary;
        this.rightPressActive = false;
        this.rightPressButton = -1;
        this.rightPressCanPrimary = false;
        this.rightPressCanRotate = false;
        if (this.rightDragRotated) {
            this.rightDragRotated = false;
            this.rightDragDistance = 0.0D;
            return false;
        }
        // Middle-click without drag → try pick block for placement (no primary action)
        if (CameraInputHandler.isPickBlockActionMouse(button) && screen.isWorldArea(mouseX, mouseY)) {
            this.rightDragDistance = 0.0D;
            tryPickHoveredBlockForPlacement();
            return false;
        }
        if (!screen.isWorldArea(mouseX, mouseY) || !canPrimary) {
            this.rightDragDistance = 0.0D;
            return false;
        }
        this.rightDragDistance = 0.0D;
        return true;
    }

    // ======================== 中键拖拽状态管理 ========================

    public void beginMiddlePress(boolean worldArea, int button, boolean panMouse, boolean pickMouse) {
        this.middlePressActive = worldArea;
        this.middlePressButton = button;
        this.middlePressCanPan = panMouse;
        this.middlePressCanPick = pickMouse;
        this.middleDragDistance = 0.0D;
    }

    public boolean handleMiddleDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.middlePressActive
                && button == this.middlePressButton
                && this.middlePressCanPan
                && screen.isWorldArea(mouseX, mouseY)) {
            this.middleDragDistance += Math.abs(dragX) + Math.abs(dragY);
            this.controller.queuePanDrag(dragX, dragY);
            return true;
        }
        return false;
    }

    /**
     * 结束中键拖拽，返回 true 表示事件已处理。
     * 如果中键按下时未发生拖拽且可拾取，则触发 tryPickHoveredBlockForPlacement。
     */
    public boolean endMiddlePress(double mouseX, double mouseY, int button) {
        if (this.middlePressActive && button == this.middlePressButton) {
            if (this.middlePressCanPick
                    && this.middleDragDistance <= MIDDLE_CLICK_DRAG_THRESHOLD
                    && screen.isWorldArea(mouseX, mouseY)) {
                tryPickHoveredBlockForPlacement();
            }
            this.middlePressActive = false;
            this.middlePressButton = -1;
            this.middlePressCanPan = false;
            this.middlePressCanPick = false;
            this.middleDragDistance = 0.0D;
            return true;
        }
        return false;
    }

    // ======================== 键盘拖拽平移 ========================

    public boolean canUseKeyboardPanDrag(double mouseX, double mouseY) {
        return isKeyboardPanDragActionHeld()
                && screen.isWorldArea(mouseX, mouseY)
                && !screen.isMouseOverFloatingWindow(mouseX, mouseY)
                && !screen.isSearchFocused();
    }

    public void updateKeyboardPanDrag(double mouseX, double mouseY) {
        if (canUseKeyboardPanDrag(mouseX, mouseY)) {
            if (!Double.isNaN(this.keyboardPanLastMouseX) && !Double.isNaN(this.keyboardPanLastMouseY)) {
                double dragX = mouseX - this.keyboardPanLastMouseX;
                double dragY = mouseY - this.keyboardPanLastMouseY;
                if (Math.abs(dragX) > 0.0D || Math.abs(dragY) > 0.0D) {
                    this.controller.queuePanDrag(dragX, dragY);
                }
            }
            this.keyboardPanLastMouseX = mouseX;
            this.keyboardPanLastMouseY = mouseY;
        } else {
            this.keyboardPanLastMouseX = Double.NaN;
            this.keyboardPanLastMouseY = Double.NaN;
        }
    }

    public boolean handleKeyboardPanDragAt(double mouseX, double mouseY, double dragX, double dragY) {
        if (canUseKeyboardPanDrag(mouseX, mouseY)) {
            this.controller.queuePanDrag(dragX, dragY);
            this.keyboardPanLastMouseX = mouseX;
            this.keyboardPanLastMouseY = mouseY;
            return true;
        }
        return false;
    }

    // ======================== 镜头垂直方向 ========================

    public boolean updateCameraVerticalHeldState(int keyCode, int scanCode, boolean down) {
        boolean handled = false;
        if (ClientKeyMappings.CAMERA_UP.matches(keyCode, scanCode)) {
            this.cameraUpActionHeld = down;
            handled = true;
        }
        if (ClientKeyMappings.CAMERA_DOWN.matches(keyCode, scanCode)) {
            this.cameraDownActionHeld = down;
            handled = true;
        }
        return handled;
    }

    public void resetCameraVerticalHeld() {
        this.cameraUpActionHeld = false;
        this.cameraDownActionHeld = false;
    }

    // ======================== 挖矿动作 ========================

    public boolean startMiningAt(double mouseX, double mouseY, int mouseButton, boolean keyboard) {
        if (screen.getPendingGuiBindSlot() >= 0
                || BlueprintPanel.isCaptureModeActive()
                || !screen.isWorldArea(mouseX, mouseY)
                || this.controller.getMode() == BuilderMode.LINK_STORAGE
                || this.controller.getMode() == BuilderMode.FUNNEL) {
            return false;
        }
        if (screen.isQuickBuildRangeDestroyMode() && !screen.isQuickBuildRangeDestroyChainMode()) {
            return screen.handleQuickBuildRangeDestroyClick(mouseX, mouseY);
        }
        if (!screen.isQuickBuildRangeDestroyMode() && screen.getShapeController().hasConfirmedDestroyWorkArea()) {
            return false;
        }
        if (screen.isQuickBuildRangeDestroyMode()
                && this.controller.getAreaMinePhase() == MiningOperationService.AREA_MINE_PHASE_NEED_HEIGHT) {
            // 第三次点击：确认范围挖掘，直接发包执行，不需要再求 BlockHit
            this.controller.confirmAreaMine(screen.getSelectedToolSlot(), screen.getShapeFillMode());
        } else {
            // 如果指示框当前选中实体，阻止方块破坏
            InteractionTypes.InteractionTarget lookTarget = screen.pickInteractionTarget(false);
            if (lookTarget != null && lookTarget.isEntityTarget()) {
                return false;
            }
            BlockHitResult hit = screen.pickBlockHit();
            if (hit == null) {
                return false;
            }
            if (screen.isQuickBuildRangeDestroyMode() && !screen.isQuickBuildRangeDestroyChainMode()) {
                // 三击选点模式（类似快速建造的 BOX 模式）：
                // 第 1 击 → setPointA (进入 NEED_SECOND)
                // 第 2 击 → setPointB (进入 NEED_HEIGHT)
                // 第 3 击 → 上面 confirmAreaMine (由 phase==NEED_HEIGHT 分支处理)
                int phase = this.controller.getAreaMinePhase();
                if (phase == MiningOperationService.AREA_MINE_PHASE_NONE) {
                    // First click: set point A
                    this.controller.setAreaMinePointA(hit.getBlockPos().immutable());
                } else if (phase == MiningOperationService.AREA_MINE_PHASE_NEED_SECOND) {
                    // Second click: set point B (defines base rectangle), enter height adjustment phase
                    this.controller.setAreaMinePointB(hit.getBlockPos().immutable());
                }
            } else if (screen.isQuickBuildRangeDestroyChainMode()) {
                List<BlockPos> preview = screen.collectUltiminePreviewBlocks();
                if (preview.isEmpty()) {
                    preview = List.of(hit.getBlockPos().immutable());
                }
                screen.getShapeController().rememberConfirmedChainDestroyPreview(preview);
                // 记录连锁破坏操作到撤回栈（等待服务端确认）
                screen.getShapeController().recordPendingBreakForUndo(preview, hit.getDirection(), screen.getSelectedToolSlot());
                this.controller.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                        screen.getSelectedToolSlot(), screen.getUltimineLimit(), (byte) 0);
            } else {
                // 记录普通挖掘操作到撤回栈（等待服务端确认）
                screen.getShapeController().recordPendingBreakForUndo(
                        List.of(hit.getBlockPos().immutable()), hit.getDirection(), screen.getSelectedToolSlot());
                this.controller.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(), screen.getSelectedToolSlot());
            }
        }
        this.leftMiningActive = true;
        this.activeMiningMouseButton = keyboard ? -1 : mouseButton;
        this.activeMiningKeyboard = keyboard;
        return true;
    }

    public void stopActiveMining() {
        if (!this.leftMiningActive && this.activeMiningMouseButton < 0 && !this.activeMiningKeyboard) {
            return;
        }
        this.leftMiningActive = false;
        this.activeMiningMouseButton = -1;
        this.activeMiningKeyboard = false;
        this.controller.abortMining(screen.getSelectedToolSlot());
    }

    public boolean isKeyboardMining() {
        return this.activeMiningKeyboard;
    }

    public int getActiveMiningMouseButton() {
        return this.activeMiningMouseButton;
    }

    // ======================== 鼠标拾取方块到物品栏 ========================

    public boolean tryPickHoveredBlockForPlacement() {
        Minecraft mc = screen.getMinecraft();
        if (mc == null || mc.level == null) {
            return false;
        }
        BlockHitResult hit = screen.pickBlockHit();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return false;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return false;
        }
        ItemStack preview = new ItemStack(item);
        if (preview.isEmpty()) {
            return false;
        }
        screen.clearShapeBuildSession();
        if (mc.player != null) {
            var inventory = mc.player.getInventory();
            RtsPickBlockPlacementSelector.Selection selection = RtsPickBlockPlacementSelector.resolve(
                    inventory.getContainerSize(),
                    slot -> {
                        ItemStack candidate = inventory.getItem(slot);
                        return !candidate.isEmpty() && candidate.getItem() == preview.getItem();
                    });
            if (selection.route() == RtsPickBlockPlacementSelector.Route.HOTBAR) {
                inventory.setSelectedSlot(selection.slot());
                this.controller.clearPlacementSelectionPreserveMode();
                this.controller.setMode(BuilderMode.INTERACT);
                return true;
            }
            // 26.1 已移除按任意背包槽位执行 vanilla pick 的网络入口。
            // 非快捷栏命中继续走 RTS 自身的放置选择，避免只改客户端背包造成不同步。
        }
        this.controller.selectItemForPlacement(itemId.toString(), preview.getHoverName().getString(), preview);
        return true;
    }

    // ======================== 输入灵敏度 ========================

    // ======================== Modifier 查询 ========================

    private static boolean isAltDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
