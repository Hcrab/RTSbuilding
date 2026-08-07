package com.rtsbuilding.rtsbuilding.uicore.routing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/** 静态锁定首批生产适配边界，避免后续维护悄悄恢复广播式输入。 */
class UiProductionAdapterContractTest {
    @Test
    void 浮动窗口层使用Core的按键独立捕获() throws IOException {
        String layer = floatingLayerSource();
        String input = floatingInputSource();
        assertTrue(layer.contains("RtsFloatingWindowInputRouter inputRouter"));
        assertTrue(input.contains("UiEventRouter<RtsWindowPanel> router"));
        assertTrue(input.contains("router.routePointer"));
    }

    @Test
    void 捕获期间拖动和释放始终阻断世界() throws IOException {
        String source = floatingInputSource();
        assertTrue(source.contains("UiPointerEvent.Type.DRAG"));
        assertTrue(source.contains("UiPointerEvent.Type.RELEASE"));
        String router = read("src/uiCore/java/com/rtsbuilding/rtsbuilding/uicore/routing/UiEventRouter.java");
        assertTrue(router.contains("pointerCapture.release(button);"));
        assertTrue(router.contains("merge(UiEventReply.BLOCK_WORLD)"));
    }

    @Test
    void z顺序不再依赖时钟粒度或先渲染一次() throws IOException {
        String window = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String input = floatingInputSource();
        assertTrue(window.contains("AtomicLong NEXT_Z_ORDER"));
        assertFalse(window.contains("System.nanoTime()"));
        assertTrue(input.contains("private void synchronizeLayers()"));
        assertTrue(input.contains("Comparator.comparingLong(RtsWindowPanel::getLastClickTime)"));
    }

    @Test
    void 浮动窗口使用Extractor分层遮住底栏物品与数量() throws IOException {
        String layer = floatingLayerSource();
        // 26.1 的 Extractor 没有 1.21 的 pose-depth 带；每个窗口用独立 matrix
        // 和 stratum 提交，仍能阻断底栏 item/text 与后续窗口之间的批次穿透。
        assertTrue(layer.contains("g.pose().pushMatrix()"));
        assertTrue(layer.contains("g.pose().popMatrix()"));
        assertTrue(layer.contains("g.nextStratum()"));
        assertFalse(layer.contains("System.nanoTime()"));
    }

    @Test
    void 隐藏窗口和切屏会清理捕获焦点与escape() throws IOException {
        String input = floatingInputSource();
        String layer = floatingLayerSource();
        String screen = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        assertTrue(input.contains("pointerCapture.releaseOwner(window)"));
        assertTrue(input.contains("keyboardFocus.clear(window)"));
        assertTrue(input.contains("void clearTransientState()"));
        assertTrue(layer.contains("clearTransientInputState"));
        assertTrue(screen.contains("this.floatingWindowLayer.clearTransientInputState();"));
    }

    @Test
    void 插件管理绘制与命中共享Kit几何和主题() throws IOException {
        String screen = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsPluginManagementScreen.java");
        assertTrue(screen.contains("resolveLayout()"));
        assertTrue(screen.contains("inventorySlotAt("));
        assertTrue(screen.contains("installedPluginAt("));
        assertTrue(screen.contains("installSelectedSlot("));
    }

    @Test
    void 顶栏按下视觉来自Core所有权而不是全局鼠标轮询() throws IOException {
        String topbar = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java");
        String screen = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        assertTrue(topbar.contains("PointerCapture<TopBarTypes.TopBarButtonId>"));
        assertTrue(topbar.contains("pointerCapture.capture(PRIMARY_MOUSE_BUTTON, button.id())"));
        assertTrue(topbar.contains("pointerCapture.ownerOf(PRIMARY_MOUSE_BUTTON) == button.id()"));
        assertFalse(topbar.contains("glfwGetMouseButton"));
        assertTrue(screen.contains("this.topBarPanel.mouseReleased(button);"));
        assertTrue(screen.contains("this.topBarPanel.clearTransientInputState();"));
    }

    @Test
    void 底栏绘制与左右键共同消费Kit网格布局() throws IOException {
        String panel = read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java")
                + read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelToolInput.java")
                + read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCraftInput.java")
                + read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelGridInput.java");
        assertTrue(panel.contains("BottomPanelGridLayout.creative("));
        assertTrue(panel.contains("BottomPanelGridLayout.storage("));
        assertTrue(panel.contains("BottomPanelGridLayout.resolve("));
        assertTrue(panel.contains("BottomPanelGridRenderer.renderStorage("));
        assertTrue(panel.contains("BottomPanelGridRenderer.renderCreative("));
        assertTrue(panel.contains(".entryIndexAt(mouseX, mouseY)"));
        assertTrue(panel.contains("BottomPanelToolLayout.standard("));
        assertTrue(panel.contains("tools.hotbarIndexAt("));
        assertTrue(panel.contains("tools.pinCellAt("));
        assertTrue(panel.contains("BottomPanelCraftLayout.resolve("));
        assertTrue(panel.contains("BottomPanelCraftRenderer.render("));
        assertTrue(panel.contains("BottomPanelCraftDockLayout.resolve("));
        assertTrue(panel.contains("BottomPanelCraftDockRenderer.render("));
        assertTrue(panel.contains("dock.slotIndexAt(mouseX, mouseY)"));
        assertTrue(panel.contains("BottomPanelCategoryLayout.resolve("));
        assertTrue(panel.contains("BottomPanelCategoryRenderer.render("));
        assertTrue(panel.contains("categoryLayout.categoryIndexAt(mouseX, mouseY)"));
        assertTrue(panel.contains("BottomPanelBrowseLayout.resolve("));
        assertTrue(panel.contains("BottomPanelBrowseRenderer.renderControls("));
        assertTrue(panel.contains("browseLayout.clearSearch.contains(mouseX, mouseY)"));
        assertTrue(panel.contains("browseLayout.previousPage.contains(mouseX, mouseY)"));
        assertTrue(panel.contains("browseLayout.nextPage.contains(mouseX, mouseY)"));
        assertTrue(panel.contains("gridInput.leftPressed("));
        assertTrue(panel.contains("gridInput.rightPressedStorage("));
        assertTrue(panel.contains("BottomPanelGridRenderer.renderFluid("));
        assertTrue(panel.contains("BottomPanelCraftRenderer.render("));
        assertFalse(panel.contains("private void drawCraftDock("));
        assertFalse(panel.contains("PanelLayouts.CraftDockLayout"));
        assertFalse(panel.contains("CRAFT_DOCK_C_SIZE"));
        assertTrue(panel.contains("BottomPanelCategoryLayout.resolve("));
        assertFalse(panel.contains("computeSearchFieldWidth("));
        assertFalse(panel.contains("getSearchClearButtonX("));
        assertFalse(panel.contains("private void drawSearchClearButton("));
        assertFalse(panel.contains("private void drawPager("));
    }

    @Test
    void categoryAndBrowseProductionHeadlessUseOneLayoutAndTheme() throws IOException {
        String categoryRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCategoryRenderer.java");
        String browseRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelBrowseRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");
        String categoryTypes = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/layout/CategoryTypes.java");

        assertTrue(categoryRenderer.contains("BottomPanelCategoryStyle.rowBackground("));
        assertTrue(categoryRenderer.contains("graphics.text(font, label, 0, 0, color, false)"));
        assertTrue(browseRenderer.contains("BottomPanelBrowseStyle.clearBackground("));
        assertTrue(browseRenderer.contains("UiCompactFrameRenderer.frame("));
        assertTrue(browseRenderer.contains("graphics.text(font, text, x, y, color, false)"));
        assertFalse(browseRenderer.contains(".hLine("));
        assertFalse(browseRenderer.contains(".vLine("));
        assertTrue(preview.contains("BottomPanelCategoryLayout.resolve("));
        assertTrue(preview.contains("BottomPanelCategoryStyle.rowBackground("));
        assertTrue(preview.contains("BottomPanelBrowseLayout.resolve("));
        assertTrue(preview.contains("BottomPanelBrowseStyle.searchBackground("));
        assertFalse(preview.contains("Math.max(56, p.searchW - 14)"));
        assertTrue(categoryTypes.contains("record CategoryClick"));
    }

    @Test
    void craftDockProductionAndHeadlessUseOneLayoutAndTheme() throws IOException {
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCraftDockRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");
        boolean legacyLayoutsExist = Files.exists(Paths.get(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/layout/PanelLayouts.java"));

        assertTrue(renderer.contains("BottomPanelCraftDockStyle.slotBackground("));
        assertTrue(renderer.contains("graphics.text(font, text, textX, textY, color, false)"));
        assertTrue(preview.contains("BottomPanelCraftDockLayout.resolve("));
        assertTrue(preview.contains("BottomPanelCraftDockStyle.slotBackground("));
        assertFalse(preview.contains("int[][] slots"));
        assertFalse(preview.contains("p.sortX + 14"));
        assertTrue(renderer.contains("BottomPanelCraftDockLayout"));
    }

    @Test
    void 生产矩形命中不再恢复闭区间助手或QuickBuild平台包装() throws IOException {
        String[] migratedSources = {
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/StorageLinkDetailHandler.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsModConfigScreen.java"
        };
        for (String path : migratedSources) {
            String source = read(path);
            assertTrue(source.contains("UiRect.contains("), path);
            assertFalse(source.contains("mouseX <= "), path);
            assertFalse(source.contains("mouseY <= "), path);
            assertFalse(source.contains("private static boolean inside("), path);
        }
        String craftTerminal = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsCraftTerminalScreen.java");
        assertTrue(craftTerminal.contains("this.layout.actionAt("));
        assertTrue(craftTerminal.contains("this.layout.storageCellAt("));
        assertFalse(craftTerminal.contains("containsRelative("));
        String builder = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String quickBuild = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        assertFalse(builder.contains("resolveQuickBuildPanelLayout("));
        assertFalse(quickBuild.contains("PanelLayouts"));
    }

    @Test
    void 生产窗口与离屏只保留同一九宫格和动画边界() throws IOException {
        String window = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsWindowPanel.java");
        String previewWindow = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");
        String previewCanvas = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/BufferedImageUiCanvas.java");
        String topBar = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/topbar/TopBarPanel.java");
        String quickBuild = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        String bottomPanel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(window.contains("UiChromeRenderer.frame("));
        assertTrue(window.contains("UiColor.interpolate("));
        assertTrue(previewWindow.contains("UiChromeRenderer.frame("));
        assertTrue(previewWindow.contains("recordNineSliceQuads(quads)"));
        assertFalse(previewCanvas.contains("void nineSlice("));
        assertFalse(previewCanvas.contains("UiNineSliceLayout"));
        assertTrue(topBar.contains("UiStateBlendAnimationSet<"));
        assertTrue(topBar.contains("SystemUiClock.INSTANCE"));
        assertTrue(quickBuild.contains("UiControlAnimationState"));
        assertTrue(quickBuild.contains("SystemUiClock.INSTANCE"));
        assertTrue(bottomPanel.contains("UiSelectionAnimationSet<"));
        assertTrue(bottomPanel.contains("SystemUiClock.INSTANCE"));
    }

    @Test
    void craftTerminal颜色与框体进入Kit主题和九宫格() throws IOException {
        String terminal = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsCraftTerminalScreen.java")
                + read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/craftterminal/"
                + "CraftTerminalRenderer.java");
        String layout = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/layout/CraftTerminalLayout.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/CraftTerminalPreviewMain.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/CraftTerminalStyle.java");

        assertTrue(terminal.contains("CraftTerminalStyle."));
        assertTrue(terminal.contains("textures/gui/ui/terminal.png"));
        assertTrue(terminal.contains("layout.skinSlices()"));
        assertTrue(terminal.contains("graphics.blit("));
        assertTrue(layout.contains("CraftTerminalUiAction actionAt("));
        assertTrue(layout.contains("int storageCellAt("));
        assertTrue(layout.contains("TextureSlice[] skinSlices()"));
        assertTrue(layout.contains("TextureSlice scrollbarHandleSlice(double fraction)"));
        assertTrue(terminal.contains("layout.scrollbarHandleSlice(scrollFraction)"));
        assertTrue(preview.contains("layout.skinSlices()"));
        assertTrue(preview.contains("layout.scrollbarHandleSlice(scrollFraction)"));
        assertTrue(preview.contains("canvas.imageRegion("));
        assertFalse(terminal.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*"));
        assertFalse(terminal.contains("RtsClientUiUtil.drawPanelFrame("));
        assertTrue(style.contains("importBackground(boolean carriedStackPresent)"));
        assertTrue(style.contains("slotBackground(boolean hovered)"));
        assertTrue(style.contains("RtsMainlineTheme.BUTTON_BORDER_LIGHT"));
    }

    @Test
    void 独立首页只消费Kit语义色板() throws IOException {
        String home = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsHomeScreen.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/StandaloneScreenStyle.java");

        assertTrue(home.contains("StandaloneScreenStyle."));
        assertFalse(home.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*"));
        assertTrue(style.contains("progressionStatus(boolean enabled)"));
        assertTrue(style.contains("homeStatus(boolean coolingDown)"));
        assertTrue(style.contains("RtsMainlineTheme.BUTTON_TEXT"));
    }

    @Test
    void 模组设置页复用独立页面主题且命中为半开区间() throws IOException {
        String config = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/RtsModConfigScreen.java");

        assertTrue(config.contains("StandaloneScreenStyle."));
        assertTrue(config.contains("UiRect.contains("));
        assertFalse(config.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*"));
        assertFalse(config.contains("mouseX <= "));
        assertFalse(config.contains("mouseY <= "));
    }

    @Test
    void 储存链接详情进入Kit主题和统一九宫格() throws IOException {
        String detail = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/StorageLinkDetailHandler.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/StorageLinkDetailStyle.java");

        assertTrue(detail.contains("StorageLinkDetailStyle."));
        assertTrue(detail.contains("UiChromeRenderer.frame("));
        assertFalse(detail.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*"));
        assertFalse(detail.contains("RtsClientUiUtil.drawPanelFrame("));
        assertTrue(style.contains("background(boolean hovered)"));
        assertTrue(style.contains("RtsMainlineTheme.WINDOW_BORDER_LIGHT"));
    }

    @Test
    void 两个模式轮盘只消费同一Kit状态色与透明度算法() throws IOException {
        String interactionTypes = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/interaction/InteractionTypes.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/ModeWheelStyle.java");

        // 26.1 已正式退役瞬态轮盘，不能让契约要求恢复一条不存在的旧输入路径。
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/mode/BuilderModeWheel.java")));
        assertFalse(Files.exists(Paths.get(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/mode/PlacementStateWheel.java")));
        assertTrue(interactionTypes.contains("interaction wheel has been retired"));
        assertTrue(style.contains("optionBorder(boolean current, double hoverProgress)"));
        assertTrue(style.contains("optionBackground(boolean current, double hoverProgress)"));
        assertTrue(style.contains("multiplyAlpha(UiColor color, double multiplier)"));
    }

    @Test
    void 顶层弹窗与伤害闪烁只消费Kit主题且弹窗使用九宫格() throws IOException {
        String overlay = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/overlay/RtsScreenOverlayRenderer.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/OverlayStyle.java");

        assertTrue(overlay.contains("renderQuestDetectPopup("));
        assertTrue(overlay.contains("renderStorageScanPopup("));
        assertTrue(overlay.contains("updateNativeCursor("));
        assertTrue(style.contains("damageFlash(double visibility)"));
        assertTrue(style.contains("questProgress(boolean error, boolean complete)"));
        assertTrue(style.contains("storageProgress(boolean running)"));
    }

    @Test
    void BuilderScreen不再拥有颜色且绑定光标复用九宫格() throws IOException {
        String builder = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String tooltip = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/TooltipStyle.java");
        String craft = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/BottomPanelCraftStyle.java");
        String dockedTooltip = builder;

        assertTrue(dockedTooltip.contains("setTooltipForNextFrame("));
        assertTrue(dockedTooltip.contains("renderLeftDockedTooltip("));
        assertTrue(builder.contains("toNativeGuiCoordinate("));
        assertTrue(builder.contains("renderLeftDockedTooltipDetail("));
        assertTrue(tooltip.contains("craftChoice(boolean craftable)"));
        assertTrue(craft.contains("SEARCH_UNEDITABLE_TEXT"));
    }

    @Test
    void 开发者任务页也只消费Kit诊断主题() throws IOException {
        String developer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/developer/RtsDeveloperTaskScreen.java");
        String style = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/theme/DeveloperScreenStyle.java");

        assertTrue(developer.contains("DeveloperScreenStyle."));
        assertFalse(developer.matches("(?s).*0x[0-9A-Fa-f]{6,8}.*"));
        assertTrue(style.contains("RtsMainlineTheme.BUTTON_TEXT"));
    }

    @Test
    void 底栏紧凑框体全部进入Kit且不再调用旧屏幕助手() throws IOException {
        String[] renderers = {
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelHeaderRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelGridRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCraftRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCraftDockRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelSortRenderer.java",
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelToolRenderer.java"
        };
        for (String path : renderers) {
            String renderer = read(path);
            assertTrue(renderer.contains("UiCompactFrameRenderer.frame("), path);
            assertTrue(renderer.contains("MinecraftUiCanvas"), path);
            assertFalse(renderer.contains("RtsClientUiUtil.drawPanelFrame("), path);
        }
    }

    @Test
    void toolRowProductionInputAndHeadlessUseOneLayoutAndTheme() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelToolRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");

        assertTrue(panel.contains("BottomPanelToolLayout.standard("));
        assertTrue(panel.contains("BottomPanelToolRenderer.render("));
        assertTrue(renderer.contains("BottomPanelToolStyle.hotbarBackground("));
        assertTrue(renderer.contains("graphics.text(font, text, textX, textY, argb(color), false)"));
        assertTrue(preview.contains("BottomPanelToolLayout.standard("));
        assertTrue(preview.contains("BottomPanelToolStyle.pinBackground("));
        assertFalse(panel.contains("private void drawEmptyHandButton("));
        assertFalse(panel.contains("countToolSlots(core"));
        assertFalse(preview.contains("p.storageX + i * 20"));
        assertFalse(preview.contains("int pinX ="));
    }

    @Test
    void sortAndHeightControlsShareLayoutThemeAndInputSemantics() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelSortRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");

        assertTrue(panel.contains("BottomPanelSortLayout.resolve("));
        assertTrue(panel.contains("BottomPanelSortRenderer.render("));
        assertTrue(panel.contains("BottomPanelSortLayout.resolve(sortX, sortY)"));
        assertTrue(panel.contains(".controlAt(mouseX, mouseY)"));
        assertTrue(renderer.contains("BottomPanelSortStyle.buttonBackground("));
        assertTrue(renderer.contains("graphics.text("));
        assertTrue(preview.contains("BottomPanelSortLayout.resolve("));
        assertTrue(preview.contains("BottomPanelSortStyle.BUTTON_BACKGROUND"));
        assertFalse(panel.contains("int heightBtnX ="));
        assertFalse(preview.contains("p.sortX + 42"));
        assertFalse(preview.contains("drawSmallButton("));
    }

    @Test
    void bottomHeaderProductionInputAndHeadlessShareOneBoundary() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelHeaderRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");

        assertTrue(panel.contains("BottomPanelHeaderLayout.resolve("));
        assertTrue(panel.contains("BottomPanelHeaderRenderer.render("));
        assertTrue(panel.contains("header.tabAt(mouseX, mouseY)"));
        assertTrue(panel.contains("header.controlAt(mouseX, mouseY)"));
        assertTrue(renderer.contains("BottomPanelHeaderStyle.tabBackground("));
        assertTrue(renderer.contains("graphics.text("));
        assertTrue(preview.contains("BottomPanelHeaderLayout.resolve("));
        assertTrue(preview.contains("BottomPanelHeaderStyle.refreshBackground("));
        assertTrue(panel.contains("header.tabAt(mouseX, mouseY)"));
        assertFalse(preview.contains("int tabX ="));
        assertFalse(preview.contains("int guideX ="));
        assertFalse(preview.contains("drawTab("));
    }

    @Test
    void bottomPanelUsesProductionInputOwnersAndKitHitGeometry() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");
        String tool = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelToolInput.java");
        String craft = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelCraftInput.java");
        String grid = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanelGridInput.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");
        assertTrue(panel.contains("BottomPanelToolInput toolInput"));
        assertTrue(panel.contains("BottomPanelGridInput gridInput"));
        assertTrue(panel.contains("BottomPanelCraftInput craftInput"));
        assertTrue(panel.contains("toolInput.mousePressed(mouseX, mouseY, 0, layout)"));
        assertTrue(panel.contains("toolInput.mousePressed(mouseX, mouseY, 1, layout)"));
        assertTrue(panel.contains("craftInput.mouseScrolled(mouseX, mouseY, scrollY, layout)"));
        assertTrue(panel.contains("BlueprintPanel.mouseScrolled("));
        assertTrue(preview.contains("BottomPanelBlueprintLayout.resolve("));
        assertTrue(tool.contains("BottomPanelToolLayout.standard("));
        assertTrue(tool.contains("tools.containsRow(mouseX, mouseY)"));
        assertTrue(craft.contains("BottomPanelCraftLayout.resolve("));
        assertTrue(grid.contains("BottomPanelGridLayout.creative("));
        assertTrue(grid.contains("BottomPanelGridLayout.storage("));
        assertTrue(panel.contains("toolInput.mousePressed("));
        assertTrue(panel.contains("craftInput.leftPressed("));
        assertTrue(panel.contains("craftInput.rightPressed("));
        assertTrue(panel.contains("gridInput.leftPressed("));
        assertFalse(panel.contains("Screen.hasShiftDown() ? 0 : 0"));
        assertTrue(panel.contains("header.controlAt(mouseX, mouseY)"));
        assertFalse(preview.contains("p.panelH - RtsMainlineLayout.BOTTOM_PANEL_HEADER_H - 8"));
    }

    @Test
    void quickBuildProductionInputAndHeadlessShareChromeThemeAndGeometry() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        String adapter = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildUiAdapter.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panel.contains("QuickBuildWindowLayout.geometry("));
        assertTrue(panel.contains("QuickBuildUiAdapter.snapshot(this)"));
        assertTrue(panel.contains("QuickBuildUiReducer.apply("));
        assertTrue(panel.contains("QuickBuildUiAdapter.apply(this, transition)"));
        assertTrue(panel.contains("QuickBuildChromeRenderer.renderMode("));
        assertTrue(panel.contains("QuickBuildChromeRenderer.renderIconGradientOverlay("));
        assertTrue(panel.contains("QuickBuildWindowLayout.CHAIN_SLIDER_H"));
        assertTrue(panel.contains("QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET"));
        assertTrue(panel.contains("mouseDragged(double mouseX, double mouseY, int button"));
        assertTrue(panel.contains("mouseReleased(double mouseX, double mouseY, int button"));
        assertTrue(adapter.contains("static QuickBuildUiState snapshot(QuickBuildPanel panel)"));
        assertTrue(adapter.contains("static boolean apply(QuickBuildPanel panel"));
        assertTrue(preview.contains("QuickBuildStyle.mode("));
        assertTrue(preview.contains("QuickBuildChromeRenderer.renderMode("));
        assertTrue(preview.contains("QuickBuildChromeRenderer.renderStatus("));
        assertTrue(preview.contains("g.chainLabelY"));
        assertTrue(preview.contains("g.chainSliderY"));
        assertTrue(preview.contains("g.chainValueX(sliderW)"));
        assertTrue(preview.contains("g.statusTextY"));
        assertTrue(preview.contains("g.missingTextX("));
        assertFalse(adapter.contains("new C2S"));
        assertFalse(adapter.contains("maxActionRadius"));
        assertFalse(preview.contains("QuickBuildWindowLayout.SECTION_TOP + 17"));
        assertFalse(preview.contains("QuickBuildWindowLayout.RIGHT_COL_X - 40"));
        assertFalse(preview.contains("g.rightX + sliderW + 6"));
        assertFalse(preview.contains("g.dividerY + 21"));
        assertFalse(preview.contains("progressW * state.progressCompleted / state.progressTotal"));
        assertFalse(preview.contains("private void drawQuickMode(BufferedImageUiCanvas canvas, int x"));
    }

    @Test
    void workflowProductionInputAndHeadlessShareChromeThemeAndGeometry() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsWorkflowPanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/WorkflowPanelRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panel.contains(
                "workflowGeometry(state).hitAt(mouseX, mouseY)"));
        assertTrue(panel.contains("WorkflowPanelRenderer.renderRow("));
        assertTrue(renderer.contains("WorkflowChromeRenderer.renderRow("));
        assertTrue(renderer.contains("WorkflowStyle.row("));
        assertTrue(preview.contains("WorkflowChromeRenderer.renderRow("));
        assertTrue(preview.contains("WorkflowStyle.row("));
        assertFalse(panel.contains("private static boolean isInside("));
        assertFalse(panel.contains("renderWorkflowRow("));
        assertFalse(panel.contains("RtsWorkflowStatus"));
        assertFalse(preview.contains("drawWorkflowButton("));
    }

    @Test
    void linkedStorageProductionInputAndHeadlessShareChromeThemeAndGeometry() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/storage/LinkedStoragePanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/storage/LinkedStoragePanelRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panel.contains("geometry.hitAt(mouseX, mouseY)"));
        assertTrue(panel.contains("LinkedStoragePanelRenderer.renderRow("));
        assertTrue(panel.contains("StorageWindowChromeRenderer.renderScrollbar("));
        assertTrue(renderer.contains("StorageWindowChromeRenderer.renderRow("));
        assertTrue(renderer.contains("StorageWindowStyle.extract("));
        assertTrue(preview.contains("StorageWindowLayout.geometry("));
        assertTrue(preview.contains("StorageWindowChromeRenderer.renderRow("));
        assertTrue(preview.contains("StorageWindowChromeRenderer.renderScrollbar("));
        assertTrue(preview.contains("StorageWindowStyle.statusText("));
        assertFalse(panel.contains("private static boolean inside("));
        assertFalse(panel.contains("private void renderScrollbar("));
        assertFalse(panel.contains("private void renderExtractToggle("));
        assertFalse(panel.contains("priorityForUpdate"));
    }

    @Test
    void workflowResumePanelsShareChromeThemeGeometryAndHalfOpenInput() throws IOException {
        String placement = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsResumePlacementPanel.java");
        String blueprint = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/RtsBlueprintResumePanel.java");
        String placementRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/PlacementResumePanelRenderer.java");
        String blueprintRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/workflow/BlueprintResumePanelRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(placement.contains("WorkflowResumeWindowLayout.placement("));
        assertTrue(placement.contains("geometry().hitAt("));
        assertTrue(blueprint.contains("WorkflowResumeWindowLayout.blueprint("));
        assertTrue(blueprint.contains("WorkflowResumeWindowLayout.scrollBlueprint("));
        assertTrue(blueprint.contains("geometry().hitAction("));
        assertTrue(placementRenderer.contains("WorkflowResumeChromeRenderer.renderPlacementAnimated("));
        assertTrue(placementRenderer.contains("WorkflowResumeStyle.action("));
        assertTrue(blueprintRenderer.contains("WorkflowResumeChromeRenderer.renderBlueprint("));
        assertTrue(blueprintRenderer.contains("WorkflowResumeStyle.action("));
        assertTrue(preview.contains("WorkflowResumeChromeRenderer.renderPlacement("));
        assertTrue(preview.contains("WorkflowResumeChromeRenderer.renderBlueprint("));
        assertFalse(placement.contains("isInsideBtn("));
        assertFalse(blueprint.contains("isInsideBtn("));
        assertFalse(placement.contains("0x"));
        assertFalse(blueprint.contains("0x"));
    }

    @Test
    void blueprintLibrarySharesChromeThemeRowsAndHalfOpenInput() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintPanel.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintLibraryPanelRenderer.java");
        String rowRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintLibraryRowRenderer.java");
        String detailsRenderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintLibraryDetailsRenderer.java");
        String input = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintLibraryPanelInput.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineTerminalRenderer.java");

        assertTrue(panel.contains("BlueprintLibraryPanelRenderer.render("));
        assertTrue(panel.contains("BlueprintLibraryPanelInput.mouseClicked("));
        assertTrue(panel.contains("BlueprintLibraryPanelInput.mouseScrolled("));
        assertTrue(renderer.contains("BlueprintLibraryChromeRenderer.renderTopBar("));
        assertTrue(renderer.contains("BlueprintLibraryRowRenderer.render("));
        assertTrue(renderer.contains("BlueprintLibraryDetailsRenderer.render("));
        assertTrue(rowRenderer.contains("BlueprintLibraryChromeRenderer.renderRow("));
        assertTrue(rowRenderer.contains("BlueprintLibraryStyle.ROW_NAME_TEXT"));
        assertTrue(detailsRenderer.contains("BlueprintLibraryChromeRenderer.renderDetailsProgress("));
        assertTrue(detailsRenderer.contains("BlueprintLibraryChromeRenderer.renderPreviewSlot("));
        assertTrue(input.contains("BlueprintLibraryLayout.hitAt("));
        assertTrue(input.contains("BlueprintLibraryLayout.scrollRows("));
        assertTrue(panel.contains("BlueprintLibraryUiAdapter.snapshot("));
        assertTrue(panel.contains("BlueprintLibraryUiAdapter.dispatch("));
        assertTrue(panel.contains("TinyFileDialogs.tinyfd_openFileDialog("));
        assertTrue(panel.contains("TinyFileDialogs.tinyfd_saveFileDialog("));
        assertTrue(panel.contains("TinyFileDialogs.tinyfd_messageBox("));
        assertTrue(panel.contains("BlueprintWriters.writeVanillaStructure("));
        assertTrue(panel.contains("Files.list(folder)"));
        assertTrue(panel.contains("BlueprintReaders.parse("));
        assertTrue(panel.contains("CAPTURE.startSave("));
        assertTrue(panel.contains("CAPTURE.pollSaveResult("));
        assertTrue(preview.contains("BlueprintLibraryChromeRenderer.renderTopBar("));
        assertTrue(preview.contains("BlueprintLibraryChromeRenderer.renderRow("));
        assertTrue(preview.contains("BlueprintLibraryStyle.ROW_NAME_TEXT"));
        assertFalse(panel.contains("private static void renderList("));
        assertFalse(panel.contains("private static void renderDetails("));
        assertFalse(panel.contains("renderCaptureLockedBottom("));
        assertFalse(preview.contains("entry.buildPercent * progressW / 100"));
        assertFalse(preview.contains("int bg = selected ? 0x"));
    }

    @Test
    void blueprintWindowIsTheOnlyPlacementAndCaptureControlSurface() throws IOException {
        String stateOwner = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintPanel.java");
        String window = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintWindowPanel.java");
        String footer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintFooterView.java");
        String screen = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");

        assertTrue(screen.contains("final BlueprintWindowPanel blueprintWindowPanel"));
        assertTrue(window.contains("BlueprintFooterView.capture("));
        assertTrue(window.contains("BlueprintFooterView.placement("));
        assertTrue(footer.contains("SAVE_CAPTURE"));
        assertTrue(footer.contains("CANCEL_CAPTURE"));
        assertTrue(footer.contains("BUILD_PREVIEW"));
        assertTrue(window.contains("RtsControlState.enabled(RtsControlRole.PRIMARY_ACTION)"));
        assertFalse(stateOwner.contains("renderPlacementHud("));
        assertFalse(stateOwner.contains("mouseClickedPlacementHud("));
        assertFalse(stateOwner.contains("renderCaptureOverlay("));
        assertFalse(stateOwner.contains("mouseClickedCaptureOverlay("));
        assertFalse(stateOwner.contains("renderNameDialog("));
        assertFalse(stateOwner.contains("mouseClickedNameDialog("));
        assertFalse(stateOwner.contains("keyPressedNameDialog("));
        assertFalse(stateOwner.contains("charTypedNameDialog("));
        assertFalse(stateOwner.contains("renderMaterialDialog("));
        assertFalse(stateOwner.contains("mouseClickedMaterialDialog("));
        assertFalse(stateOwner.contains("mouseScrolledMaterialDialog("));
        assertFalse(stateOwner.contains("keyPressedMaterialDialog("));
        assertFalse(window.contains("private void drawButtonHighlight("));
        assertFalse(window.contains("0x"));
    }

    @Test
    void windowButtonsShareExactProductionChromeAndThemeWithHeadlessReplay() throws IOException {
        String button = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/widget/WindowButton.java");
        String productionTexture = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/theme/DefaultButtonTextureRenderer.java");
        String textureCatalog = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/theme/DefaultButtonTextureCatalog.java");
        String textureLayout = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/layout/DefaultButtonTextureLayout.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");
        String previewButton = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/DefaultButtonPreviewRenderer.java");

        assertTrue(button.contains("DefaultButtonTextureRenderer.renderAnimated("));
        assertTrue(button.contains("UiControlVisualStyle.animated("));
        assertTrue(button.contains("UiControlAnimationState"));
        assertTrue(button.contains("WindowButtonStyle.MISSING_TEXTURE"));
        assertTrue(productionTexture.contains("DefaultButtonTextureLayout.slices("));
        assertTrue(textureCatalog.contains("general/default_button.png"));
        assertTrue(textureLayout.contains("SHEET_WIDTH = 4"));
        assertTrue(preview.contains("DefaultButtonPreviewRenderer.render("));
        assertTrue(previewButton.contains("DefaultButtonTextureLayout.slices("));
        assertTrue(preview.contains("UiControlVisualStyle.resolve("));
        assertFalse(button.contains("RtsClientUiUtil.drawPanelFrame("));
        assertFalse(button.contains("0xFFFF0000"));
        assertFalse(preview.contains("int fill = !enabled"));
        assertFalse(preview.contains("int light = !enabled"));
    }

    @Test
    void funnelBufferProductionAndHeadlessShareGeometryChromeThemeAndHalfOpenInput() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/funnel/FunnelBufferPanel.java");
        String layout = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/layout/FunnelBufferLayout.java");
        String chrome = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/canvas/FunnelBufferChromeRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panel.contains("FunnelBufferLayout.geometry("));
        assertTrue(panel.contains("geometry.hitAt("));
        assertTrue(panel.contains("FunnelBufferChromeRenderer.renderToggle("));
        assertTrue(panel.contains("FunnelBufferChromeRenderer.renderRow("));
        assertTrue(panel.contains("FunnelBufferStyle.COUNT_TEXT"));
        assertTrue(layout.contains("toggle.contains(mouseX, mouseY)"));
        assertTrue(layout.contains("panel.contains(mouseX, mouseY)"));
        assertTrue(chrome.contains("FunnelBufferStyle.ROW_HOVER_OVERLAY"));
        assertTrue(preview.contains("FunnelBufferLayout.geometry("));
        assertTrue(preview.contains("FunnelBufferChromeRenderer.renderPanel("));
        assertTrue(preview.contains("FunnelBufferStyle.EMPTY_TEXT"));
        assertFalse(panel.contains("private static boolean inside("));
        assertFalse(panel.contains("0x"));
        assertFalse(preview.contains("state.panelVisible ? 0xAA2C4E3D"));
    }

    @Test
    void guideProductionAndHeadlessShareGeometryChromeThemeAndHalfOpenInput() throws IOException {
        String panel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/guide/GuidePanel.java");
        String layout = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/layout/GuideWindowLayout.java");
        String chrome = read(
                "src/uiKit/java/com/rtsbuilding/rtsbuilding/uikit/canvas/GuideWindowChromeRenderer.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panel.contains("GuideWindowLayout.geometry("));
        assertTrue(panel.contains("GuideWindowLayout.hitAt("));
        assertTrue(panel.contains("GuideWindowChromeRenderer.renderTopic("));
        assertTrue(panel.contains("GuideWindowChromeRenderer.renderScrollbar("));
        assertTrue(panel.contains("GuideWindowStyle.topicContent("));
        assertTrue(layout.contains("topicScrollRoute.contains(mouseX, mouseY)"));
        assertTrue(chrome.contains("GuideWindowStyle.topicBackground(selection, hover)"));
        assertTrue(preview.contains("GuideWindowLayout.geometry("));
        assertTrue(preview.contains("GuideWindowChromeRenderer.renderTopic("));
        assertTrue(preview.contains("GuideWindowChromeRenderer.renderScrollbar("));
        assertTrue(preview.contains("GuideWindowStyle.BODY_TEXT"));
        assertTrue(preview.contains("assets.guide(texture)"));
        assertFalse(panel.contains("private static boolean inside("));
        assertFalse(panel.contains("private void drawVerticalScrollbar("));
        assertFalse(panel.contains("0x"));
        assertFalse(preview.contains("private void drawGuideScrollbar("));
        assertFalse(preview.contains("UiMainlinePreviewStyle.frame(canvas, new UiRect(tabX"));
    }

    @Test
    void sharedWindowTextBoxAndSliderUseKitGeometryChromeAndTheme() throws IOException {
        String textBox = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/widget/WindowTextBox.java");
        String slider = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/widget/WindowSlider.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(textBox.contains("WindowTextBoxLayout.geometry("));
        assertTrue(textBox.contains("WindowTextBoxChromeRenderer.render("));
        assertTrue(textBox.contains("WindowTextBoxStyle.PLACEHOLDER"));
        assertTrue(slider.contains("WindowSliderLayout.geometry("));
        assertTrue(slider.contains("WindowSliderLayout.valueAt("));
        assertTrue(slider.contains("bounds().contains(mouseX, mouseY)"));
        assertTrue(slider.contains("WindowSliderChromeRenderer.render("));
        assertTrue(preview.contains("WindowSliderChromeRenderer.render("));
        assertTrue(preview.contains("WindowSliderLayout.geometry("));
        assertFalse(textBox.contains("TEXT_PADDING_X"));
        assertFalse(textBox.contains("0x"));
        assertFalse(slider.contains("private int knobPosition("));
        assertFalse(slider.contains("0x"));
        assertFalse(preview.contains("int fill = (state.chainLimit"));
    }

    @Test
    void remainingProductionArgbIsRetiredOrResolvedAtKitThemeBoundary() throws IOException {
        String panelUi = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintPanelUi.java");
        String inspector = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintMaterialInspector.java");
        String materialState = read(
                "src/uiCore/java/com/rtsbuilding/rtsbuilding/uicore/blueprint/BlueprintMaterialUiState.java");
        String materialDialog = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintMaterialDialog.java");
        String quickBuild = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        String util = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsClientUiUtil.java");
        String preview = read(
                "src/uiPreview/java/com/rtsbuilding/rtsbuilding/uipreview/UiMainlineWindowRenderer.java");

        assertTrue(panelUi.contains("drawButton("));
        assertTrue(inspector.contains("BlueprintMaterialUiState.Tone.MISSING"));
        assertTrue(inspector.contains("BlueprintMaterialUiState.Tone.READY"));
        assertTrue(materialState.contains("public final Tone tone"));
        assertFalse(materialState.contains("public final int color"));
        assertTrue(materialDialog.contains("BlueprintDialogStyle.materialTone(line.tone())"));
        assertTrue(preview.contains("BlueprintDialogStyle.materialTone(line.tone)"));
        assertTrue(quickBuild.contains("RtsTextureRenderer.NO_TINT"));
        assertTrue(util.contains("RtsMainlineTheme.SLOT_COUNT_BACKGROUND.toArgb()"));
        assertFalse(inspector.contains("0x"));
        assertFalse(quickBuild.contains("0x"));
        assertTrue(util.contains("RtsMainlineTheme.SLOT_COUNT_BACKGROUND"));
    }

    @Test
    void slotCountOverlayDisablesDropShadow() throws IOException {
        String util = read("src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsClientUiUtil.java");
        assertTrue(util.contains(
                "text(font, countText, scaledX - textWidth, scaledY, color, false)"));
        assertFalse(util.contains(
                "text(font, countText, scaledX - textWidth, scaledY, color, true)"));
    }

    @Test
    void containerOverlaySharesKitThemeChromeAndHalfOpenInput() throws IOException {
        String gate = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java");
        String renderer = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayRenderer.java");
        String helper = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/input/overlay/OverlayLayoutHelper.java");
        String combined = gate + renderer + helper;

        assertTrue(gate.contains("OverlayLayoutHelper"));
        assertTrue(renderer.contains("OverlayLayoutHelper"));
        assertTrue(helper.contains("inside("));
        assertTrue(combined.contains("OverlayInteraction"));
    }

    @Test
    void activeCraftPopupEntrancesShareKitThemeLayoutAndChrome() throws IOException {
        String quantity = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/popup/RtsCraftQuantityDialog.java");
        String feedback = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/popup/RtsCraftFeedbackPopup.java");
        String bottom = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(quantity.contains("resolveLayout("));
        assertTrue(quantity.contains("consumePendingRequest("));
        assertTrue(quantity.contains("getSelectedOption("));
        assertTrue(feedback.contains("CraftFeedbackLayout."));
        assertTrue(feedback.contains("CraftFeedbackStyle."));
        assertTrue(feedback.contains("UiChromeRenderer.frame"));
        assertTrue(bottom.contains("RtsMainlineLayout.TOP_H + 6"));
        assertFalse(feedback.contains("RtsClientUiUtil.drawPanelFrame("));
        assertFalse(feedback.contains("0x"));
    }

    @Test
    void sharedClientUtilityKeepsLegacyFrameAtOneCompatibilityBoundary() throws IOException {
        String utility = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/util/RtsClientUiUtil.java");

        assertTrue(utility.contains("public static void drawPanelFrame("));
        assertTrue(utility.contains("RtsMainlineTheme.SLOT_COUNT_BACKGROUND"));
    }

    @Test
    void supersededPrivateUiHelpersStayRemovedFromGodFiles() throws IOException {
        String builder = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/standalone/BuilderScreen.java");
        String shape = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/handler/ScreenShapeController.java");
        String blueprint = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/blueprint/BlueprintPanel.java");
        String controller = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java");
        String quickBuild = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/quickbuild/QuickBuildPanel.java");
        String bottomPanel = read(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/BottomPanel.java");

        assertTrue(bottomPanel.contains("BottomBarUiAdapter"));
        assertTrue(shape.contains("ShapeSelectionLimiter"));
        assertFalse(blueprint.contains("private static List<BlueprintEntry> filteredEntries("));
        assertTrue(controller.contains("StorageStateManager"));
        assertTrue(quickBuild.contains("QuickBuildUiAdapter.snapshot("));
    }

    private static String floatingLayerSource() throws IOException {
        return read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsFloatingWindowLayer.java");
    }

    private static String floatingInputSource() throws IOException {
        return read("src/main/java/com/rtsbuilding/rtsbuilding/client/screen/panel/RtsFloatingWindowInputRouter.java");
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
