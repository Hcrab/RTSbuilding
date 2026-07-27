package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.RtsAiChatPanel;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.ITextComponent;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/** BuilderScreen 的组件与交互会话状态；不处理输入优先级或绘制。 */
abstract class BuilderScreenComponentState extends GuiScreen {
    ClientRtsController controller;
    GuiTextField searchBox;
    GuiTextField craftSearchBox;
    final FunnelBufferPanel funnelBufferPanel = new FunnelBufferPanel();
    final QuickBuildPanel quickBuildPanel = new QuickBuildPanel();
    final LinkedStoragePanel linkedStoragePanel = new LinkedStoragePanel();
    final BlueprintWindowPanel blueprintWindowPanel = new BlueprintWindowPanel();
    final RtsCraftQuantityWindowPanel craftQuantityWindowPanel = new RtsCraftQuantityWindowPanel();
    final BlueprintNameWindowPanel blueprintNameWindowPanel = new BlueprintNameWindowPanel();
    final BlueprintMaterialWindowPanel blueprintMaterialWindowPanel = new BlueprintMaterialWindowPanel();
    final RtsCullingManager cullingManager = RtsCullingClientState.persistentManager();
    final RtsCullingPanel cullingPanel = new RtsCullingPanel(this.cullingManager);
    final TopBarPanel topBarPanel = new TopBarPanel();
    final BottomPanel bottomPanel = new BottomPanel();
    LeftDockedTooltipRenderer leftDockedTooltipRenderer;
    final ScreenShapeController shapeController = new ScreenShapeController();
    final ScreenCursorPicker cursorPicker = new ScreenCursorPicker();
    final CameraInputHandler cameraInput = new CameraInputHandler();
    final BuilderModeWheel modeWheel = new BuilderModeWheel();
    final PlacedBlockRotationHandles rotationHandles = new PlacedBlockRotationHandles();
    final PlacementStateWheel placementStateWheel = new PlacementStateWheel();
    final GuidePanel guidePanel = new GuidePanel();
    final RtsAiChatPanel aiChatPanel = new RtsAiChatPanel();
    final GearMenuPanel gearMenuPanel = new GearMenuPanel();
    RtsScreenUiStateManager uiStateManager;
    RtsGuiScaleCoordinator guiScaleCoordinator;
    BuilderScreenPointerClickRouter pointerClickRouter;
    BuilderScreenPrimaryActionRouter primaryActionRouter;
    BuilderScreenScrollRouter scrollRouter;
    BuilderScreenKeyPressRouter keyPressRouter;
    RtsScreenOverlayRenderer overlayRenderer;
    PlayerStatusRenderer playerStatusRenderer;
    RtsFloatingWindowLayer floatingWindowLayer;
    StorageLinkDetailHandler storageLinkDetailHandler;
    final RtsWorkflowPanel workflowPanel = new RtsWorkflowPanel();
    final RtsResumePlacementPanel resumePlacementPanel = new RtsResumePlacementPanel();
    final RtsBlueprintResumePanel blueprintResumePanel = new RtsBlueprintResumePanel();
    boolean funnelHotkeyHeld = false;
    BuilderMode modeBeforeFunnelHotkey = BuilderMode.INTERACT;
    boolean funnelHotkeyTemporaryMode = false;
    int funnelMouseHoldButton = -1;
    boolean modeWheelAltWasDown = false;
    int modeWheelConsumedMouseButton = -1;
    int placementStateWheelConsumedMouseButton = -1;
    double placementWheelRestoreMouseX = Double.NaN;
    double placementWheelRestoreMouseY = Double.NaN;
    int lastMouseX = 0;
    int lastMouseY = 0;
    int pendingGuiBindSlot = -1;
    long lastCtrlRightClickTime = 0;
    static final long CTRL_DOUBLE_CLICK_THRESHOLD_MS = 300;
    int rtsFlightToggleCooldownTicks = 0;

    BuilderScreenComponentState(ITextComponent title) {
        // 1.12 的 GuiScreen 不持有标题；保留参数以稳定构造边界。
    }
}
