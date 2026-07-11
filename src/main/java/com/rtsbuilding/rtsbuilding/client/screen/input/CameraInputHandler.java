package com.rtsbuilding.rtsbuilding.client.screen.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MIDDLE_CLICK_DRAG_THRESHOLD;

/**
 * 澶勭??RTS 闀滃ご鍜岃緭鍏ヤ氦浜掔殑鐘舵€佺鐞嗐??
 * <p>
 * 鍖呭惈榧犳爣鎷栨??鍙抽敭鏃嬭浆銆佷腑閿钩??鎷惧??銆佹寲鐭垮姩浣溿€侀敭鐩橀暅澶存帶鍒跺拰閿洏鎷栨嫿骞崇Щ鐨勭姸鎬併??
 * 鎵€鏈夌姸鎬佸湪 BuilderScreen 鐨勪簨浠舵柟娉曚腑琚娇鐢紝鏈被璐熻矗瀛樺偍鍜岀鐞嗚繖浜涚姸鎬侊紝
 * 骞舵彁渚涜緟鍔╂柟娉曡繘琛岃緭鍏ュ垽鏂拰鍔ㄤ綔鎵ц??
 */
public final class CameraInputHandler {
    private BuilderScreen screen;
    private ClientRtsController controller;

    // ======================== 榧犳??闀滃ご鐘舵??========================

    /** 鍙抽敭鎷栨嫿鏄惁婵€娲?*/
    private boolean rightPressActive = false;
    /** 瑙﹀彂鍙抽敭鎷栨嫿鐨勯紶鏍囨寜閽?*/
    private int rightPressButton = -1;
    /** 褰撳墠鍙抽敭鏄惁鍙Е鍙戜富瑕佸姩浣?*/
    private boolean rightPressCanPrimary = false;
    /** 褰撳墠鍙抽敭鏄惁鍙Е鍙戞棆??*/
    private boolean rightPressCanRotate = false;
    /** 鏄惁宸插彂鐢熸棆杞嫋鎷斤紙鐢ㄤ簬鍖哄垎鐐瑰嚮鍜屾嫋鎷斤級 */
    private boolean rightDragRotated = false;
    /** 鍙抽敭鎷栨嫿绱Н璺濈??*/
    private double rightDragDistance = 0.0D;

    /** 涓敭鎷栨嫿鏄惁婵€娲?*/
    private boolean middlePressActive = false;
    /** 瑙﹀彂涓敭鎷栨嫿鐨勯紶鏍囨寜閽?*/
    private int middlePressButton = -1;
    /** 褰撳墠涓敭鏄惁鍙钩??*/
    private boolean middlePressCanPan = false;
    /** 褰撳墠涓敭鏄惁鍙嬀鍙栨柟鍧?*/
    private boolean middlePressCanPick = false;
    /** 涓敭鎷栨嫿绱Н璺濈??*/
    private double middleDragDistance = 0.0D;

    /** 閿洏鎷栨嫿骞崇Щ - 涓婃榧犳爣 X (鐢ㄤ簬璁＄畻澧為?? */
    private double keyboardPanLastMouseX = Double.NaN;
    /** 閿洏鎷栨嫿骞崇Щ - 涓婃榧犳爣 Y */
    private double keyboardPanLastMouseY = Double.NaN;

    /** 宸﹂敭鎸栫熆鏄惁婵€娲?*/
    private boolean leftMiningActive = false;
    /** 鎸栫熆婵€娲绘椂鐨勯紶鏍囨寜閽紙閿洏瑙﹀彂鏃朵负 -1??*/
    private int activeMiningMouseButton = -1;
    /** 鎸栫熆鏄惁鐢遍敭鐩樿Е鍙?*/
    private boolean activeMiningKeyboard = false;

    /** 闀滃ご鍚戜笂鍔ㄤ綔鏄惁姝ｅ湪鎸変??*/
    private boolean cameraUpActionHeld = false;
    /** 闀滃ご鍚戜笅鍔ㄤ綔鏄惁姝ｅ湪鎸変??*/
    private boolean cameraDownActionHeld = false;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    // ======================== 闈欐€佽緭鍏ヨ緟鍔╂柟娉?========================

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

    // ======================== 闀滃ご/杈撳叆鐘舵€佹煡??========================

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

    // ======================== 鍙抽敭鎷栨嫿鐘舵€佺鐞?========================

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
     * 缁撴潫鍙抽敭鎷栨嫿锛岃繑??true 琛ㄧず闇€瑕佽皟??runPrimaryActionAt??
     * 浠呭綋鎷栨嫿鏈彂鐢熸棆杞笖鍙Е鍙戜富瑕佸姩浣滄椂杩斿??true??
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
            return false; // 宸插彂鐢熸棆杞紝涓嶈Е鍙戝姩??
        }
        if (!screen.isWorldArea(mouseX, mouseY) || !canPrimary) {
            this.rightDragDistance = 0.0D;
            return false;
        }
        this.rightDragDistance = 0.0D;
        return true; // 璋冪敤鏂归渶鎵ц runPrimaryActionAt
    }

    // ======================== 涓敭鎷栨嫿鐘舵€佺鐞?========================

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
     * 缁撴潫涓敭鎷栨嫿锛岃繑??true 琛ㄧず浜嬩欢宸插鐞嗐€?
     * 濡傛灉涓敭鎸変笅鏃舵湭鍙戠敓鎷栨嫿涓斿彲鎷惧彇锛屽垯瑙﹀??tryPickHoveredBlockForPlacement??
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

    // ======================== 閿洏鎷栨嫿骞崇Щ ========================

    public boolean canUseKeyboardPanDrag(double mouseX, double mouseY) {
        return isKeyboardPanDragActionHeld()
                && screen.isWorldArea(mouseX, mouseY)
                && !screen.isMouseOverFloatingWindow(mouseX, mouseY)
                && !screen.isDraggingInputSensitivity()
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

    // ======================== 闀滃ご鍨傜洿鏂瑰悜 ========================

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

    // ======================== 鎸栫熆鍔ㄤ綔 ========================

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
        if (screen.isQuickBuildRangeDestroyMode() && !screen.isQuickBuildRangeDestroyChainMode()) {
            return screen.handleQuickBuildRangeDestroyClick(mouseX, mouseY);
        }
        if (!screen.isQuickBuildRangeDestroyMode() && screen.getShapeController().hasConfirmedDestroyWorkArea()) {
            return false;
        }
        if (screen.isUltimineOpen()) {
            this.controller.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(), screen.getSelectedToolSlot(),
                    screen.getUltimineLimit(), (byte) screen.getUltimineMode().ordinal());
            screen.setUltimineLastSentLimit(screen.getUltimineLimit());
        } else if (screen.isQuickBuildRangeDestroyChainMode()) {
            List<BlockPos> preview = screen.collectUltiminePreviewBlocks();
            screen.getShapeController().rememberConfirmedChainDestroyPreview(
                    preview.isEmpty() ? List.of(hit.getBlockPos().immutable()) : preview);
            this.controller.startUltimine(hit.getBlockPos(), hit.getDirection().get3DDataValue(),
                    screen.getSelectedToolSlot(), screen.getUltimineLimit(), (byte) 0);
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

    // ======================== 榧犳爣鎷惧彇鏂瑰潡鍒扮墿鍝佹??========================

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
        if (mc.player != null) {
            var inventory = mc.player.getInventory();
            RtsPickBlockPlacementSelector.Selection selection = RtsPickBlockPlacementSelector.resolve(
                    inventory.getContainerSize(),
                    slot -> {
                        ItemStack candidate = inventory.getItem(slot);
                        return !candidate.isEmpty() && candidate.getItem() == preview.getItem();
                    });
            if (selection.route() == RtsPickBlockPlacementSelector.Route.HOTBAR) {
                inventory.selected = selection.slot();
                this.controller.clearPlacementSelectionPreserveMode();
                this.controller.setMode(BuilderMode.INTERACT);
                return true;
            }
            if (selection.route() == RtsPickBlockPlacementSelector.Route.MAIN_INVENTORY
                    && mc.gameMode != null) {
                mc.gameMode.handlePickItem(selection.slot());
                this.controller.clearPlacementSelectionPreserveMode();
                this.controller.setMode(BuilderMode.INTERACT);
                return true;
            }
        }
        this.controller.selectItemForPlacement(itemId.toString(), preview.getHoverName().getString(), preview);
        return true;
    }

    // ======================== 杈撳叆鐏垫晱??========================

    public void updateInputSensitivityFromMouse(double mouseX) {
        int menuW = Math.min(300, screen.width - 24);
        int menuX = (screen.width - menuW) / 2;
        int trackX = menuX + 16;
        int trackW = menuW - 32;
        double fraction = (mouseX - trackX) / (double) trackW;
        fraction = Mth.clamp(fraction, 0.0D, 1.0D);
        this.controller.setInputSensitivityByFraction(fraction);
    }

    // ======================== Modifier 鏌ヨ??========================

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
