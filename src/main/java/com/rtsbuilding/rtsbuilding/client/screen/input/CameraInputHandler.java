package com.rtsbuilding.rtsbuilding.client.screen.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MIDDLE_CLICK_DRAG_THRESHOLD;

/**
 * 处理 RTS 镜头和输入交互的状态管理。
 * <p>
 * 包含鼠标拖拽(右键旋转、中键平移/拾取)、挖矿动作、键盘镜头控制和键盘拖拽平移的状态。
 * 所有状态在 BuilderScreen 的事件方法中被使用，本类负责存储和管理这些状态，
 * 并提供辅助方法进行输入判断和动作执行。
 */
public final class CameraInputHandler {
    private BuilderScreen screen;
    private ClientRtsController controller;

    // ======================== 鼠标/镜头状态 ========================

    /** 右键拖拽是否激活 */
    private boolean rightPressActive = false;
    /** 触发右键拖拽的鼠标按钮 */
    private int rightPressButton = -1;
    /** 当前右键是否可触发主要动作 */
    private boolean rightPressCanPrimary = false;
    /** 当前右键是否可触发旋转 */
    private boolean rightPressCanRotate = false;
    /** 是否已发生旋转拖拽（用于区分点击和拖拽） */
    private boolean rightDragRotated = false;
    /** 右键拖拽累积距离 */
    private double rightDragDistance = 0.0D;

    /** 中键拖拽是否激活 */
    private boolean middlePressActive = false;
    /** 触发中键拖拽的鼠标按钮 */
    private int middlePressButton = -1;
    /** 当前中键是否可平移 */
    private boolean middlePressCanPan = false;
    /** 当前中键是否可拾取方块 */
    private boolean middlePressCanPick = false;
    /** 中键拖拽累积距离 */
    private double middleDragDistance = 0.0D;

    /** 键盘拖拽平移 - 上次鼠标 X (用于计算增量) */
    private double keyboardPanLastMouseX = Double.NaN;
    /** 键盘拖拽平移 - 上次鼠标 Y */
    private double keyboardPanLastMouseY = Double.NaN;

    /** 左键挖矿是否激活 */
    private boolean leftMiningActive = false;
    /** 挖矿激活时的鼠标按钮（键盘触发时为 -1） */
    private int activeMiningMouseButton = -1;
    /** 挖矿是否由键盘触发 */
    private boolean activeMiningKeyboard = false;

    /** 镜头向上动作是否正在按住 */
    private boolean cameraUpActionHeld = false;
    /** 镜头向下动作是否正在按住 */
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

    public boolean handleRightDrag(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.rightPressActive
                && button == this.rightPressButton
                && this.rightPressCanRotate
                && screen.isWorldArea(mouseX, mouseY)
                && !isAltDown()) {
            this.rightDragDistance += Math.abs(dragX) + Math.abs(dragY);
            if (this.rightDragDistance > 1.5D) {
                this.rightDragRotated = true;
            }
            this.controller.queueRotateDrag(dragX, dragY);
            return true;
        }
        return false;
    }

    /**
     * 结束右键拖拽，返回 true 表示需要调用 runPrimaryActionAt。
     * 仅当拖拽未发生旋转且可触发主要动作时返回 true。
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
            return false; // 已发生旋转，不触发动作
        }
        if (!screen.isWorldArea(mouseX, mouseY) || !canPrimary) {
            this.rightDragDistance = 0.0D;
            return false;
        }
        this.rightDragDistance = 0.0D;
        return true; // 调用方需执行 runPrimaryActionAt
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
                && !screen.isCraftQuantityDialogOpen()
                && !screen.isDraggingInputSensitivity()
                && !screen.isShapeWheelOpen()
                && !screen.isInteractionWheelOpen()
                && !screen.isGuideOpen()
                && !screen.isGearMenuOpen()
                && !BlueprintPanel.isNameDialogOpen()
                && !BlueprintPanel.isMaterialDialogOpen()
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
        BlockHitResult hit = screen.pickBlockHit();
        if (hit == null) {
            return false;
        }
        if (screen.isUltimineOpen()) {
            this.controller.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(), screen.getSelectedToolSlot(), screen.getUltimineLimit());
            screen.setUltimineLastSentLimit(screen.getUltimineLimit());
        } else {
            this.controller.startMining(hit.getBlockPos(), hit.getDirection().get3DDataValue(), screen.getSelectedToolSlot());
            screen.setUltimineLastSentLimit(1);
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
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (itemId == null) {
            return false;
        }
        ItemStack preview = new ItemStack(item);
        if (preview.isEmpty()) {
            return false;
        }
        screen.clearShapeBuildSession();
        this.controller.selectItemForPlacement(itemId.toString(), preview.getHoverName().getString(), preview);
        return true;
    }

    // ======================== 输入灵敏度 ========================

    public void updateInputSensitivityFromMouse(double mouseX) {
        int menuW = Math.min(300, screen.width - 24);
        int menuX = (screen.width - menuW) / 2;
        int trackX = menuX + 16;
        int trackW = menuW - 32;
        double fraction = (mouseX - trackX) / (double) trackW;
        fraction = Mth.clamp(fraction, 0.0D, 1.0D);
        this.controller.setInputSensitivityByFraction(fraction);
    }

    // ======================== Modifier 查询 ========================

    private static boolean isAltDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
